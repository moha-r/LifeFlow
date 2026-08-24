package lifeflow.ui;

import lifeflow.service.CsvReportExporter;
import lifeflow.service.LifeFlowController;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RequestStatus;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("serial")
public final class ReportsPanel extends JPanel {
    private final LifeFlowController controller;
    private final PieChartPanel pieChartPanel;
    private final JLabel donorsValue = metricValue("reportsDonorsValue");
    private final JLabel unitsValue = metricValue("reportsUnitsValue");
    private final JLabel pendingValue = metricValue("reportsPendingValue");
    private final JLabel fulfilledValue = metricValue("reportsFulfilledValue");
    private final JPanel fulfilmentHolder = new JPanel(new CardLayout());
    private final JPanel auditHolder = new JPanel(new CardLayout());
    private DefaultTableModel fulfilmentModel;
    private DefaultTableModel auditModel;

    public ReportsPanel(LifeFlowController controller) {
        this.controller = controller;
        this.pieChartPanel = new PieChartPanel();

        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);

        PageShell shell = new PageShell("Reports & Audit",
                "System analytics, fulfilment history, and operation logs.");

        shell.setActions(buildActions());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(Box.createVerticalStrut(4));
        content.add(buildMetrics());
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        content.add(buildMainRow());
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        content.add(buildAuditCard());

        shell.setBody(content);
        add(shell, BorderLayout.CENTER);
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);

        JButton inventoryButton = UiComponents.secondaryButton("Inventory CSV");
        inventoryButton.addActionListener(e -> exportCsv("inventory_report.csv",
                (path) -> CsvReportExporter.exportInventory(path,
                        controller.getStateSnapshot(), controller.today())));

        JButton donorsButton = UiComponents.secondaryButton("Donors CSV");
        donorsButton.addActionListener(e -> exportCsv("donors_report.csv",
                (path) -> CsvReportExporter.exportDonors(path,
                        controller.getStateSnapshot(), controller.today())));

        JButton requestsButton = UiComponents.secondaryButton("Requests CSV");
        requestsButton.addActionListener(e -> exportCsv("requests_report.csv",
                (path) -> CsvReportExporter.exportRequests(path,
                        controller.getStateSnapshot())));

        JButton appointmentsButton = UiComponents.secondaryButton("Appointments CSV");
        appointmentsButton.addActionListener(e -> exportCsv("appointments_report.csv",
                (path) -> CsvReportExporter.exportAppointments(path,
                        controller.getStateSnapshot())));

        JButton auditButton = UiComponents.secondaryButton("Audit CSV");
        auditButton.addActionListener(e -> exportCsv("audit_report.csv",
                (path) -> CsvReportExporter.exportAudit(path,
                        controller.getStateSnapshot())));

        JButton summaryButton = UiComponents.primaryButton("Summary Report");
        summaryButton.addActionListener(e -> exportCsv("lifeflow_summary.html",
                (path) -> lifeflow.service.HtmlReportExporter.exportSummary(path,
                        controller.getStateSnapshot(), controller.today())));

        actions.add(inventoryButton);
        actions.add(donorsButton);
        actions.add(requestsButton);
        actions.add(appointmentsButton);
        actions.add(auditButton);
        actions.add(summaryButton);
        return actions;
    }

    private void exportCsv(String suggestedName, CsvWriter writer) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Report");
        chooser.setSelectedFile(new File(suggestedName));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            writer.write(chooser.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(this, "Report exported successfully.",
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel buildMetrics() {
        JPanel metrics = new JPanel(new GridLayout(1, 4, 10, 0));
        metrics.setName("reportsMetrics");
        metrics.setOpaque(false);
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        metrics.setPreferredSize(new Dimension(800, 78));
        metrics.add(metricCard("REGISTERED DONORS", donorsValue, UiTheme.CORAL));
        metrics.add(metricCard("AVAILABLE UNITS", unitsValue, UiTheme.SUCCESS));
        metrics.add(metricCard("PENDING REQUESTS", pendingValue, UiTheme.WARNING));
        metrics.add(metricCard("FULFILLED REQUESTS", fulfilledValue, UiTheme.DANGER));
        return metrics;
    }

    private JPanel metricCard(String title, JLabel value, Color accent) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UiTheme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 4, 1, 1, accent),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        heading.setForeground(UiTheme.MUTED);
        card.add(heading, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private static JLabel metricValue(String name) {
        JLabel label = new JLabel("0");
        label.setName(name);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
        label.setForeground(UiTheme.NAVY);
        return label;
    }

    private JPanel buildMainRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 330));
        row.setPreferredSize(new Dimension(900, 330));

        GridBagConstraints chart = new GridBagConstraints();
        chart.gridx = 0;
        chart.gridy = 0;
        chart.weightx = 0.42;
        chart.weighty = 1;
        chart.fill = GridBagConstraints.BOTH;
        chart.insets = new Insets(0, 0, 0, 10);
        row.add(buildChartCard(), chart);

        GridBagConstraints fulfilments = new GridBagConstraints();
        fulfilments.gridx = 1;
        fulfilments.gridy = 0;
        fulfilments.weightx = 0.58;
        fulfilments.weighty = 1;
        fulfilments.fill = GridBagConstraints.BOTH;
        row.add(buildFulfilmentCard(), fulfilments);
        return row;
    }

    private JPanel buildChartCard() {
        JPanel card = UiComponents.densePanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        card.add(cardHeader("Inventory by Blood Type",
                "Available units distribution"), BorderLayout.NORTH);
        card.add(pieChartPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFulfilmentCard() {
        JPanel card = UiComponents.densePanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        card.add(cardHeader("Fulfilment History",
                "Requests matched with blood units"), BorderLayout.NORTH);

        fulfilmentModel = UiComponents.readOnlyModel(
                "Request", "Processed On", "Blood Type", "Units");
        JTable table = new JTable(fulfilmentModel);
        UiComponents.configureTable(table);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(280);

        fulfilmentHolder.add(emptyState("No fulfilled requests yet.\n"
                + "Process a request from the Matching workspace to record it here."),
                "empty");
        fulfilmentHolder.add(UiComponents.tableScroll(table), "table");
        card.add(fulfilmentHolder, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildAuditCard() {
        JPanel card = UiComponents.densePanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        card.add(cardHeader("Audit Trail", "Recent system operations"),
                BorderLayout.NORTH);

        auditModel = UiComponents.readOnlyModel("Timestamp", "Operation");
        JTable table = new JTable(auditModel);
        UiComponents.configureTable(table);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(0).setMaxWidth(170);
        table.getColumnModel().getColumn(1).setPreferredWidth(600);

        auditHolder.add(emptyState("No operations logged yet.\n"
                + "Actions such as adding donors, units, or requests will appear here."),
                "empty");
        auditHolder.add(UiComponents.tableScroll(table), "table");
        card.add(auditHolder, BorderLayout.CENTER);
        return card;
    }

    private JPanel emptyState(String message) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiTheme.SURFACE);
        JLabel label = UiComponents.muted("<html><center>" + message.replace("\n", "<br>")
                + "</center></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label);
        return panel;
    }

    private JPanel cardHeader(String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiTheme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(10, 13, 10, 13)));
        JLabel heading = UiComponents.heading(title);
        heading.setForeground(UiTheme.NAVY);
        JLabel detail = UiComponents.muted(subtitle);
        detail.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(heading, BorderLayout.WEST);
        header.add(detail, BorderLayout.EAST);
        return header;
    }

    public void refreshData() {
        java.time.LocalDate today = controller.today();

        donorsValue.setText(String.valueOf(controller.getDonors().size()));
        unitsValue.setText(String.valueOf(controller.getAvailableUnitCount(today)));
        pendingValue.setText(String.valueOf(controller.getPendingRequestCount()));
        fulfilledValue.setText(String.valueOf(controller.getFulfilments().size()));

        HashMap<BloodType, Integer> stock = new HashMap<>();
        for (BloodUnit unit : controller.getUnits()) {
            if (unit.isAvailable(today)) {
                stock.merge(unit.getBloodType(), 1, Integer::sum);
            }
        }
        pieChartPanel.updateData(stock);

        LifeFlowState snapshot = controller.getStateSnapshot();
        List<BloodRequest> requests = snapshot.getRequests();

        fulfilmentModel.setRowCount(0);
        for (FulfilmentRecord record : snapshot.getFulfilments()) {
            String bloodType = "";
            boolean completed = false;
            for (BloodRequest request : requests) {
                if (request.getId().equalsIgnoreCase(record.requestId())) {
                    bloodType = request.getBloodType().name();
                    completed = request.getStatus() == RequestStatus.FULFILLED;
                    break;
                }
            }
            if (!completed) {
                continue;
            }
            fulfilmentModel.addRow(new Object[]{record.requestId(),
                    record.processedDate().toString(), bloodType,
                    String.join(", ", record.unitIds())});
        }
        showTable(fulfilmentHolder, fulfilmentModel.getRowCount() > 0);

        auditModel.setRowCount(0);
        ArrayList<String> logs = snapshot.getLogs();
        for (String log : logs) {
            if (log.startsWith("[") && log.contains("] ")) {
                int end = log.indexOf("] ");
                auditModel.addRow(new Object[]{log.substring(1, end),
                        log.substring(end + 2)});
            } else {
                auditModel.addRow(new Object[]{"", log});
            }
        }
        showTable(auditHolder, auditModel.getRowCount() > 0);
    }

    private static void showTable(JPanel holder, boolean hasRows) {
        CardLayout layout = (CardLayout) holder.getLayout();
        layout.show(holder, hasRows ? "table" : "empty");
    }

    @FunctionalInterface
    private interface CsvWriter {
        void write(java.nio.file.Path path) throws Exception;
    }
}
