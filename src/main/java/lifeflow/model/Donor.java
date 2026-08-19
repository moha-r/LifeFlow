package lifeflow.model;

import java.time.LocalDate;
import java.util.Locale;

/** Stores one donor profile, not an internal donation event. */
public class Donor implements Identifiable {
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

    @Override
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

    @Override
    public String toString() {
        return String.format("Donor{id='%s', name='%s', age=%d, weight=%.1fkg, bloodType=%s}",
                id, name, age, weightKg, bloodType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Donor other)) return false;
        return id.equalsIgnoreCase(other.id);
    }

    @Override
    public int hashCode() {
        return id.toLowerCase(Locale.ROOT).hashCode();
    }
}
