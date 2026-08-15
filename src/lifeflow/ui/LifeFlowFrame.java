package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.persistence.FileManager;
import lifeflow.service.LifeFlowController;

/** Modern application shell with sidebar navigation and live page refresh. */
@SuppressWarnings({"serial", "this-escape"})
public final class LifeFlowFrame extends JFrame {
    private static final String DASHBOARD = "Dashboard";
    private static final String DONORS = "Donors";
    private static final String INVENTORY = "Blood Inventory";
    private static final String REQUESTS = "Blood Requests";
    private static final String MATCHING = "Matching";

    private final LifeFlowController controller;
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel breadcrumbLabel = new JLabel("Workspace / Overview");
    private final Timer statusTimer = new Timer(3500, event -> statusLabel.setText(" "));
    private SidebarPanel sidebarPanel;

    private DashboardPanel dashboardPanel;
    private DonorsPanel donorsPanel;
    private InventoryPanel inventoryPanel;
    private RequestsPanel requestsPanel;
    private MatchingPanel matchingPanel;

    public LifeFlowFrame(ArrayList<Donor> donors, ArrayList<BloodUnit> units,
                         ArrayList<BloodRequest> requests, FileManager fileManager) {
        super("LifeFlow - Blood Donation and Emergency Matching");
        controller = new LifeFlowController(donors, units, requests, fileManager);
        configureWindow();
        buildContent();
        refreshAllPages();
        showPage(DASHBOARD);
    }

    private void configureWindow() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1280, 780);
        setMinimumSize(new Dimension(1050, 680));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                try {
                    controller.saveAll();
                    dispose();
                } catch (IOException exception) {
                    JOptionPane.showMessageDialog(LifeFlowFrame.this,
                            "LifeFlow could not save its data.\n" + exception.getMessage(),
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void buildContent() {
        donorsPanel = new DonorsPanel(controller, this::refreshAllPages, this::showStatus);
        inventoryPanel = new InventoryPanel(controller, this::refreshAllPages, this::showStatus);
        requestsPanel = new RequestsPanel(controller, this::refreshAllPages, this::showStatus);
        matchingPanel = new MatchingPanel(controller, this::refreshAllPages, this::showStatus);
        dashboardPanel = new DashboardPanel(controller,
                donorsPanel::showAddDialog,
                inventoryPanel::showAddDialog,
                requestsPanel::showAddDialog,
                matchingPanel::processNextRequest);

        pages.setBackground(UiTheme.BACKGROUND);
        pages.add(new BoundedContentPanel(dashboardPanel), DASHBOARD);
        pages.add(new BoundedContentPanel(donorsPanel), DONORS);
        pages.add(new BoundedContentPanel(inventoryPanel), INVENTORY);
        pages.add(new BoundedContentPanel(requestsPanel), REQUESTS);
        pages.add(new BoundedContentPanel(matchingPanel), MATCHING);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(UiTheme.BACKGROUND);
        main.add(buildUtilityBar(), BorderLayout.NORTH);
        main.add(pages, BorderLayout.CENTER);
        main.add(buildStatusBar(), BorderLayout.SOUTH);

        sidebarPanel = new SidebarPanel(this::showPage);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BACKGROUND);
        root.add(sidebarPanel, BorderLayout.WEST);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel buildUtilityBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UiTheme.SURFACE);
        bar.setPreferredSize(new Dimension(0, UiTheme.UTILITY_HEIGHT));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(0, UiTheme.SPACE_LG,
                        0, UiTheme.SPACE_LG)));
        breadcrumbLabel.setFont(UiTheme.SMALL);
        breadcrumbLabel.setForeground(UiTheme.MUTED);
        bar.add(breadcrumbLabel, BorderLayout.WEST);
        JLabel ready = new JLabel("●  SYSTEM READY");
        ready.setOpaque(true);
        ready.setBackground(UiTheme.SUCCESS_LIGHT);
        ready.setForeground(UiTheme.SUCCESS);
        ready.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 10));
        ready.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
        bar.add(ready, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UiTheme.SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(8, 18, 8, 18)));
        statusLabel.setForeground(UiTheme.NAVY);
        statusLabel.setFont(UiTheme.SMALL);
        bar.add(statusLabel, BorderLayout.WEST);
        JLabel saved = new JLabel("Local data storage", SwingConstants.RIGHT);
        saved.setFont(UiTheme.SMALL);
        saved.setForeground(UiTheme.MUTED);
        bar.add(saved, BorderLayout.EAST);
        return bar;
    }

    private void showPage(String page) {
        pageLayout.show(pages, page);
        if (sidebarPanel != null) {
            sidebarPanel.showActive(page);
        }
        breadcrumbLabel.setText("Workspace / " + displayPageName(page));
    }

    private static String displayPageName(String page) {
        if (page.equals(DASHBOARD)) {
            return "Overview";
        }
        if (page.equals(INVENTORY)) {
            return "Inventory";
        }
        if (page.equals(REQUESTS)) {
            return "Requests";
        }
        return page;
    }

    private void showStatus(String message) {
        statusLabel.setText(message == null || message.isBlank() ? " " : "●  " + message);
        statusLabel.setForeground(message != null && (message.contains("cannot")
                || message.contains("Could not") || message.contains("insufficient"))
                ? UiTheme.DANGER : UiTheme.SUCCESS);
        statusTimer.restart();
    }

    private void refreshAllPages() {
        dashboardPanel.refreshData();
        donorsPanel.refreshData();
        inventoryPanel.refreshData();
        requestsPanel.refreshData();
        matchingPanel.refreshData();
    }
}
