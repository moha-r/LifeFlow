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
