package lifeflow.model;

/** Determines how strict the blood type matching should be during fulfillment. */
public enum MatchMode {
    /** 
     * The unit must be exactly the same blood type as the request. 
     * Default mode for standard operations.
     */
    EXACT,

    /** 
     * The unit must be compatible with the requested type according to 
     * universal donor/recipient rules (e.g., O- can be given to anyone).
     * Used for emergencies when exact types are unavailable.
     */
    COMPATIBLE
}
