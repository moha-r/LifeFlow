package lifeflow.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Represents the 8 standard ABO/Rh blood groups and their compatibility rules. */
public enum BloodType {
    A_POS, A_NEG, B_POS, B_NEG, AB_POS, AB_NEG, O_POS, O_NEG;

    private static final Map<BloodType, Set<BloodType>> COMPATIBILITY_MATRIX = new EnumMap<>(BloodType.class);

    static {
        // Defines which types can RECEIVE from which types
        COMPATIBILITY_MATRIX.put(O_NEG, EnumSet.of(O_NEG));
        COMPATIBILITY_MATRIX.put(O_POS, EnumSet.of(O_POS, O_NEG));
        COMPATIBILITY_MATRIX.put(A_NEG, EnumSet.of(A_NEG, O_NEG));
        COMPATIBILITY_MATRIX.put(A_POS, EnumSet.of(A_POS, A_NEG, O_POS, O_NEG));
        COMPATIBILITY_MATRIX.put(B_NEG, EnumSet.of(B_NEG, O_NEG));
        COMPATIBILITY_MATRIX.put(B_POS, EnumSet.of(B_POS, B_NEG, O_POS, O_NEG));
        COMPATIBILITY_MATRIX.put(AB_NEG, EnumSet.of(AB_NEG, A_NEG, B_NEG, O_NEG));
        COMPATIBILITY_MATRIX.put(AB_POS, EnumSet.allOf(BloodType.class));
    }

    /**
     * Checks if this blood type can safely receive blood from the donor type.
     *
     * @param donorType the blood type of the donated unit
     * @return true if compatible, false otherwise
     */
    public boolean canReceiveFrom(BloodType donorType) {
        if (donorType == null) return false;
        return COMPATIBILITY_MATRIX.get(this).contains(donorType);
    }
}
