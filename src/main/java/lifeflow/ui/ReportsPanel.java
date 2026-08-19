package lifeflow.ui;

import lifeflow.service.LifeFlowController;
import lifeflow.service.CsvReportExporter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

public class ReportsPanel extends JPanel {
    private final LifeFlowController controller;
    private final PieChartPanel pieChartPanel;
    private final JTable auditTable;
    private final DefaultTableModel tableModel;

    public ReportsPanel(LifeFlowController controller) {
        this.controller = controller;
        this.pieChartPanel = new PieChartPanel();
        
        setLayout(new BorderLayout(10, 10));
        setBackground(UiTheme.BACKGROUND);

        PageShell shell = new PageShell("Reports & Audit", "System analytics and operation logs.");
        
        // Export Actions
        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        exportPanel.setOpaque(false);
        JButton exportBtn = lifeflow.ui.UiComponents.primaryButton("Export Inventory CSV");
        exportBtn.addActionListener(e -> {
            try {
                CsvReportExporter.exportInventory(java.nio.file.Path.of(System.getProperty("user.home"), "Desktop", "Inventory_Report.csv"), controller.getStateSnapshot(), java.time.LocalDate.now());
                JOptionPane.showMessageDialog(this, "Inventory exported to CSV successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to export: " + ex.getMessage());
            }
        });
        exportPanel.add(exportBtn);
        shell.setActions(exportPanel);
        
        JPanel content = new JPanel(new GridLayout(1, 2, 20, 20));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Left side: Pie Chart
        JPanel chartContainer = new JPanel();
        chartContainer.setLayout(new BorderLayout());
        JLabel chartTitle = new JLabel("Inventory by Blood Type");
        chartTitle.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 16));
        chartContainer.add(chartTitle, BorderLayout.NORTH);
        chartContainer.add(pieChartPanel, BorderLayout.CENTER);
        content.add(chartContainer);

        // Right side: Audit Trail
        JPanel auditContainer = new JPanel();
        auditContainer.setLayout(new BorderLayout());
        JLabel auditTitle = new JLabel("Audit Trail");
        auditTitle.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 16));
        auditContainer.add(auditTitle, BorderLayout.NORTH);
        
        tableModel = new DefaultTableModel(new String[]{"Operation Log"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        auditTable = new JTable(tableModel);
        auditTable.setFillsViewportHeight(true);
        auditTable.setRowHeight(30);
        auditContainer.add(new JScrollPane(auditTable), BorderLayout.CENTER);
        content.add(auditContainer);

        shell.setBody(content);
        add(shell, BorderLayout.CENTER);
    }

    public void refreshData() {
        java.util.HashMap<lifeflow.model.BloodType, Integer> map = new java.util.HashMap<>();
        for (lifeflow.model.BloodUnit u : controller.getUnits()) {
            if (u.isAvailable(java.time.LocalDate.now())) {
                map.put(u.getBloodType(), map.getOrDefault(u.getBloodType(), 0) + 1);
            }
        }
        pieChartPanel.updateData(map);
        
        tableModel.setRowCount(0);
        ArrayList<String> logs = controller.getStateSnapshot().getLogs();
        for (String log : logs) {
            tableModel.addRow(new Object[]{log});
        }
    }
}
