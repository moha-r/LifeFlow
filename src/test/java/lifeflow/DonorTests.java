package lifeflow;

import java.time.LocalDate;
import lifeflow.model.BloodType;
import lifeflow.model.Donor;

final class DonorTests {
    private DonorTests() {
    }

    static void run() {
        acceptsMinimumAgeAndWeight();
        rejectsAgeAndWeightOutsideRules();
        enforcesThreeMonthDonationGap();
        rejectsFutureDonationDate();
        exposesEncapsulatedStateThroughAccessors();
    }

    private static void acceptsMinimumAgeAndWeight() {
        LocalDate donationDate = LocalDate.of(2026, 8, 3);
        Donor donor = new Donor("D001", "Aisha", 18, 45.0,
                BloodType.A_POS, null);

        assert donor.isEligible(donationDate)
                : "A donor at the minimum age and weight should be eligible";
    }

    private static void rejectsAgeAndWeightOutsideRules() {
        LocalDate today = LocalDate.of(2026, 8, 3);

        assert !new Donor("D1", "Young", 17, 50.0, BloodType.O_POS, null)
                .isEligible(today);
        assert !new Donor("D2", "Old", 61, 50.0, BloodType.O_POS, null)
                .isEligible(today);
        assert !new Donor("D3", "Light", 30, 44.9, BloodType.O_POS, null)
                .isEligible(today);
    }

    private static void enforcesThreeMonthDonationGap() {
        LocalDate lastDonation = LocalDate.of(2026, 5, 3);
        Donor donor = new Donor("D004", "Kumar", 30, 70.0,
                BloodType.B_POS, lastDonation);

        assert !donor.isEligible(LocalDate.of(2026, 8, 2));
        assert donor.isEligible(LocalDate.of(2026, 8, 3));
    }

    private static void rejectsFutureDonationDate() {
        Donor donor = new Donor("D005", "Mira", 25, 55.0,
                BloodType.AB_NEG, null);

        assert !donor.isEligible(LocalDate.now().plusDays(1));
    }

    private static void exposesEncapsulatedStateThroughAccessors() {
        Donor donor = new Donor("D006", "Nur", 24, 52.5,
                BloodType.O_NEG, null);
        LocalDate donationDate = LocalDate.of(2026, 8, 3);

        donor.setName("Nur A.");
        donor.setAge(25);
        donor.setWeightKg(53.0);
        donor.setBloodType(BloodType.O_POS);
        donor.recordDonation(donationDate);

        assert donor.getId().equals("D006");
        assert donor.getName().equals("Nur A.");
        assert donor.getAge() == 25;
        assert donor.getWeightKg() == 53.0;
        assert donor.getBloodType() == BloodType.O_POS;
        assert donor.getLastDonationDate().equals(donationDate);
    }
}
