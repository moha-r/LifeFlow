package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import lifeflow.model.Hospital;
import lifeflow.model.LifeFlowState;
import lifeflow.persistence.LifeFlowStore;
import lifeflow.persistence.StorageInfo;
import lifeflow.service.HospitalRegistry;
import lifeflow.service.LifeFlowController;

/** Modern application shell with sidebar navigation and live page refresh. */
@SuppressWarnings({"serial", "this-escape"})
public final class LifeFlowFrame extends JFrame {
    private static final String DASHBOARD = "Dashboard";
    private static final String DONORS = "Donors";
    private static final String INVENTORY = "Blood Inventory";
    private static final String REQUESTS = "Blood Requests";
    private static final String MATCHING = "Matching";
    private static final String REPORTS = "Reports";
    private static final String APPOINTMENTS = "Appointments";
    private static final String CENTERS = "Donation Centers";

    private final LifeFlowController controller;
    private final HospitalRegistry registry;
    private final SessionSwitcher switcher;
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel breadcrumbLabel = new JLabel("Workspace / Overview");
    private final JLabel storageHealthLabel = new JLabel();
    private final JLabel storagePathLabel = new JLabel();
    private final Timer statusTimer = new Timer(3500, event -> statusLabel.setText(" "));
    private SidebarPanel sidebarPanel;

    private DashboardPanel dashboardPanel;
    private ReportsPanel reportsPanel;
    private DonorsPanel donorsPanel;
    private InventoryPanel inventoryPanel;
    private RequestsPanel requestsPanel;
    private MatchingPanel matchingPanel;
    private AppointmentsPanel appointmentsPanel;
    private HospitalsPanel centersPanel;

    public LifeFlowFrame(LifeFlowState state, LifeFlowStore store,
                         HospitalRegistry registry, SessionSwitcher switcher) {
        super("LifeFlow - Blood Donation and Emergency Matching");
        this.registry = registry;
        this.switcher = switcher;
        controller = new LifeFlowController(state, store);
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
                closeApplication();
            }
        });
    }

    private void buildContent() {
        donorsPanel = new DonorsPanel(controller, this::refreshAllPages, this::showStatus);
        inventoryPanel = new InventoryPanel(controller, this::refreshAllPages, this::showStatus);
        requestsPanel = new RequestsPanel(controller, this::refreshAllPages, this::showStatus);
        matchingPanel = new MatchingPanel(controller, this::refreshAllPages, this::showStatus);
        reportsPanel = new ReportsPanel(controller);
        appointmentsPanel = new AppointmentsPanel(controller, registry,
                this::refreshAllPages, this::showStatus);
        centersPanel = new HospitalsPanel(registry, controller,
                this::refreshAllPages, this::showStatus);
        dashboardPanel = new DashboardPanel(controller,
                donorsPanel::showAddDialog,
                inventoryPanel::showAddDialog,
                requestsPanel::showAddDialog,
                () -> showPage(MATCHING));

        pages.setBackground(UiTheme.BACKGROUND);
        pages.add(new BoundedContentPanel(dashboardPanel), DASHBOARD);
        pages.add(new BoundedContentPanel(donorsPanel), DONORS);
        pages.add(new BoundedContentPanel(inventoryPanel), INVENTORY);
        pages.add(new BoundedContentPanel(requestsPanel), REQUESTS);
        pages.add(new BoundedContentPanel(matchingPanel), MATCHING);
        pages.add(new BoundedContentPanel(reportsPanel), REPORTS);
        pages.add(new BoundedContentPanel(appointmentsPanel), APPOINTMENTS);
        pages.add(new BoundedContentPanel(centersPanel), CENTERS);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(UiTheme.BACKGROUND);
        main.add(buildUtilityBar(), BorderLayout.NORTH);
        main.add(pages, BorderLayout.CENTER);
        main.add(buildStatusBar(), BorderLayout.SOUTH);

        sidebarPanel = new SidebarPanel(this::showPage);
        sidebarPanel.setSignOutHandler(this::signOut);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BACKGROUND);
        root.add(sidebarPanel, BorderLayout.WEST);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
    }

    private void signOut() {
        releaseAndDispose();
        switcher.showLogin();
    }

    private void closeApplication() {
        releaseAndDispose();
        switcher.exitApplication();
    }

    private void releaseAndDispose() {
        try {
            controller.close();
            dispose();
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(LifeFlowFrame.this,
                    "LifeFlow could not release its storage lock.\n"
                            + exception.getMessage(),
                    "Close Error", JOptionPane.ERROR_MESSAGE);
            dispose();
        }
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
        storageHealthLabel.setOpaque(true);
        storageHealthLabel.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 10));
        storageHealthLabel.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
        bar.add(storageHealthLabel, BorderLayout.EAST);
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
        storagePathLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        storagePathLabel.setFont(UiTheme.SMALL);
        storagePathLabel.setForeground(UiTheme.MUTED);
        bar.add(storagePathLabel, BorderLayout.EAST);
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

    private void showStatus(UiNotice notice) {
        StorageInfo storage = controller.getStorageInfo();
        boolean backupPending = !storage.backupCurrent()
                && !storage.recoveryRequired() && controller.getRevision() > 0;
        if (backupPending && notice != null
                && notice.level() == NoticeLevel.SUCCESS) {
            notice = UiNotice.warning("Data saved to JSON; backup needs retry.");
        }
        String message = notice == null ? "" : notice.message();
        statusLabel.setText(message.isBlank() ? " " : "●  " + message);
        NoticeLevel level = notice == null ? NoticeLevel.INFO : notice.level();
        statusLabel.setForeground(switch (level) {
            case SUCCESS -> UiTheme.SUCCESS;
            case WARNING -> UiTheme.WARNING;
            case ERROR -> UiTheme.DANGER;
            case INFO -> UiTheme.NAVY;
        });
        statusTimer.restart();
    }

    private void refreshAllPages() {
        try {
            controller.autoDeclineStaleRequests();
        } catch (IOException ignored) {
            // A stale-request tidy is best effort; the next refresh retries.
        }
        dashboardPanel.refreshData();
        donorsPanel.refreshData();
        inventoryPanel.refreshData();
        requestsPanel.refreshData();
        matchingPanel.refreshData();
        reportsPanel.refreshData();
        appointmentsPanel.refreshData();
        centersPanel.refreshData();
        refreshStorageStatus();
    }

    private void refreshStorageStatus() {
        StorageInfo info = controller.getStorageInfo();
        String state;
        if (info.recoveryRequired()) {
            state = "●  RECOVERY REQUIRED";
            storageHealthLabel.setBackground(UiTheme.DANGER_LIGHT);
            storageHealthLabel.setForeground(UiTheme.DANGER);
        } else if (!info.backupCurrent()) {
            state = "●  BACKUP PENDING";
            storageHealthLabel.setBackground(UiTheme.WARNING_LIGHT);
            storageHealthLabel.setForeground(UiTheme.WARNING);
        } else {
            state = "●  STORAGE READY";
            storageHealthLabel.setBackground(UiTheme.SUCCESS_LIGHT);
            storageHealthLabel.setForeground(UiTheme.SUCCESS);
        }
        storageHealthLabel.setText(state);
        storageHealthLabel.setToolTipText(info.detail());
        String path = info.dataFile().toAbsolutePath().normalize().toString();
        storagePathLabel.setText("JSON · " + path);
        storagePathLabel.setToolTipText(path);
    }
}
