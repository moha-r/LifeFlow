package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import lifeflow.model.AppointmentStatus;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.DonationAppointment;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.model.exception.EligibilityException;
import lifeflow.model.exception.EntityNotFoundException;
import lifeflow.model.exception.ImmutableRecordException;
import lifeflow.model.exception.ValidationException;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.service.LifeFlowController;
import org.junit.jupiter.api.Test;

final class AppointmentBookingTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Test
    void bookCreatesAppointmentWithSequentialIds() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            DonationAppointment first = harness.controller().bookDonationAppointment(
                    "D1", "H1", TODAY.plusDays(3), null);
            DonationAppointment second = harness.controller().bookDonationAppointment(
                    "D2", "H1", TODAY.plusDays(5), null);

            assertEquals("A000001", first.getId());
            assertEquals("A000002", second.getId());
            assertEquals(AppointmentStatus.BOOKED, first.getStatus());
            assertEquals("H1", first.getHospitalId());
            assertEquals(TODAY.plusDays(3), first.getAppointmentDate());
            assertEquals(1, harness.controller().getAppointmentsForDonor("D1").size());
            assertEquals(1, harness.controller().getAppointmentsForDonor("D2").size());
        }
    }

    @Test
    void pastDateIsRejected() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            assertThrows(ValidationException.class, () ->
                    harness.controller().bookDonationAppointment("D1", "H1",
                            TODAY.minusDays(1), null));
        }
    }

    @Test
    void doubleBookingIsRejected() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            harness.controller().bookDonationAppointment("D1", "H1",
                    TODAY.plusDays(3), null);
            assertThrows(ValidationException.class, () ->
                    harness.controller().bookDonationAppointment("D1", "H1",
                            TODAY.plusDays(9), null));
        }
    }

    @Test
    void ineligibleDonorCannotBook() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            assertThrows(EligibilityException.class, () ->
                    harness.controller().bookDonationAppointment("D3", "H1",
                            TODAY.plusDays(3), null));
        }
    }

    @Test
    void deferralEndsByAppointmentDateSoBookingSucceeds() throws Exception {
        try (Harness harness = new Harness(setup ->
                setup.donors.add(new Donor("D4", "Nora Ali", 30, 60,
                        BloodType.A_POS, TODAY.minusMonths(2))))) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D4", "H1",
                            TODAY.plusMonths(2), null);
            assertEquals("A000001", appointment.getId());
        }
    }

    @Test
    void volunteerRejectsIncompatibleRequest() throws Exception {
        try (Harness harness = new Harness(setup ->
                setup.requests.add(new RegularRequest("R1",
                        "City Hospital", BloodType.O_NEG, 1, TODAY,
                        RequestStatus.PENDING)))) {
            assertThrows(ValidationException.class, () ->
                    harness.controller().bookDonationAppointment("D1", "H1",
                            TODAY.plusDays(2), "R1"));
        }
    }

    @Test
    void volunteerLinksCompatibleRequest() throws Exception {
        try (Harness harness = new Harness(setup ->
                setup.requests.add(new RegularRequest("R1",
                        "City Hospital", BloodType.O_POS, 1, TODAY,
                        RequestStatus.PENDING)))) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1",
                            TODAY.plusDays(2), "R1");
            assertEquals("R1", appointment.getLinkedRequestId());
        }
    }

    @Test
    void volunteerRequiresPendingRequest() throws Exception {
        try (Harness harness = new Harness(setup ->
                setup.requests.add(new RegularRequest("R1",
                        "City Hospital", BloodType.O_POS, 1, TODAY,
                        RequestStatus.CANCELLED)))) {
            assertThrows(ValidationException.class, () ->
                    harness.controller().bookDonationAppointment("D1", "H1",
                            TODAY.plusDays(2), "R1"));
        }
    }

    @Test
    void missingDonorCannotBook() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            assertThrows(EntityNotFoundException.class, () ->
                    harness.controller().bookDonationAppointment("MISSING",
                            "H1", TODAY.plusDays(2), null));
        }
    }

    @Test
    void cancelIsRestrictedToOwnerAndBookedState() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY.plusDays(2), null);
            assertThrows(ValidationException.class, () ->
                    harness.controller().cancelDonationAppointment(
                            appointment.getId(), "D2"));
            assertThrows(EntityNotFoundException.class, () ->
                    harness.controller().cancelDonationAppointment(
                            "NOT-FOUND", "D1"));

            harness.controller().cancelDonationAppointment(
                    appointment.getId(), "D1");
            assertEquals(AppointmentStatus.CANCELLED,
                    harness.controller().getAppointmentsForDonor("D1").get(0)
                            .getStatus());
            assertThrows(ImmutableRecordException.class, () ->
                    harness.controller().cancelDonationAppointment(
                            appointment.getId(), "D1"));
        }
    }

    @Test
    void completeRecordsUnitAndMarksCompleted() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY, null);

            harness.controller().completeDonationAppointment(
                    appointment.getId(), "H1", TODAY);

            DonationAppointment updated = harness.controller()
                    .getAppointmentsForDonor("D1").get(0);
            assertEquals(AppointmentStatus.COMPLETED, updated.getStatus());
            BloodUnit unit = harness.controller().getUnits().get(0);
            assertEquals("U000001", unit.getId());
            assertEquals("D1", unit.getDonorId());
            assertEquals(UnitStatus.AVAILABLE, unit.getStatus());
            assertTrue(harness.controller().getStateSnapshot().getLogs().get(0)
                    .contains("unit U000001"));
        }
    }

    @Test
    void completeIsRestrictedToOwningHospital() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY, null);
            assertThrows(ValidationException.class, () ->
                    harness.controller().completeDonationAppointment(
                            appointment.getId(), "H2", TODAY));
        }
    }

    @Test
    void completeBeforeAppointmentDateIsRejected() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY.plusDays(2), null);
            assertThrows(ValidationException.class, () ->
                    harness.controller().completeDonationAppointment(
                            appointment.getId(), "H1", TODAY));
        }
    }

    @Test
    void completeWhileDeferredKeepsAppointmentBooked() throws Exception {
        try (Harness harness = new Harness(setup -> {
            setup.donors.add(new Donor("D5", "Deferred Donor", 28, 65,
                    BloodType.B_POS, TODAY.minusDays(30)));
            setup.appointments.add(new DonationAppointment("A000001",
                    "D5", "H1", TODAY, null, AppointmentStatus.BOOKED));
        })) {
            assertThrows(EligibilityException.class, () ->
                    harness.controller().completeDonationAppointment(
                            "A000001", "H1", TODAY));
            assertEquals(AppointmentStatus.BOOKED,
                    harness.controller().getAppointmentsForDonor("D5").get(0)
                            .getStatus());
            assertEquals(0, harness.controller().getUnits().size());
        }
    }

    @Test
    void staleAppointmentAllowsRebooking() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            DonationAppointment stale = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY, null);
            assertTrue(stale.isStale(TODAY.plusDays(1)));
            assertFalse(stale.isStale(TODAY));

            harness.controller().cancelDonationAppointment(stale.getId(), "D1");
            DonationAppointment fresh = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY.plusDays(3), null);
            assertNotNull(fresh);
        }
    }

    @Test
    void urgentNeedsExcludeOwnVolunteers() throws Exception {
        try (Harness harness = new Harness(setup ->
                setup.requests.add(new RegularRequest("R1",
                        "City Hospital", BloodType.O_POS, 1, TODAY,
                        RequestStatus.PENDING)))) {
            assertEquals(1, harness.controller()
                    .getUrgentNeedsForDonor("D1").size());

            harness.controller().bookDonationAppointment("D1", "H1",
                    TODAY.plusDays(2), "R1");
            assertEquals(0, harness.controller()
                    .getUrgentNeedsForDonor("D1").size());
            assertEquals(1, harness.controller()
                    .getVolunteerCountForRequest("R1"));
        }
    }

    @Test
    void decliningRequestCancelsLinkedVolunteerAppointments() throws Exception {
        try (Harness harness = new Harness(setup ->
                setup.requests.add(new RegularRequest("R1",
                        "City Hospital", BloodType.O_POS, 1, TODAY,
                        RequestStatus.PENDING)))) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1",
                            TODAY.plusDays(2), "R1");
            assertEquals(1, harness.controller()
                    .getVolunteerCountForRequest("R1"));

            harness.controller().declineRequest("R1", "closed");

            assertEquals(AppointmentStatus.CANCELLED,
                    harness.controller().getAppointmentsForDonor("D1")
                            .get(0).getStatus());
            assertEquals(0, harness.controller()
                    .getVolunteerCountForRequest("R1"));
            assertEquals(appointment.getId(), harness.controller()
                    .getAppointmentsForDonor("D1").get(0).getId());
        }
    }

    @Test
    void fulfillingRequestCancelsLinkedVolunteerAppointments() throws Exception {
        try (Harness harness = new Harness(setup -> {
            setup.donors.add(new Donor("D5", "Volunteer Donor", 28, 65,
                    BloodType.O_POS, null));
            setup.requests.add(new RegularRequest("R1",
                    "City Hospital", BloodType.O_POS, 1, TODAY,
                    RequestStatus.PENDING));
        })) {
            harness.controller().addBloodUnit("U000001", "D1", TODAY);
            harness.controller().bookDonationAppointment("D5", "H1",
                    TODAY.plusDays(2), "R1");
            assertEquals(1, harness.controller()
                    .getVolunteerCountForRequest("R1"));

            harness.controller().processSpecificRequest("R1", TODAY,
                    lifeflow.model.MatchMode.EXACT);

            assertEquals(AppointmentStatus.CANCELLED,
                    harness.controller().getAppointmentsForDonor("D5")
                            .get(0).getStatus());
            assertEquals(0, harness.controller()
                    .getVolunteerCountForRequest("R1"));
        }
    }

    @Test
    void autoDecliningStaleRequestsCancelsLinkedAppointments()
            throws Exception {
        try (Harness harness = new Harness(setup ->
                setup.requests.add(new RegularRequest("R1",
                        "City Hospital", BloodType.O_POS, 1,
                        TODAY.minusDays(30), RequestStatus.PENDING)))) {
            harness.controller().bookDonationAppointment("D1", "H1",
                    TODAY.plusDays(2), "R1");

            harness.controller().autoDeclineStaleRequests();

            assertEquals(AppointmentStatus.CANCELLED,
                    harness.controller().getAppointmentsForDonor("D1")
                            .get(0).getStatus());
        }
    }

    @Test
    void bloodTypeCannotChangeWhileAppointmentIsActive() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            harness.controller().bookDonationAppointment("D1", "H1",
                    TODAY.plusDays(2), null);
            assertThrows(ImmutableRecordException.class, () ->
                    harness.controller().updateDonor("D1", "Sara Ali", 25, 62,
                            BloodType.B_POS, null));
        }
    }

    @Test
    void stateSurvivesStoreRoundTrip() throws Exception {
        try (Harness harness = new Harness(setup -> {
        })) {
            harness.controller().bookDonationAppointment("D1", "H1",
                    TODAY.plusDays(3), null);
            harness.controller().close();
            JsonLifeFlowStore reopened = new JsonLifeFlowStore(harness.dir());
            try {
                LifeFlowState reloaded = reopened.load();
                assertEquals(1, reloaded.getAppointments().size());
                DonationAppointment appointment =
                        reloaded.getAppointments().get(0);
                assertEquals("A000001", appointment.getId());
                assertEquals(TODAY.plusDays(3),
                        appointment.getAppointmentDate());
                assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
            } finally {
                reopened.close();
            }
        }
    }

    @Test
    void volunteerDonationFulfilsSingleUnitRequestImmediately() throws Exception {
        try (Harness harness = new Harness(setup ->
                setup.requests.add(new RegularRequest("R1",
                        "City Hospital", BloodType.O_POS, 1, TODAY,
                        RequestStatus.PENDING)))) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY, "R1");
            harness.controller().completeDonationAppointment(
                    appointment.getId(), "H1", TODAY);

            BloodRequest request = harness.controller().getRequests().get(0);
            assertEquals(RequestStatus.FULFILLED, request.getStatus());
            BloodUnit unit = harness.controller().getUnits().get(0);
            assertEquals(UnitStatus.USED, unit.getStatus());
            assertEquals(1, harness.controller().getFulfilments().size());
            assertEquals("U000001",
                    harness.controller().getFulfilments().get(0).unitIds().get(0));
        }
    }

    @Test
    void volunteerDonationsReserveUnitsUntilRequestIsComplete()
            throws Exception {
        try (Harness harness = new Harness(setup -> {
            setup.donors.add(new Donor("D5", "Volunteer Donor", 28, 65,
                    BloodType.O_POS, null));
            setup.requests.add(new RegularRequest("R1",
                    "City Hospital", BloodType.O_POS, 2, TODAY,
                    RequestStatus.PENDING));
        })) {
            DonationAppointment first = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY, "R1");
            harness.controller().completeDonationAppointment(
                    first.getId(), "H1", TODAY);

            assertEquals(RequestStatus.PENDING,
                    harness.controller().getRequests().get(0).getStatus());
            BloodUnit reserved = harness.controller().getUnits().get(0);
            assertEquals(UnitStatus.RESERVED, reserved.getStatus());
            assertEquals(1, harness.controller().getFulfilments().get(0)
                    .unitIds().size());

            DonationAppointment second = harness.controller()
                    .bookDonationAppointment("D5", "H1", TODAY, "R1");
            harness.controller().completeDonationAppointment(
                    second.getId(), "H1", TODAY);

            assertEquals(RequestStatus.FULFILLED,
                    harness.controller().getRequests().get(0).getStatus());
            assertEquals(2, harness.controller().getUnits().size());
            assertTrue(harness.controller().getUnits().stream()
                    .allMatch(unit -> unit.getStatus() == UnitStatus.USED));
            assertEquals(2, harness.controller().getFulfilments().get(0)
                    .unitIds().size());
        }
    }

    @Test
    void reservedUnitsAreNotMatchedForOtherRequests() throws Exception {
        try (Harness harness = new Harness(setup -> {
            setup.requests.add(new RegularRequest("R1",
                    "City Hospital", BloodType.O_POS, 2, TODAY,
                    RequestStatus.PENDING));
            setup.requests.add(new RegularRequest("R2",
                    "City Hospital", BloodType.O_POS, 1, TODAY,
                    RequestStatus.PENDING));
        })) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY, "R1");
            harness.controller().completeDonationAppointment(
                    appointment.getId(), "H1", TODAY);
            assertEquals(UnitStatus.RESERVED,
                    harness.controller().getUnits().get(0).getStatus());

            assertThrows(lifeflow.model.exception.InsufficientStockException.class,
                    () -> harness.controller().processSpecificRequest(
                            "R2", TODAY, lifeflow.model.MatchMode.EXACT));

            assertEquals(RequestStatus.PENDING,
                    harness.controller().getRequests().get(1).getStatus());
            assertEquals(UnitStatus.RESERVED,
                    harness.controller().getUnits().get(0).getStatus());
            assertEquals(RequestStatus.PENDING,
                    harness.controller().getRequests().get(0).getStatus());
        }
    }

    @Test
    void matchingCompletesPartiallyReservedRequest() throws Exception {
        try (Harness harness = new Harness(setup -> {
            setup.donors.add(new Donor("D6", "Extra Donor", 27, 70,
                    BloodType.O_POS, null));
            setup.requests.add(new RegularRequest("R1",
                    "City Hospital", BloodType.O_POS, 2, TODAY,
                    RequestStatus.PENDING));
        })) {
            harness.controller().addBloodUnit("U000001", "D6", TODAY);
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY, "R1");
            harness.controller().completeDonationAppointment(
                    appointment.getId(), "H1", TODAY);
            assertEquals(UnitStatus.RESERVED,
                    harness.controller().getUnits().get(1).getStatus());

            lifeflow.model.MatchResult result = harness.controller()
                    .processNextRequest(TODAY);
            assertEquals(lifeflow.model.MatchOutcome.FULFILLED,
                    result.outcome());

            assertEquals(RequestStatus.FULFILLED,
                    harness.controller().getRequests().get(0).getStatus());
            assertEquals(2, harness.controller().getUnits().size());
            assertTrue(harness.controller().getUnits().stream()
                    .allMatch(unit -> unit.getStatus() == UnitStatus.USED));
            assertEquals(2, harness.controller().getFulfilments().get(0)
                    .unitIds().size());
        }
    }

    @Test
    void decliningRequestReleasesReservedUnitsBackToStock() throws Exception {
        try (Harness harness = new Harness(setup ->
                setup.requests.add(new RegularRequest("R1",
                        "City Hospital", BloodType.O_POS, 2, TODAY,
                        RequestStatus.PENDING)))) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY, "R1");
            harness.controller().completeDonationAppointment(
                    appointment.getId(), "H1", TODAY);
            assertEquals(UnitStatus.RESERVED,
                    harness.controller().getUnits().get(0).getStatus());

            harness.controller().declineRequest("R1", "no longer needed");

            assertEquals(UnitStatus.AVAILABLE,
                    harness.controller().getUnits().get(0).getStatus());
            assertEquals(0, harness.controller().getFulfilments().size());
            assertEquals(RequestStatus.CANCELLED,
                    harness.controller().getRequests().get(0).getStatus());
        }
    }

    @Test
    void autoDeclineSkipsRequestsWithCommittedUnits() throws Exception {
        try (Harness harness = new Harness(setup -> {
            setup.requests.add(new RegularRequest("R1",
                    "City Hospital", BloodType.O_POS, 2,
                    TODAY.minusDays(30), RequestStatus.PENDING));
            setup.requests.add(new RegularRequest("R2",
                    "City Hospital", BloodType.O_POS, 1,
                    TODAY.minusDays(30), RequestStatus.PENDING));
        })) {
            DonationAppointment appointment = harness.controller()
                    .bookDonationAppointment("D1", "H1", TODAY, "R1");
            harness.controller().completeDonationAppointment(
                    appointment.getId(), "H1", TODAY);
            assertEquals(UnitStatus.RESERVED,
                    harness.controller().getUnits().get(0).getStatus());

            harness.controller().autoDeclineStaleRequests();

            assertEquals(RequestStatus.PENDING,
                    harness.controller().getRequests().get(0).getStatus());
            assertEquals(UnitStatus.RESERVED,
                    harness.controller().getUnits().get(0).getStatus());
            assertEquals(RequestStatus.CANCELLED,
                    harness.controller().getRequests().get(1).getStatus());
        }
    }

    private static final class Harness implements AutoCloseable {
        private static final class Setup {
            private final ArrayList<Donor> donors = new ArrayList<>();
            private final ArrayList<BloodRequest> requests = new ArrayList<>();
            private final ArrayList<DonationAppointment> appointments =
                    new ArrayList<>();
        }

        private final java.nio.file.Path dir;
        private final JsonLifeFlowStore store;
        private final LifeFlowController controller;

        private Harness(java.util.function.Consumer<Setup> setup)
                throws Exception {
            dir = Files.createTempDirectory("lifeflow-appointments-");
            store = new JsonLifeFlowStore(dir);
            Setup prepared = new Setup();
            prepared.donors.add(new Donor("D1", "Sara Ali", 25, 62,
                    BloodType.O_POS, null));
            prepared.donors.add(new Donor("D2", "Omar Khan", 30, 80,
                    BloodType.B_POS, null));
            prepared.donors.add(new Donor("D3", "Old Man", 75, 60,
                    BloodType.AB_POS, null));
            if (setup != null) {
                setup.accept(prepared);
            }
            LifeFlowState state = new LifeFlowState(1, prepared.donors,
                    new ArrayList<>(), prepared.requests, new ArrayList<>(),
                    prepared.appointments, new ArrayList<>());
            controller = new LifeFlowController(state, store,
                    Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE));
        }

        private LifeFlowController controller() {
            return controller;
        }

        private java.nio.file.Path dir() {
            return dir;
        }

        @Override
        public void close() throws Exception {
            try {
                controller.close();
            } catch (java.io.IOException alreadyClosed) {
                // The round-trip test closes the store explicitly first.
            }
        }
    }
}