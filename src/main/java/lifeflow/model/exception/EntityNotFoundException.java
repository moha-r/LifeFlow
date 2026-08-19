package lifeflow.model.exception;

/** Thrown when a referenced entity cannot be found by its ID. */
public class EntityNotFoundException extends LifeFlowException {
    private final String entityType;

    /**
     * @param entityType human-readable type name (e.g. "Donor", "Blood unit")
     * @param id         the identifier that was not found
     */
    public EntityNotFoundException(String entityType, String id) {
        super(entityType + " '" + id + "' was not found.", id);
        this.entityType = entityType;
    }

    /** Returns the type of entity that was not found (e.g. "Donor"). */
    public String getEntityType() {
        return entityType;
    }
}
