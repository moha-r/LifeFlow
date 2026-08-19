package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.service.BloodInventory;
import lifeflow.service.MatchingService;
import org.junit.jupiter.api.Test;

/** Guards the queue semantics shared by the dashboard and matching panels. */
final class MatchingConsistencyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @Test
    void nextFulfillableMayBeLowerPriorityThanNextPending() {
        BloodInventory inventory = BloodInventory.from(List.of(
                unit("U1", BloodType.A_POS, TODAY.minusDays(3))));
        MatchingService service = new MatchingService(inventory);
        ArrayList<BloodRequest> requests = new ArrayList<>(List.of(
                new EmergencyRequest("R1", "Ward", BloodType.B_POS, 1,
                        TODAY.minusDays(2), RequestStatus.PENDING),
                new RegularRequest("R2", "Clinic", BloodType.A_POS, 1,
                        TODAY.minusDays(1), RequestStatus.PENDING)));

        assertEquals("R1", service.findNextPending(requests).getId());
        assertEquals("R2", service.findNextFulfillable(requests, TODAY).getId());
    }

    @Test
    void orderBreaksTiesByRequestDateThenId() {
        ArrayList<BloodRequest> requests = new ArrayList<>(List.of(
                new RegularRequest("R2", "Clinic", BloodType.O_POS, 1,
                        TODAY.minusDays(2), RequestStatus.PENDING),
                new RegularRequest("R1", "Clinic", BloodType.O_POS, 1,
                        TODAY.minusDays(2), RequestStatus.PENDING),
                new RegularRequest("R3", "Clinic", BloodType.O_POS, 1,
                        TODAY.minusDays(3), RequestStatus.PENDING)));

        assertEquals(List.of("R3", "R1", "R2"),
                requests.stream().sorted(MatchingService.ORDER)
                        .map(BloodRequest::getId).toList());
    }

    private static BloodUnit unit(String id, BloodType type, LocalDate donation) {
        return new BloodUnit(id, "D1", type, donation,
                donation.plusDays(35), UnitStatus.AVAILABLE);
    }
}