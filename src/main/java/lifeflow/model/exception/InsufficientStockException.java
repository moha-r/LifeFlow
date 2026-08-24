package lifeflow.model.exception;

import lifeflow.model.BloodType;

/** Thrown when available blood stock cannot fulfill a request in full. */
public class InsufficientStockException extends LifeFlowException {
    private static final long serialVersionUID = 1L;

    private final BloodType bloodType;
    private final int requested;
    private final int available;

    /**
     * @param requestId the ID of the request that could not be fulfilled
     * @param bloodType the blood type that was requested
     * @param requested the number of units requested
     * @param available the number of units actually available
     */
    public InsufficientStockException(String requestId, BloodType bloodType,
                                       int requested, int available) {
        super(requestId + " needs " + requested + " unit(s) of " + bloodType
              + ", but only " + available + " are available.", requestId);
        this.bloodType = bloodType;
        this.requested = requested;
        this.available = available;
    }

    /** Returns the blood type that was requested. */
    public BloodType getBloodType() {
        return bloodType;
    }

    /** Returns the number of units that were requested. */
    public int getRequested() {
        return requested;
    }

    /** Returns the number of units that were actually available. */
    public int getAvailable() {
        return available;
    }
}
