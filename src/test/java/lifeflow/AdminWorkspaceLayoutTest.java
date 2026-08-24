package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lifeflow.model.LifeFlowState;
import lifeflow.persistence.LifeFlowStore;
import lifeflow.persistence.StorageInfo;
import lifeflow.service.LifeFlowController;
import lifeflow.ui.DashboardPanel;
import lifeflow.ui.SidebarPanel;
import lifeflow.ui.UiTheme;
import org.junit.jupiter.api.Test;

final class AdminWorkspaceLayoutTest {
    @Test
    void sidebarUsesCompactGroupedNavigationAndRestrainedActiveState()
            throws Exception {
        SidebarPanel[] holder = new SidebarPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] =
                new SidebarPanel(page -> { }));
        SidebarPanel sidebar = holder[0];

        assertEquals(232, UiTheme.SIDEBAR_WIDTH);
        assertEquals(232, sidebar.getPreferredSize().width);
        assertNotNull(namedComponent(sidebar, "operationsNavigation"));
        assertNotNull(namedComponent(sidebar, "managementNavigation"));

        SwingUtilities.invokeAndWait(() -> sidebar.showActive("Dashboard"));
        ArrayList<JButton> active = activeButtons(sidebar);
        assertEquals(1, active.size());
        assertEquals(3, active.get(0).getClientProperty(
                "activeIndicatorWidth"));
    }

    @Test
    void dashboardLeadsWithCompactSummaryNextActionAndStockGrid()
            throws Exception {
        LifeFlowController controller = new LifeFlowController(
                new LifeFlowState(), new MemoryStore());
        DashboardPanel[] holder = new DashboardPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new DashboardPanel(
                controller, () -> { }, () -> { }, () -> { }, () -> { }));
        DashboardPanel dashboard = holder[0];

        Component summary = namedComponent(dashboard, "dashboardSummary");
        assertNotNull(summary);
        assertTrue(summary.getPreferredSize().height <= 76);
        Component stockGrid = namedComponent(dashboard, "inventoryStockGrid");
        assertTrue(stockGrid instanceof JPanel);
        assertEquals(8, ((JPanel) stockGrid).getComponentCount());
        assertNotNull(namedComponent(dashboard, "nextActionPanel"));
        assertNotNull(namedComponent(dashboard, "requestQueueTable"));
    }

    private static ArrayList<JButton> activeButtons(Container root) {
        ArrayList<JButton> matches = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button
                    && Boolean.TRUE.equals(button.getClientProperty(
                    "navActive"))) {
                matches.add(button);
            }
            if (component instanceof Container container) {
                matches.addAll(activeButtons(container));
            }
        }
        return matches;
    }

    private static Component namedComponent(Container root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
            if (component instanceof Container container) {
                Component found = namedComponent(container, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static final class MemoryStore implements LifeFlowStore {
        @Override
        public LifeFlowState load() {
            return new LifeFlowState();
        }

        @Override
        public void save(LifeFlowState state) {
        }

        @Override
        public StorageInfo getStorageInfo() {
            return new StorageInfo(Path.of("test.json"), true, false,
                    "ready");
        }

        @Override
        public void close() {
        }
    }
}
