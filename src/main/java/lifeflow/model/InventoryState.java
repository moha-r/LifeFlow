package lifeflow.model;

/** Operational state derived from a unit's dates and persisted usage status. */
public enum InventoryState {
    SCHEDULED,
    AVAILABLE,
    EXPIRED,
    USED
}
