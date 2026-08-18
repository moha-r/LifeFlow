package lifeflow.model;

import java.time.LocalDate;

/** Represents one whole-blood unit stored by the simulation. */
public class BloodUnit {
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
        return status == UnitStatus.AVAILABLE
                && !donationDate.isAfter(date)
                && !expiryDate.isBefore(date);
    }

    public boolean isExpired(LocalDate date) {
        return expiryDate.isBefore(date);
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

    public void updateExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public void setStatus(UnitStatus status) {
        this.status = status;
    }
}
