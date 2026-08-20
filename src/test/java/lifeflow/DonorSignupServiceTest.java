package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import lifeflow.model.BloodType;
import lifeflow.model.DonorAccount;
import lifeflow.model.Donor;
import lifeflow.model.exception.ValidationException;
import lifeflow.persistence.JsonDonorStore;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.service.DonorRegistry;
import lifeflow.service.DonorSignupService;
import org.junit.jupiter.api.Test;

final class DonorSignupServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Test
    void signupCreatesAccountAndProfileTogether() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-signup-");
        DonorRegistry registry = registry(dir);

        DonorAccount account = new DonorSignupService(registry, dir)
                .signup("Sara Ali", 24, 62, BloodType.O_POS,
                        "sara.ali", "pass123");

        assertNotNull(account);
        assertEquals("D000001", account.getDonorId());
        Donor donor = loadDonors(dir).get(0);
        assertEquals("Sara Ali", donor.getName());
        assertEquals(BloodType.O_POS, donor.getBloodType());
    }

    @Test
    void signupWithInvalidAgeIsRejectedAndRollsBackAccount() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-signup-");
        DonorRegistry registry = registry(dir);
        DonorSignupService service = new DonorSignupService(registry, dir);

        assertThrows(ValidationException.class,
                () -> service.signup("Sara Ali", 200, 62, BloodType.O_POS,
                        "sara.ali", "pass123"));
        assertTrue(registry.findAll().isEmpty());
        assertTrue(loadDonors(dir).isEmpty());
    }

    @Test
    void signupWithDuplicateUsernameRollsBackProfile() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-signup-");
        DonorRegistry registry = registry(dir);
        DonorSignupService service = new DonorSignupService(registry, dir);
        service.signup("Sara Ali", 24, 62, BloodType.O_POS,
                "sara.ali", "pass123");

        assertThrows(lifeflow.model.exception.DuplicateIdException.class,
                () -> service.signup("Omar Khan", 30, 80, BloodType.A_POS,
                        "SARA.ALI", "pass456"));
        assertEquals(1, loadDonors(dir).size());
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void signupGeneratesSequentialDonorIds() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-signup-");
        DonorRegistry registry = registry(dir);
        DonorSignupService service = new DonorSignupService(registry, dir);

        DonorAccount first = service.signup("Sara Ali", 24, 62,
                BloodType.O_POS, "sara.ali", "pass123");
        DonorAccount second = service.signup("Omar Khan", 30, 80,
                BloodType.A_POS, "omar.k", "pass456");

        assertEquals("D000001", first.getDonorId());
        assertEquals("D000002", second.getDonorId());
        assertEquals(first, registry.findByDonorId("D000001"));
        assertEquals(second, registry.findByDonorId("D000002"));
    }

    private static java.util.List<Donor> loadDonors(java.nio.file.Path dir)
            throws Exception {
        try (JsonLifeFlowStore store = new JsonLifeFlowStore(dir)) {
            return store.load().getDonors();
        }
    }

    private static DonorRegistry registry(java.nio.file.Path dir) throws Exception {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        return new DonorRegistry(new ArrayList<>(), new JsonDonorStore(dir), clock);
    }
}