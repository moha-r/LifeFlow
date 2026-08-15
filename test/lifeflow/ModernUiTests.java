package lifeflow;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.util.ArrayList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JTable;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.persistence.FileManager;
import lifeflow.service.LifeFlowController;
import lifeflow.ui.BoundedContentPanel;
import lifeflow.ui.DashboardPanel;
import lifeflow.ui.DonorsPanel;
import lifeflow.ui.InventoryPanel;
import lifeflow.ui.MatchingPanel;
import lifeflow.ui.PageShell;
import lifeflow.ui.RequestsPanel;
import lifeflow.ui.SidebarPanel;
import lifeflow.ui.UiTheme;

final class ModernUiTests {
    private ModernUiTests() {
    }

    static void run() throws Exception {
        boundedContentCapsWidePages();
        pageShellExposesSharedSections();
        sidebarKeepsOneActivePage();
        dashboardUsesDenseOperationsLayout();
        dashboardRefreshesLiveCounts();
        dataPagesConstructAndRefreshHeadlessly();
    }

    private static void boundedContentCapsWidePages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel child = new JPanel();
            BoundedContentPanel bounded = new BoundedContentPanel(child);
            bounded.setSize(1600, 700);
            bounded.doLayout();
            assert child.getWidth() == UiTheme.CONTENT_MAX_WIDTH
                    : "Wide pages must stop at the approved content width";
            assert child.getX() == (1600 - UiTheme.CONTENT_MAX_WIDTH) / 2
                    : "Bounded content must stay centered";
        });
    }

    private static void pageShellExposesSharedSections() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PageShell shell = new PageShell("Donor registry", "Manage donors.");
            assert namedComponent(shell, "pageHeader") != null;
            assert namedComponent(shell, "pageToolbar") != null;
            assert namedComponent(shell, "pageBody") != null;
            assert namedComponent(shell, "pageFooter") != null;
        });
    }

    private static void sidebarKeepsOneActivePage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SidebarPanel sidebar = new SidebarPanel(page -> { });
            sidebar.showActive("Dashboard");
            assert sidebar.getActiveCount() == 1;
            assert sidebar.isActive("Dashboard");
            sidebar.showActive("Donors");
            assert sidebar.getActiveCount() == 1;
            assert sidebar.isActive("Donors");
            assert !sidebar.isActive("Dashboard");
            assert !UiTheme.SIDEBAR_HOVER.equals(UiTheme.CORAL)
                    : "Hover and active navigation must look different";
        });
    }

    private static void dashboardUsesDenseOperationsLayout() throws Exception {
        LifeFlowController controller = new LifeFlowController(
                new ArrayList<Donor>(), new ArrayList<BloodUnit>(),
                new ArrayList<BloodRequest>(),
                new FileManager(Files.createTempDirectory("lifeflow-dashboard-layout-")));
        DashboardPanel[] panel = new DashboardPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new DashboardPanel(
                controller, () -> { }, () -> { }, () -> { }, () -> { }));

        assert namedComponent(panel[0], "dashboardMetrics") != null;
        Component inventory = namedComponent(panel[0], "inventoryStatusTable");
        assert inventory instanceof JTable;
        assert ((JTable) inventory).getColumnCount() == 4;
        assert namedComponent(panel[0], "priorityRequestPanel") != null;
        assert namedComponent(panel[0], "requestQueueTable") instanceof JTable;
    }

    private static void dashboardRefreshesLiveCounts() throws Exception {
        LifeFlowController controller = new LifeFlowController(
                new ArrayList<Donor>(), new ArrayList<BloodUnit>(),
                new ArrayList<BloodRequest>(),
                new FileManager(Files.createTempDirectory("lifeflow-ui-")));
        DashboardPanel[] panel = new DashboardPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new DashboardPanel(
                controller, () -> { }, () -> { }, () -> { }, () -> { }));

        assert labelText(panel[0], "totalDonorsValue").equals("0");
        controller.addDonor("D1", "Dashboard Donor", 25, 55.0,
                BloodType.A_POS, null);
        SwingUtilities.invokeAndWait(panel[0]::refreshData);
        assert labelText(panel[0], "totalDonorsValue").equals("1");
    }

    private static void dataPagesConstructAndRefreshHeadlessly() throws Exception {
        LifeFlowController controller = new LifeFlowController(
                new ArrayList<Donor>(), new ArrayList<BloodUnit>(),
                new ArrayList<BloodRequest>(),
                new FileManager(Files.createTempDirectory("lifeflow-pages-")));
        SwingUtilities.invokeAndWait(() -> {
            DonorsPanel donors = new DonorsPanel(controller, () -> { }, message -> { });
            InventoryPanel inventory = new InventoryPanel(controller, () -> { }, message -> { });
            RequestsPanel requests = new RequestsPanel(controller, () -> { }, message -> { });
            MatchingPanel matching = new MatchingPanel(controller, () -> { }, message -> { });
            donors.refreshData();
            inventory.refreshData();
            requests.refreshData();
            matching.refreshData();
            assert donors.getComponentCount() > 0;
            assert inventory.getComponentCount() > 0;
            assert requests.getComponentCount() > 0;
            assert matching.getComponentCount() > 0;
        });
    }

    private static String labelText(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (component instanceof JLabel label && name.equals(label.getName())) {
                return label.getText();
            }
            if (component instanceof Container container) {
                String found = labelText(container, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
}
