package lifeflow.model;

/** Explains the outcome of the simplified donor eligibility check. */
public enum EligibilityReason {
    ELIGIBLE,
    FUTURE_DATE,
    AGE_OUT_OF_RANGE,
    UNDERWEIGHT,
    WAITING_PERIOD
}
