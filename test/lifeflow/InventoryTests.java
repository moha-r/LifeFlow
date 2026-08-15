package lifeflow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.UnitStatus;
import lifeflow.service.BloodInventory;

final class InventoryTests {
    private InventoryTests() {
    }

    static void run() {
        filtersUnavailableAndDifferentBloodTypes();
        countsAvailableStockByBloodType();
    }

    private static void filtersUnavailableAndDifferentBloodTypes() {
        LocalDate today = LocalDate.of(2026, 8, 3);
        BloodInventory inventory = new BloodInventory();
        inventory.addUnit(unit("U1", BloodType.A_POS, today.plusDays(5), UnitStatus.AVAILABLE));
        inventory.addUnit(unit("U2", BloodType.A_POS, today.minusDays(1), UnitStatus.AVAILABLE));
        inventory.addUnit(unit("U3", BloodType.A_POS, today.plusDays(5), UnitStatus.USED));
        inventory.addUnit(unit("U4", BloodType.O_POS, today.plusDays(5), UnitStatus.AVAILABLE));

        ArrayList<BloodUnit> available = inventory.getAvailableUnits(BloodType.A_POS, today);

        assert available.size() == 1;
        assert available.get(0).getId().equals("U1");
        assert inventory.containsId("U4");
        assert !inventory.containsId("missing");
    }

    private static void countsAvailableStockByBloodType() {
        LocalDate today = LocalDate.of(2026, 8, 3);
        BloodInventory inventory = new BloodInventory();
        inventory.addUnit(unit("U1", BloodType.B_POS, today, UnitStatus.AVAILABLE));
        inventory.addUnit(unit("U2", BloodType.B_POS, today.plusDays(1), UnitStatus.AVAILABLE));
        inventory.addUnit(unit("U3", BloodType.B_POS, today.plusDays(1), UnitStatus.USED));

        HashMap<BloodType, Integer> counts = inventory.getStockCounts(today);

        assert counts.get(BloodType.B_POS) == 2;
        assert counts.get(BloodType.A_NEG) == 0;
        assert inventory.getUnits().size() == 3;
    }

    static BloodUnit unit(String id, BloodType type, LocalDate expiry,
                          UnitStatus status) {
        return new BloodUnit(id, "D1", type, LocalDate.of(2026, 7, 1),
                expiry, status);
    }
}
