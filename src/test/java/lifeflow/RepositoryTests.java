package lifeflow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lifeflow.model.BloodType;
import lifeflow.model.Donor;
import lifeflow.model.Identifiable;
import lifeflow.model.Repository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the generic repository class that replaces duplicated find loops. */
class RepositoryTests {

    @Test
    void findByIdReturnsEntityWhenPresent() {
        List<Donor> list = new ArrayList<>();
        list.add(new Donor("D001", "Aisha", 25, 70, BloodType.A_POS, null));
        list.add(new Donor("D002", "Ali", 30, 80, BloodType.B_POS, null));

        Repository<Donor> repo = new Repository<>(list);
        Donor found = repo.findById("D002");
        
        assertNotNull(found);
        assertEquals("Ali", found.getName());
    }

    @Test
    void findByIdIsCaseInsensitive() {
        List<Donor> list = new ArrayList<>();
        list.add(new Donor("D001", "Aisha", 25, 70, BloodType.A_POS, null));

        Repository<Donor> repo = new Repository<>(list);
        
        assertNotNull(repo.findById("d001"), "Should find by lowercase ID");
        assertNotNull(repo.findById("D001"), "Should find by uppercase ID");
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        List<Donor> list = new ArrayList<>();
        list.add(new Donor("D001", "Aisha", 25, 70, BloodType.A_POS, null));

        Repository<Donor> repo = new Repository<>(list);
        assertNull(repo.findById("D005"));
    }

    @Test
    void findByIdReturnsNullWhenIdIsNull() {
        List<Donor> list = new ArrayList<>();
        list.add(new Donor("D001", "Aisha", 25, 70, BloodType.A_POS, null));

        Repository<Donor> repo = new Repository<>(list);
        assertNull(repo.findById(null));
    }

    @Test
    void nextIdGeneratesSequentialIds() {
        List<Donor> list = new ArrayList<>();
        list.add(new Donor("D000001", "Aisha", 25, 70, BloodType.A_POS, null));
        list.add(new Donor("D000002", "Ali", 30, 80, BloodType.B_POS, null));

        Repository<Donor> repo = new Repository<>(list);
        assertEquals("D000003", repo.nextId("D"));
    }

    @Test
    void nextIdIgnoresNonMatchingPrefixes() {
        List<MockEntity> list = new ArrayList<>();
        list.add(new MockEntity("D000001"));
        list.add(new MockEntity("U000005")); // Different prefix

        Repository<MockEntity> repo = new Repository<>(list);
        assertEquals("D000002", repo.nextId("D"),
                "Should only increment based on IDs with matching prefix");
    }

    @Test
    void nextIdIgnoresInvalidSuffixes() {
        List<MockEntity> list = new ArrayList<>();
        list.add(new MockEntity("D000001"));
        list.add(new MockEntity("D-CUSTOM")); // Not a number

        Repository<MockEntity> repo = new Repository<>(list);
        assertEquals("D000002", repo.nextId("D"),
                "Should ignore non-numeric suffixes");
    }

    @Test
    void nextIdStartsAtOneForEmptyList() {
        Repository<Donor> repo = new Repository<>(new ArrayList<>());
        assertEquals("D000001", repo.nextId("D"));
    }

    private static class MockEntity implements Identifiable {
        private final String id;
        MockEntity(String id) { this.id = id; }
        @Override public String getId() { return id; }
    }
}
