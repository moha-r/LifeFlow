package lifeflow.model;

import java.time.LocalDate;

/** Stores a donor and applies the simplified project eligibility rules. */
public class Donor {
    private String id;
    private String name;
    private int age;
    private double weightKg;
    private BloodType bloodType;
    private LocalDate lastDonationDate;

    public Donor(String id, String name, int age, double weightKg,
                 BloodType bloodType, LocalDate lastDonationDate) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.weightKg = weightKg;
        this.bloodType = bloodType;
        this.lastDonationDate = lastDonationDate;
    }

    public boolean isEligible(LocalDate donationDate) {
        if (donationDate == null || donationDate.isAfter(LocalDate.now())) {
            return false;
        }
        if (age < 18 || age > 60 || weightKg < 45.0) {
            return false;
        }
        return lastDonationDate == null
                || !donationDate.isBefore(lastDonationDate.plusMonths(3));
    }

    public void recordDonation(LocalDate donationDate) {
        this.lastDonationDate = donationDate;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public void setBloodType(BloodType bloodType) {
        this.bloodType = bloodType;
    }

    public LocalDate getLastDonationDate() {
        return lastDonationDate;
    }
}
