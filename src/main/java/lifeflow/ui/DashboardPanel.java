package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
import javax.swing.JTable;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
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

/** Organized operational overview: key metrics, stock health, and the next safe action. */
@SuppressWarnings("serial")
public final class DashboardPanel extends JPanel {
    private final LifeFlowController controller;
    private final JLabel donorsValue = metricValue("totalDonorsValue");
    private final JLabel unitsValue = metricValue("availableUnitsValue");
    private final JLabel pendingValue = metricValue("pendingRequestsValue");
    private final JLabel emergenciesValue = metricValue("emergencyRequestsValue");
    private final JLabel appointmentsValue = metricValue("upcomingAppointmentsValue");
    private final JLabel expiringSoonValue = compactMetricValue("expiringSoonValue");
    private final StockCell[] stockCells = createStockCells();
    private final DefaultTableModel eligibleDonorsModel = new DefaultTableModel(
            new String[]{"Donor", "Type", "Eligible Since"}, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };
    private final DefaultTableModel requestModel = UiComponents.readOnlyModel(
            "ID", "Requester", "Blood Type", "Qty", "Status");
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

        JPanel summary = buildSummary();
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(summary);
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

    private JPanel buildSummary() {
        JPanel summary = UiComponents.densePanel(new BorderLayout());
        summary.setName("dashboardSummary");
        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        summary.setPreferredSize(new Dimension(800, 72));

        JPanel metrics = new JPanel(new GridLayout(1, 5, 0, 0));
        metrics.setName("dashboardMetrics");
        metrics.setOpaque(false);
        metrics.add(summaryMetric("DONORS", donorsValue, null, false));
        metrics.add(summaryMetric("AVAILABLE UNITS", unitsValue,
                expiringSoonValue, true));
        metrics.add(summaryMetric("PENDING", pendingValue, null, true));
        emergenciesValue.setForeground(UiTheme.DANGER);
        metrics.add(summaryMetric("EMERGENCIES", emergenciesValue, null, true));
        metrics.add(summaryMetric("APPOINTMENTS", appointmentsValue, null, true));
        summary.add(metrics, BorderLayout.CENTER);
        return summary;
    }

    private JPanel summaryMetric(String title, JLabel value, JLabel detail,
                                 boolean separated) {
        JPanel metric = new JPanel(new BorderLayout(0, 1));
        metric.setOpaque(false);
        metric.setBorder(BorderFactory.createCompoundBorder(
                separated
                        ? BorderFactory.createMatteBorder(0, 1, 0, 0,
                        UiTheme.BORDER)
                        : BorderFactory.createEmptyBorder(),
                BorderFactory.createEmptyBorder(9, 14, 8, 14)));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        heading.setForeground(UiTheme.MUTED);
        metric.add(heading, BorderLayout.NORTH);
        metric.add(value, BorderLayout.CENTER);
        if (detail != null) {
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            footer.setOpaque(false);
            JLabel caption = new JLabel("Expiring soon  ");
            caption.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
            caption.setForeground(UiTheme.MUTED);
            footer.add(caption);
            footer.add(detail);
            metric.add(footer, BorderLayout.SOUTH);
        }
        return metric;
    }

    private JPanel buildOperationsRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        row.setPreferredSize(new Dimension(900, 340));

        GridBagConstraints action = new GridBagConstraints();
        action.gridx = 0;
        action.gridy = 0;
        action.weightx = 0.38;
        action.weighty = 1;
        action.fill = GridBagConstraints.BOTH;
        action.insets = new java.awt.Insets(0, 0, 0, 12);
        row.add(buildActionColumn(), action);

        GridBagConstraints stock = new GridBagConstraints();
        stock.gridx = 1;
        stock.gridy = 0;
        stock.weightx = 0.62;
        stock.weighty = 1;
        stock.fill = GridBagConstraints.BOTH;
        row.add(buildStockPanel(), stock);
        return row;
    }

    private JPanel buildActionColumn() {
        JPanel column = new JPanel(new GridBagLayout());
        column.setOpaque(false);
        column.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));

        GridBagConstraints priority = new GridBagConstraints();
        priority.gridx = 0;
        priority.gridy = 0;
        priority.weightx = 1;
        priority.weighty = 0.63;
        priority.fill = GridBagConstraints.BOTH;
        priority.insets = new java.awt.Insets(0, 0, 12, 0);
        column.add(buildPriorityPanel(), priority);

        GridBagConstraints recall = new GridBagConstraints();
        recall.gridx = 0;
        recall.gridy = 1;
        recall.weightx = 1;
        recall.weighty = 0.37;
        recall.fill = GridBagConstraints.BOTH;
        column.add(buildEligiblePanel(), recall);
        return column;
    }

    private JPanel buildStockPanel() {
        JPanel panel = denseSection("Blood availability",
                "Available and non-expired");
        panel.setName("inventoryOverviewPanel");
        JPanel grid = new JPanel(new GridLayout(2, 4, 0, 0));
        grid.setName("inventoryStockGrid");
        grid.setBackground(UiTheme.SURFACE);
        for (StockCell cell : stockCells) {
            grid.add(cell);
        }
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPriorityPanel() {
        JPanel panel = denseSection("Next action", "Highest priority ready");
        panel.setName("nextActionPanel");
        JPanel content = new JPanel();
        content.setName("priorityRequestPanel");
        content.setBackground(UiTheme.SURFACE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        nextKind.setAlignmentX(Component.LEFT_ALIGNMENT);
        nextRequester.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(nextKind);
        content.add(Box.createVerticalStrut(5));
        content.add(nextRequester);
        content.add(Box.createVerticalStrut(12));

        JPanel quantities = new JPanel(new GridLayout(1, 2, 8, 0));
        quantities.setOpaque(false);
        quantities.add(quantityBox("REQUIRED", requiredValue));
        quantities.add(quantityBox("AVAILABLE", availableValue));
        quantities.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        content.add(quantities);
        content.add(Box.createVerticalGlue());
        processButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        processButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        processButton.setPreferredSize(new Dimension(170, 36));
        content.add(processButton);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildEligiblePanel() {
        JPanel panel = denseSection("Eligible for recall",
                "Donors ready to donate again");
        JTable eligibleTable = new JTable(eligibleDonorsModel);
        UiComponents.configureTable(eligibleTable);
        eligibleTable.setRowHeight(26);
        eligibleTable.getTableHeader().setPreferredSize(new Dimension(0, 30));
        JScrollPane scroll = new JScrollPane(eligibleTable);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel quantityBox(String title, JLabel value) {
        JPanel box = new RoundedBox(new BorderLayout(0, 3));
        box.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JLabel label = new JLabel(title);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        label.setForeground(UiTheme.MUTED);
        box.add(label, BorderLayout.NORTH);
        box.add(value, BorderLayout.CENTER);
        return box;
    }

    private JPanel buildRequestQueue() {
        JPanel panel = denseSection("Request queue", "Current request state");
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        panel.setPreferredSize(new Dimension(900, 190));
        requestTable.setName("requestQueueTable");
        UiComponents.configureTable(requestTable);
        requestTable.setRowHeight(32);
        requestTable.getTableHeader().setPreferredSize(new Dimension(0, 34));
        requestTable.getColumnModel().getColumn(4)
                .setCellRenderer(UiComponents.statusRenderer());
        JScrollPane scroll = new JScrollPane(requestTable);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel denseSection(String title, String caption) {
        JPanel panel = UiComponents.densePanel(new BorderLayout());
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(11, 14, 11, 14)));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        heading.setForeground(UiTheme.NAVY);
        JLabel detail = new JLabel(caption, SwingConstants.RIGHT);
        detail.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
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
        appointmentsValue.setText(Integer.toString(
                controller.getUpcomingAppointmentCount()));

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
        eligibleDonorsModel.setRowCount(0);
        for (lifeflow.model.Donor d : snapshot.getDonors()) {
            lifeflow.model.EligibilityResult er =
                    controller.checkDonorEligibility(d.getId(), today);
            if (er.eligible()) {
                eligibleDonorsModel.addRow(new Object[]{d.getName(),
                        displayType(d.getBloodType()),
                        er.lastDonationDate() == null ? "Never"
                                : er.lastDonationDate()});
            }
        }
        unitsValue.setText(Integer.toString(availableUnits));
        expiringSoonValue.setText(Integer.toString(expiringSoon));
        for (BloodType type : BloodType.values()) {
            int count = stock.get(type);
            int expiring = expiringByType.get(type);
            stockCells[type.ordinal()].update(count, expiring,
                    stockStatus(count));
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

    private static StockCell[] createStockCells() {
        BloodType[] types = BloodType.values();
        StockCell[] cells = new StockCell[types.length];
        for (int index = 0; index < types.length; index++) {
            cells[index] = new StockCell(types[index], index);
        }
        return cells;
    }

    private static JLabel metricValue(String name) {
        JLabel label = new JLabel("0");
        label.setName(name);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        label.setForeground(UiTheme.NAVY);
        return label;
    }

    private static JLabel compactValue(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        label.setForeground(UiTheme.NAVY);
        return label;
    }

    private static JLabel compactMetricValue(String name) {
        JLabel label = new JLabel("0");
        label.setName(name);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        label.setForeground(UiTheme.WARNING);
        return label;
    }

    static String displayType(BloodType type) {
        return type.name().replace("_POS", "+").replace("_NEG", "-");
    }

    /** One cell in the eight-type stock matrix. */
    private static final class StockCell extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JLabel count = new JLabel("0", SwingConstants.RIGHT);
        private final JLabel state = new JLabel("EMPTY");
        private final JLabel expiry = new JLabel("No expiry risk",
                SwingConstants.RIGHT);

        private StockCell(BloodType type, int index) {
            super(new BorderLayout(8, 8));
            setBackground(index % 2 == 0 ? UiTheme.SURFACE
                    : UiTheme.ROW_ALT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0,
                            index < 4 ? 1 : 0,
                            index % 4 == 3 ? 0 : 1, UiTheme.BORDER),
                    BorderFactory.createEmptyBorder(13, 14, 12, 14)));

            JLabel bloodType = new JLabel(displayType(type));
            bloodType.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            bloodType.setForeground(UiTheme.NAVY);
            count.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            count.setForeground(UiTheme.NAVY);
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(bloodType, BorderLayout.WEST);
            top.add(count, BorderLayout.EAST);
            add(top, BorderLayout.CENTER);

            state.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            expiry.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
            expiry.setForeground(UiTheme.MUTED);
            JPanel bottom = new JPanel(new BorderLayout(6, 0));
            bottom.setOpaque(false);
            bottom.add(state, BorderLayout.WEST);
            bottom.add(expiry, BorderLayout.EAST);
            add(bottom, BorderLayout.SOUTH);
        }

        private void update(int available, int expiring, String status) {
            count.setText(Integer.toString(available));
            state.setText(status);
            state.setForeground(switch (status) {
                case "READY" -> UiTheme.SUCCESS;
                case "LOW" -> UiTheme.WARNING;
                default -> UiTheme.DANGER;
            });
            expiry.setText(expiring == 0 ? "No expiry risk"
                    : expiring + " expiring soon");
            expiry.setForeground(expiring == 0 ? UiTheme.MUTED
                    : UiTheme.WARNING);
        }
    }

    /** Soft pill box used for the required/available quantity pair. */
    private static final class RoundedBox extends JPanel {
        private static final long serialVersionUID = 1L;

        private RoundedBox(java.awt.LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(new Color(0xF6F8FB));
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            copy.setColor(UiTheme.BORDER);
            copy.setStroke(new java.awt.BasicStroke(1f));
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            copy.dispose();
            super.paintComponent(graphics);
        }
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
