package lifeflow.model.exception;

/**
 * Abstract base for all LifeFlow domain exceptions.
 * Provides a consistent way to carry the entity ID involved in the error.
 *
 * <p>Subclasses represent specific error categories such as duplicate IDs,
 * missing entities, validation failures, eligibility rejections, and
 * immutable-record violations. Client code can catch this single type
 * to handle any domain error uniformly, or catch a specific subclass
 * for targeted recovery.</p>
 */
public abstract class LifeFlowException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String entityId;

    /**
     * @param message  human-readable error description
     * @param entityId the ID of the entity that caused the error, or null
     */
    protected LifeFlowException(String message, String entityId) {
        super(message);
        this.entityId = entityId;
    }

    /** Returns the ID of the entity that caused the error, or null. */
    public String getEntityId() {
        return entityId;
    }
}
