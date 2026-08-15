package lifeflow;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.util.ArrayList;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.persistence.FileManager;
import lifeflow.service.LifeFlowController;
import lifeflow.ui.DashboardPanel;
import lifeflow.ui.DonorsPanel;
import lifeflow.ui.InventoryPanel;
import lifeflow.ui.MatchingPanel;
import lifeflow.ui.RequestsPanel;

final class ModernUiTests {
    private ModernUiTests() {
    }

    static void run() throws Exception {
        dashboardRefreshesLiveCounts();
        dataPagesConstructAndRefreshHeadlessly();
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
}
