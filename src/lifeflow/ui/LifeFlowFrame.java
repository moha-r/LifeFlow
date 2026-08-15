package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
    private final Map<String, JButton> navigation = new LinkedHashMap<>();
    private final JLabel statusLabel = new JLabel(" ");
    private final Timer statusTimer = new Timer(3500, event -> statusLabel.setText(" "));

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
        pages.add(dashboardPanel, DASHBOARD);
        pages.add(donorsPanel, DONORS);
        pages.add(inventoryPanel, INVENTORY);
        pages.add(requestsPanel, REQUESTS);
        pages.add(matchingPanel, MATCHING);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(UiTheme.BACKGROUND);
        main.add(pages, BorderLayout.CENTER);
        main.add(buildStatusBar(), BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BACKGROUND);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(UiTheme.SURFACE);
        sidebar.setPreferredSize(new Dimension(275, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiTheme.BORDER));

        JPanel top = new JPanel();
        top.setBackground(UiTheme.SURFACE);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(28, 20, 20, 20));
        top.add(buildBrand());
        top.add(Box.createVerticalStrut(34));
        addNavButton(top, DASHBOARD, "◉ Dashboard");
        top.add(Box.createVerticalStrut(7));
        addNavButton(top, DONORS, "♡ Donors");
        top.add(Box.createVerticalStrut(7));
        addNavButton(top, INVENTORY, "▦ Inventory");
        top.add(Box.createVerticalStrut(7));
        addNavButton(top, REQUESTS, "☷ Requests");
        top.add(Box.createVerticalStrut(7));
        addNavButton(top, MATCHING, "⇄ Matching");
        sidebar.add(top, BorderLayout.NORTH);

        JPanel notice = new JPanel();
        notice.setBackground(UiTheme.CORAL_LIGHT);
        notice.setLayout(new BoxLayout(notice, BoxLayout.Y_AXIS));
        notice.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(14, 16, 20, 16),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0xF7CCD6)),
                        BorderFactory.createEmptyBorder(12, 12, 12, 12))));
        JLabel label = new JLabel("Educational simulation");
        label.setFont(UiTheme.BODY_BOLD);
        label.setForeground(UiTheme.CORAL_DARK);
        notice.add(label);
        notice.add(Box.createVerticalStrut(6));
        JLabel copy = new JLabel("<html>Not for medical or<br>transfusion decisions.</html>");
        copy.setFont(UiTheme.SMALL);
        copy.setForeground(UiTheme.MUTED);
        notice.add(copy);
        sidebar.add(notice, BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel buildBrand() {
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        JLabel mark = new JLabel("♥");
        mark.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 31));
        mark.setForeground(UiTheme.CORAL);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("LifeFlow");
        name.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 19));
        name.setForeground(UiTheme.NAVY);
        JLabel subtitle = new JLabel("Give blood. Save lives.");
        subtitle.setFont(UiTheme.SMALL);
        subtitle.setForeground(UiTheme.CORAL);
        copy.add(name);
        copy.add(subtitle);
        brand.add(mark);
        brand.add(copy);
        return brand;
    }

    private void addNavButton(JPanel parent, String page, String text) {
        JButton button = UiComponents.navButton(text);
        button.addActionListener(event -> showPage(page));
        navigation.put(page, button);
        parent.add(button);
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
        for (Map.Entry<String, JButton> entry : navigation.entrySet()) {
            UiComponents.setNavActive(entry.getValue(), entry.getKey().equals(page));
        }
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
