package lifeflow.model;

import java.time.LocalDate;
import java.util.Locale;

/** Represents one whole-blood unit stored by the simulation. */
public class BloodUnit implements Identifiable {
    private String id;
    private String donorId;
    private BloodType bloodType;
    private LocalDate donationDate;
    private LocalDate expiryDate;
    private UnitStatus status;

    public BloodUnit(String id, String donorId, BloodType bloodType,
                     LocalDate donationDate, LocalDate expiryDate,
                     UnitStatus status) {
        this.id = id;
        this.donorId = donorId;
        this.bloodType = bloodType;
        this.donationDate = donationDate;
        this.expiryDate = expiryDate;
        this.status = status;
    }

    public boolean isAvailable(LocalDate date) {
        return getInventoryState(date) == InventoryState.AVAILABLE;
    }

    public boolean isExpired(LocalDate date) {
        return getInventoryState(date) == InventoryState.EXPIRED;
    }

    public InventoryState getInventoryState(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Inventory date is required.");
        }
        if (status == UnitStatus.USED) {
            return InventoryState.USED;
        }
        if (status == UnitStatus.DISCARDED) {
            return InventoryState.DISCARDED;
        }
        if (status == UnitStatus.RESERVED) {
            return InventoryState.RESERVED;
        }
        if (donationDate.isAfter(date)) {
            return InventoryState.SCHEDULED;
        }
        return expiryDate.isBefore(date)
                ? InventoryState.EXPIRED : InventoryState.AVAILABLE;
    }

    public String getId() {
        return id;
    }

    public String getDonorId() {
        return donorId;
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public LocalDate getDonationDate() {
        return donationDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public UnitStatus getStatus() {
        return status;
    }

    public void correctDates(LocalDate donationDate, LocalDate expiryDate) {
        this.donationDate = donationDate;
        this.expiryDate = expiryDate;
    }

    /** Marks this unit as consumed by a fulfilled blood request. */
    public void markUsed() {
        status = UnitStatus.USED;
    }

    /** Commits this unit to a pending request that is not yet fully covered. */
    public void markReserved() {
        status = UnitStatus.RESERVED;
    }

    /** Releases a reserved unit back to the general stock. */
    public void markAvailable() {
        status = UnitStatus.AVAILABLE;
    }

    public void markDiscarded() {
        status = UnitStatus.DISCARDED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BloodUnit bloodUnit)) return false;
        return id.equalsIgnoreCase(bloodUnit.id);
    }

    @Override
    public int hashCode() {
        return id.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return String.format("BloodUnit{id='%s', bloodType=%s, status=%s}",
                id, bloodType, status);
    }
}
