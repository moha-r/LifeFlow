package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import lifeflow.model.LifeFlowState;
import lifeflow.model.BloodType;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.service.LifeFlowController;
import lifeflow.ui.DashboardPanel;
import lifeflow.ui.DonorsPanel;
import lifeflow.ui.InventoryPanel;
import lifeflow.ui.MatchingPanel;
import lifeflow.ui.UiComponents;
import lifeflow.ui.UiTheme;
import org.junit.jupiter.api.Test;

final class DonorInventoryUiTest {
    @Test
    void donorWorkspaceExposesEligibilityAndBloodTypeFilters() throws Exception {
        LifeFlowController controller = controller("lifeflow-donor-ui-");
        DonorsPanel[] panel = new DonorsPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new DonorsPanel(
                controller, () -> { }, notice -> { }));

        JTable table = firstTable(panel[0]);
        assertNotNull(table);
        assertEquals(8, table.getColumnCount());
        assertEquals("Eligibility", table.getColumnName(6));
        assertEquals("Units", table.getColumnName(7));
        assertNotNull(named(panel[0], "eligibilityFilter"));
        assertNotNull(named(panel[0], "donorBloodTypeFilter"));
    }

    @Test
    void appointmentsWorkspaceShowsEveryBookingAcrossCenters()
            throws Exception {
        LifeFlowController controller = controller("lifeflow-appointments-ui-");
        controller.addDonor("D000001", "Aisha", 25, 55.0,
                BloodType.O_POS, null);
        controller.bookDonationAppointment("D000001", "H1",
                controller.today().plusDays(2), null);
        lifeflow.service.HospitalRegistry registry = registry();
        lifeflow.ui.AppointmentsPanel[] panel =
                new lifeflow.ui.AppointmentsPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] =
                new lifeflow.ui.AppointmentsPanel(controller, registry,
                        () -> { }, notice -> { }));

        JTable table = firstTable(panel[0]);
        assertNotNull(table);
        assertEquals(6, table.getColumnCount());
        assertEquals("Linked Request", table.getColumnName(5));
        assertEquals(1, table.getModel().getRowCount());
        assertEquals("BOOKED", table.getModel().getValueAt(0, 4));
        assertNotNull(named(panel[0], "appointmentStatusFilter"));
    }

    @Test
    void centersWorkspaceListsRegisteredHospitals() throws Exception {
        LifeFlowController controller = controller("lifeflow-centers-ui-");
        lifeflow.service.HospitalRegistry registry = registry();
        registry.register("Riyadh Central Hospital", "riyadh.central",
                "pass123");
        lifeflow.ui.HospitalsPanel[] panel = new lifeflow.ui.HospitalsPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] =
                new lifeflow.ui.HospitalsPanel(registry, controller,
                        () -> { }, notice -> { }));

        JTable table = firstTable(panel[0]);
        assertNotNull(table);
        assertEquals(4, table.getColumnCount());
        assertEquals(1, table.getModel().getRowCount());
        assertEquals("Riyadh Central Hospital",
                table.getModel().getValueAt(0, 1));
        assertNotNull(findButton(panel[0], "+ Add center"));
        assertNotNull(findButton(panel[0], "Edit selected"));
        assertNotNull(findButton(panel[0], "Remove"));
    }

    @Test
    void requestWorkspaceShowsVolunteerCounts() throws Exception {
        LifeFlowController controller = controller("lifeflow-requests-ui-");
        controller.addDonor("D000001", "Aisha", 25, 55.0,
                BloodType.O_POS, null);
        controller.addRequest("R000001", "Clinic", BloodType.O_POS, 1, false);
        controller.bookDonationAppointment("D000001", "H1",
                controller.today().plusDays(2), "R000001");
        lifeflow.ui.RequestsPanel[] panel = new lifeflow.ui.RequestsPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] =
                new lifeflow.ui.RequestsPanel(controller, () -> { },
                        notice -> { }));

        JTable table = firstTable(panel[0]);
        assertNotNull(table);
        assertEquals(9, table.getColumnCount());
        assertEquals("Volunteers", table.getColumnName(8));
        assertEquals(1, table.getModel().getValueAt(0, 8));
    }

    @Test
    void inventoryWorkspaceShowsDaysLeftAndDateCorrectionAction() throws Exception {
        LifeFlowController controller = controller("lifeflow-inventory-ui-");
        InventoryPanel[] panel = new InventoryPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new InventoryPanel(
                controller, () -> { }, notice -> { }));

        JTable table = firstTable(panel[0]);
        assertNotNull(table);
        assertEquals(7, table.getColumnCount());
        assertEquals("Days Left", table.getColumnName(5));
        assertEquals("Status", table.getColumnName(6));
        assertNotNull(named(panel[0], "inventoryStatusFilter"));
        assertNotNull(named(panel[0], "inventoryBloodTypeFilter"));
        assertNotNull(findButton(panel[0], "Correct dates"));
    }

    @Test
    void dashboardShowsUnitsThatExpireWithinSevenDays() throws Exception {
        LifeFlowController controller = controller("lifeflow-dashboard-expiry-");
        controller.addDonor("D000001", "Aisha", 25, 55.0,
                BloodType.A_POS, null);
        controller.addBloodUnit("U000001", "D000001",
                controller.today().minusDays(30));
        DashboardPanel[] panel = new DashboardPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new DashboardPanel(
                controller, () -> { }, () -> { }, () -> { }, () -> { }));

        Component expiring = named(panel[0], "expiringSoonValue");
        assertNotNull(expiring);
        assertEquals("1", ((javax.swing.JLabel) expiring).getText());
    }

    @Test
    void statusRendererUsesSemanticColoursForEligibilityAndInventory() {
        JTable table = new JTable(1, 1);
        var renderer = UiComponents.statusRenderer();

        Component eligible = renderer.getTableCellRendererComponent(
                table, "ELIGIBLE", false, false, 0, 0);
        assertEquals(UiTheme.SUCCESS, eligible.getForeground());
        Component deferred = renderer.getTableCellRendererComponent(
                table, "DEFERRED", false, false, 0, 0);
        assertEquals(UiTheme.WARNING, deferred.getForeground());
        Component notEligible = renderer.getTableCellRendererComponent(
                table, "NOT ELIGIBLE", false, false, 0, 0);
        assertEquals(UiTheme.DANGER, notEligible.getForeground());
        Component expired = renderer.getTableCellRendererComponent(
                table, "EXPIRED", false, false, 0, 0);
        assertEquals(UiTheme.DANGER, expired.getForeground());
    }

    @Test
    void matchingWorkspaceShowsTheFirstRequestThatCanActuallyBeFulfilled()
            throws Exception {
        LifeFlowController controller = controller("lifeflow-matching-ui-");
        controller.addDonor("D000001", "Aisha", 25, 55.0,
                BloodType.O_NEG, null);
        controller.addBloodUnit("U000001", "D000001", controller.today());
        controller.addRequest("R000001", "Unavailable first", BloodType.O_POS,
                1, false);
        controller.addRequest("R000002", "Ready next", BloodType.O_NEG,
                1, false);
        MatchingPanel[] panel = new MatchingPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new MatchingPanel(
                controller, () -> { }, notice -> { }));

        Component selected = named(panel[0], "selectedMatchingRequest");

        assertNotNull(selected);
        assertEquals("REGULAR · R000002", ((javax.swing.JLabel) selected).getText());
    }

    @Test
    void dashboardMarksRequestsAsWaitingOrReadyFromCurrentExactMatchStock()
            throws Exception {
        LifeFlowController controller = controller("lifeflow-dashboard-queue-");
        controller.addDonor("D000001", "Aisha", 25, 55.0,
                BloodType.O_NEG, null);
        controller.addBloodUnit("U000001", "D000001", controller.today());
        controller.addRequest("R000001", "Unavailable", BloodType.O_POS, 1, false);
        controller.addRequest("R000002", "Ready", BloodType.O_NEG, 1, false);
        DashboardPanel[] panel = new DashboardPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new DashboardPanel(
                controller, () -> { }, () -> { }, () -> { }, () -> { }));

        JTable queue = (JTable) named(panel[0], "requestQueueTable");

        assertNotNull(queue);
        assertEquals("WAITING FOR STOCK", queue.getModel().getValueAt(0, 4));
        assertEquals("READY TO MATCH", queue.getModel().getValueAt(1, 4));
    }

    private static LifeFlowController controller(String prefix) throws Exception {
        return new LifeFlowController(new LifeFlowState(),
                new JsonLifeFlowStore(Files.createTempDirectory(prefix)));
    }

    private static lifeflow.service.HospitalRegistry registry()
            throws Exception {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.Clock clock = java.time.Clock.fixed(
                java.time.LocalDate.of(2026, 8, 20)
                        .atStartOfDay(zone).toInstant(), zone);
        return new lifeflow.service.HospitalRegistry(new java.util.ArrayList<>(),
                new lifeflow.persistence.JsonHospitalStore(
                        Files.createTempDirectory("lifeflow-centers-")),
                clock);
    }

    private static Component named(Container root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
            if (component instanceof Container container) {
                Component found = named(container, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JTable firstTable(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof JTable table) {
                return table;
            }
            if (component instanceof Container container) {
                JTable found = firstTable(container);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JButton findButton(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container container) {
                JButton found = findButton(container, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
