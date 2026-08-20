package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import lifeflow.model.Hospital;
import lifeflow.model.exception.DuplicateIdException;
import lifeflow.model.exception.ValidationException;
import lifeflow.persistence.JsonHospitalStore;
import lifeflow.service.HospitalRegistry;
import org.junit.jupiter.api.Test;

final class HospitalRegistryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Test
    void registrationCreatesAccountWithGeneratedId() throws Exception {
        HospitalRegistry registry = registry();

        Hospital hospital = registry.register("Riyadh Central Hospital",
                "riyadh.central", "pass123");

        assertEquals("H1", hospital.getId());
        assertEquals("Riyadh Central Hospital", hospital.getName());
        assertEquals(TODAY, hospital.getRegistrationDate());
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void usernameIsCaseInsensitiveAndUnique() throws Exception {
        HospitalRegistry registry = registry();
        registry.register("Hospital A", "alpha", "pass123");

        assertThrows(DuplicateIdException.class,
                () -> registry.register("Hospital B", "ALPHA", "pass456"));
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void passwordsShorterThanMinimumAreRejected() throws Exception {
        HospitalRegistry registry = registry();

        assertThrows(ValidationException.class,
                () -> registry.register("Hospital A", "alpha", "123"));
        assertTrue(registry.findAll().isEmpty());
    }

    @Test
    void blankNameOrUsernameIsRejected() throws Exception {
        HospitalRegistry registry = registry();

        assertThrows(ValidationException.class,
                () -> registry.register("  ", "alpha", "pass123"));
        assertThrows(ValidationException.class,
                () -> registry.register("Hospital A", " ", "pass123"));
        assertTrue(registry.findAll().isEmpty());
    }

    @Test
    void authenticationMatchesExactCredentials() throws Exception {
        HospitalRegistry registry = registry();
        Hospital hospital = registry.register("Hospital A", "alpha", "pass123");

        assertEquals(hospital, registry.authenticate("alpha", "pass123"));
        assertEquals(hospital, registry.authenticate("ALPHA", "pass123"));
        assertNull(registry.authenticate("alpha", "wrong"));
        assertNull(registry.authenticate("other", "pass123"));
        assertNull(registry.authenticate("alpha", ""));
    }

    @Test
    void accountsSurviveReloadFromDisk() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-hosp-");
        HospitalRegistry first = new HospitalRegistry(new ArrayList<>(),
                new JsonHospitalStore(dir));
        first.register("Hospital A", "alpha", "pass123");
        first.close();

        HospitalRegistry reloaded = new HospitalRegistry(
                new JsonHospitalStore(dir).load(), new JsonHospitalStore(dir));
        assertEquals(1, reloaded.findAll().size());
        assertNotNull(reloaded.authenticate("alpha", "pass123"));
        assertFalse(reloaded.authenticate("alpha", "nope") != null);
    }

    @Test
    void updateReplacesNameKeepingIdAndCredentials() throws Exception {
        HospitalRegistry registry = registry();
        Hospital hospital = registry.register("Hospital A", "alpha", "pass123");
        assertThrows(ValidationException.class, () ->
                registry.update(new Hospital("H1", "  ", "alpha", "pass123",
                        TODAY)));

        Hospital updated = new Hospital(hospital.getId(), "Renamed Hospital",
                hospital.getUsername(), hospital.getPassword(),
                hospital.getRegistrationDate());
        assertEquals(updated, registry.update(updated));
        assertEquals("Renamed Hospital",
                registry.authenticate("alpha", "pass123").getName());
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void updateUnknownIdIsRejected() throws Exception {
        HospitalRegistry registry = registry();
        registry.register("Hospital A", "alpha", "pass123");

        assertThrows(lifeflow.model.exception.EntityNotFoundException.class,
                () -> registry.update(new Hospital("MISSING", "Hospital B",
                        "beta", "pass123", TODAY)));
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void removeDeletesAccountAndSaves() throws Exception {
        HospitalRegistry registry = registry();
        registry.register("Hospital A", "alpha", "pass123");
        registry.register("Hospital B", "beta", "pass456");

        assertTrue(registry.remove("H1"));
        assertNull(registry.authenticate("alpha", "pass123"));
        assertEquals(1, registry.findAll().size());
        assertFalse(registry.remove("H1"));
        assertFalse(registry.remove("MISSING"));
    }

    @Test
    void registrationContinuesPastTakenCandidateIds() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-hosp-");
        HospitalRegistry registry = new HospitalRegistry(new ArrayList<>(),
                new JsonHospitalStore(dir), fixedClock());
        registry.register("Hospital One", "one", "pass123");
        registry.register("Hospital Two", "two", "pass123");
        registry.register("Hospital Three", "three", "pass123");
        registry.register("Hospital Four", "four", "pass123");
        registry.remove("H1");
        registry.remove("H2");

        Hospital hospital = registry.register("Hospital Five", "five", "pass123");

        assertEquals("H5", hospital.getId());
        assertEquals(3, registry.findAll().size());
    }

    private static Clock fixedClock() {
        return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
    }

    private static HospitalRegistry registry() throws Exception {
        return new HospitalRegistry(new ArrayList<>(),
                new JsonHospitalStore(Files.createTempDirectory("lifeflow-hosp-")),
                fixedClock());
    }
}