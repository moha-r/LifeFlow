package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.Donor;
import lifeflow.model.DonationAppointment;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.Hospital;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RequestStatus;
import lifeflow.model.exception.LifeFlowException;
import lifeflow.persistence.LifeFlowStore;
import lifeflow.service.HospitalRegistry;
import lifeflow.service.LifeFlowController;

/** Self-service portal where a hospital places and tracks its blood requests. */
@SuppressWarnings({"serial", "this-escape"})
public final class HospitalPortalFrame extends JFrame {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final LifeFlowController controller;
    private final Hospital hospital;
    private final HospitalRegistry registry;
    private final SessionSwitcher switcher;

    private final DefaultTableModel model =
            UiComponents.readOnlyModel("Request ID", "Kind", "Blood Type",
                    "Quantity", "Date", "Status", "Volunteers", "Units");
    private final JTable table = new JTable(model);
    private final DefaultTableModel appointmentsModel =
            UiComponents.readOnlyModel("Appointment", "Donor", "Date",
                    "Status");
    private final JTable appointmentsTable = new JTable(appointmentsModel);
    private final JLabel notice = new JLabel(" ");
    private final JComboBox<BloodType> type = new JComboBox<>(BloodType.values());
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
    private final JComboBox<String> kind = new JComboBox<>(
            new String[]{"REGULAR", "EMERGENCY"});

    public HospitalPortalFrame(LifeFlowState state, LifeFlowStore store,
                               Hospital hospital, HospitalRegistry registry,
                               SessionSwitcher switcher) {
        super("LifeFlow — Hospital Portal");
        this.hospital = hospital;
        this.registry = registry;
        this.switcher = switcher;
        controller = new LifeFlowController(state, store);
        configureWindow();
        buildContent();
        refreshData();
    }

    private void configureWindow() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1040, 640);
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeApplication();
            }
        });
    }

    private void buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BACKGROUND);
        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 20));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(UiTheme.SPACE_LG,
                UiTheme.SPACE_LG, UiTheme.SPACE_LG, UiTheme.SPACE_LG));
        body.add(buildRequestCard(), BorderLayout.WEST);
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(buildRequestsCard());
        center.add(Box.createVerticalStrut(20));
        center.add(buildAppointmentsCard());
        body.add(center, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D copy = (Graphics2D) g.create();
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                copy.setPaint(new java.awt.GradientPaint(0, 0, UiTheme.SIDEBAR,
                        getWidth(), getHeight(), new Color(0x1F2A3F)));
                copy.fillRect(0, 0, getWidth(), getHeight());
                copy.setColor(new Color(0x2D374B));
                copy.fillRect(0, getHeight() - 1, getWidth(), 1);
                copy.dispose();
                super.paintComponent(g);
            }
        };
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(0, 64));
        bar.setBorder(BorderFactory.createEmptyBorder(0, UiTheme.SPACE_LG,
                0, UiTheme.SPACE_LG));

        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        JLabel drop = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D copy = (Graphics2D) g.create();
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Path2D shape = new Path2D.Double();
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int r = 9;
                shape.moveTo(cx, cy - r);
                shape.curveTo(cx + r, cy - r * 0.35, cx + r * 0.55, cy + r,
                        cx, cy + r);
                shape.curveTo(cx - r * 0.55, cy + r, cx - r, cy - r * 0.35,
                        cx, cy - r);
                shape.closePath();
                copy.setColor(UiTheme.CORAL);
                copy.fill(shape);
                copy.dispose();
            }
        };
        drop.setPreferredSize(new Dimension(26, 26));
        brand.add(drop, BorderLayout.WEST);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("LifeFlow");
        name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        name.setForeground(Color.WHITE);
        titles.add(name);
        JLabel role = new JLabel("HOSPITAL PORTAL");
        role.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        role.setForeground(UiTheme.SIDEBAR_MUTED);
        titles.add(role);
        brand.add(titles, BorderLayout.CENTER);
        bar.add(brand, BorderLayout.WEST);

        JPanel account = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        account.setOpaque(false);
        JLabel who = new JLabel(hospital.getName());
        who.setFont(UiTheme.BODY_BOLD);
        who.setForeground(Color.WHITE);
        who.setHorizontalAlignment(SwingConstants.RIGHT);
        account.add(who);
        JButton signOut = UiComponents.signOutButton("Sign out");
        signOut.addActionListener(event -> signOut());
        account.add(signOut);
        bar.add(account, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildRequestCard() {
        JPanel card = UiComponents.card(new BorderLayout(0, 16));
        card.setPreferredSize(new Dimension(330, 0));
        card.setBorder(BorderFactory.createEmptyBorder(
                UiTheme.SPACE_LG, UiTheme.SPACE_LG, UiTheme.SPACE_LG,
                UiTheme.SPACE_LG));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading("Request blood"));
        header.add(Box.createVerticalStrut(4));
        header.add(UiComponents.muted("Emergency requests are matched first."));
        card.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("BLOOD TYPE"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 14, 0);
        UiComponents.styleInput(type);
        form.add(type, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("QUANTITY (UNITS)"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 14, 0);
        UiComponents.styleInput(quantity);
        form.add(quantity, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("REQUEST KIND"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        UiComponents.styleInput(kind);
        form.add(kind, gbc);
        card.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        notice.setName("portalNotice");
        notice.setFont(UiTheme.SMALL);
        notice.setForeground(UiTheme.MUTED);
        notice.setHorizontalAlignment(SwingConstants.LEFT);
        JButton submit = UiComponents.primaryButton("Submit request");
        submit.setName("submitRequestButton");
        submit.addActionListener(event -> submitRequest());
        footer.add(notice, BorderLayout.NORTH);
        footer.add(submit, BorderLayout.SOUTH);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildRequestsCard() {
        JPanel card = UiComponents.card(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(
                UiTheme.SPACE_LG, UiTheme.SPACE_LG, UiTheme.SPACE_LG,
                UiTheme.SPACE_LG));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UiComponents.heading("My requests"), BorderLayout.WEST);
        JLabel count = new JLabel(" ");
        count.setName("portalRequestCount");
        count.setFont(UiTheme.SMALL);
        count.setForeground(UiTheme.MUTED);
        header.add(count, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        table.setName("portalRequestsTable");
        UiComponents.configureTable(table);
        table.getColumnModel().getColumn(5)
                .setCellRenderer(UiComponents.statusRenderer());
        int[] widths = {110, 95, 90, 75, 95, 95, 80, 120};
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
        JScrollPane scroll = UiComponents.tableScroll(table);
        card.add(scroll, BorderLayout.CENTER);

        JButton cancel = UiComponents.secondaryButton("Cancel selected");
        cancel.setName("cancelRequestButton");
        cancel.setEnabled(false);
        cancel.addActionListener(event -> cancelSelectedRequest());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            cancel.setEnabled(canCancelSelected());
        });
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(cancel);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildAppointmentsCard() {
        JPanel card = UiComponents.card(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(
                UiTheme.SPACE_LG, UiTheme.SPACE_LG, UiTheme.SPACE_LG,
                UiTheme.SPACE_LG));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UiComponents.heading("Upcoming appointments"),
                BorderLayout.WEST);
        JLabel count = new JLabel(" ");
        count.setName("hospitalAppointmentCount");
        count.setFont(UiTheme.SMALL);
        count.setForeground(UiTheme.MUTED);
        header.add(count, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        appointmentsTable.setName("hospitalAppointmentsTable");
        UiComponents.configureTable(appointmentsTable);
        appointmentsTable.getColumnModel().getColumn(3)
                .setCellRenderer(UiComponents.statusRenderer());
        JScrollPane scroll = UiComponents.tableScroll(appointmentsTable);
        scroll.setPreferredSize(new Dimension(0, 150));
        card.add(scroll, BorderLayout.CENTER);

        JButton record = UiComponents.primaryButton("Record donation");
        record.setName("recordDonationButton");
        record.setEnabled(false);
        record.addActionListener(event -> recordDonation());
        appointmentsTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            record.setEnabled(canRecordSelected());
        });
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        JLabel hint = UiComponents.muted(
                "Record the donation once the donor has given blood.");
        hint.setHorizontalAlignment(SwingConstants.LEFT);
        footer.add(hint, BorderLayout.NORTH);
        footer.add(record, BorderLayout.SOUTH);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private void refreshData() {
        model.setRowCount(0);
        int pending = 0;
        for (BloodRequest request : controller.getRequests()) {
            if (!isMyRequest(request)) {
                continue;
            }
            String units = "—";
            if (request.getStatus() == RequestStatus.FULFILLED) {
                for (FulfilmentRecord record : controller.getFulfilments()) {
                    if (record.requestId().equalsIgnoreCase(request.getId())) {
                        units = record.unitIds().size() + " units ("
                                + String.join(", ", record.unitIds()) + ")";
                        break;
                    }
                }
            }
            model.addRow(new Object[]{
                    request.getId(),
                    request.getKind(),
                    DashboardPanel.displayType(request.getBloodType()),
                    request.getQuantity(),
                    request.getRequestDate().format(DATE),
                    request.getStatus().name(),
                    controller.getVolunteerCountForRequest(request.getId()),
                    units
            });
            if (request.getStatus().name().equals("PENDING")) {
                pending++;
            }
        }
        JLabel count = findLabel("portalRequestCount");
        if (count != null) {
            count.setText(model.getRowCount() + " request(s) · "
                    + pending + " pending");
        }
        refreshAppointments();
    }

    private boolean isMyRequest(BloodRequest request) {
        if (request.getHospitalId() != null && !request.getHospitalId().isBlank()) {
            return request.getHospitalId().equalsIgnoreCase(hospital.getId());
        }
        return hospital.getName().equalsIgnoreCase(request.getRequesterName());
    }

    private void refreshAppointments() {
        appointmentsModel.setRowCount(0);
        ArrayList<DonationAppointment> appointments =
                controller.getAppointmentsForHospital(hospital.getId());
        appointments.sort(java.util.Comparator.comparing(
                DonationAppointment::getAppointmentDate));
        for (DonationAppointment appointment : appointments) {
            String status = appointment.getStatus().name();
            if (appointment.isStale(controller.today())) {
                status = "MISSED";
            }
            appointmentsModel.addRow(new Object[]{
                    appointment.getId(),
                    donorName(appointment.getDonorId()),
                    appointment.getAppointmentDate().format(DATE),
                    status
            });
        }
        JLabel count = findLabel("hospitalAppointmentCount");
        if (count != null) {
            count.setText(appointments.size() + " appointment(s)");
        }
    }

    private String donorName(String donorId) {
        for (Donor donor : controller.getDonors()) {
            if (donor.getId().equalsIgnoreCase(donorId)) {
                return donor.getName();
            }
        }
        return donorId;
    }

    private DonationAppointment selectedAppointment() {
        int row = appointmentsTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        String id = String.valueOf(appointmentsModel.getValueAt(row, 0));
        for (DonationAppointment appointment :
                controller.getAppointmentsForHospital(hospital.getId())) {
            if (appointment.getId().equalsIgnoreCase(id)) {
                return appointment;
            }
        }
        return null;
    }

    private boolean canRecordSelected() {
        DonationAppointment appointment = selectedAppointment();
        return appointment != null && appointment.isBooked()
                && !appointment.isStale(controller.today())
                && !appointment.getAppointmentDate().isAfter(controller.today());
    }

    private void recordDonation() {
        DonationAppointment appointment = selectedAppointment();
        if (appointment == null) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        javax.swing.JDialog dialog = new javax.swing.JDialog(owner,
                "Record donation", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(javax.swing.JDialog.DISPOSE_ON_CLOSE);

        javax.swing.JTextField dateField = new javax.swing.JTextField(
                controller.today().toString());
        dateField.setName("donationDateField");
        UiComponents.styleInput(dateField);
        javax.swing.JLabel feedback = new javax.swing.JLabel(" ");
        feedback.setName("donationRecordError");
        feedback.setFont(UiTheme.SMALL);
        feedback.setForeground(UiTheme.DANGER);

        javax.swing.JPanel form = new javax.swing.JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = 0;
        left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(7, 0, 7, 18);
        javax.swing.JLabel fieldLabel = new javax.swing.JLabel("Donation date (yyyy-MM-dd)");
        fieldLabel.setFont(UiTheme.BODY_BOLD);
        fieldLabel.setForeground(UiTheme.NAVY);
        form.add(fieldLabel, left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = 0;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(7, 0, 7, 0);
        form.add(dateField, right);

        javax.swing.JButton cancel = UiComponents.secondaryButton("Cancel");
        javax.swing.JButton save = UiComponents.primaryButton("Record donation");
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                controller.completeDonationAppointment(appointment.getId(),
                        hospital.getId(),
                        java.time.LocalDate.parse(dateField.getText().trim()));
                dialog.dispose();
                refreshData();
                notice.setForeground(UiTheme.SUCCESS);
                notice.setText("Donation recorded. Unit added to inventory.");
            } catch (java.time.format.DateTimeParseException exception) {
                setFeedback(feedback, "Use yyyy-MM-dd for the date.");
            } catch (LifeFlowException | IOException exception) {
                setFeedback(feedback, exception.getMessage());
            }
        });

        javax.swing.JPanel content = new javax.swing.JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        javax.swing.JPanel header = new javax.swing.JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading("Record donation"));
        header.add(Box.createVerticalStrut(5));
        header.add(UiComponents.muted("Donor " + donorName(appointment.getDonorId())
                + " · " + appointment.getAppointmentDate().format(DATE)));
        content.add(header, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        javax.swing.JPanel buttons = new javax.swing.JPanel(
                new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);
        javax.swing.JPanel footer = new javax.swing.JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.add(feedback, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        UiComponents.configureDialogKeys(dialog, save, cancel);
        dialog.setSize(560, 250);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(dateField::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private void submitRequest() {
        try {
            controller.addRequest(controller.getNextRequestId(),
                    hospital.getName(), hospital.getId(),
                    (BloodType) type.getSelectedItem(),
                    (Integer) quantity.getValue(),
                    "EMERGENCY".equals(kind.getSelectedItem()));
            notice.setForeground(UiTheme.SUCCESS);
            notice.setText("Request submitted successfully.");
            refreshData();
        } catch (LifeFlowException | IOException exception) {
            notice.setForeground(UiTheme.DANGER);
            notice.setText(exception.getMessage());
        }
    }

    private boolean canCancelSelected() {
        BloodRequest request = selectedRequest();
        return request != null && request.getStatus() == RequestStatus.PENDING;
    }

    private void cancelSelectedRequest() {
        BloodRequest request = selectedRequest();
        if (request == null) {
            return;
        }
        try {
            controller.declineRequest(request.getId(),
                    "Requested by " + hospital.getName());
            refreshData();
            notice.setForeground(UiTheme.SUCCESS);
            notice.setText("Request " + request.getId() + " cancelled.");
        } catch (LifeFlowException | IOException exception) {
            notice.setForeground(UiTheme.DANGER);
            notice.setText(exception.getMessage());
        }
    }

    private BloodRequest selectedRequest() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        String id = String.valueOf(model.getValueAt(row, 0));
        for (BloodRequest request : controller.getRequests()) {
            if (request.getId().equalsIgnoreCase(id)) {
                return request;
            }
        }
        return null;
    }

    private static void setFeedback(JLabel label, String message) {
        label.setText("<html>" + message.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;") + "</html>");
        label.setToolTipText(message);
        label.setForeground(UiTheme.DANGER);
    }

    private void signOut() {
        releaseAndDispose();
        switcher.showLogin();
    }

    private void closeApplication() {
        releaseAndDispose();
        switcher.exitApplication();
    }

    private void releaseAndDispose() {
        try {
            controller.close();
        } catch (IOException ignored) {
            // The portal never holds the recovery path open; dispose regardless.
        }
        dispose();
    }

    private JLabel findLabel(String name) {
        return findIn(this, name);
    }

    private static JLabel findIn(java.awt.Component component, String name) {
        if (name.equals(component.getName())) {
            return (JLabel) component;
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                JLabel match = findIn(child, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        label.setForeground(UiTheme.MUTED);
        return label;
    }
}