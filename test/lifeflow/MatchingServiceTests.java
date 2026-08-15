package lifeflow;

import java.time.LocalDate;
import java.util.ArrayList;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.service.BloodInventory;
import lifeflow.service.MatchingService;

final class MatchingServiceTests {
    private MatchingServiceTests() {
    }

    static void run() {
        selectsEmergencyThenOldestRequest();
        fulfillsOnlyWhenTheFullQuantityExists();
        leavesStateUnchangedWhenStockIsInsufficient();
    }

    private static void selectsEmergencyThenOldestRequest() {
        MatchingService service = new MatchingService(new BloodInventory());
        ArrayList<BloodRequest> requests = new ArrayList<>();
        requests.add(regular("R1", LocalDate.of(2026, 8, 1), 1));
        requests.add(emergency("R2", LocalDate.of(2026, 8, 2), 1));
        requests.add(emergency("R3", LocalDate.of(2026, 7, 31), 1));

        BloodRequest next = service.findNextPending(requests);

        assert next.getId().equals("R3");
    }

    private static void fulfillsOnlyWhenTheFullQuantityExists() {
        LocalDate today = LocalDate.of(2026, 8, 3);
        BloodInventory inventory = new BloodInventory();
        inventory.addUnit(InventoryTests.unit("U1", BloodType.O_POS,
                today.plusDays(2), UnitStatus.AVAILABLE));
        inventory.addUnit(InventoryTests.unit("U2", BloodType.O_POS,
                today.plusDays(3), UnitStatus.AVAILABLE));
        inventory.addUnit(InventoryTests.unit("U3", BloodType.A_POS,
                today.plusDays(3), UnitStatus.AVAILABLE));
        MatchingService service = new MatchingService(inventory);
        BloodRequest request = emergency("R1", today, 2);

        ArrayList<BloodUnit> matched = service.match(request, today);

        assert matched.size() == 2;
        assert matched.get(0).getStatus() == UnitStatus.USED;
        assert matched.get(1).getStatus() == UnitStatus.USED;
        assert request.getStatus() == RequestStatus.FULFILLED;
    }

    private static void leavesStateUnchangedWhenStockIsInsufficient() {
        LocalDate today = LocalDate.of(2026, 8, 3);
        BloodInventory inventory = new BloodInventory();
        BloodUnit onlyUnit = InventoryTests.unit("U1", BloodType.O_POS,
                today.plusDays(2), UnitStatus.AVAILABLE);
        inventory.addUnit(onlyUnit);
        MatchingService service = new MatchingService(inventory);
        BloodRequest request = regular("R1", today, 2);

        ArrayList<BloodUnit> matched = service.match(request, today);

        assert matched.isEmpty();
        assert onlyUnit.getStatus() == UnitStatus.AVAILABLE;
        assert request.getStatus() == RequestStatus.PENDING;
    }

    private static BloodRequest regular(String id, LocalDate date, int quantity) {
        return new RegularRequest(id, "Clinic", BloodType.O_POS, quantity,
                date, RequestStatus.PENDING);
    }

    private static BloodRequest emergency(String id, LocalDate date, int quantity) {
        return new EmergencyRequest(id, "Hospital", BloodType.O_POS, quantity,
                date, RequestStatus.PENDING);
    }
}
