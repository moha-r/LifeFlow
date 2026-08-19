package lifeflow.ui;

import lifeflow.service.CsvReportExporter;
import lifeflow.service.LifeFlowController;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.LifeFlowState;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

@SuppressWarnings("serial")
public class ReportsPanel extends JPanel {
    private final LifeFlowController controller;
    private final PieChartPanel pieChartPanel;
    private JTable auditTable;
    private DefaultTableModel tableModel;

    public ReportsPanel(LifeFlowController controller) {
        this.controller = controller;
        this.pieChartPanel = new PieChartPanel();

        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);

        PageShell shell = new PageShell("Reports & Audit",
                "System analytics, export tools, and operation logs.");

        // Export action button
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actionPanel.setOpaque(false);
        JButton exportBtn = UiComponents.primaryButton("Export Inventory CSV");
        exportBtn.setPreferredSize(new Dimension(180, 34));
        exportBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Inventory Report");
            chooser.setSelectedFile(new java.io.File("inventory_report.csv"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    CsvReportExporter.exportInventory(
                            chooser.getSelectedFile().toPath(),
                            controller.getStateSnapshot(),
                            controller.today());
                    JOptionPane.showMessageDialog(this,
                            "Report exported successfully.",
                            "Export Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "Export failed: " + ex.getMessage(),
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        actionPanel.add(exportBtn);
        shell.setActions(actionPanel);

        // Main content: two cards side by side
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(
                UiTheme.SPACE_SM, 0, UiTheme.SPACE_SM, 0));

        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0; left.gridy = 0;
        left.weightx = 0.45; left.weighty = 1;
        left.fill = GridBagConstraints.BOTH;
        left.insets = new Insets(0, 0, 0, UiTheme.SPACE_SM);
        content.add(buildChartCard(), left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1; right.gridy = 0;
        right.weightx = 0.55; right.weighty = 1;
        right.fill = GridBagConstraints.BOTH;
        content.add(buildAuditCard(), right);

        shell.setBody(content);
        add(shell, BorderLayout.CENTER);
    }

    private JPanel buildChartCard() {
        JPanel card = UiComponents.densePanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        JPanel header = cardHeader("Inventory by Blood Type",
                "Available units distribution");
        card.add(header, BorderLayout.NORTH);
        card.add(pieChartPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildAuditCard() {
        JPanel card = UiComponents.densePanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        JPanel header = cardHeader("Audit Trail", "Recent system operations");
        card.add(header, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
                new String[]{"Timestamp", "Operation"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        auditTable = new JTable(tableModel);
        UiComponents.configureTable(auditTable);
        auditTable.setRowHeight(28);
        auditTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        auditTable.getColumnModel().getColumn(0).setMaxWidth(170);
        auditTable.getColumnModel().getColumn(1).setPreferredWidth(400);

        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.setBackground(UiTheme.SURFACE);
        tableWrapper.add(UiComponents.tableScroll(auditTable),
                BorderLayout.CENTER);

        // Empty state
        JPanel emptyState = new JPanel(new GridBagLayout());
        emptyState.setBackground(UiTheme.SURFACE);
        JLabel emptyLabel = UiComponents.muted(
                "No operations logged yet. Actions will appear here.");
        emptyState.add(emptyLabel);

        card.add(tableWrapper, BorderLayout.CENTER);
        return card;
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
        // Update pie chart
        HashMap<BloodType, Integer> map = new HashMap<>();
        for (BloodUnit u : controller.getUnits()) {
            if (u.isAvailable(controller.today())) {
                map.put(u.getBloodType(),
                        map.getOrDefault(u.getBloodType(), 0) + 1);
            }
        }
        pieChartPanel.updateData(map);

        // Update audit trail
        tableModel.setRowCount(0);
        ArrayList<String> logs = controller.getStateSnapshot().getLogs();
        for (String log : logs) {
            // Parse "[timestamp] message" format
            if (log.startsWith("[") && log.contains("] ")) {
                int end = log.indexOf("] ");
                String ts = log.substring(1, end);
                String msg = log.substring(end + 2);
                tableModel.addRow(new Object[]{ts, msg});
            } else {
                tableModel.addRow(new Object[]{"", log});
            }
        }
    }
}
