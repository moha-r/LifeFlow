package lifeflow;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import lifeflow.model.BloodType;

/** Tests the new compatibility matrix logic. */
class BloodCompatibilityTests {

    @Test
    void opnegCanOnlyReceiveFromONeg() {
        assertTrue(BloodType.O_NEG.canReceiveFrom(BloodType.O_NEG));
        assertFalse(BloodType.O_NEG.canReceiveFrom(BloodType.O_POS));
        assertFalse(BloodType.O_NEG.canReceiveFrom(BloodType.A_NEG));
        assertFalse(BloodType.O_NEG.canReceiveFrom(BloodType.AB_POS));
    }

    @Test
    void opposCanReceiveFromONegAndOPos() {
        assertTrue(BloodType.O_POS.canReceiveFrom(BloodType.O_POS));
        assertTrue(BloodType.O_POS.canReceiveFrom(BloodType.O_NEG));
        assertFalse(BloodType.O_POS.canReceiveFrom(BloodType.A_POS));
        assertFalse(BloodType.O_POS.canReceiveFrom(BloodType.B_NEG));
    }

    @Test
    void abposCanReceiveFromEveryone() {
        for (BloodType type : BloodType.values()) {
            assertTrue(BloodType.AB_POS.canReceiveFrom(type), 
                    "AB+ should receive from " + type);
        }
    }

    @Test
    void aNegCanReceiveFromANegAndONeg() {
        assertTrue(BloodType.A_NEG.canReceiveFrom(BloodType.A_NEG));
        assertTrue(BloodType.A_NEG.canReceiveFrom(BloodType.O_NEG));
        assertFalse(BloodType.A_NEG.canReceiveFrom(BloodType.A_POS));
        assertFalse(BloodType.A_NEG.canReceiveFrom(BloodType.AB_NEG));
    }
}
