package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.InventoryState;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RequestStatus;
import lifeflow.service.BloodInventory;
import lifeflow.service.LifeFlowController;
import lifeflow.service.MatchingService;

/** Dense operational overview for stock, demand, and the next safe action. */
@SuppressWarnings("serial")
public final class DashboardPanel extends JPanel {
    private final LifeFlowController controller;
    private final JLabel donorsValue = metricValue("totalDonorsValue");
    private final JLabel unitsValue = metricValue("availableUnitsValue");
    private final JLabel pendingValue = metricValue("pendingRequestsValue");
    private final JLabel emergenciesValue = metricValue("emergencyRequestsValue");
    private final JLabel expiringSoonValue = compactMetricValue("expiringSoonValue");
    private final DefaultTableModel inventoryModel = UiComponents.readOnlyModel(
            "Blood Type", "Available", "Stock Level", "Status");
    private final DefaultTableModel requestModel = UiComponents.readOnlyModel(
            "ID", "Requester", "Blood Type", "Qty", "Status");
    private final JTable inventoryTable = new JTable(inventoryModel);
    private final JTable requestTable = new JTable(requestModel);
    private final JLabel nextKind = UiComponents.heading("No pending requests");
    private final JLabel nextRequester = UiComponents.muted("The queue is clear.");
    private final JLabel requiredValue = compactValue("0");
    private final JLabel availableValue = compactValue("0");
    private final JButton processButton = UiComponents.primaryButton("Process request");

    public DashboardPanel(LifeFlowController controller, Runnable addDonor,
                          Runnable addUnit, Runnable addRequest, Runnable processNext) {
        super(new BorderLayout());
        this.controller = controller;
        setBackground(UiTheme.BACKGROUND);

        PageShell shell = new PageShell("Operations overview",
                "Monitor available blood stock and process urgent demand.");
        shell.setActions(buildQuickActions(addDonor, addUnit, addRequest));
        shell.setBody(buildScrollableBody());
        add(shell, BorderLayout.CENTER);

        processButton.addActionListener(event -> processNext.run());
        refreshData();
    }

    private JPanel buildQuickActions(Runnable addDonor, Runnable addUnit,
                                     Runnable addRequest) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        JButton donor = UiComponents.secondaryButton("+ Donor");
        JButton unit = UiComponents.secondaryButton("+ Unit");
        JButton request = UiComponents.primaryButton("+ Request");
        donor.setPreferredSize(new Dimension(94, 34));
        unit.setPreferredSize(new Dimension(86, 34));
        request.setPreferredSize(new Dimension(118, 34));
        donor.addActionListener(event -> addDonor.run());
        unit.addActionListener(event -> addUnit.run());
        request.addActionListener(event -> addRequest.run());
        actions.add(donor);
        actions.add(unit);
        actions.add(request);
        return actions;
    }

    private JScrollPane buildScrollableBody() {
        JPanel content = new ViewportWidthPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JPanel metrics = buildMetrics();
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(metrics);
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        JPanel operations = buildOperationsRow();
        operations.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(operations);
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        JPanel queue = buildRequestQueue();
        queue.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(queue);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setName("dashboardScroll");
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(UiTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildMetrics() {
        JPanel metrics = new JPanel(new GridLayout(1, 4, 10, 0));
        metrics.setName("dashboardMetrics");
        metrics.setOpaque(false);
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        metrics.setPreferredSize(new Dimension(800, 78));
        metrics.add(metricCard("REGISTERED DONORS", donorsValue, UiTheme.CORAL));
        metrics.add(availabilityMetricCard());
        metrics.add(metricCard("PENDING REQUESTS", pendingValue, UiTheme.WARNING));
        metrics.add(metricCard("EMERGENCY", emergenciesValue, UiTheme.DANGER));
        return metrics;
    }

    private JPanel metricCard(String title, JLabel value, Color accent) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UiTheme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 4, 1, 1, accent),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel heading = new JLabel(title);
        heading.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 10));
        heading.setForeground(UiTheme.MUTED);
        card.add(heading, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JPanel availabilityMetricCard() {
        JPanel card = metricCard("AVAILABLE UNITS", unitsValue, UiTheme.SUCCESS);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footer.setOpaque(false);
        JLabel caption = new JLabel("Expiring within 7 days: ");
        caption.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.PLAIN, 9));
        caption.setForeground(UiTheme.MUTED);
        footer.add(caption);
        footer.add(expiringSoonValue);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildOperationsRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 292));
        row.setPreferredSize(new Dimension(900, 292));

        GridBagConstraints inventory = new GridBagConstraints();
        inventory.gridx = 0;
        inventory.gridy = 0;
        inventory.weightx = 0.68;
        inventory.weighty = 1;
        inventory.fill = GridBagConstraints.BOTH;
        inventory.insets = new java.awt.Insets(0, 0, 0, 10);
        row.add(buildInventoryPanel(), inventory);

        GridBagConstraints priority = new GridBagConstraints();
        priority.gridx = 1;
        priority.gridy = 0;
        priority.weightx = 0.32;
        priority.weighty = 1;
        priority.fill = GridBagConstraints.BOTH;
        row.add(buildPriorityPanel(), priority);
        return row;
    }

    private JPanel buildInventoryPanel() {
        JPanel panel = denseSection("Inventory status",
                "Available, non-expired units");
        inventoryTable.setName("inventoryStatusTable");
        UiComponents.configureTable(inventoryTable);
        inventoryTable.setRowHeight(27);
        inventoryTable.getTableHeader().setPreferredSize(new Dimension(0, 32));
        inventoryTable.getColumnModel().getColumn(0).setPreferredWidth(110);
        inventoryTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        inventoryTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        inventoryTable.getColumnModel().getColumn(3)
                .setCellRenderer(UiComponents.statusRenderer());
        JScrollPane scroll = new JScrollPane(inventoryTable);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPriorityPanel() {
        JPanel panel = denseSection("Next fulfilable request",
                "Highest priority request with full stock");
        panel.setName("priorityRequestPanel");
        JPanel content = new JPanel();
        content.setBackground(UiTheme.SURFACE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 15, 14, 15));
        content.add(nextKind);
        content.add(Box.createVerticalStrut(5));
        content.add(nextRequester);
        content.add(Box.createVerticalStrut(13));

        JPanel quantities = new JPanel(new GridLayout(1, 2, 7, 0));
        quantities.setOpaque(false);
        quantities.add(quantityBox("REQUIRED", requiredValue));
        quantities.add(quantityBox("AVAILABLE", availableValue));
        quantities.setMaximumSize(new Dimension(Integer.MAX_VALUE, 61));
        content.add(quantities);
        content.add(Box.createVerticalGlue());
        processButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        processButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        processButton.setPreferredSize(new Dimension(160, 34));
        content.add(processButton);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel quantityBox(String title, JLabel value) {
        JPanel box = new JPanel(new BorderLayout(0, 3));
        box.setBackground(new Color(0xF6F7F9));
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)));
        JLabel label = new JLabel(title);
        label.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 9));
        label.setForeground(UiTheme.MUTED);
        box.add(label, BorderLayout.NORTH);
        box.add(value, BorderLayout.CENTER);
        return box;
    }

    private JPanel buildRequestQueue() {
        JPanel panel = denseSection("Request queue", "Current request state");
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        panel.setPreferredSize(new Dimension(900, 160));
        requestTable.setName("requestQueueTable");
        UiComponents.configureTable(requestTable);
        requestTable.setRowHeight(30);
        requestTable.getTableHeader().setPreferredSize(new Dimension(0, 32));
        requestTable.getColumnModel().getColumn(4)
                .setCellRenderer(UiComponents.statusRenderer());
        JScrollPane scroll = new JScrollPane(requestTable);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel denseSection(String title, String caption) {
        JPanel panel = UiComponents.densePanel(new BorderLayout());
        panel.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiTheme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(10, 13, 10, 13)));
        JLabel heading = new JLabel(title);
        heading.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 13));
        heading.setForeground(UiTheme.NAVY);
        JLabel detail = new JLabel(caption, SwingConstants.RIGHT);
        detail.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.PLAIN, 10));
        detail.setForeground(UiTheme.MUTED);
        header.add(heading, BorderLayout.WEST);
        header.add(detail, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        return panel;
    }

    public final void refreshData() {
        LocalDate today = controller.today();
        LifeFlowState snapshot = controller.getStateSnapshot();
        donorsValue.setText(Integer.toString(snapshot.getDonors().size()));
        int pending = 0;
        int emergencies = 0;
        for (BloodRequest request : snapshot.getRequests()) {
            if (request.getStatus() == RequestStatus.PENDING) {
                pending++;
                if (request instanceof EmergencyRequest) {
                    emergencies++;
                }
            }
        }
        pendingValue.setText(Integer.toString(pending));
        emergenciesValue.setText(Integer.toString(emergencies));

        HashMap<BloodType, Integer> stock = new HashMap<>();
        HashMap<BloodType, Integer> expiringByType = new HashMap<>();
        for (BloodType type : BloodType.values()) {
            stock.put(type, 0);
            expiringByType.put(type, 0);
        }
        int availableUnits = 0;
        int expiringSoon = 0;
        for (BloodUnit unit : snapshot.getUnits()) {
            if (unit.getInventoryState(today) != InventoryState.AVAILABLE) {
                continue;
            }
            availableUnits++;
            stock.merge(unit.getBloodType(), 1, Integer::sum);
            long days = ChronoUnit.DAYS.between(today, unit.getExpiryDate());
            if (days >= 0 && days <= 7) {
                expiringSoon++;
                expiringByType.merge(unit.getBloodType(), 1, Integer::sum);
            }
        }
        unitsValue.setText(Integer.toString(availableUnits));
        expiringSoonValue.setText(Integer.toString(expiringSoon));
        inventoryModel.setRowCount(0);
        for (BloodType type : BloodType.values()) {
            int count = stock.get(type);
            int expiring = expiringByType.get(type);
            inventoryModel.addRow(new Object[]{displayType(type), count,
                    Math.min(100, count * 33) + "%", stockStatus(count)
                    + (expiring == 0 ? "" : " · " + expiring + " EXPIRING")});
        }

        requestModel.setRowCount(0);
        for (BloodRequest request : snapshot.getRequests()) {
            String status;
            if (request.getStatus() != RequestStatus.PENDING) {
                status = request.getStatus().name();
            } else if (stock.get(request.getBloodType()) >= request.getQuantity()) {
                status = request instanceof EmergencyRequest
                        ? "EMERGENCY · READY" : "READY TO MATCH";
            } else {
                status = request instanceof EmergencyRequest
                        ? "EMERGENCY · WAITING" : "WAITING FOR STOCK";
            }
            requestModel.addRow(new Object[]{request.getId(), request.getRequesterName(),
                    displayType(request.getBloodType()), request.getQuantity(), status});
        }

        BloodRequest request = nextFulfillable(snapshot, today);
        if (request == null) {
            BloodRequest waiting = nextPending(snapshot);
            if (waiting == null) {
                nextKind.setText("No pending requests");
                nextKind.setForeground(UiTheme.NAVY);
                nextRequester.setText("The queue is clear.");
                requiredValue.setText("0");
                availableValue.setText("0");
                availableValue.setForeground(UiTheme.MUTED);
            } else {
                int available = stock.get(waiting.getBloodType());
                nextKind.setText("Waiting for stock · " + waiting.getId());
                nextKind.setForeground(UiTheme.DANGER);
                nextRequester.setText(waiting.getRequesterName() + " · "
                        + displayType(waiting.getBloodType()));
                requiredValue.setText(Integer.toString(waiting.getQuantity()));
                availableValue.setText(Integer.toString(available));
                availableValue.setForeground(UiTheme.DANGER);
            }
            processButton.setEnabled(false);
        } else {
            int available = stock.get(request.getBloodType());
            nextKind.setText(request.getKind() + " · " + request.getId());
            nextKind.setForeground(request instanceof EmergencyRequest
                    ? UiTheme.DANGER : UiTheme.NAVY);
            nextRequester.setText(request.getRequesterName() + " · "
                    + displayType(request.getBloodType()));
            requiredValue.setText(Integer.toString(request.getQuantity()));
            availableValue.setText(Integer.toString(available));
            availableValue.setForeground(available >= request.getQuantity()
                    ? UiTheme.SUCCESS : UiTheme.DANGER);
            processButton.setEnabled(true);
        }
    }

    private static BloodRequest nextPending(LifeFlowState snapshot) {
        return new MatchingService(BloodInventory.from(snapshot.getUnits()))
                .findNextPending(snapshot.getRequests());
    }

    private static BloodRequest nextFulfillable(LifeFlowState snapshot,
                                                 LocalDate date) {
        return new MatchingService(BloodInventory.from(snapshot.getUnits()))
                .findNextFulfillable(snapshot.getRequests(), date);
    }

    private static String stockStatus(int count) {
        if (count == 0) {
            return "EMPTY";
        }
        return count <= 2 ? "LOW" : "READY";
    }

    private static JLabel metricValue(String name) {
        JLabel label = new JLabel("0");
        label.setName(name);
        label.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 23));
        label.setForeground(UiTheme.NAVY);
        return label;
    }

    private static JLabel compactValue(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 16));
        label.setForeground(UiTheme.NAVY);
        return label;
    }

    private static JLabel compactMetricValue(String name) {
        JLabel label = new JLabel("0");
        label.setName(name);
        label.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 9));
        label.setForeground(UiTheme.WARNING);
        return label;
    }

    static String displayType(BloodType type) {
        return type.name().replace("_POS", "+").replace("_NEG", "-");
    }

    private static final class ViewportWidthPanel extends JPanel
            implements Scrollable {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect,
                                              int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect,
                                               int orientation, int direction) {
            return Math.max(16, visibleRect.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
