package lifeflow.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;

/** Owns the in-memory collection of blood units. */
public class BloodInventory {
    private final ArrayList<BloodUnit> units = new ArrayList<>();

    public static BloodInventory from(List<BloodUnit> source) {
        BloodInventory inventory = new BloodInventory();
        for (BloodUnit unit : source) {
            inventory.addUnit(unit);
        }
        return inventory;
    }

    public void addUnit(BloodUnit unit) {
        units.add(unit);
    }

    public boolean containsId(String id) {
        for (BloodUnit unit : units) {
            if (unit.getId().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<BloodUnit> getAvailableUnits(BloodType bloodType,
                                                   LocalDate date) {
        ArrayList<BloodUnit> available = new ArrayList<>();
        for (BloodUnit unit : units) {
            if (unit.getBloodType() == bloodType && unit.isAvailable(date)) {
                available.add(unit);
            }
        }
        available.sort(Comparator.comparing(BloodUnit::getExpiryDate)
                .thenComparing(BloodUnit::getDonationDate)
                .thenComparing(BloodUnit::getId,
                        String.CASE_INSENSITIVE_ORDER));
        return available;
    }

    public HashMap<BloodType, Integer> getStockCounts(LocalDate date) {
        HashMap<BloodType, Integer> counts = new HashMap<>();
        for (BloodType type : BloodType.values()) {
            counts.put(type, 0);
        }
        for (BloodUnit unit : units) {
            if (unit.isAvailable(date)) {
                BloodType type = unit.getBloodType();
                counts.put(type, counts.get(type) + 1);
            }
        }
        return counts;
    }

    public ArrayList<BloodUnit> getUnits() {
        return new ArrayList<>(units);
    }
}
