package lifeflow.model;

/** Describes the result of processing the next pending blood request. */
public enum MatchOutcome {
    NO_PENDING_REQUEST,
    INSUFFICIENT_STOCK,
    FULFILLED
}
