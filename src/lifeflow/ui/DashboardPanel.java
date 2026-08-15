package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.service.LifeFlowController;

/** Operational overview with live metrics and the most important next action. */
@SuppressWarnings("serial")
public final class DashboardPanel extends JPanel {
    private final LifeFlowController controller;
    private final JLabel donorsValue = metricValue("totalDonorsValue");
    private final JLabel unitsValue = metricValue("availableUnitsValue");
    private final JLabel pendingValue = metricValue("pendingRequestsValue");
    private final JLabel emergenciesValue = metricValue("emergencyRequestsValue");
    private final EnumMap<BloodType, JLabel> stockValues = new EnumMap<>(BloodType.class);
    private final JLabel nextTitle = UiComponents.heading("No pending requests");
    private final JLabel nextDetails = UiComponents.muted(
            "New requests will appear here automatically.");
    private final JButton processButton = UiComponents.primaryButton("Process request");

    public DashboardPanel(LifeFlowController controller, Runnable addDonor,
                          Runnable addUnit, Runnable addRequest, Runnable processNext) {
        super(new BorderLayout());
        this.controller = controller;
        setBackground(UiTheme.BACKGROUND);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(28, 30, 28, 30));
        content.add(buildHeader());
        content.add(Box.createVerticalStrut(22));
        content.add(buildMetrics());
        content.add(Box.createVerticalStrut(18));
        content.add(buildStockCard());
        content.add(Box.createVerticalStrut(18));
        content.add(buildBottomRow(addDonor, addUnit, addRequest));

        processButton.addActionListener(event -> processNext.run());
        processButton.setPreferredSize(new Dimension(174, 40));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        refreshData();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(UiComponents.title("Dashboard"));
        copy.add(Box.createVerticalStrut(5));
        copy.add(UiComponents.muted("A clear view of donors, blood stock, and urgent demand."));
        header.add(copy, BorderLayout.WEST);
        JLabel date = UiComponents.muted(LocalDate.now().toString());
        date.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(date, BorderLayout.EAST);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        return header;
    }

    private JPanel buildMetrics() {
        JPanel metrics = new JPanel(new GridLayout(1, 4, 14, 0));
        metrics.setOpaque(false);
        metrics.add(metricCard("Total donors", donorsValue, UiTheme.CORAL));
        metrics.add(metricCard("Available units", unitsValue, UiTheme.SUCCESS));
        metrics.add(metricCard("Pending requests", pendingValue, UiTheme.WARNING));
        metrics.add(metricCard("Emergencies", emergenciesValue, UiTheme.DANGER));
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        return metrics;
    }

    private JPanel metricCard(String title, JLabel value, Color accent) {
        JPanel card = UiComponents.card(new BorderLayout());
        JLabel marker = new JLabel("●");
        marker.setForeground(accent);
        marker.setFont(UiTheme.HEADING);
        card.add(marker, BorderLayout.EAST);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(UiComponents.muted(title));
        copy.add(Box.createVerticalStrut(8));
        copy.add(value);
        card.add(copy, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildStockCard() {
        JPanel card = UiComponents.card(new BorderLayout(0, 14));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(UiComponents.heading("Blood availability"), BorderLayout.WEST);
        heading.add(UiComponents.muted("Available and non-expired units"), BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);

        JPanel types = new JPanel(new GridLayout(1, BloodType.values().length, 10, 0));
        types.setOpaque(false);
        for (BloodType type : BloodType.values()) {
            JPanel item = new JPanel();
            item.setOpaque(true);
            item.setBackground(new Color(0xF8F9FC));
            item.setBorder(BorderFactory.createEmptyBorder(12, 6, 12, 6));
            item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(displayType(type), SwingConstants.CENTER);
            name.setAlignmentX(Component.CENTER_ALIGNMENT);
            name.setFont(UiTheme.BODY_BOLD);
            name.setForeground(UiTheme.NAVY);
            JLabel value = new JLabel("0", SwingConstants.CENTER);
            value.setAlignmentX(Component.CENTER_ALIGNMENT);
            value.setFont(UiTheme.HEADING);
            stockValues.put(type, value);
            item.add(name);
            item.add(Box.createVerticalStrut(6));
            item.add(value);
            types.add(item);
        }
        card.add(types, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 146));
        return card;
    }

    private JPanel buildBottomRow(Runnable addDonor, Runnable addUnit, Runnable addRequest) {
        JPanel row = new JPanel(new GridLayout(1, 2, 14, 0));
        row.setOpaque(false);
        row.add(buildNextRequestCard());
        row.add(buildQuickActions(addDonor, addUnit, addRequest));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        return row;
    }

    private JPanel buildNextRequestCard() {
        JPanel card = UiComponents.card(new BorderLayout(0, 14));
        card.add(UiComponents.heading("Next priority request"), BorderLayout.NORTH);
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.add(nextTitle);
        details.add(Box.createVerticalStrut(7));
        details.add(nextDetails);
        card.add(details, BorderLayout.CENTER);
        JPanel action = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        action.setOpaque(false);
        action.add(processButton);
        card.add(action, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildQuickActions(Runnable addDonor, Runnable addUnit, Runnable addRequest) {
        JPanel card = UiComponents.card(new BorderLayout(0, 14));
        card.add(UiComponents.heading("Quick actions"), BorderLayout.NORTH);
        JPanel actions = new JPanel(new GridLayout(3, 1, 0, 8));
        actions.setOpaque(false);
        JButton donor = UiComponents.secondaryButton("+  Register donor");
        JButton unit = UiComponents.secondaryButton("+  Add blood unit");
        JButton request = UiComponents.primaryButton("+  Create request");
        donor.addActionListener(event -> addDonor.run());
        unit.addActionListener(event -> addUnit.run());
        request.addActionListener(event -> addRequest.run());
        actions.add(donor);
        actions.add(unit);
        actions.add(request);
        card.add(actions, BorderLayout.CENTER);
        return card;
    }

    public final void refreshData() {
        LocalDate today = LocalDate.now();
        donorsValue.setText(Integer.toString(controller.getDonors().size()));
        unitsValue.setText(Integer.toString(controller.getAvailableUnitCount(today)));
        pendingValue.setText(Integer.toString(controller.getPendingRequestCount()));
        emergenciesValue.setText(Integer.toString(controller.getPendingEmergencyCount()));

        HashMap<BloodType, Integer> counts = controller.getStockCounts(today);
        for (BloodType type : BloodType.values()) {
            int count = counts.get(type);
            JLabel label = stockValues.get(type);
            label.setText(Integer.toString(count));
            label.setForeground(count == 0 ? UiTheme.DANGER
                    : count <= 2 ? UiTheme.WARNING : UiTheme.SUCCESS);
        }

        BloodRequest request = controller.getNextPendingRequest();
        if (request == null) {
            nextTitle.setText("No pending requests");
            nextDetails.setText("New requests will appear here automatically.");
            processButton.setEnabled(false);
        } else {
            nextTitle.setText(request.getKind() + " · " + request.getId());
            nextDetails.setText(displayType(request.getBloodType()) + "  ·  "
                    + request.getQuantity() + " unit(s)  ·  " + request.getRequesterName());
            nextTitle.setForeground(request.getKind().equals("EMERGENCY")
                    ? UiTheme.DANGER : UiTheme.NAVY);
            processButton.setEnabled(true);
        }
        revalidate();
        repaint();
    }

    private static JLabel metricValue(String name) {
        JLabel label = new JLabel("0");
        label.setName(name);
        label.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 29));
        label.setForeground(UiTheme.NAVY);
        return label;
    }

    static String displayType(BloodType type) {
        return type.name().replace("_POS", "+").replace("_NEG", "-");
    }
}
