package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lifeflow.model.Hospital;
import lifeflow.persistence.JsonDonorStore;
import lifeflow.persistence.JsonHospitalStore;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.service.AdminAuth;
import lifeflow.service.DonorRegistry;
import lifeflow.service.DonorSignupService;
import lifeflow.service.HospitalRegistry;
import lifeflow.ui.LoginPanel;
import lifeflow.ui.LoginResult;
import org.junit.jupiter.api.Test;

final class LoginTest {
    @Test
    void registrationControlsUseComfortableTouchTargets() throws Exception {
        Session session = emptySession();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new LoginPanel(
                session.hospitalRegistry, session.donorRegistry,
                session.signupService, ignored -> { }));

        SwingUtilities.invokeAndWait(() -> {
            panel[0].setDonorRole();
            click(panel[0], "createAccountLink");
            panel[0].setSize(panel[0].getPreferredSize());
            layoutTree(panel[0]);
        });

        assertTrue(panel[0].getPreferredSize().width >= 960);
        assertTrue(panel[0].getPreferredSize().height >= 700);
        for (String name : List.of("donorName", "donorAge", "donorWeight",
                "donorBloodType", "donorUsername", "donorPassword",
                "donorConfirm")) {
            JComponent field = (JComponent) findIn(panel[0], name);
            assertNotNull(field, name);
            assertTrue(field.getPreferredSize().height >= 48, name);
            assertTrue(field.getHeight() >= 44, name + " actual height");
        }
    }

    @Test
    void donorMeasurementsHaveSeparateHighContrastLabels() throws Exception {
        Session session = emptySession();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new LoginPanel(
                session.hospitalRegistry, session.donorRegistry,
                session.signupService, ignored -> { }));

        JLabel ageLabel = (JLabel) findIn(panel[0], "donorAgeLabel");
        JLabel weightLabel = (JLabel) findIn(panel[0], "donorWeightLabel");
        assertNotNull(ageLabel);
        assertNotNull(weightLabel);
        assertEquals("AGE", ageLabel.getText());
        assertEquals("WEIGHT (KG)", weightLabel.getText());
        assertTrue(contrastAgainstWhite(ageLabel.getForeground()) >= 7.0);
        assertTrue(ageLabel.getFont().getSize() >= 12);
    }

    @Test
    void brandRailOmitsLegacyTagline() throws Exception {
        Session session = emptySession();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new LoginPanel(
                session.hospitalRegistry, session.donorRegistry,
                session.signupService, ignored -> { }));

        assertFalse(allLabelText(panel[0]).contains("Blood Donation"));
        assertFalse(allLabelText(panel[0]).contains("Emergency Matching"));
    }

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
        Session session = emptySession();
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(session.hospitalRegistry,
                    session.donorRegistry, session.signupService, outcome::set);
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
        Session session = emptySession();
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(session.hospitalRegistry,
                    session.donorRegistry, session.signupService, outcome::set);
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
        Session session = emptySession();
        Hospital hospital = session.hospitalRegistry.register("Riyadh Central",
                "riyadh.central", "pass123");
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(session.hospitalRegistry,
                    session.donorRegistry, session.signupService, outcome::set);
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
        Session session = emptySession();
        session.hospitalRegistry.register("Riyadh Central", "riyadh.central", "pass123");
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(session.hospitalRegistry,
                    session.donorRegistry, session.signupService, outcome::set);
            panel[0].setRole(false);
            panel[0].getUsernameField().setText("riyadh.central");
            panel[0].getPasswordField().setText("wrong");
            panel[0].signIn();
        });
        assertNull(outcome.get());
        assertTrue(panel[0].getErrorLabel().getText().contains("Invalid"));
    }

    @Test
    void donorRoleAuthenticatesAgainstDonorRegistry() throws Exception {
        Session session = emptySession();
        session.signupService.signup("Sara Ali", 24, 62,
                lifeflow.model.BloodType.O_POS, "sara.ali", "pass123");
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(session.hospitalRegistry,
                    session.donorRegistry, session.signupService, outcome::set);
            panel[0].setDonorRole();
            panel[0].getUsernameField().setText("sara.ali");
            panel[0].getPasswordField().setText("pass123");
            panel[0].signIn();
        });
        assertNotNull(outcome.get());
        assertFalse(outcome.get().admin());
        assertNull(outcome.get().hospital());
        assertNotNull(outcome.get().donor());
        assertEquals("D000001", outcome.get().donor().getDonorId());
    }

    @Test
    void wrongDonorPasswordFailsAndClearsField() throws Exception {
        Session session = emptySession();
        session.signupService.signup("Sara Ali", 24, 62,
                lifeflow.model.BloodType.O_POS, "sara.ali", "pass123");
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(session.hospitalRegistry,
                    session.donorRegistry, session.signupService, outcome::set);
            panel[0].setDonorRole();
            panel[0].getUsernameField().setText("sara.ali");
            panel[0].getPasswordField().setText("wrong");
            panel[0].signIn();
        });
        assertNull(outcome.get());
        assertTrue(panel[0].getErrorLabel().getText().contains("Invalid"));
    }

    @Test
    void donorRegistrationCardCreatesAccountAndProfile() throws Exception {
        Session session = emptySession();
        AtomicReference<LoginResult> outcome = new AtomicReference<>();
        LoginPanel[] panel = new LoginPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new LoginPanel(session.hospitalRegistry,
                    session.donorRegistry, session.signupService, outcome::set);
            panel[0].setDonorRole();
            setField(panel[0], "donorName", "Sara Ali");
            setField(panel[0], "donorAge", "24");
            setField(panel[0], "donorWeight", "62");
            setField(panel[0], "donorUsername", "sara.ali");
            setFieldPassword(panel[0], "donorPassword", "pass123");
            setFieldPassword(panel[0], "donorConfirm", "pass123");
            click(panel[0], "donorRegisterButton");
        });
        assertNotNull(outcome.get());
        assertNotNull(outcome.get().donor());
        assertEquals("D000001", outcome.get().donor().getDonorId());
        assertEquals(1, session.donorRegistry.findAll().size());
    }

    private static void setField(LoginPanel panel, String name, String value) {
        ((javax.swing.JTextField) findIn(panel, name)).setText(value);
    }

    private static void setFieldPassword(LoginPanel panel, String name, String value) {
        ((javax.swing.JPasswordField) findIn(panel, name)).setText(value);
    }

    private static void click(LoginPanel panel, String name) {
        ((javax.swing.JButton) findIn(panel, name)).doClick();
    }

    private static java.awt.Component findIn(java.awt.Component component,
                                             String name) {
        if (name.equals(component.getName())) {
            return component;
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                java.awt.Component match = findIn(child, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static String allLabelText(java.awt.Component component) {
        StringBuilder text = new StringBuilder();
        collectLabelText(component, text);
        return text.toString();
    }

    private static void collectLabelText(java.awt.Component component,
                                         StringBuilder text) {
        if (component instanceof JLabel label) {
            text.append(label.getText()).append('\n');
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                collectLabelText(child, text);
            }
        }
    }

    private static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layoutTree(nested);
            }
        }
    }

    private static double contrastAgainstWhite(java.awt.Color color) {
        return 1.05 / (relativeLuminance(color) + 0.05);
    }

    private static double relativeLuminance(java.awt.Color color) {
        double red = linearChannel(color.getRed() / 255.0);
        double green = linearChannel(color.getGreen() / 255.0);
        double blue = linearChannel(color.getBlue() / 255.0);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearChannel(double value) {
        return value <= 0.04045 ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static Session emptySession() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-login-");
        HospitalRegistry hospitals = new HospitalRegistry(new ArrayList<>(),
                new JsonHospitalStore(dir));
        DonorRegistry donors = new DonorRegistry(new ArrayList<>(),
                new JsonDonorStore(dir));
        DonorSignupService signup = new DonorSignupService(donors, dir);
        return new Session(hospitals, donors, signup);
    }

    private record Session(HospitalRegistry hospitalRegistry,
                           DonorRegistry donorRegistry,
                           DonorSignupService signupService) {
    }
}
