package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EmergencyRequest;
import lifeflow.service.LifeFlowController;

/** Explicit request-to-inventory matching workflow with atomic feedback. */
@SuppressWarnings("serial")
public final class MatchingPanel extends JPanel {
    private static final String EMPTY = "empty";
    private static final String ACTIVE = "active";

    private final LifeFlowController controller;
    private final Runnable onDataChanged;
    private final Consumer<String> status;
    private final CardLayout stateLayout = new CardLayout();
    private final JPanel state = new JPanel(stateLayout);
    private final JPanel steps = new JPanel(new GridLayout(1, 3));
    private final JLabel requestKind = new JLabel("No pending requests");
    private final JLabel requestDetails = UiComponents.muted("The queue is clear.");
    private final JLabel requiredValue = valueLabel("0");
    private final JLabel availableValue = valueLabel("0");
    private final DefaultTableModel compatibleModel = UiComponents.readOnlyModel(
            "Unit ID", "Donor", "Expiry", "Status");
    private final JTable compatibleTable = new JTable(compatibleModel);
    private final JButton process = UiComponents.primaryButton(
            "Process request atomically");
    private final JPanel resultPanel = new JPanel(new BorderLayout());
    private final JLabel resultTitle = new JLabel("Ready for the next operation");
    private final JLabel resultDetails = new JLabel(
            "Matching results will appear here without blocking the workflow.");

    public MatchingPanel(LifeFlowController controller, Runnable onDataChanged,
                         Consumer<String> status) {
        super(new BorderLayout());
        this.controller = controller;
        this.onDataChanged = onDataChanged;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);

        PageShell shell = new PageShell("Matching workspace",
                "Emergency requests are processed first, then the oldest request.");
        shell.setActions(buildHeaderAction());
        shell.setBody(buildWorkflow());
        add(shell, BorderLayout.CENTER);

        process.setName("processMatchingButton");
        process.addActionListener(event -> processNextRequest());
        refreshData();
    }

    private JPanel buildHeaderAction() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        JButton refresh = UiComponents.secondaryButton("Refresh queue");
        refresh.setPreferredSize(new Dimension(118, 34));
        refresh.addActionListener(event -> refreshData());
        actions.add(refresh);
        return actions;
    }

    private JPanel buildWorkflow() {
        JPanel workflow = new JPanel(new BorderLayout(0, UiTheme.SPACE_SM));
        workflow.setOpaque(false);

        steps.setName("matchingSteps");
        steps.setBackground(UiTheme.SURFACE);
        steps.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        steps.add(step("1", "Priority request selected", true));
        steps.add(step("2", "Compatible inventory checked", true));
        steps.add(step("3", "Review and process", false));
        steps.setPreferredSize(new Dimension(700, 54));
        workflow.add(steps, BorderLayout.NORTH);

        state.setOpaque(false);
        state.add(buildEmptyState(), EMPTY);
        state.add(buildActiveState(), ACTIVE);
        workflow.add(state, BorderLayout.CENTER);

        configureResultPanel();
        workflow.add(resultPanel, BorderLayout.SOUTH);
        return workflow;
    }

    private JPanel step(String number, String label, boolean complete) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 12));
        item.setBackground(UiTheme.SURFACE);
        JLabel dot = new JLabel(complete ? "✓" : number, SwingConstants.CENTER);
        dot.setOpaque(true);
        dot.setPreferredSize(new Dimension(28, 28));
        dot.setBackground(complete ? UiTheme.SUCCESS_LIGHT : UiTheme.CORAL_LIGHT);
        dot.setForeground(complete ? UiTheme.SUCCESS : UiTheme.DANGER);
        dot.setBorder(BorderFactory.createLineBorder(complete
                ? new Color(0xA8DCCB) : new Color(0xF2AFBF)));
        JLabel text = new JLabel(label);
        text.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                complete ? java.awt.Font.PLAIN : java.awt.Font.BOLD, 11));
        text.setForeground(complete ? UiTheme.MUTED : UiTheme.NAVY);
        item.add(dot);
        item.add(text);
        return item;
    }

    private JPanel buildEmptyState() {
        JPanel empty = UiComponents.densePanel(new GridBagLayout());
        empty.setName("matchingEmptyState");
        empty.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel title = UiComponents.heading("No pending requests");
        title.setAlignmentX(CENTER_ALIGNMENT);
        JLabel detail = UiComponents.muted(
                "Create a blood request to start the matching workflow.");
        detail.setAlignmentX(CENTER_ALIGNMENT);
        copy.add(title);
        copy.add(Box.createVerticalStrut(7));
        copy.add(detail);
        empty.add(copy);
        return empty;
    }

    private JPanel buildActiveState() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        GridBagConstraints request = new GridBagConstraints();
        request.gridx = 0;
        request.gridy = 0;
        request.weightx = 0.36;
        request.weighty = 1;
        request.fill = GridBagConstraints.BOTH;
        request.insets = new java.awt.Insets(0, 0, 0, 10);
        row.add(buildRequestPanel(), request);

        GridBagConstraints units = new GridBagConstraints();
        units.gridx = 1;
        units.gridy = 0;
        units.weightx = 0.64;
        units.weighty = 1;
        units.fill = GridBagConstraints.BOTH;
        row.add(buildCompatiblePanel(), units);
        return row;
    }

    private JPanel buildRequestPanel() {
        JPanel panel = denseSection("Selected request", "#1 in queue");
        JPanel content = new JPanel();
        content.setBackground(UiTheme.SURFACE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 14, 15));
        requestKind.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 19));
        requestKind.setForeground(UiTheme.NAVY);
        content.add(requestKind);
        content.add(Box.createVerticalStrut(5));
        content.add(requestDetails);
        content.add(Box.createVerticalStrut(13));
        JPanel quantities = new JPanel(new GridLayout(1, 2, 7, 0));
        quantities.setOpaque(false);
        quantities.add(quantityBox("REQUIRED", requiredValue));
        quantities.add(quantityBox("AVAILABLE", availableValue));
        quantities.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        content.add(quantities);
        content.add(Box.createVerticalStrut(10));
        JLabel atomic = new JLabel("<html>All-or-nothing: insufficient stock changes no status.</html>");
        atomic.setOpaque(true);
        atomic.setBackground(UiTheme.WARNING_LIGHT);
        atomic.setForeground(new Color(0x9B660C));
        atomic.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.PLAIN, 10));
        atomic.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
        atomic.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        content.add(atomic);
        content.add(Box.createVerticalGlue());
        process.setAlignmentX(LEFT_ALIGNMENT);
        process.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        process.setPreferredSize(new Dimension(220, 36));
        content.add(process);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCompatiblePanel() {
        JPanel panel = denseSection("Compatible units selected",
                "Same ABO and Rh type");
        compatibleTable.setName("compatibleUnits");
        UiComponents.configureTable(compatibleTable);
        compatibleTable.setRowHeight(34);
        compatibleTable.getColumnModel().getColumn(3)
                .setCellRenderer(UiComponents.statusRenderer());
        JScrollPane scroll = new JScrollPane(compatibleTable);
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
        JLabel detail = new JLabel(caption);
        detail.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.PLAIN, 10));
        detail.setForeground(UiTheme.MUTED);
        header.add(heading, BorderLayout.WEST);
        header.add(detail, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
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

    private void configureResultPanel() {
        resultPanel.setName("matchingResult");
        resultPanel.setBackground(UiTheme.SURFACE);
        resultPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(10, 13, 10, 13)));
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        resultTitle.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 12));
        resultDetails.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.PLAIN, 10));
        resultDetails.setForeground(UiTheme.MUTED);
        copy.add(resultTitle);
        copy.add(Box.createVerticalStrut(3));
        copy.add(resultDetails);
        resultPanel.add(copy, BorderLayout.WEST);
    }

    public void processNextRequest() {
        BloodRequest request = controller.getNextPendingRequest();
        if (request == null) {
            setResult("Queue is empty", "No request was processed.", UiTheme.MUTED);
            return;
        }
        int available = controller.getStockCounts(LocalDate.now())
                .get(request.getBloodType());
        try {
            ArrayList<BloodUnit> matched = controller.processNextRequest(LocalDate.now());
            if (matched.isEmpty()) {
                setResult("Matching paused",
                        "Request " + request.getId() + " needs "
                                + request.getQuantity() + " unit(s), but only "
                                + available + " are available. No state changed.",
                        UiTheme.DANGER);
                status.accept("Matching paused: insufficient compatible stock.");
            } else {
                StringBuilder ids = new StringBuilder();
                for (BloodUnit unit : matched) {
                    if (!ids.isEmpty()) {
                        ids.append(", ");
                    }
                    ids.append(unit.getId());
                }
                setResult("Request fulfilled successfully",
                        "Request " + request.getId() + " used units " + ids + ".",
                        UiTheme.SUCCESS);
                status.accept("Request fulfilled and inventory updated.");
                onDataChanged.run();
            }
            refreshData();
        } catch (IOException exception) {
            setResult("The result could not be saved", exception.getMessage(),
                    UiTheme.DANGER);
            JOptionPane.showMessageDialog(this,
                    "LifeFlow could not save the matching result.\n"
                            + exception.getMessage(),
                    "Storage Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void refreshData() {
        BloodRequest request = controller.getNextPendingRequest();
        if (request == null) {
            stateLayout.show(state, EMPTY);
            steps.setVisible(false);
            process.setEnabled(false);
            compatibleModel.setRowCount(0);
            return;
        }

        stateLayout.show(state, ACTIVE);
        steps.setVisible(true);
        process.setEnabled(true);
        int available = controller.getStockCounts(LocalDate.now())
                .get(request.getBloodType());
        requestKind.setText(request.getKind() + " · " + request.getId());
        requestKind.setForeground(request instanceof EmergencyRequest
                ? UiTheme.DANGER : UiTheme.NAVY);
        requestDetails.setText(request.getRequesterName() + " · "
                + DashboardPanel.displayType(request.getBloodType()));
        requiredValue.setText(Integer.toString(request.getQuantity()));
        availableValue.setText(Integer.toString(available));
        availableValue.setForeground(available >= request.getQuantity()
                ? UiTheme.SUCCESS : UiTheme.DANGER);

        compatibleModel.setRowCount(0);
        int selected = 0;
        for (BloodUnit unit : controller.getUnits()) {
            if (selected >= request.getQuantity()) {
                break;
            }
            if (unit.getBloodType() == request.getBloodType()
                    && unit.isAvailable(LocalDate.now())) {
                compatibleModel.addRow(new Object[]{unit.getId(), donorName(unit),
                        unit.getExpiryDate(), "ELIGIBLE"});
                selected++;
            }
        }
    }

    private String donorName(BloodUnit unit) {
        for (Donor donor : controller.getDonors()) {
            if (donor.getId().equalsIgnoreCase(unit.getDonorId())) {
                return donor.getName() + " (" + donor.getId() + ")";
            }
        }
        return unit.getDonorId();
    }

    private void setResult(String title, String details, Color color) {
        resultTitle.setText(title);
        resultTitle.setForeground(color);
        resultDetails.setText(details == null || details.isBlank()
                ? "No additional details are available." : details);
        resultPanel.setBackground(color == UiTheme.SUCCESS
                ? UiTheme.SUCCESS_LIGHT
                : color == UiTheme.DANGER ? UiTheme.DANGER_LIGHT : UiTheme.SURFACE);
    }

    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 16));
        label.setForeground(UiTheme.NAVY);
        return label;
    }
}
