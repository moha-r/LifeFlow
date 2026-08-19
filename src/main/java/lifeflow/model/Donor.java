package lifeflow.model;

import java.time.LocalDate;

/** Stores one donor profile, not an internal donation event. */
public class Donor {
    private String id;
    private String name;
    private int age;
    private double weightKg;
    private BloodType bloodType;
    private LocalDate externalLastDonationDate;

    public Donor(String id, String name, int age, double weightKg,
                 BloodType bloodType, LocalDate externalLastDonationDate) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.weightKg = weightKg;
        this.bloodType = bloodType;
        this.externalLastDonationDate = externalLastDonationDate;
    }

    public void updateDetails(String name, int age, double weightKg,
                              BloodType bloodType,
                              LocalDate externalLastDonationDate) {
        this.name = name;
        this.age = age;
        this.weightKg = weightKg;
        this.bloodType = bloodType;
        this.externalLastDonationDate = externalLastDonationDate;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public LocalDate getExternalLastDonationDate() {
        return externalLastDonationDate;
    }
}
