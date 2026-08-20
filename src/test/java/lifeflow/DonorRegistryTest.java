package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import lifeflow.model.DonorAccount;
import lifeflow.model.exception.DuplicateIdException;
import lifeflow.model.exception.ValidationException;
import lifeflow.persistence.JsonDonorStore;
import lifeflow.service.DonorRegistry;
import org.junit.jupiter.api.Test;

final class DonorRegistryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Test
    void registrationCreatesAccountLinkedToDonor() throws Exception {
        DonorRegistry registry = registry();

        DonorAccount account = registry.register("D1", "sara.ali", "pass123");

        assertEquals("DA1", account.getId());
        assertEquals("D1", account.getDonorId());
        assertEquals(TODAY, account.getRegistrationDate());
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void usernameIsCaseInsensitiveAndUnique() throws Exception {
        DonorRegistry registry = registry();
        registry.register("D1", "sara.ali", "pass123");

        assertThrows(DuplicateIdException.class,
                () -> registry.register("D2", "SARA.ALI", "pass456"));
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void passwordsShorterThanMinimumAreRejected() throws Exception {
        DonorRegistry registry = registry();

        assertThrows(ValidationException.class,
                () -> registry.register("D1", "sara.ali", "123"));
        assertTrue(registry.findAll().isEmpty());
    }

    @Test
    void blankDonorIdOrUsernameIsRejected() throws Exception {
        DonorRegistry registry = registry();

        assertThrows(ValidationException.class,
                () -> registry.register("  ", "sara.ali", "pass123"));
        assertThrows(ValidationException.class,
                () -> registry.register("D1", " ", "pass123"));
        assertTrue(registry.findAll().isEmpty());
    }

    @Test
    void authenticationMatchesExactCredentials() throws Exception {
        DonorRegistry registry = registry();
        DonorAccount account = registry.register("D1", "sara.ali", "pass123");

        assertEquals(account, registry.authenticate("sara.ali", "pass123"));
        assertEquals(account, registry.authenticate("SARA.ALI", "pass123"));
        assertNull(registry.authenticate("sara.ali", "wrong"));
        assertNull(registry.authenticate("other", "pass123"));
        assertNull(registry.authenticate("sara.ali", ""));
    }

    @Test
    void findByIdAndByDonorIdResolveAccounts() throws Exception {
        DonorRegistry registry = registry();
        DonorAccount account = registry.register("D7", "omar.k", "pass123");

        assertEquals(account, registry.findById("DA1"));
        assertEquals(account, registry.findByDonorId("d7"));
        assertNull(registry.findById("DA99"));
        assertNull(registry.findByDonorId("D99"));
    }

    @Test
    void changePasswordVerifiesCurrentAndPersists() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-donor-");
        DonorRegistry registry = new DonorRegistry(new ArrayList<>(),
                new JsonDonorStore(dir), fixedClock());
        registry.register("D1", "sara.ali", "pass123");

        assertThrows(ValidationException.class,
                () -> registry.changePassword("sara.ali", "wrong", "newpass"));
        assertThrows(ValidationException.class,
                () -> registry.changePassword("sara.ali", "pass123", "12"));
        registry.changePassword("sara.ali", "pass123", "newpass");

        assertNull(registry.authenticate("sara.ali", "pass123"));
        assertNotNull(registry.authenticate("sara.ali", "newpass"));

        DonorRegistry reloaded = new DonorRegistry(
                new JsonDonorStore(dir).load(), new JsonDonorStore(dir));
        assertNotNull(reloaded.authenticate("sara.ali", "newpass"));
    }

    @Test
    void removeDeletesAccountAndPersists() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-donor-");
        DonorRegistry registry = new DonorRegistry(new ArrayList<>(),
                new JsonDonorStore(dir), fixedClock());
        registry.register("D1", "sara.ali", "pass123");
        registry.register("D2", "omar.k", "pass123");

        registry.remove("SARA.ALI");

        assertEquals(1, registry.findAll().size());
        assertNull(registry.authenticate("sara.ali", "pass123"));
        registry.remove("missing");
        DonorRegistry reloaded = new DonorRegistry(
                new JsonDonorStore(dir).load(), new JsonDonorStore(dir));
        assertEquals(1, reloaded.findAll().size());
    }

    @Test
    void accountsSurviveReloadFromDisk() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-donor-");
        DonorRegistry first = new DonorRegistry(new ArrayList<>(),
                new JsonDonorStore(dir), fixedClock());
        first.register("D1", "sara.ali", "pass123");
        first.close();

        DonorRegistry reloaded = new DonorRegistry(
                new JsonDonorStore(dir).load(), new JsonDonorStore(dir));
        assertEquals(1, reloaded.findAll().size());
        assertNotNull(reloaded.authenticate("sara.ali", "pass123"));
    }

    @Test
    void registrationContinuesPastTakenCandidateIds() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-donor-");
        DonorRegistry registry = new DonorRegistry(new ArrayList<>(),
                new JsonDonorStore(dir), fixedClock());
        registry.register("D1", "one", "pass123");
        registry.register("D2", "two", "pass123");
        registry.register("D3", "three", "pass123");
        registry.register("D4", "four", "pass123");
        registry.remove("one");
        registry.remove("two");

        DonorAccount account = registry.register("D5", "five", "pass123");

        assertEquals("DA5", account.getId());
        assertEquals(3, registry.findAll().size());
    }

    @Test
    void donorCannotRegisterTwoAccountsForTheSameDonor() throws Exception {
        DonorRegistry registry = registry();
        registry.register("D1", "sara.ali", "pass123");

        assertThrows(ValidationException.class,
                () -> registry.register("d1", "sara.two", "pass456"));
        assertEquals(1, registry.findAll().size());
    }

    private static Clock fixedClock() {
        return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
    }

    private static DonorRegistry registry() throws Exception {
        return new DonorRegistry(new ArrayList<>(),
                new JsonDonorStore(Files.createTempDirectory("lifeflow-donor-")),
                fixedClock());
    }
}