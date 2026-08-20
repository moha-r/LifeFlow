package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import lifeflow.model.Hospital;
import lifeflow.persistence.JsonHospitalStore;
import lifeflow.service.AdminAuth;
import lifeflow.service.HospitalRegistry;
import lifeflow.ui.LoginPanel;
import lifeflow.ui.LoginResult;
import org.junit.jupiter.api.Test;

final class LoginTest {
    @Test
    void adminCredentialsAreAcceptedExactly() {
        assertTrue(AdminAuth.authenticate("admin", "admin123"));
        assertFalse(AdminAuth.authenticate("admin", "wrong"));
        assertFalse(AdminAuth.authenticate("root", "admin123"));
        assertFalse(AdminAuth.authenticate("admin", ""));
        assertFalse(AdminAuth.authenticate("", "admin123"));
    }

    @Test
    void successfulAdminSignInRunsCallback() throws Exception {
        HospitalRegistry registry = emptyRegistry();
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(registry, outcome::set);
            panel[0].getUsernameField().setText("admin");
            panel[0].getPasswordField().setText("admin123");
            panel[0].signIn();
        });
        assertNotNull(outcome.get());
        assertTrue(outcome.get().admin());
        assertEquals(" ", panel[0].getErrorLabel().getText());
    }

    @Test
    void failedSignInShowsErrorAndClearsPassword() throws Exception {
        HospitalRegistry registry = emptyRegistry();
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(registry, outcome::set);
            panel[0].getUsernameField().setText("admin");
            panel[0].getPasswordField().setText("nope");
            panel[0].signIn();
        });
        assertNull(outcome.get());
        assertTrue(panel[0].getErrorLabel().getText().contains("Invalid"));
        assertEquals(0, panel[0].getPasswordField().getPassword().length);
    }

    @Test
    void hospitalRoleAuthenticatesAgainstRegistry() throws Exception {
        HospitalRegistry registry = emptyRegistry();
        Hospital hospital = registry.register("Riyadh Central",
                "riyadh.central", "pass123");
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(registry, outcome::set);
            panel[0].setRole(false);
            panel[0].getUsernameField().setText("riyadh.central");
            panel[0].getPasswordField().setText("pass123");
            panel[0].signIn();
        });
        assertNotNull(outcome.get());
        assertFalse(outcome.get().admin());
        assertEquals(hospital.getId(), outcome.get().hospital().getId());
    }

    @Test
    void wrongHospitalPasswordFailsAndClearsField() throws Exception {
        HospitalRegistry registry = emptyRegistry();
        registry.register("Riyadh Central", "riyadh.central", "pass123");
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(registry, outcome::set);
            panel[0].setRole(false);
            panel[0].getUsernameField().setText("riyadh.central");
            panel[0].getPasswordField().setText("wrong");
            panel[0].signIn();
        });
        assertNull(outcome.get());
        assertTrue(panel[0].getErrorLabel().getText().contains("Invalid"));
    }

    private static HospitalRegistry emptyRegistry() throws Exception {
        return new HospitalRegistry(new ArrayList<>(),
                new JsonHospitalStore(
                        Files.createTempDirectory("lifeflow-login-")));
    }
}