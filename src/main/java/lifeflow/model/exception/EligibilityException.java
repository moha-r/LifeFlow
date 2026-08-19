package lifeflow.model.exception;

import lifeflow.model.EligibilityResult;

/** Thrown when a donor does not meet the donation eligibility rules. */
public class EligibilityException extends LifeFlowException {
    private final EligibilityResult result;

    /**
     * @param result the eligibility evaluation that determined the donor is ineligible
     */
    public EligibilityException(EligibilityResult result) {
        super(result.message(), null);
        this.result = result;
    }

    /** Returns the full eligibility evaluation result. */
    public EligibilityResult getResult() {
        return result;
    }
}
