package lifeflow.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import lifeflow.model.Donor;
import lifeflow.model.EligibilityReason;
import lifeflow.model.EligibilityResult;

/** Centralises the simplified educational donation rules used by LifeFlow. */
public final class DonationPolicy {
    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 60;
    public static final double MIN_WEIGHT_KG = 45.0;
    public static final int WAITING_MONTHS = 3;
    public static final int UNIT_SHELF_LIFE_DAYS = 35;

    private final Clock clock;

    public DonationPolicy() {
        this(Clock.systemDefaultZone());
    }

    public DonationPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public EligibilityResult evaluate(Donor donor, LocalDate proposedDonationDate,
                                      LocalDate effectiveLastDonationDate) {
        Objects.requireNonNull(donor, "donor");
        if (proposedDonationDate == null) {
            return result(false, EligibilityReason.DATE_REQUIRED,
                    effectiveLastDonationDate, "Donation date is required.");
        }
        if (proposedDonationDate.isAfter(LocalDate.now(clock))) {
            return result(false, EligibilityReason.FUTURE_DATE,
                    effectiveLastDonationDate,
                    "Donation date cannot be in the future.");
        }
        if (donor.getAge() < MIN_AGE || donor.getAge() > MAX_AGE) {
            return result(false, EligibilityReason.AGE_OUT_OF_RANGE,
                    effectiveLastDonationDate,
                    "Donor age must be between 18 and 60.");
        }
        if (donor.getWeightKg() < MIN_WEIGHT_KG) {
            return result(false, EligibilityReason.UNDERWEIGHT,
                    effectiveLastDonationDate,
                    "Donor weight must be at least 45 kg.");
        }
        if (effectiveLastDonationDate != null) {
            LocalDate nextDate = effectiveLastDonationDate.plusMonths(WAITING_MONTHS);
            if (proposedDonationDate.isBefore(nextDate)) {
                return new EligibilityResult(false, EligibilityReason.WAITING_PERIOD,
                        effectiveLastDonationDate, nextDate,
                        "Last donation: " + effectiveLastDonationDate
                                + ". Next eligible date: " + nextDate + ".");
            }
            return new EligibilityResult(true, EligibilityReason.ELIGIBLE,
                    effectiveLastDonationDate, nextDate,
                    "Donor is eligible on " + proposedDonationDate + ".");
        }
        return new EligibilityResult(true, EligibilityReason.ELIGIBLE,
                null, null, "Donor is eligible on " + proposedDonationDate + ".");
    }

    public LocalDate calculateExpiry(LocalDate donationDate) {
        if (donationDate == null) {
            throw new IllegalArgumentException("Donation date is required.");
        }
        return donationDate.plusDays(UNIT_SHELF_LIFE_DAYS);
    }

    private static EligibilityResult result(boolean eligible, EligibilityReason reason,
                                            LocalDate lastDonationDate,
                                            String message) {
        LocalDate nextDate = lastDonationDate == null ? null
                : lastDonationDate.plusMonths(WAITING_MONTHS);
        return new EligibilityResult(eligible, reason, lastDonationDate, nextDate,
                message);
    }
}
