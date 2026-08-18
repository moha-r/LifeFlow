package lifeflow.model;

import java.time.LocalDate;

/** Detailed eligibility result used by both the controller and Swing UI. */
public record EligibilityResult(boolean eligible, EligibilityReason reason,
                                LocalDate nextEligibleDate, String message) {
}
