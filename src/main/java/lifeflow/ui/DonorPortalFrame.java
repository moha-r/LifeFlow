package lifeflow.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import lifeflow.model.AppointmentStatus;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.DonorAccount;
import lifeflow.model.DonationAppointment;
import lifeflow.model.EligibilityReason;
import lifeflow.model.EligibilityResult;
import lifeflow.model.Hospital;
import lifeflow.model.LifeFlowState;
import lifeflow.model.exception.LifeFlowException;
import lifeflow.persistence.LifeFlowStore;
import lifeflow.service.DonationPolicy;
import lifeflow.service.DonorRegistry;
import lifeflow.service.HospitalRegistry;
import lifeflow.service.LifeFlowController;

/** Self-service portal where a donor views status, history, and profile. */
@SuppressWarnings({"serial", "this-escape"})
public final class DonorPortalFrame extends JFrame {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final LifeFlowController controller;
    private final DonorAccount account;
    private final DonorRegistry registry;
    private final HospitalRegistry hospitalRegistry;
    private final SessionSwitcher switcher;

    private final DefaultTableModel model =
            UiComponents.readOnlyModel("Unit ID", "Donation Date", "Blood Type",
                    "Status");
    private final JTable table = new JTable(model);
    private final DefaultTableModel appointmentsModel =
            UiComponents.readOnlyModel("Appointment", "Hospital", "Date",
                    "Status");
    private final JTable appointmentsTable = new JTable(appointmentsModel);
    private final DefaultTableModel urgentModel =
            UiComponents.readOnlyModel("Request", "Kind", "Blood Type",
                    "Quantity", "Date");
    private final JTable urgentTable = new JTable(urgentModel);
    private final JLabel notice = new JLabel(" ");
    private final StatusChipLabel statusChip = new StatusChipLabel();
    private final JLabel statusReason = new JLabel(" ");
    private final JLabel statusSummary = new JLabel(" ");
    private final JLabel donationCount = new JLabel(" ");
    private final JButton editProfileButton =
            UiComponents.secondaryButton("Edit profile");
    private final JButton changePasswordButton =
            UiComponents.secondaryButton("Change password");
    private final JButton bookButton = UiComponents.primaryButton("Book a donation");
    private final JButton cancelButton = UiComponents.secondaryButton("Cancel");
    private final JButton volunteerButton = UiComponents.primaryButton("Volunteer");

    public DonorPortalFrame(LifeFlowState state, LifeFlowStore store,
                            DonorAccount account, DonorRegistry donorRegistry,
                            HospitalRegistry hospitalRegistry,
                            SessionSwitcher switcher) {
        super("LifeFlow — Donor Portal");
        this.account = account;
        this.registry = donorRegistry;
        this.hospitalRegistry = hospitalRegistry;
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
        JPanel west = new JPanel();
        west.setOpaque(false);
        west.setLayout(new BoxLayout(west, BoxLayout.Y_AXIS));
        west.add(buildStatusCard());
        west.add(Box.createVerticalStrut(20));
        west.add(buildUrgentCard());
        body.add(west, BorderLayout.WEST);
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(buildHistoryCard());
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
        JLabel role = new JLabel("DONOR PORTAL");
        role.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        role.setForeground(UiTheme.SIDEBAR_MUTED);
        titles.add(role);
        brand.add(titles, BorderLayout.CENTER);
        bar.add(brand, BorderLayout.WEST);

        JPanel account = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        account.setOpaque(false);
        JLabel who = new JLabel();
        who.setName("donorNameLabel");
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

    private JPanel buildStatusCard() {
        JPanel card = UiComponents.card(new BorderLayout(0, 14));
        card.setPreferredSize(new Dimension(330, 0));
        card.setBorder(BorderFactory.createEmptyBorder(
                UiTheme.SPACE_LG, UiTheme.SPACE_LG, UiTheme.SPACE_LG,
                UiTheme.SPACE_LG));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading("Donation status"));
        header.add(Box.createVerticalStrut(4));
        header.add(UiComponents.muted(
                "Computed from your profile and donation history."));
        card.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        statusChip.setName("donorStatusChip");
        statusChip.setOpaque(false);
        statusChip.setHorizontalAlignment(SwingConstants.CENTER);
        statusChip.setPreferredSize(new Dimension(290, 40));
        statusChip.setMaximumSize(new Dimension(290, 40));
        statusChip.setAlignmentX(CENTER_ALIGNMENT);
        statusReason.setName("donorStatusReason");
        statusReason.setFont(UiTheme.BODY);
        statusReason.setForeground(UiTheme.NAVY);
        statusReason.setHorizontalAlignment(SwingConstants.CENTER);
        statusSummary.setName("donorStatusSummary");
        statusSummary.setFont(UiTheme.SMALL);
        statusSummary.setForeground(UiTheme.MUTED);
        statusSummary.setHorizontalAlignment(SwingConstants.CENTER);
        center.add(statusChip);
        center.add(Box.createVerticalStrut(12));
        center.add(statusReason);
        center.add(Box.createVerticalStrut(10));
        center.add(statusSummary);
        card.add(center, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildHistoryCard() {
        JPanel card = UiComponents.card(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(
                UiTheme.SPACE_LG, UiTheme.SPACE_LG, UiTheme.SPACE_LG,
                UiTheme.SPACE_LG));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UiComponents.heading("My donations"), BorderLayout.WEST);
        donationCount.setName("donorDonationCount");
        donationCount.setFont(UiTheme.SMALL);
        donationCount.setForeground(UiTheme.MUTED);
        header.add(donationCount, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        table.setName("donorDonationsTable");
        UiComponents.configureTable(table);
        table.getColumnModel().getColumn(3)
                .setCellRenderer(UiComponents.statusRenderer());
        JScrollPane scroll = UiComponents.tableScroll(table);
        card.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        notice.setName("portalNotice");
        notice.setFont(UiTheme.SMALL);
        notice.setForeground(UiTheme.MUTED);
        notice.setHorizontalAlignment(SwingConstants.LEFT);
        editProfileButton.setName("donorEditProfileButton");
        editProfileButton.addActionListener(event -> showEditDialog());
        changePasswordButton.setName("donorChangePasswordButton");
        changePasswordButton.addActionListener(event -> showPasswordDialog());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        actions.setOpaque(false);
        actions.add(editProfileButton);
        actions.add(changePasswordButton);
        footer.add(notice, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.SOUTH);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildUrgentCard() {
        JPanel card = UiComponents.card(new BorderLayout(0, 14));
        card.setPreferredSize(new Dimension(330, 0));
        card.setBorder(BorderFactory.createEmptyBorder(
                UiTheme.SPACE_LG, UiTheme.SPACE_LG, UiTheme.SPACE_LG,
                UiTheme.SPACE_LG));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UiComponents.heading("Urgent needs"), BorderLayout.WEST);
        card.add(header, BorderLayout.NORTH);

        urgentTable.setName("donorUrgentTable");
        UiComponents.configureTable(urgentTable);
        urgentTable.getColumnModel().getColumn(1)
                .setCellRenderer(UiComponents.statusRenderer());
        JScrollPane scroll = UiComponents.tableScroll(urgentTable);
        scroll.setPreferredSize(new Dimension(300, 150));
        card.add(scroll, BorderLayout.CENTER);

        volunteerButton.setName("donorVolunteerButton");
        volunteerButton.setEnabled(false);
        volunteerButton.addActionListener(event -> openBookDialog(selectedUrgent()));
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        JLabel hint = UiComponents.muted(
                "Requests your blood type can support.");
        hint.setHorizontalAlignment(SwingConstants.LEFT);
        footer.add(hint, BorderLayout.NORTH);
        footer.add(volunteerButton, BorderLayout.SOUTH);
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
        header.add(UiComponents.heading("My appointments"), BorderLayout.WEST);
        JLabel count = new JLabel(" ");
        count.setName("donorAppointmentCount");
        count.setFont(UiTheme.SMALL);
        count.setForeground(UiTheme.MUTED);
        header.add(count, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        appointmentsTable.setName("donorAppointmentsTable");
        UiComponents.configureTable(appointmentsTable);
        appointmentsTable.getColumnModel().getColumn(3)
                .setCellRenderer(UiComponents.statusRenderer());
        appointmentsTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            boolean active = false;
            for (DonationAppointment appointment :
                    controller.getAppointmentsForDonor(account.getDonorId())) {
                if (appointment.isBooked() && !appointment.isStale(
                        controller.today())) {
                    active = true;
                    break;
                }
            }
            cancelButton.setEnabled(active
                    && appointmentsTable.getSelectedRow() >= 0);
        });
        JScrollPane scroll = UiComponents.tableScroll(appointmentsTable);
        scroll.setPreferredSize(new Dimension(0, 150));
        card.add(scroll, BorderLayout.CENTER);

        bookButton.setName("donorBookButton");
        bookButton.addActionListener(event -> openBookDialog(null));
        cancelButton.setName("donorCancelButton");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(event -> cancelSelected());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        actions.setOpaque(false);
        actions.add(cancelButton);
        actions.add(bookButton);
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        JLabel hint = UiComponents.muted(
                "Book a slot at your hospital, then donate on the day.");
        hint.setHorizontalAlignment(SwingConstants.LEFT);
        footer.add(hint, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.SOUTH);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private void refreshData() {
        JLabel who = findLabel("donorNameLabel");
        if (who != null) {
            who.setText(account.getUsername());
        }
        Donor donor = findDonor(account.getDonorId());
        if (donor == null) {
            statusChip.setChip("PROFILE REMOVED", UiTheme.DANGER_LIGHT,
                    UiTheme.DANGER);
            statusReason.setText("Your donor profile was removed by the administrator.");
            statusSummary.setText(" ");
            donationCount.setText("0 donation(s)");
            editProfileButton.setEnabled(false);
            refreshTable();
            refreshAppointments(donor);
            return;
        }
        editProfileButton.setEnabled(true);
        refreshStatus(donor);
        refreshTable();
        refreshAppointments(donor);
        refreshUrgent(donor);
    }

    private void refreshAppointments(Donor donor) {
        appointmentsModel.setRowCount(0);
        ArrayList<DonationAppointment> appointments =
                controller.getAppointmentsForDonor(account.getDonorId());
        appointments.sort(Comparator.comparing(DonationAppointment::getAppointmentDate));
        boolean active = false;
        for (DonationAppointment appointment : appointments) {
            String status = appointment.getStatus().name();
            if (appointment.isStale(controller.today())) {
                status = "MISSED";
            } else if (appointment.isBooked()) {
                active = true;
            }
            appointmentsModel.addRow(new Object[]{
                    appointment.getId(),
                    hospitalName(appointment.getHospitalId()),
                    appointment.getAppointmentDate().format(DATE),
                    status
            });
        }
        JLabel count = findLabel("donorAppointmentCount");
        if (count != null) {
            count.setText(appointments.size() + " appointment(s)");
        }
        boolean hasSelection = appointmentsTable.getSelectedRow() >= 0;
        cancelButton.setEnabled(active && hasSelection);
        if (donor == null) {
            bookButton.setEnabled(false);
            volunteerButton.setEnabled(false);
        } else {
            bookButton.setEnabled(!active);
            volunteerButton.setEnabled(false);
        }
    }

    private void refreshUrgent(Donor donor) {
        urgentModel.setRowCount(0);
        ArrayList<BloodRequest> needs = new ArrayList<>();
        if (donor != null) {
            needs = controller.getUrgentNeedsForDonor(donor.getId());
        }
        for (BloodRequest request : needs) {
            urgentModel.addRow(new Object[]{
                    request.getId(),
                    request.getKind(),
                    DashboardPanel.displayType(request.getBloodType()),
                    request.getQuantity(),
                    request.getRequestDate().format(DATE)
            });
        }
        volunteerButton.setEnabled(donor != null && !needs.isEmpty()
                && !controller.donorHasActiveAppointment(account.getDonorId()));
    }

    private BloodRequest selectedUrgent() {
        int row = urgentTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        String id = String.valueOf(urgentModel.getValueAt(row, 0));
        for (BloodRequest request : controller.getRequests()) {
            if (request.getId().equalsIgnoreCase(id)) {
                return request;
            }
        }
        return null;
    }

    private String hospitalName(String hospitalId) {
        Hospital hospital = hospitalRegistry.findById(hospitalId);
        return hospital == null ? hospitalId : hospital.getName();
    }

    private void cancelSelected() {
        int row = appointmentsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        DonationAppointment appointment = findAppointment(
                String.valueOf(appointmentsModel.getValueAt(row, 0)));
        if (appointment == null || !appointment.isBooked()) {
            return;
        }
        try {
            controller.cancelDonationAppointment(appointment.getId(),
                    account.getDonorId());
            refreshData();
            notice.setForeground(UiTheme.SUCCESS);
            notice.setText("Appointment cancelled.");
        } catch (LifeFlowException | IOException exception) {
            notice.setForeground(UiTheme.DANGER);
            notice.setText(exception.getMessage());
        }
    }

    private DonationAppointment findAppointment(String id) {
        for (DonationAppointment appointment :
                controller.getAppointmentsForDonor(account.getDonorId())) {
            if (appointment.getId().equalsIgnoreCase(id)) {
                return appointment;
            }
        }
        return null;
    }

    private void openBookDialog(BloodRequest linkedRequest) {
        Donor donor = findDonor(account.getDonorId());
        if (donor == null) {
            notice.setForeground(UiTheme.DANGER);
            notice.setText("Your donor profile is no longer available.");
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner,
                linkedRequest == null ? "Book a donation" : "Volunteer for a request",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JComboBox<Hospital> hospitals = new JComboBox<>(
                hospitalRegistry.findAll().toArray(new Hospital[0]));
        hospitals.setName("appointmentHospital");
        hospitals.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            javax.swing.JLabel label = new javax.swing.JLabel(
                    value == null ? "" : value.getName());
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            label.setBorder(javax.swing.BorderFactory.createEmptyBorder(
                    4, 8, 4, 8));
            return label;
        });
        JTextField dateField = new JTextField(
                controller.today().toString());
        dateField.setName("appointmentDate");
        JLabel eligibility = new JLabel(" ");
        eligibility.setName("appointmentEligibility");
        eligibility.setFont(UiTheme.SMALL);
        JLabel feedback = new JLabel(" ");
        feedback.setName("appointmentError");
        feedback.setFont(UiTheme.SMALL);
        feedback.setForeground(UiTheme.DANGER);
        UiComponents.styleInput(hospitals);
        UiComponents.styleInput(dateField);

        Runnable preview = () -> {
            try {
                LocalDate date = LocalDate.parse(dateField.getText().trim());
                EligibilityResult result = previewEligibility(donor, date);
                if (result.eligible()) {
                    eligibility.setForeground(UiTheme.SUCCESS);
                    eligibility.setText("Eligible on " + date.format(DATE) + ".");
                } else {
                    eligibility.setForeground(UiTheme.DANGER);
                    eligibility.setText("<html>" + escape(result.message())
                            + "</html>");
                }
            } catch (DateTimeParseException exception) {
                eligibility.setForeground(UiTheme.DANGER);
                eligibility.setText("Use yyyy-MM-dd for the date.");
            }
        };

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
        addFormRow(form, 0, "Hospital", hospitals);
        addFormRow(form, 1, "Appointment date (yyyy-MM-dd)", dateField);
        if (linkedRequest != null) {
            JLabel linked = new JLabel(linkedRequest.getId() + " · "
                    + DashboardPanel.displayType(linkedRequest.getBloodType())
                    + " · " + linkedRequest.getQuantity() + " unit(s)");
            linked.setName("appointmentLinkedRequest");
            linked.setFont(UiTheme.BODY_BOLD);
            linked.setForeground(UiTheme.NAVY);
            addFormRow(form, 2, "Supporting request", linked);
        }
        addFormRow(form, linkedRequest == null ? 2 : 3, "Eligibility preview",
                eligibility);

        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton book = UiComponents.primaryButton(
                linkedRequest == null ? "Book appointment" : "Confirm volunteer");
        book.setName("appointmentBookButton");
        cancel.addActionListener(event -> dialog.dispose());
        book.addActionListener(event -> {
            try {
                Hospital hospital = (Hospital) hospitals.getSelectedItem();
                if (hospital == null) {
                    setFeedback(feedback, "Select a hospital.");
                    return;
                }
                LocalDate date = LocalDate.parse(dateField.getText().trim());
                controller.bookDonationAppointment(donor.getId(),
                        hospital.getId(), date,
                        linkedRequest == null ? null : linkedRequest.getId());
                dialog.dispose();
                refreshData();
                notice.setForeground(UiTheme.SUCCESS);
                notice.setText(linkedRequest == null
                        ? "Appointment booked successfully."
                        : "You volunteered for " + linkedRequest.getId()
                                + ". Thank you!");
            } catch (DateTimeParseException exception) {
                setFeedback(feedback, "Use yyyy-MM-dd for the date.");
            } catch (LifeFlowException | IOException exception) {
                setFeedback(feedback, exception.getMessage());
            }
        });
        dateField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                preview.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                preview.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                preview.run();
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading(linkedRequest == null
                ? "Book a donation" : "Volunteer for a request"));
        header.add(Box.createVerticalStrut(5));
        header.add(UiComponents.muted(linkedRequest == null
                ? "Pick a hospital and a date to donate."
                : "Your donation will support this request."));
        content.add(header, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(book);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.add(feedback, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        UiComponents.configureDialogKeys(dialog, book, cancel);
        dialog.setSize(560, linkedRequest == null ? 360 : 400);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(preview);
        SwingUtilities.invokeLater(dateField::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private EligibilityResult previewEligibility(Donor donor, LocalDate date) {
        ZoneId zone = ZoneId.systemDefault();
        Clock fixed = Clock.fixed(date.atStartOfDay(zone).toInstant(), zone);
        return new DonationPolicy(fixed).evaluate(donor, date,
                effectiveLastDonation(donor));
    }

    private LocalDate effectiveLastDonation(Donor donor) {
        LocalDate latest = donor.getExternalLastDonationDate();
        for (BloodUnit unit : controller.getStateSnapshot().getUnits()) {
            if (unit.getDonorId().equalsIgnoreCase(donor.getId())
                    && (latest == null || unit.getDonationDate().isAfter(latest))) {
                latest = unit.getDonationDate();
            }
        }
        return latest;
    }

    private void refreshStatus(Donor donor) {
        EligibilityResult result = evaluate(donor, controller.today());
        String label;
        Color foreground;
        Color background;
        if (result.eligible()) {
            label = "ELIGIBLE TO DONATE";
            foreground = UiTheme.SUCCESS;
            background = UiTheme.SUCCESS_LIGHT;
        } else if (result.reason() == EligibilityReason.WAITING_PERIOD) {
            label = "DEFERRED";
            foreground = UiTheme.WARNING;
            background = UiTheme.WARNING_LIGHT;
        } else {
            label = "NOT ELIGIBLE";
            foreground = UiTheme.DANGER;
            background = UiTheme.DANGER_LIGHT;
        }
        statusChip.setChip(label, background, foreground);
        statusReason.setText(result.message());
        String last = donor.getExternalLastDonationDate() == null
                ? "—" : donor.getExternalLastDonationDate().format(DATE);
        statusSummary.setText("<html><div style='text-align:center'>"
                + "Age " + donor.getAge() + " · " + donor.getWeightKg() + " kg · "
                + DashboardPanel.displayType(donor.getBloodType())
                + "<br>Last donation: " + last + "</div></html>");
    }

    private void refreshTable() {
        model.setRowCount(0);
        for (BloodUnit unit : controller.getStateSnapshot().getUnits()) {
            if (!unit.getDonorId().equalsIgnoreCase(account.getDonorId())) {
                continue;
            }
            model.addRow(new Object[]{
                    unit.getId(),
                    unit.getDonationDate().format(DATE),
                    DashboardPanel.displayType(unit.getBloodType()),
                    unit.getStatus().name()
            });
        }
        donationCount.setText(model.getRowCount() + " donation(s)");
    }

    private Donor findDonor(String donorId) {
        for (Donor donor : controller.getStateSnapshot().getDonors()) {
            if (donor.getId().equalsIgnoreCase(donorId)) {
                return donor;
            }
        }
        return null;
    }

    private EligibilityResult evaluate(Donor donor, LocalDate today) {
        Map<String, LocalDate> latest = new HashMap<>();
        for (BloodUnit unit : controller.getStateSnapshot().getUnits()) {
            if (unit.getDonorId().equalsIgnoreCase(donor.getId())) {
                latest.merge(unit.getDonorId(), unit.getDonationDate(),
                        (first, second) -> first.isAfter(second) ? first : second);
            }
        }
        LocalDate effective = donor.getExternalLastDonationDate();
        LocalDate internal = latest.get(donor.getId());
        if (internal != null && (effective == null || internal.isAfter(effective))) {
            effective = internal;
        }
        ZoneId zone = ZoneId.systemDefault();
        Clock fixed = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone);
        return new DonationPolicy(fixed).evaluate(donor, today, effective);
    }

    private void showEditDialog() {
        Donor donor = findDonor(account.getDonorId());
        if (donor == null) {
            notice.setForeground(UiTheme.DANGER);
            notice.setText("Your donor profile is no longer available.");
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Edit my profile",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextField name = new JTextField(donor.getName());
        JTextField age = new JTextField(Integer.toString(donor.getAge()));
        JTextField weight = new JTextField(Double.toString(donor.getWeightKg()));
        JComboBox<BloodType> type = new JComboBox<>(BloodType.values());
        type.setSelectedItem(donor.getBloodType());
        type.setEnabled(!hasUnits(donor.getId()));
        if (!type.isEnabled()) {
            type.setToolTipText("Blood type is locked because units exist.");
        }
        JCheckBox hasExternal =
                new JCheckBox("Has donated outside LifeFlow?");
        hasExternal.setOpaque(false);
        hasExternal.setFont(UiTheme.BODY);
        LocalDate external = donor.getExternalLastDonationDate();
        hasExternal.setSelected(external != null);
        JTextField externalDate = new JTextField(
                external == null ? "" : external.toString());
        JPanel externalRow = new JPanel(new BorderLayout());
        externalRow.setOpaque(false);
        externalRow.add(externalDate, BorderLayout.CENTER);
        externalRow.setVisible(hasExternal.isSelected());
        UiComponents.styleInput(name);
        UiComponents.styleInput(age);
        UiComponents.styleInput(weight);
        UiComponents.styleInput(type);
        UiComponents.styleInput(externalDate);

        JLabel feedback = new JLabel(" ");
        feedback.setFont(UiTheme.SMALL);
        feedback.setForeground(UiTheme.DANGER);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
        addFormRow(form, 0, "Full name", name);
        addFormRow(form, 1, "Age", age);
        addFormRow(form, 2, "Weight (kg)", weight);
        addFormRow(form, 3, "Blood type", type);
        addFormRow(form, 4, "External history", hasExternal);
        addFormRow(form, 5, "Latest external date", externalRow);

        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton("Save changes");
        hasExternal.addActionListener(event -> {
            externalRow.setVisible(hasExternal.isSelected());
            dialog.pack();
            dialog.setSize(560, hasExternal.isSelected() ? 470 : 430);
        });
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                int donorAge = Integer.parseInt(age.getText().trim());
                double donorWeight = Double.parseDouble(weight.getText().trim());
                LocalDate externalDonation = hasExternal.isSelected()
                        ? LocalDate.parse(externalDate.getText().trim()) : null;
                controller.updateDonor(donor.getId(), name.getText(), donorAge,
                        donorWeight, (BloodType) type.getSelectedItem(),
                        externalDonation);
                dialog.dispose();
                refreshData();
                notice.setForeground(UiTheme.SUCCESS);
                notice.setText("Profile updated. Status recalculated.");
            } catch (DateTimeParseException exception) {
                setFeedback(feedback, "Use yyyy-MM-dd for the external date.");
            } catch (NumberFormatException exception) {
                setFeedback(feedback, "Age and weight must be valid numbers.");
            } catch (LifeFlowException | IOException exception) {
                setFeedback(feedback, exception.getMessage());
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading("Edit donor profile"));
        header.add(Box.createVerticalStrut(5));
        header.add(UiComponents.muted(
                "Your eligibility is recalculated automatically."));
        content.add(header, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.add(feedback, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        UiComponents.configureDialogKeys(dialog, save, cancel);
        dialog.setSize(560, hasExternal.isSelected() ? 470 : 430);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(name::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private void showPasswordDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Change password",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPasswordField current = new JPasswordField();
        current.setName("donorCurrentPassword");
        JPasswordField replacement = new JPasswordField();
        replacement.setName("donorNewPassword");
        JPasswordField confirm = new JPasswordField();
        confirm.setName("donorConfirmPassword");
        UiComponents.styleInput(current);
        UiComponents.styleInput(replacement);
        UiComponents.styleInput(confirm);
        JButton toggle = new JButton("Show");
        toggle.setName("donorPasswordToggle");
        toggle.setFont(UiTheme.BODY_BOLD);
        toggle.setForeground(UiTheme.NAVY);
        toggle.setFocusPainted(false);
        toggle.setContentAreaFilled(false);
        toggle.setOpaque(false);
        toggle.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        toggle.addActionListener(event -> {
            boolean hidden = current.getEchoChar() != 0;
            current.setEchoChar(hidden ? (char) 0 : '\u2022');
            replacement.setEchoChar(hidden ? (char) 0 : '\u2022');
            confirm.setEchoChar(hidden ? (char) 0 : '\u2022');
            toggle.setText(hidden ? "Hide" : "Show");
        });

        JLabel feedback = new JLabel(" ");
        feedback.setFont(UiTheme.SMALL);
        feedback.setForeground(UiTheme.DANGER);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
        addFormRow(form, 0, "Current password", current);
        addFormRow(form, 1, "New password", replacement);
        addFormRow(form, 2, "Confirm new password", confirm);

        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton("Update password");
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            if (!new String(replacement.getPassword())
                    .equals(new String(confirm.getPassword()))) {
                setFeedback(feedback, "New passwords do not match.");
                return;
            }
            try {
                registry.changePassword(account.getUsername(),
                        new String(current.getPassword()),
                        new String(replacement.getPassword()));
                dialog.dispose();
                notice.setForeground(UiTheme.SUCCESS);
                notice.setText("Password updated successfully.");
            } catch (LifeFlowException | IOException exception) {
                setFeedback(feedback, exception.getMessage());
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading("Change password"));
        header.add(Box.createVerticalStrut(5));
        header.add(UiComponents.muted(
                "At least " + DonorRegistry.MIN_PASSWORD_LENGTH + " characters."));
        content.add(header, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);
        buttons.add(toggle);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.add(feedback, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        UiComponents.configureDialogKeys(dialog, save, cancel);
        dialog.setSize(560, 330);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(current::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private boolean hasUnits(String donorId) {
        return controller.getStateSnapshot().getUnits().stream().anyMatch(unit ->
                unit.getDonorId().equalsIgnoreCase(donorId));
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

    private static void setFeedback(JLabel label, String message) {
        label.setText("<html>" + escape(message) + "</html>");
        label.setToolTipText(message);
        label.setForeground(UiTheme.DANGER);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void addFormRow(JPanel form, int row, String label,
                                   Component input) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(7, 0, 7, 18);
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(UiTheme.BODY_BOLD);
        fieldLabel.setForeground(UiTheme.NAVY);
        form.add(fieldLabel, left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(7, 0, 7, 0);
        form.add(input, right);
    }

    private JLabel findLabel(String name) {
        return findIn(this, name);
    }

    private static JLabel findIn(Component component, String name) {
        if (name.equals(component.getName())) {
            return (JLabel) component;
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                JLabel match = findIn(child, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    /** Rounded chip that shows the donor eligibility state. */
    private static final class StatusChipLabel extends JLabel {
        private static final long serialVersionUID = 1L;
        private Color chipBackground = UiTheme.SUCCESS_LIGHT;

        private StatusChipLabel() {
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setPreferredSize(new Dimension(290, 40));
            setMaximumSize(new Dimension(290, 40));
            setAlignmentX(CENTER_ALIGNMENT);
        }

        private void setChip(String text, Color background, Color foreground) {
            setText(text);
            chipBackground = background;
            setForeground(foreground);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(chipBackground);
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                    getHeight(), getHeight());
            copy.dispose();
            super.paintComponent(graphics);
        }
    }
}