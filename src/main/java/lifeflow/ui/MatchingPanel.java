package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.LifeFlowState;
import lifeflow.model.exception.LifeFlowException;
import lifeflow.service.BloodInventory;
import lifeflow.service.LifeFlowController;
import lifeflow.model.MatchOutcome;
import lifeflow.model.MatchResult;

@SuppressWarnings("serial")
public final class MatchingPanel extends JPanel implements lifeflow.service.StateObserver {
    private final LifeFlowController controller;
    private final Consumer<UiNotice> status;

    private final DefaultTableModel requestsModel = UiComponents.readOnlyModel("Request ID", "Kind", "Blood Type", "Qty");
    private final JTable requestsTable = new JTable(requestsModel);
    private final JComboBox<lifeflow.model.MatchMode> matchModeSelector = new JComboBox<>(lifeflow.model.MatchMode.values());

    private final DefaultTableModel compatibleModel = UiComponents.readOnlyModel("Unit ID", "Donor", "Expiry", "Action");
    private final JTable compatibleTable = new JTable(compatibleModel);
    { compatibleTable.setName("compatibleUnits"); }

    private final CardLayout analysisLayout = new CardLayout();
    private final JLabel matchingResult = new JLabel();
    private final JLabel atomicNotice = new JLabel("changes nothing");
    { atomicNotice.setName("atomicNotice"); }
    { matchingResult.setName("matchingResult"); }
    private final JPanel analysisPanel = new JPanel(analysisLayout);

    private final JLabel requestTitle = UiComponents.heading("Select a request");
    { requestTitle.setName("selectedMatchingRequest"); }
    private final JLabel requestSubtitle = UiComponents.muted("Details will appear here");
    private final JLabel requiredValue = valueLabel("-");
    private final JLabel availableValue = valueLabel("-");
    private final JButton processButton = UiComponents.primaryButton("Fulfill Selected Request");
    { processButton.setName("processMatchingButton"); }

    private String selectedRequestId = null;

    public MatchingPanel(LifeFlowController controller, Runnable onDataChanged, Consumer<UiNotice> status) {
        super(new BorderLayout());
        this.controller = controller;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);

        PageShell shell = new PageShell("Matching & Dispatch", "Fulfill blood requests interactively from inventory.");
        
        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerActions.setOpaque(false);
        matchModeSelector.setPreferredSize(new Dimension(130, 34));
        matchModeSelector.addActionListener(e -> refreshData());
        JButton refreshQueue = UiComponents.primaryButton("Refresh queue");
        refreshQueue.setPreferredSize(new Dimension(135, 34));
        headerActions.add(refreshQueue);
        headerActions.add(new JLabel("Mode:"));
        headerActions.add(matchModeSelector);
        shell.setActions(headerActions);

        shell.setBody(buildSplitView());
        add(shell, BorderLayout.CENTER);

        requestsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        requestsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = requestsTable.getSelectedRow();
                if (row >= 0) {
                    selectedRequestId = requestsModel.getValueAt(row, 0).toString();
                } else {
                    selectedRequestId = null;
                }
                updateAnalysisView();
            }
        });

        processButton.addActionListener(e -> processSelectedRequest());

        refreshData();
    }

    private JPanel buildSplitView() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setOpaque(false);
        leftPanel.setBorder(BorderFactory.createTitledBorder("Pending Requests Queue"));
        requestsTable.setFillsViewportHeight(true);
        leftPanel.add(UiComponents.tableScroll(requestsTable), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);
        rightPanel.setBorder(BorderFactory.createTitledBorder("Match Analysis"));

        JPanel emptyState = new JPanel(new GridBagLayout());
        emptyState.add(matchingResult);
        emptyState.add(atomicNotice);
        emptyState.setName("matchingEmptyState");
        emptyState.setOpaque(false);
        emptyState.add(UiComponents.muted("Select a request from the queue to view analysis."));
        analysisPanel.setOpaque(false);
        analysisPanel.add(emptyState, "EMPTY");

        JPanel activeState = buildActiveAnalysisPanel();
        activeState.setName("matchingSteps");
        analysisPanel.add(activeState, "ACTIVE");

        rightPanel.add(analysisPanel, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(450);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
        container.add(splitPane, BorderLayout.CENTER);
        return container;
    }

    private JPanel buildActiveAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(requestTitle);
        header.add(Box.createVerticalStrut(5));
        header.add(requestSubtitle);
        panel.add(header, BorderLayout.NORTH);

        JPanel metrics = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        metrics.setOpaque(false);
        metrics.add(metricBox("Required", requiredValue));
        metrics.add(metricBox("Available Compatible", availableValue));

        JPanel tableArea = new JPanel(new BorderLayout());
        tableArea.setOpaque(false);
        compatibleTable.setFillsViewportHeight(true);
        tableArea.add(UiComponents.tableScroll(compatibleTable), BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(metrics, BorderLayout.NORTH);
        center.add(tableArea, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        processButton.setPreferredSize(new Dimension(200, 40));
        footer.add(processButton);
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel metricBox(String title, JLabel value) {
        JPanel box = new JPanel(new BorderLayout(0, 2));
        box.setOpaque(false);
        JLabel label = UiComponents.muted(title);
        label.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
        box.add(label, BorderLayout.NORTH);
        box.add(value, BorderLayout.CENTER);
        return box;
    }

    private void updateAnalysisView() {
        if (selectedRequestId == null) {
            processButton.setEnabled(false); analysisLayout.show(analysisPanel, "EMPTY");
            return;
        }
        
        LifeFlowState snapshot = controller.getStateSnapshot();
        BloodRequest request = snapshot.getRequests().stream()
                .filter(r -> r.getId().equals(selectedRequestId) && r.getStatus() == lifeflow.model.RequestStatus.PENDING)
                .findFirst().orElse(null);
                
        if (request == null) {
            processButton.setEnabled(false); analysisLayout.show(analysisPanel, "EMPTY");
            return;
        }

        analysisLayout.show(analysisPanel, "ACTIVE");
        lifeflow.model.MatchMode mode = (lifeflow.model.MatchMode) matchModeSelector.getSelectedItem();
        BloodInventory inventory = BloodInventory.from(snapshot.getUnits());
        java.util.ArrayList<BloodUnit> availableUnits = inventory.getCompatibleUnits(
                request.getBloodType(), mode, controller.today());
        int available = availableUnits.size();

        requestTitle.setText(request.getKind() + " · " + request.getId());
        requestTitle.setForeground(request instanceof EmergencyRequest ? UiTheme.DANGER : UiTheme.NAVY);
        requestSubtitle.setText(request.getRequesterName() + " · " + DashboardPanel.displayType(request.getBloodType()));
        
        requiredValue.setText(Integer.toString(request.getQuantity()));
        availableValue.setText(Integer.toString(available));
        
        if (available >= request.getQuantity()) {
            availableValue.setForeground(UiTheme.SUCCESS);
            processButton.setEnabled(true);
            processButton.setText("Fulfill Request");
        } else {
            availableValue.setForeground(UiTheme.DANGER);
            processButton.setEnabled(false);
            processButton.setText("Insufficient Units");
        }

        compatibleModel.setRowCount(0);
        Map<String, String> donorNames = new HashMap<>();
        for (Donor donor : snapshot.getDonors()) {
            donorNames.put(donor.getId().toLowerCase(java.util.Locale.ROOT), donor.getName());
        }
        int selectedCount = 0;
        for (BloodUnit unit : availableUnits) {
            if (selectedCount >= request.getQuantity()) break;
            compatibleModel.addRow(new Object[]{
                unit.getId(),
                donorNames.getOrDefault(unit.getDonorId().toLowerCase(java.util.Locale.ROOT), unit.getDonorId()),
                unit.getExpiryDate(),
                "TO BE DISPATCHED"
            });
            selectedCount++;
        }
    }

    private void processSelectedRequest() {
        if (selectedRequestId == null) return;
        lifeflow.model.MatchMode mode = (lifeflow.model.MatchMode) matchModeSelector.getSelectedItem();
        try {
            MatchResult result = controller.processSpecificRequest(selectedRequestId, controller.today(), mode);
            if (result.outcome() == MatchOutcome.INSUFFICIENT_STOCK) {
                status.accept(UiNotice.warning("Matching paused: insufficient compatible stock."));
            } else if (result.outcome() == MatchOutcome.FULFILLED) {
                status.accept(UiNotice.success("Request " + selectedRequestId + " fulfilled successfully."));
            } else {
                status.accept(UiNotice.info("No request was processed."));
            }
            // Controller will call onStateChanged automatically
        } catch (LifeFlowException | IOException exception) {
            status.accept(UiNotice.warning("Error: " + exception.getMessage()));
        }
    }

    public void onStateChanged() {
        refreshData();
    }

    public void refreshData() {
        LifeFlowState snapshot = controller.getStateSnapshot();
        requestsModel.setRowCount(0);
        
        java.util.List<BloodRequest> pending = snapshot.getRequests().stream()
                .filter(r -> r.getStatus() == lifeflow.model.RequestStatus.PENDING)
                .sorted(lifeflow.service.MatchingService.ORDER)
                .toList();
                
        for (BloodRequest req : pending) {
            requestsModel.addRow(new Object[]{
                req.getId(),
                req.getKind(),
                DashboardPanel.displayType(req.getBloodType()),
                req.getQuantity()
            });
        }
        
        // Restore selection if still exists
        if (selectedRequestId == null && requestsModel.getRowCount() > 0) {
            // Auto-select first fulfillable
            java.util.List<BloodRequest> pendingReqs = snapshot.getRequests().stream().filter(r -> r.getStatus() == lifeflow.model.RequestStatus.PENDING).toList();
            lifeflow.model.BloodRequest fulfillable = new lifeflow.service.MatchingService(lifeflow.service.BloodInventory.from(snapshot.getUnits())).findNextFulfillable(pendingReqs, controller.today());
            if (fulfillable != null) {
                selectedRequestId = fulfillable.getId();
            }
        }
        if (selectedRequestId != null) {
            boolean found = false;
            for (int i = 0; i < requestsModel.getRowCount(); i++) {
                if (requestsModel.getValueAt(i, 0).equals(selectedRequestId)) {
                    requestsTable.setRowSelectionInterval(i, i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                selectedRequestId = null;
                requestsTable.clearSelection();
            }
        }
        updateAnalysisView();
    }

    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 18));
        label.setForeground(UiTheme.NAVY);
        return label;
    }
}
