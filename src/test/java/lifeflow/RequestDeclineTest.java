package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.model.exception.ImmutableRecordException;
import lifeflow.model.exception.ValidationException;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.service.LifeFlowController;
import org.junit.jupiter.api.Test;

final class RequestDeclineTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Test
    void declinePendingRequestCancelsItAndLogsTheReason() throws Exception {
        LifeFlowController controller = controller();
        controller.addRequest("R000001", "Clinic", BloodType.A_POS, 2, false);

        controller.declineRequest("R000001", "Patient discharged");

        assertEquals(RequestStatus.CANCELLED, controller.getRequests().get(0).getStatus());
        assertEquals(0, controller.getPendingRequestCount());
        assertTrue(controller.getStateSnapshot().getLogs().stream()
                .anyMatch(log -> log.contains("Declined request R000001")
                        && log.contains("Patient discharged")));
    }

    @Test
    void fulfilledRequestCannotBeDeclined() throws Exception {
        LifeFlowController controller = controller();
        controller.addDonor("D000001", "Aisha", 25, 55.0, BloodType.O_NEG, null);
        controller.addBloodUnit("U000001", "D000001", TODAY);
        controller.addRequest("R000001", "Clinic", BloodType.O_NEG, 1, false);
        controller.processNextRequest(TODAY);

        assertThrows(ImmutableRecordException.class,
                () -> controller.declineRequest("R000001", "Too late"));
    }

    @Test
    void cancelledRequestCannotBeDeclinedTwice() throws Exception {
        LifeFlowController controller = controller();
        controller.addRequest("R000001", "Clinic", BloodType.A_POS, 1, false);
        controller.declineRequest("R000001", "No longer needed");

        assertThrows(ImmutableRecordException.class,
                () -> controller.declineRequest("R000001", "Again"));
    }

    @Test
    void declineReasonIsRequired() throws Exception {
        LifeFlowController controller = controller();
        controller.addRequest("R000001", "Clinic", BloodType.A_POS, 1, false);

        assertThrows(ValidationException.class,
                () -> controller.declineRequest("R000001", "  "));
        assertEquals(RequestStatus.PENDING, controller.getRequests().get(0).getStatus());
    }

    @Test
    void staleRequestsAreAutoCancelledWithinTheirGracePeriod() throws Exception {
        ArrayList<BloodRequest> requests = new ArrayList<>(List.of(
                new RegularRequest("R-OLD", "Clinic", BloodType.A_POS, 1,
                        TODAY.minusDays(8), RequestStatus.PENDING),
                new RegularRequest("R-FRESH", "Clinic", BloodType.A_POS, 1,
                        TODAY.minusDays(5), RequestStatus.PENDING),
                new EmergencyRequest("E-OLD", "ER", BloodType.O_NEG, 1,
                        TODAY.minusDays(3), RequestStatus.PENDING),
                new EmergencyRequest("E-FRESH", "ER", BloodType.O_NEG, 1,
                        TODAY.minusDays(1), RequestStatus.PENDING)));
        LifeFlowController controller = controller(new LifeFlowState(1,
                new ArrayList<>(), new ArrayList<>(),
                requests, new ArrayList<>(),
                new ArrayList<>()));

        controller.autoDeclineStaleRequests();

        assertEquals(RequestStatus.CANCELLED, controller.getRequests().get(0).getStatus());
        assertEquals(RequestStatus.PENDING, controller.getRequests().get(1).getStatus());
        assertEquals(RequestStatus.CANCELLED, controller.getRequests().get(2).getStatus());
        assertEquals(RequestStatus.PENDING, controller.getRequests().get(3).getStatus());
        assertEquals(2, controller.getPendingRequestCount());
        assertTrue(controller.getStateSnapshot().getLogs().stream()
                .anyMatch(log -> log.contains("Auto-cancelled request R-OLD")
                        && log.contains("7 days")));
        assertTrue(controller.getStateSnapshot().getLogs().stream()
                .anyMatch(log -> log.contains("Auto-cancelled request E-OLD")
                        && log.contains("2 days")));
    }

    @Test
    void autoCancelSkipsRequestsWithNoPendingStatus() throws Exception {
        ArrayList<BloodRequest> requests = new ArrayList<>(List.of(
                new RegularRequest("R-FULFILLED", "Clinic", BloodType.A_POS, 1,
                        TODAY.minusDays(30), RequestStatus.FULFILLED),
                new RegularRequest("R-CANCELLED", "Clinic", BloodType.A_POS, 1,
                        TODAY.minusDays(30), RequestStatus.CANCELLED)));
        ArrayList<FulfilmentRecord> fulfilments = new ArrayList<>(List.of(
                new FulfilmentRecord("R-FULFILLED", TODAY.minusDays(29),
                        List.of("U000001"))));
        ArrayList<BloodUnit> units = new ArrayList<>(List.of(
                new BloodUnit("U000001", "D000001", BloodType.A_POS,
                        TODAY.minusDays(40), TODAY.minusDays(10),
                        UnitStatus.USED)));
        ArrayList<Donor> donors = new ArrayList<>(List.of(
                new Donor("D000001", "Aisha", 25, 55.0, BloodType.A_POS, null)));
        LifeFlowController controller = controller(new LifeFlowState(1,
                donors, units,
                new ArrayList<>(requests), new ArrayList<>(fulfilments),
                new ArrayList<>()));

        controller.autoDeclineStaleRequests();

        assertFalse(controller.getStateSnapshot().getLogs().stream()
                .anyMatch(log -> log.contains("Auto-cancelled")));
        assertEquals(RequestStatus.FULFILLED, controller.getRequests().get(0).getStatus());
        assertEquals(RequestStatus.CANCELLED, controller.getRequests().get(1).getStatus());
    }

    @Test
    void cancelledRequestCannotBeEdited() throws Exception {
        LifeFlowController controller = controller();
        controller.addRequest("R000001", "Clinic", BloodType.A_POS, 2, false);
        controller.declineRequest("R000001", "No longer needed");

        assertThrows(ImmutableRecordException.class,
                () -> controller.updatePendingRequest("R000001", "Clinic",
                        BloodType.O_NEG, 9));
        assertEquals(RequestStatus.CANCELLED,
                controller.getRequests().get(0).getStatus());
        assertEquals(2, controller.getRequests().get(0).getQuantity());
        assertEquals(BloodType.A_POS,
                controller.getRequests().get(0).getBloodType());
    }

    @Test
    void cancelledRequestCannotBeProcessed() throws Exception {
        LifeFlowController controller = controller();
        controller.addDonor("D000001", "Aisha", 25, 55.0, BloodType.O_NEG, null);
        controller.addBloodUnit("U000001", "D000001", TODAY);
        controller.addRequest("R000001", "Clinic", BloodType.O_NEG, 1, false);
        controller.declineRequest("R000001", "No longer needed");

        assertThrows(ImmutableRecordException.class,
                () -> controller.processSpecificRequest("R000001", TODAY,
                        lifeflow.model.MatchMode.EXACT));
        assertEquals(RequestStatus.CANCELLED,
                controller.getRequests().get(0).getStatus());
        assertTrue(controller.getFulfilments().isEmpty());
    }

    @Test
    void partiallyCoveredRequestGuardsUnsafeEdits() throws Exception {
        ArrayList<Donor> donors = new ArrayList<>(List.of(
                new Donor("D000001", "Aisha", 25, 55.0, BloodType.O_POS, null)));
        ArrayList<BloodUnit> units = new ArrayList<>(List.of(
                new BloodUnit("U000001", "D000001", BloodType.O_POS,
                        TODAY.minusDays(5), TODAY.plusDays(30),
                        UnitStatus.RESERVED)));
        ArrayList<BloodRequest> requests = new ArrayList<>(List.of(
                new RegularRequest("R000001", "Clinic", BloodType.A_POS, 2,
                        TODAY.minusDays(3), RequestStatus.PENDING)));
        ArrayList<FulfilmentRecord> fulfilments = new ArrayList<>(List.of(
                new FulfilmentRecord("R000001", TODAY, List.of("U000001"))));
        LifeFlowController controller = controller(new LifeFlowState(1,
                donors, units, requests, fulfilments, new ArrayList<>()));

        assertThrows(ValidationException.class,
                () -> controller.updatePendingRequest("R000001", "Clinic",
                        BloodType.A_POS, 1));
        assertThrows(ValidationException.class,
                () -> controller.updatePendingRequest("R000001", "Clinic",
                        BloodType.A_NEG, 3));

        controller.updatePendingRequest("R000001", "Clinic", BloodType.B_POS, 3);

        assertEquals(3, controller.getRequests().get(0).getQuantity());
        assertEquals(BloodType.B_POS,
                controller.getRequests().get(0).getBloodType());
        assertEquals(RequestStatus.PENDING,
                controller.getRequests().get(0).getStatus());
    }

    @Test
    void discardedUnitCannotBeCorrected() throws Exception {
        LifeFlowController controller = controller();
        controller.addDonor("D000001", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addBloodUnit("U000001", "D000001", TODAY.minusDays(10));
        controller.discardBloodUnit("U000001");

        assertThrows(ImmutableRecordException.class,
                () -> controller.updateUnusedBloodUnitDonationDate(
                        "U000001", TODAY.minusDays(9)));
        assertEquals(UnitStatus.DISCARDED,
                controller.getUnits().get(0).getStatus());
    }

    private static LifeFlowController controller() throws Exception {
        return controller(new LifeFlowState());
    }

    private static LifeFlowController controller(LifeFlowState state) throws Exception {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        return new LifeFlowController(state,
                new JsonLifeFlowStore(Files.createTempDirectory("lifeflow-decline-")),
                clock);
    }
}