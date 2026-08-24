package lifeflow.model.exception;

/** Thrown when input data fails domain validation rules. */
public class ValidationException extends LifeFlowException {
    private static final long serialVersionUID = 1L;

    private final String fieldName;

    /**
     * @param message   human-readable validation failure description
     * @param fieldName the name of the field that failed validation
     */
    public ValidationException(String message, String fieldName) {
        super(message, null);
        this.fieldName = fieldName;
    }

    /** Returns the name of the field that failed validation. */
    public String getFieldName() {
        return fieldName;
    }
}
