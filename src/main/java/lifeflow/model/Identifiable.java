package lifeflow.model;

/**
 * Contract for any domain entity that has a unique string identifier.
 * Allows generic operations like lookup, duplicate checking, and repository storage.
 */
public interface Identifiable {
    /** Returns the unique identifier for this entity. */
    String getId();
}
