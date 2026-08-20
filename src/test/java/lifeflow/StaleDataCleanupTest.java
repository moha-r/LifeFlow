package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lifeflow.model.AppointmentStatus;
import lifeflow.model.BloodType;
import lifeflow.model.Donor;
import lifeflow.model.DonationAppointment;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.service.LifeFlowController;
import org.junit.jupiter.api.Test;

final class StaleDataCleanupTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Test
    void staleBookedAppointmentsAreAutoCancelled() throws Exception {
        ArrayList<Donor> donors = new ArrayList<>(List.of(
                new Donor("D000001", "Aisha", 25, 55.0, BloodType.O_NEG, null)));
        ArrayList<DonationAppointment> appointments = new ArrayList<>(List.of(
                new DonationAppointment("A000001", "D000001", "H1",
                        TODAY.minusDays(3), null, AppointmentStatus.BOOKED),
                new DonationAppointment("A000002", "D000001", "H1",
                        TODAY.plusDays(2), null, AppointmentStatus.BOOKED)));
        LifeFlowController controller = controller(new LifeFlowState(1,
                donors, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), appointments, new ArrayList<>()));

        controller.autoDeclineStaleRequests();

        ArrayList<DonationAppointment> result =
                controller.getAppointmentsForDonor("D000001");
        assertEquals(AppointmentStatus.CANCELLED, result.get(0).getStatus());
        assertEquals(AppointmentStatus.BOOKED, result.get(1).getStatus());
        assertTrue(controller.getStateSnapshot().getLogs().stream()
                .anyMatch(log -> log.contains("Cancelled missed appointment A000001")));
    }

    @Test
    void staleAppointmentsAreNotCountedAsVolunteers() throws Exception {
        ArrayList<Donor> donors = new ArrayList<>(List.of(
                new Donor("D000001", "Aisha", 25, 55.0, BloodType.A_POS, null)));
        ArrayList<lifeflow.model.BloodRequest> requests = new ArrayList<>(List.of(
                new RegularRequest("R000001", "Clinic", BloodType.A_POS, 2,
                        TODAY.minusDays(1), RequestStatus.PENDING)));
        ArrayList<DonationAppointment> appointments = new ArrayList<>(List.of(
                new DonationAppointment("A000001", "D000001", "H1",
                        TODAY.minusDays(2), "R000001", AppointmentStatus.BOOKED),
                new DonationAppointment("A000002", "D000001", "H1",
                        TODAY.plusDays(2), "R000001", AppointmentStatus.BOOKED)));
        LifeFlowController controller = controller(new LifeFlowState(1,
                donors, new ArrayList<>(), requests,
                new ArrayList<>(), appointments, new ArrayList<>()));

        assertEquals(1, controller.getVolunteerCountForRequest("R000001"));
    }

    @Test
    void urgentNeedsExcludeRequestsPastTheirGracePeriod() throws Exception {
        ArrayList<Donor> donors = new ArrayList<>(List.of(
                new Donor("D000001", "Aisha", 25, 55.0, BloodType.A_POS, null)));
        ArrayList<lifeflow.model.BloodRequest> requests = new ArrayList<>(List.of(
                new RegularRequest("R-OLD", "Clinic", BloodType.A_POS, 1,
                        TODAY.minusDays(8), RequestStatus.PENDING),
                new RegularRequest("R-FRESH", "Clinic", BloodType.A_POS, 1,
                        TODAY.minusDays(2), RequestStatus.PENDING),
                new EmergencyRequest("E-OLD", "ER", BloodType.A_POS, 1,
                        TODAY.minusDays(3), RequestStatus.PENDING)));
        LifeFlowController controller = controller(new LifeFlowState(1,
                donors, new ArrayList<>(), requests,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));

        ArrayList<lifeflow.model.BloodRequest> needs =
                controller.getUrgentNeedsForDonor("D000001");

        assertEquals(1, needs.size());
        assertEquals("R-FRESH", needs.get(0).getId());
    }

    private static LifeFlowController controller(LifeFlowState state)
            throws Exception {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        return new LifeFlowController(state,
                new JsonLifeFlowStore(Files.createTempDirectory("lifeflow-stale-")),
                clock);
    }
}