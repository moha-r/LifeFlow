package lifeflow;

import java.time.LocalDate;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.Identifiable;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies toString, equals, and hashCode contracts on all model entities,
 * plus the Identifiable interface compliance.
 */
class ObjectContractTests {

    // ── Donor ──────────────────────────────────────────────────────

    @Test
    void donorToStringContainsIdAndName() {
        Donor donor = donor("D001", "Aisha");
        String text = donor.toString();
        assertTrue(text.contains("D001"), "toString must include ID");
        assertTrue(text.contains("Aisha"), "toString must include name");
        assertTrue(text.contains("Donor"), "toString must include class name");
    }

    @Test
    void donorEqualsReflexive() {
        Donor donor = donor("D001", "Aisha");
        assertEquals(donor, donor, "equals must be reflexive");
    }

    @Test
    void donorEqualsSymmetricSameId() {
        Donor a = donor("D001", "Aisha");
        Donor b = donor("D001", "Different Name");
        assertEquals(a, b, "Donors with same ID must be equal");
        assertEquals(b, a, "equals must be symmetric");
    }

    @Test
    void donorEqualsCaseInsensitive() {
        Donor lower = donor("d001", "Aisha");
        Donor upper = donor("D001", "Aisha");
        assertEquals(lower, upper, "equals must be case-insensitive on ID");
    }

    @Test
    void donorNotEqualsDifferentId() {
        Donor a = donor("D001", "Aisha");
        Donor b = donor("D002", "Aisha");
        assertNotEquals(a, b, "Donors with different IDs must not be equal");
    }

    @Test
    void donorNotEqualsNull() {
        Donor donor = donor("D001", "Aisha");
        assertNotEquals(null, donor, "Donor must not equal null");
    }

    @Test
    void donorNotEqualsOtherType() {
        Donor donor = donor("D001", "Aisha");
        assertNotEquals("D001", donor, "Donor must not equal a String");
    }

    @Test
    void donorHashCodeConsistentWithEquals() {
        Donor a = donor("D001", "Aisha");
        Donor b = donor("d001", "Other");
        assertEquals(a.hashCode(), b.hashCode(),
                "Equal objects must have the same hash code");
    }

    @Test
    void donorImplementsIdentifiable() {
        Donor donor = donor("D001", "Aisha");
        assertInstanceOf(Identifiable.class, donor);
        assertEquals("D001", ((Identifiable) donor).getId());
    }

    // ── BloodUnit ─────────────────────────────────────────────────

    @Test
    void unitToStringContainsIdAndType() {
        BloodUnit unit = unit("U001", "D001", BloodType.O_NEG);
        String text = unit.toString();
        assertTrue(text.contains("U001"), "toString must include ID");
        assertTrue(text.contains("O_NEG"), "toString must include blood type");
        assertTrue(text.contains("BloodUnit"), "toString must include class name");
    }

    @Test
    void unitEqualsSameId() {
        BloodUnit a = unit("U001", "D001", BloodType.A_POS);
        BloodUnit b = unit("U001", "D002", BloodType.B_NEG);
        assertEquals(a, b, "Units with same ID must be equal");
    }

    @Test
    void unitEqualsCaseInsensitive() {
        BloodUnit a = unit("u001", "D001", BloodType.O_NEG);
        BloodUnit b = unit("U001", "D001", BloodType.O_NEG);
        assertEquals(a, b, "equals must be case-insensitive");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void unitNotEqualsDifferentId() {
        BloodUnit a = unit("U001", "D001", BloodType.A_POS);
        BloodUnit b = unit("U002", "D001", BloodType.A_POS);
        assertNotEquals(a, b);
    }

    @Test
    void unitImplementsIdentifiable() {
        BloodUnit u = unit("U001", "D001", BloodType.O_NEG);
        assertInstanceOf(Identifiable.class, u);
        assertEquals("U001", ((Identifiable) u).getId());
    }

    // ── BloodRequest (polymorphic toString) ────────────────────────

    @Test
    void regularRequestToStringShowsKind() {
        BloodRequest request = new RegularRequest("R001", "Clinic",
                BloodType.A_POS, 2, LocalDate.of(2026, 1, 1), RequestStatus.PENDING);
        String text = request.toString();
        assertTrue(text.contains("REGULAR"), "Regular request toString must show REGULAR");
        assertTrue(text.contains("R001"));
        assertTrue(text.contains("Clinic"));
    }

    @Test
    void emergencyRequestToStringShowsKind() {
        BloodRequest request = new EmergencyRequest("R002", "Hospital ER",
                BloodType.O_NEG, 3, LocalDate.of(2026, 1, 1), RequestStatus.PENDING);
        String text = request.toString();
        assertTrue(text.contains("EMERGENCY"), "Emergency request toString must show EMERGENCY");
        assertTrue(text.contains("R002"));
    }

    @Test
    void requestEqualsSameIdRegardlessOfType() {
        BloodRequest regular = new RegularRequest("R001", "Clinic",
                BloodType.A_POS, 1, LocalDate.of(2026, 1, 1), RequestStatus.PENDING);
        BloodRequest emergency = new EmergencyRequest("R001", "Hospital",
                BloodType.O_NEG, 5, LocalDate.of(2026, 6, 1), RequestStatus.FULFILLED);
        assertEquals(regular, emergency,
                "Requests with same ID are equal regardless of subtype");
        assertEquals(regular.hashCode(), emergency.hashCode());
    }

    @Test
    void requestNotEqualsDifferentId() {
        BloodRequest a = new RegularRequest("R001", "A",
                BloodType.A_POS, 1, LocalDate.of(2026, 1, 1), RequestStatus.PENDING);
        BloodRequest b = new RegularRequest("R002", "A",
                BloodType.A_POS, 1, LocalDate.of(2026, 1, 1), RequestStatus.PENDING);
        assertNotEquals(a, b);
    }

    @Test
    void requestImplementsIdentifiable() {
        BloodRequest r = new RegularRequest("R001", "X",
                BloodType.B_POS, 1, LocalDate.of(2026, 1, 1), RequestStatus.PENDING);
        assertInstanceOf(Identifiable.class, r);
        assertEquals("R001", ((Identifiable) r).getId());
    }

    // ── Cross-type inequality ─────────────────────────────────────

    @Test
    void donorDoesNotEqualUnitWithSameId() {
        Donor donor = donor("X001", "Test");
        BloodUnit unit = unit("X001", "D001", BloodType.A_POS);
        assertNotEquals(donor, unit,
                "Different entity types must not be equal even with same ID");
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static Donor donor(String id, String name) {
        return new Donor(id, name, 25, 70.0, BloodType.O_NEG, null);
    }

    private static BloodUnit unit(String id, String donorId, BloodType type) {
        LocalDate donated = LocalDate.of(2026, 1, 1);
        return new BloodUnit(id, donorId, type, donated, donated.plusDays(35),
                UnitStatus.AVAILABLE);
    }
}
