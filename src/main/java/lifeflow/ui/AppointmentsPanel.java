package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import lifeflow.model.DonationAppointment;
import lifeflow.model.LifeFlowState;
import lifeflow.service.HospitalRegistry;
import lifeflow.service.LifeFlowController;

/** Appointment workspace showing every donation booking across all centers. */
@SuppressWarnings("serial")
public final class AppointmentsPanel extends JPanel implements lifeflow.service.StateObserver {
    private static final String TABLE_VIEW = "table";
    private static final String EMPTY_VIEW = "empty";

    private final LifeFlowController controller;
    private final HospitalRegistry registry;
    private final Consumer<UiNotice> status;
    private final DefaultTableModel model = UiComponents.readOnlyModel(
            "Appointment", "Donor", "Hospital", "Date", "Status", "Linked Request");
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = UiComponents.searchField("Search appointments");
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[]{
            "All statuses", "BOOKED", "COMPLETED", "CANCELLED"});
    private final JLabel recordCount = UiComponents.muted("0 RECORDS");
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel center = new JPanel(centerLayout);

    public AppointmentsPanel(LifeFlowController controller, HospitalRegistry registry,
                             Runnable onDataChanged, Consumer<UiNotice> status) {
        super(new BorderLayout());
        this.controller = controller;
        this.registry = registry;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);
        configureFilters();
        PageShell shell = new PageShell("Donation appointments",
                "Every booked, completed, and cancelled donation across centers.");
        shell.setToolbar(buildToolbar());
        shell.setBody(buildCenter());
        add(shell, BorderLayout.CENTER);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(4)
                .setCellRenderer(UiComponents.statusRenderer());
        int[] widths = {110, 180, 180, 110, 105, 120};
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
        installFilters();
        refreshData();
    }

    private void configureFilters() {
        statusFilter.setName("appointmentStatusFilter");
        UiComponents.styleInput(statusFilter);
        statusFilter.setPreferredSize(new Dimension(150, 34));
        search.setPreferredSize(new Dimension(205, 34));
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(UiTheme.SURFACE);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(search);
        filters.add(statusFilter);
        toolbar.add(filters, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        actions.setOpaque(false);
        actions.add(recordCount);
        toolbar.add(actions, BorderLayout.EAST);
        return toolbar;
    }

    private JPanel buildCenter() {
        center.setOpaque(false);
        JPanel card = UiComponents.densePanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        JScrollPane scroll = UiComponents.tableScroll(table);
        card.add(scroll, BorderLayout.CENTER);
        center.add(card, TABLE_VIEW);

        JPanel empty = UiComponents.densePanel(new java.awt.GridBagLayout());
        empty.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        JPanel message = new JPanel();
        message.setOpaque(false);
        message.setLayout(new javax.swing.BoxLayout(message,
                javax.swing.BoxLayout.Y_AXIS));
        JLabel title = UiComponents.heading("No appointments yet");
        title.setAlignmentX(CENTER_ALIGNMENT);
        JLabel subtitle = UiComponents.muted(
                "Donors book appointments from the donor portal.");
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        message.add(title);
        message.add(javax.swing.Box.createVerticalStrut(7));
        message.add(subtitle);
        empty.add(message);
        center.add(empty, EMPTY_VIEW);
        return center;
    }

    private void installFilters() {
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void removeUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void changedUpdate(DocumentEvent event) { updateFilter(); }
        });
        statusFilter.addActionListener(event -> updateFilter());
    }

    private void updateFilter() {
        ArrayList<RowFilter<Object, Object>> filters = new ArrayList<>();
        String text = UiComponents.searchValue(search);
        if (!text.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
        String statusValue = String.valueOf(statusFilter.getSelectedItem());
        if (!statusValue.startsWith("All")) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(statusValue) + "$", 4));
        }
        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        recordCount.setText(sorter.getViewRowCount() + " RECORDS");
    }

    public void onStateChanged() {
        refreshData();
    }

    public void refreshData() {
        LifeFlowState snapshot = controller.getStateSnapshot();
        LocalDate today = controller.today();
        model.setRowCount(0);
        snapshot.getAppointments().stream()
                .sorted(Comparator.comparing(DonationAppointment::getAppointmentDate)
                        .thenComparing(DonationAppointment::getId,
                                String.CASE_INSENSITIVE_ORDER))
                .forEach(appointment -> model.addRow(new Object[]{
                        appointment.getId(),
                        donorName(snapshot, appointment.getDonorId()),
                        hospitalName(appointment.getHospitalId()),
                        appointment.getAppointmentDate().toString(),
                        appointment.isStale(today) && appointment.isBooked()
                                ? "MISSED" : appointment.getStatus().name(),
                        appointment.getLinkedRequestId() == null ? "—"
                                : appointment.getLinkedRequestId()}));
        updateFilter();
        centerLayout.show(center, model.getRowCount() == 0 ? EMPTY_VIEW : TABLE_VIEW);
    }

    private String donorName(LifeFlowState snapshot, String donorId) {
        return snapshot.getDonors().stream()
                .filter(donor -> donor.getId().equalsIgnoreCase(donorId))
                .map(donor -> donor.getName() + " (" + donorId + ")")
                .findFirst().orElse(donorId);
    }

    private String hospitalName(String hospitalId) {
        var hospital = registry.findById(hospitalId);
        return hospital == null ? hospitalId : hospital.getName();
    }
}