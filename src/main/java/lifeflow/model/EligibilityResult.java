package lifeflow.model;

import java.io.Serializable;
import java.time.LocalDate;

/** Detailed eligibility result used by both the controller and Swing UI. */
public record EligibilityResult(boolean eligible, EligibilityReason reason,
                                LocalDate lastDonationDate,
                                LocalDate nextEligibleDate, String message)
        implements Serializable {
    private static final long serialVersionUID = 1L;
}
