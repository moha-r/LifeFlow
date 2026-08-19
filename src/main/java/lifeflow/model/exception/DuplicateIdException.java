package lifeflow.model.exception;

/** Thrown when attempting to add an entity with an ID that already exists. */
public class DuplicateIdException extends LifeFlowException {

    /**
     * @param entityType human-readable type name (e.g. "Donor", "Blood unit")
     * @param id         the duplicate identifier
     */
    public DuplicateIdException(String entityType, String id) {
        super(entityType + " ID '" + id + "' already exists.", id);
    }
}
