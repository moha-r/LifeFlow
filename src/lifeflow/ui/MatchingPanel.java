package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
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
import javax.swing.JPanel;
import javax.swing.JTextArea;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodUnit;
import lifeflow.service.LifeFlowController;

/** Focused matching workspace for the highest-priority pending request. */
@SuppressWarnings("serial")
public final class MatchingPanel extends JPanel {
    private final LifeFlowController controller;
    private final Runnable onDataChanged;
    private final Consumer<String> status;
    private final JLabel requestKind = UiComponents.heading("No pending requests");
    private final JLabel requestDetails = UiComponents.muted(
            "Create a request to begin matching.");
    private final JLabel availabilityValue = new JLabel("—");
    private final JLabel availabilityLabel = UiComponents.muted("Available matching units");
    private final JButton process = UiComponents.primaryButton("Process next request");
    private final JTextArea result = new JTextArea();
    private final JPanel resultCard = UiComponents.card(new BorderLayout(0, 12));

    public MatchingPanel(LifeFlowController controller, Runnable onDataChanged,
                         Consumer<String> status) {
        super(new BorderLayout(0, 18));
        this.controller = controller;
        this.onDataChanged = onDataChanged;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(28, 30, 28, 30));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);
        process.addActionListener(event -> processNextRequest());
        process.setPreferredSize(new java.awt.Dimension(190, 40));
        refreshData();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(UiComponents.title("Matching"));
        copy.add(Box.createVerticalStrut(5));
        copy.add(UiComponents.muted(
                "LifeFlow selects emergencies first, then the oldest request."));
        header.add(copy, BorderLayout.WEST);
        JPanel action = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        action.setOpaque(false);
        action.add(process);
        header.add(action, BorderLayout.EAST);
        return header;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setOpaque(false);
        JPanel overview = new JPanel(new GridLayout(1, 2, 16, 0));
        overview.setOpaque(false);
        overview.add(buildRequestCard());
        overview.add(buildAvailabilityCard());
        content.add(overview, BorderLayout.NORTH);

        result.setEditable(false);
        result.setLineWrap(true);
        result.setWrapStyleWord(true);
        result.setOpaque(false);
        result.setFont(UiTheme.BODY);
        result.setForeground(UiTheme.MUTED);
        result.setText("The matching result and used unit IDs will appear here.");
        resultCard.add(UiComponents.heading("Latest matching result"), BorderLayout.NORTH);
        resultCard.add(result, BorderLayout.CENTER);
        content.add(resultCard, BorderLayout.CENTER);
        return content;
    }

    private JPanel buildRequestCard() {
        JPanel card = UiComponents.card(new BorderLayout(0, 16));
        card.add(UiComponents.heading("Next priority request"), BorderLayout.NORTH);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(requestKind);
        copy.add(Box.createVerticalStrut(8));
        copy.add(requestDetails);
        card.add(copy, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildAvailabilityCard() {
        JPanel card = UiComponents.card(new BorderLayout(0, 12));
        card.add(UiComponents.heading("Inventory check"), BorderLayout.NORTH);
        availabilityValue.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 34));
        availabilityValue.setForeground(UiTheme.NAVY);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(availabilityValue);
        copy.add(Box.createVerticalStrut(7));
        copy.add(availabilityLabel);
        card.add(copy, BorderLayout.CENTER);
        return card;
    }

    public void processNextRequest() {
        BloodRequest request = controller.getNextPendingRequest();
        if (request == null) {
            status.accept("There are no pending requests.");
            result.setForeground(UiTheme.MUTED);
            result.setText("No request was processed because the queue is empty.");
            return;
        }
        int available = controller.getStockCounts(LocalDate.now()).get(request.getBloodType());
        try {
            ArrayList<BloodUnit> matched = controller.processNextRequest(LocalDate.now());
            if (matched.isEmpty()) {
                result.setForeground(UiTheme.DANGER);
                result.setText("Request " + request.getId() + " needs "
                        + request.getQuantity() + " unit(s) of "
                        + DashboardPanel.displayType(request.getBloodType()) + ", but only "
                        + available + " are available. No units were changed and the request "
                        + "remains pending.");
                status.accept("Matching paused: insufficient compatible stock.");
            } else {
                StringBuilder ids = new StringBuilder();
                for (BloodUnit unit : matched) {
                    if (!ids.isEmpty()) {
                        ids.append(", ");
                    }
                    ids.append(unit.getId());
                }
                result.setForeground(UiTheme.SUCCESS);
                result.setText("Request " + request.getId()
                        + " was fulfilled successfully.\n\nUsed unit IDs: " + ids);
                status.accept("Request fulfilled and inventory updated.");
                onDataChanged.run();
            }
            refreshData();
        } catch (IOException exception) {
            result.setForeground(UiTheme.DANGER);
            result.setText("The match could not be saved: " + exception.getMessage());
            status.accept("Could not save the matching result.");
        }
    }

    public void refreshData() {
        BloodRequest request = controller.getNextPendingRequest();
        if (request == null) {
            requestKind.setText("No pending requests");
            requestKind.setForeground(UiTheme.NAVY);
            requestDetails.setText("Create a request to begin matching.");
            availabilityValue.setText("—");
            availabilityValue.setForeground(UiTheme.MUTED);
            availabilityLabel.setText("Available matching units");
            process.setEnabled(false);
            return;
        }
        int available = controller.getStockCounts(LocalDate.now()).get(request.getBloodType());
        requestKind.setText(request.getKind() + " · " + request.getId());
        requestKind.setForeground(request.getKind().equals("EMERGENCY")
                ? UiTheme.DANGER : UiTheme.NAVY);
        requestDetails.setText(request.getRequesterName() + "  ·  "
                + DashboardPanel.displayType(request.getBloodType()) + "  ·  Needs "
                + request.getQuantity() + " unit(s)");
        availabilityValue.setText(available + " / " + request.getQuantity());
        availabilityValue.setForeground(available >= request.getQuantity()
                ? UiTheme.SUCCESS : UiTheme.DANGER);
        availabilityLabel.setText(available >= request.getQuantity()
                ? "Enough compatible stock is available"
                : "Compatible stock is insufficient");
        process.setEnabled(true);
    }
}
