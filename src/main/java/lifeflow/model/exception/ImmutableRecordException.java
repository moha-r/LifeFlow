package lifeflow.model.exception;

/**
 * Thrown when attempting to modify a record that is locked.
 * Records become immutable after they are consumed (e.g. used blood units,
 * fulfilled requests, or donors whose blood type is linked to existing units).
 */
public class ImmutableRecordException extends LifeFlowException {
    private static final long serialVersionUID = 1L;

    /**
     * @param entityType human-readable type name (e.g. "Blood unit", "Request")
     * @param id         the identifier of the locked record
     * @param reason     why the record cannot be modified
     */
    public ImmutableRecordException(String entityType, String id, String reason) {
        super("Cannot modify " + entityType + " '" + id + "': " + reason, id);
    }
}
