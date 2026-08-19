package lifeflow;

import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import lifeflow.model.BloodType;
import lifeflow.model.Donor;
import lifeflow.service.DonationPolicy;

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

        assert policyFor(donationDate).evaluate(donor, donationDate, null).eligible()
                : "A donor at the minimum age and weight should be eligible";
    }

    private static void rejectsAgeAndWeightOutsideRules() {
        LocalDate today = LocalDate.of(2026, 8, 3);

        DonationPolicy policy = policyFor(today);
        assert !policy.evaluate(new Donor("D1", "Young", 17, 50.0,
                BloodType.O_POS, null), today, null).eligible();
        assert !policy.evaluate(new Donor("D2", "Old", 61, 50.0,
                BloodType.O_POS, null), today, null).eligible();
        assert !policy.evaluate(new Donor("D3", "Light", 30, 44.9,
                BloodType.O_POS, null), today, null).eligible();
    }

    private static void enforcesThreeMonthDonationGap() {
        LocalDate lastDonation = LocalDate.of(2026, 5, 3);
        Donor donor = new Donor("D004", "Kumar", 30, 70.0,
                BloodType.B_POS, lastDonation);

        DonationPolicy policy = policyFor(LocalDate.of(2026, 8, 3));
        assert !policy.evaluate(donor, LocalDate.of(2026, 8, 2),
                lastDonation).eligible();
        assert policy.evaluate(donor, LocalDate.of(2026, 8, 3),
                lastDonation).eligible();
    }

    private static void rejectsFutureDonationDate() {
        Donor donor = new Donor("D005", "Mira", 25, 55.0,
                BloodType.AB_NEG, null);

        assert !new DonationPolicy().evaluate(donor,
                LocalDate.now().plusDays(1), null).eligible();
    }

    private static void exposesEncapsulatedStateThroughAccessors() {
        Donor donor = new Donor("D006", "Nur", 24, 52.5,
                BloodType.O_NEG, null);
        LocalDate donationDate = LocalDate.of(2026, 8, 3);

        donor.updateDetails("Nur A.", 25, 53.0, BloodType.O_POS, donationDate);

        assert donor.getId().equals("D006");
        assert donor.getName().equals("Nur A.");
        assert donor.getAge() == 25;
        assert donor.getWeightKg() == 53.0;
        assert donor.getBloodType() == BloodType.O_POS;
        assert donor.getExternalLastDonationDate().equals(donationDate);
    }

    private static DonationPolicy policyFor(LocalDate date) {
        ZoneId zone = ZoneId.systemDefault();
        return new DonationPolicy(Clock.fixed(
                date.atStartOfDay(zone).toInstant(), zone));
    }
}
