package lifeflow;

import java.time.LocalDate;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.UnitStatus;

final class BloodUnitTests {
    private BloodUnitTests() {
    }

    static void run() {
        checksAvailabilityAndExpiry();
        exposesStateThroughAccessors();
    }

    private static void checksAvailabilityAndExpiry() {
        LocalDate today = LocalDate.of(2026, 8, 3);
        BloodUnit valid = unit("U001", today, UnitStatus.AVAILABLE);
        BloodUnit expired = unit("U002", today.minusDays(1), UnitStatus.AVAILABLE);
        BloodUnit used = unit("U003", today.plusDays(1), UnitStatus.USED);

        assert valid.isAvailable(today) : "A unit remains available on its expiry date";
        assert !expired.isAvailable(today) : "An expired unit must not be available";
        assert !used.isAvailable(today) : "A used unit must not be available";
    }

    private static void exposesStateThroughAccessors() {
        LocalDate donationDate = LocalDate.of(2026, 7, 20);
        LocalDate expiryDate = LocalDate.of(2026, 8, 20);
        BloodUnit unit = new BloodUnit("U010", "D010", BloodType.B_NEG,
                donationDate, expiryDate, UnitStatus.AVAILABLE);

        unit.setStatus(UnitStatus.USED);

        assert unit.getId().equals("U010");
        assert unit.getDonorId().equals("D010");
        assert unit.getBloodType() == BloodType.B_NEG;
        assert unit.getDonationDate().equals(donationDate);
        assert unit.getExpiryDate().equals(expiryDate);
        assert unit.getStatus() == UnitStatus.USED;
    }

    private static BloodUnit unit(String id, LocalDate expiryDate, UnitStatus status) {
        return new BloodUnit(id, "D001", BloodType.A_POS,
                LocalDate.of(2026, 7, 1), expiryDate, status);
    }
}
