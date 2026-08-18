package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lifeflow.model.BloodType;
import lifeflow.model.Donor;
import lifeflow.model.EligibilityReason;
import lifeflow.service.DonationPolicy;
import org.junit.jupiter.api.Test;

final class DonationPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);
    private final DonationPolicy policy = new DonationPolicy(Clock.fixed(
            Instant.parse("2026-08-19T04:00:00Z"), ZoneOffset.UTC));

    @Test
    void firstTimeDonorIsEligibleAndExpiryIsCalculatedFromDonationDate() {
        Donor donor = donor(25, 55.0, null);

        var result = policy.evaluate(donor, TODAY, null);

        assertTrue(result.eligible());
        assertEquals(EligibilityReason.ELIGIBLE, result.reason());
        assertEquals(null, result.lastDonationDate());
        assertEquals(null, result.nextEligibleDate());
        assertEquals(TODAY.plusDays(35), policy.calculateExpiry(TODAY));
    }

    @Test
    void waitingPeriodReportsBothLastAndNextEligibleDates() {
        LocalDate lastDonation = LocalDate.of(2026, 6, 30);
        Donor donor = donor(25, 55.0, lastDonation);

        var deferred = policy.evaluate(donor, TODAY, lastDonation);
        DonationPolicy laterPolicy = new DonationPolicy(Clock.fixed(
                Instant.parse("2026-09-30T04:00:00Z"), ZoneOffset.UTC));
        var eligible = laterPolicy.evaluate(donor, lastDonation.plusMonths(3),
                lastDonation);

        assertFalse(deferred.eligible());
        assertEquals(EligibilityReason.WAITING_PERIOD, deferred.reason());
        assertEquals(lastDonation, deferred.lastDonationDate());
        assertEquals(LocalDate.of(2026, 9, 30), deferred.nextEligibleDate());
        assertTrue(eligible.eligible());
    }

    @Test
    void policyEnforcesAgeWeightAndDateBoundaries() {
        assertFalse(policy.evaluate(donor(17, 55.0, null), TODAY, null).eligible());
        assertTrue(policy.evaluate(donor(18, 45.0, null), TODAY, null).eligible());
        assertTrue(policy.evaluate(donor(60, 45.0, null), TODAY, null).eligible());
        assertFalse(policy.evaluate(donor(61, 55.0, null), TODAY, null).eligible());
        assertFalse(policy.evaluate(donor(25, 44.9, null), TODAY, null).eligible());
        assertEquals(EligibilityReason.FUTURE_DATE,
                policy.evaluate(donor(25, 55.0, null), TODAY.plusDays(1), null)
                        .reason());
    }

    private static Donor donor(int age, double weight, LocalDate externalDate) {
        return new Donor("D000001", "Aisha", age, weight, BloodType.A_POS,
                externalDate);
    }
}
