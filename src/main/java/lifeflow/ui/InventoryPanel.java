package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EligibilityResult;
import lifeflow.model.InventoryState;
import lifeflow.model.LifeFlowState;
import lifeflow.model.UnitStatus;
import lifeflow.service.DonationPolicy;
import lifeflow.service.LifeFlowController;

/** Inventory workspace for recording and correcting donation events. */
@SuppressWarnings("serial")
public final class InventoryPanel extends JPanel {
    private final LifeFlowController controller;
    private final Runnable onDataChanged;
    private final Consumer<UiNotice> status;
    private final DefaultTableModel model = UiComponents.readOnlyModel(
            "Unit ID", "Donor", "Blood Type", "Donation Date", "Expiry Date",
            "Days Left", "Status");
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = UiComponents.searchField("Search blood units");
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[]{
            "All statuses", "AVAILABLE", "EXPIRED", "USED", "EXPIRING IN 7 DAYS"});
    private final JComboBox<String> bloodTypeFilter = new JComboBox<>();
    private final JLabel recordCount = UiComponents.muted("0 RECORDS");
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel center = new JPanel(centerLayout);

    public InventoryPanel(LifeFlowController controller, Runnable onDataChanged,
                          Consumer<UiNotice> status) {
        super(new BorderLayout());
        this.controller = controller;
        this.onDataChanged = onDataChanged;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);
        configureFilters();
        PageShell shell = new PageShell("Inventory registry",
                "Record donations, track expiry, and review unit usage.");
        shell.setActions(buildPageActions());
        shell.setToolbar(buildToolbar());
        shell.setBody(buildCenter());
        add(shell, BorderLayout.CENTER);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(6).setCellRenderer(UiComponents.statusRenderer());
        int[] widths = {95, 190, 80, 110, 110, 120, 95};
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(javax.swing.JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                java.awt.Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                String state = t.getModel().getValueAt(t.convertRowIndexToModel(row), 6).toString();
                if (!isSelected) {
                    if ("EXPIRED".equals(state)) {
                        c.setBackground(new java.awt.Color(255, 235, 238));
                        c.setForeground(lifeflow.ui.UiTheme.DANGER);
                    } else if ("DISCARDED".equals(state) || "USED".equals(state)) {
                        c.setBackground(t.getBackground());
                        c.setForeground(java.awt.Color.GRAY);
                    } else {
                        c.setBackground(t.getBackground());
                        c.setForeground(t.getForeground());
                    }
                }
                return c;
            }
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    showEditDialog();
                }
            }
        });
        installFilters();
        refreshData();
    }

    private void configureFilters() {
        statusFilter.setName("inventoryStatusFilter");
        bloodTypeFilter.setName("inventoryBloodTypeFilter");
        bloodTypeFilter.addItem("All blood types");
        for (BloodType type : BloodType.values()) {
            bloodTypeFilter.addItem(DashboardPanel.displayType(type));
        }
        UiComponents.styleInput(statusFilter);
        UiComponents.styleInput(bloodTypeFilter);
        search.setPreferredSize(new java.awt.Dimension(200, 34));
        statusFilter.setPreferredSize(new java.awt.Dimension(165, 34));
        bloodTypeFilter.setPreferredSize(new java.awt.Dimension(135, 34));
    }

    private JPanel buildPageActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        JButton add = UiComponents.primaryButton("+ Add unit");
        add.setPreferredSize(new java.awt.Dimension(116, 34));
        add.addActionListener(event -> showAddDialog());
        actions.add(add);
        return actions;
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
        filters.add(bloodTypeFilter);
        toolbar.add(filters, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        actions.setOpaque(false);
        JButton edit = UiComponents.secondaryButton("Correct dates");
        edit.setPreferredSize(new java.awt.Dimension(132, 34));
        edit.addActionListener(event -> showEditDialog());
        actions.add(recordCount);
        actions.add(edit);
        toolbar.add(actions, BorderLayout.EAST);
        return toolbar;
    }

    private JPanel buildCenter() {
        center.setOpaque(false);
        JPanel tableCard = UiComponents.densePanel(new BorderLayout());
        tableCard.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        tableCard.add(UiComponents.tableScroll(table), BorderLayout.CENTER);
        center.add(tableCard, "table");
        JPanel empty = UiComponents.densePanel(new GridBagLayout());
        empty.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        JPanel message = new JPanel();
        message.setOpaque(false);
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        JLabel title = UiComponents.heading("No blood units recorded");
        title.setAlignmentX(CENTER_ALIGNMENT);
        JLabel copy = UiComponents.muted(
                "Register a donor profile, then record an eligible donation.");
        copy.setAlignmentX(CENTER_ALIGNMENT);
        message.add(title);
        message.add(Box.createVerticalStrut(7));
        message.add(copy);
        empty.add(message);
        center.add(empty, "empty");
        return center;
    }

    private void installFilters() {
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void removeUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void changedUpdate(DocumentEvent event) { updateFilter(); }
        });
        statusFilter.addActionListener(event -> updateFilter());
        bloodTypeFilter.addActionListener(event -> updateFilter());
    }

    private void updateFilter() {
        ArrayList<RowFilter<Object, Object>> filters = new ArrayList<>();
        String text = UiComponents.searchValue(search);
        if (!text.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
        String selectedStatus = String.valueOf(statusFilter.getSelectedItem());
        if ("EXPIRING IN 7 DAYS".equals(selectedStatus)) {
            filters.add(new RowFilter<>() {
                @Override
                public boolean include(Entry<?, ?> entry) {
                    Object days = entry.getValue(5);
                    Object state = entry.getValue(6);
                    if (!"AVAILABLE".equals(String.valueOf(state))) {
                        return false;
                    }
                    try {
                        long value = Long.parseLong(String.valueOf(days));
                        return value >= 0 && value <= 7;
                    } catch (NumberFormatException exception) {
                        return false;
                    }
                }
            });
        } else if (!selectedStatus.startsWith("All")) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(selectedStatus) + "$", 6));
        }
        String type = String.valueOf(bloodTypeFilter.getSelectedItem());
        if (!type.startsWith("All")) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(type) + "$", 2));
        }
        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        recordCount.setText(sorter.getViewRowCount() + " RECORDS");
    }

    public void showAddDialog() {
        LifeFlowState snapshot = controller.getStateSnapshot();
        if (snapshot.getDonors().isEmpty()) {
            status.accept(UiNotice.info(
                    "Register a donor before recording a blood unit."));
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Add blood unit",
                Dialog.ModalityType.APPLICATION_MODAL);
        JTextField id = new JTextField(controller.getNextUnitId());
        id.setEditable(false);
        JComboBox<DonorOption> donor = new JComboBox<>();
        donor.setName("donorSelector");
        ArrayList<DonorOption> options = donorOptions(snapshot, controller.today());
        options.forEach(donor::addItem);
        int firstEligible = firstEligibleIndex(options);
        if (firstEligible >= 0) {
            donor.setSelectedIndex(firstEligible);
        }
        JTextField donation = new JTextField(controller.today().toString());
        JTextField expiry = new JTextField(
                controller.today().plusDays(DonationPolicy.UNIT_SHELF_LIFE_DAYS)
                        .toString());
        expiry.setEditable(false);
        UiComponents.styleInput(id);
        UiComponents.styleInput(donor);
        UiComponents.styleInput(donation);
        UiComponents.styleInput(expiry);

        JButton todayButton = UiComponents.secondaryButton("Today");
        todayButton.setPreferredSize(new java.awt.Dimension(82, 38));
        JPanel donationInput = new JPanel(new BorderLayout(8, 0));
        donationInput.setOpaque(false);
        donationInput.add(donation, BorderLayout.CENTER);
        donationInput.add(todayButton, BorderLayout.EAST);
        JLabel feedback = feedbackLabel();
        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton("Add unit");
        JPanel form = formPanel();
        addFormRow(form, 0, "Unit ID (auto)", id);
        addFormRow(form, 1, "Donor", donor);
        addFormRow(form, 2, "Donation date", donationInput);
        addFormRow(form, 3, "Expiry date (calculated)", expiry);
        Runnable validate = () -> updateAddFormState(id, donor, donation, expiry,
                feedback, save);
        installValidation(donation, validate);
        donor.addActionListener(event -> validate.run());
        todayButton.addActionListener(event -> donation.setText(
                controller.today().toString()));
        validate.run();
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                DonorOption selected = (DonorOption) donor.getSelectedItem();
                if (selected == null) {
                    throw new IllegalArgumentException("Select a donor.");
                }
                controller.addBloodUnit(id.getText(), selected.id,
                        LocalDate.parse(donation.getText().trim()));
                dialog.dispose();
                onDataChanged.run();
                status.accept(UiNotice.success("Blood unit recorded successfully."));
            } catch (DateTimeParseException exception) {
                showValidation(feedback, save,
                        "Use yyyy-MM-dd for the donation date.", false);
            } catch (IllegalArgumentException | IOException exception) {
                showValidation(feedback, save, exception.getMessage(), false);
            }
        });
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent event) {
                donor.requestFocusInWindow();
            }
        });
        finishDialog(dialog, "Record a donation",
                "One whole-blood donation creates one unit with a 35-day shelf life.",
                form, feedback, save, cancel, 500);
    }

    private void showEditDialog() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            status.accept(UiNotice.info("Select an unused blood unit to correct."));
            return;
        }
        String id = model.getValueAt(table.convertRowIndexToModel(viewRow), 0).toString();
        BloodUnit unit = controller.getStateSnapshot().getUnits().stream()
                .filter(item -> item.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
        if (unit == null) {
            return;
        }
        if (unit.getStatus() == UnitStatus.USED) {
            status.accept(UiNotice.warning("Used blood units cannot be corrected."));
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Correct blood unit dates",
                Dialog.ModalityType.APPLICATION_MODAL);
        JTextField idField = new JTextField(unit.getId());
        JTextField donorField = new JTextField(donorDisplay(unit));
        JTextField typeField = new JTextField(
                DashboardPanel.displayType(unit.getBloodType()));
        JTextField donation = new JTextField(unit.getDonationDate().toString());
        JTextField expiry = new JTextField(unit.getExpiryDate().toString());
        idField.setEditable(false);
        donorField.setEditable(false);
        typeField.setEditable(false);
        expiry.setEditable(false);
        UiComponents.styleInput(idField);
        UiComponents.styleInput(donorField);
        UiComponents.styleInput(typeField);
        UiComponents.styleInput(donation);
        UiComponents.styleInput(expiry);
        JPanel form = formPanel();
        addFormRow(form, 0, "Unit ID", idField);
        addFormRow(form, 1, "Donor", donorField);
        addFormRow(form, 2, "Blood type", typeField);
        addFormRow(form, 3, "Correct donation date", donation);
        addFormRow(form, 4, "Expiry date (calculated)", expiry);
        JLabel feedback = feedbackLabel();
        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton("Save correction");
        Runnable validate = () -> {
            try {
                LocalDate corrected = LocalDate.parse(donation.getText().trim());
                if (corrected.isAfter(controller.today())) {
                    throw new IllegalArgumentException(
                            "Donation date cannot be in the future.");
                }
                expiry.setText(corrected.plusDays(
                        DonationPolicy.UNIT_SHELF_LIFE_DAYS).toString());
                showValidation(feedback, save,
                        "The complete donor history will be checked before saving.", true);
            } catch (DateTimeParseException exception) {
                showValidation(feedback, save,
                        "Use yyyy-MM-dd for the donation date.", false);
            } catch (IllegalArgumentException exception) {
                showValidation(feedback, save, exception.getMessage(), false);
            }
        };
        installValidation(donation, validate);
        validate.run();
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                LocalDate corrected = LocalDate.parse(donation.getText().trim());
                InventoryState before = unit.getInventoryState(controller.today());
                InventoryState after = derivedState(corrected, controller.today());
                if (before != after && (before == InventoryState.EXPIRED
                        || after == InventoryState.EXPIRED)) {
                    int choice = JOptionPane.showConfirmDialog(dialog,
                            "This correction changes the unit from " + before
                                    + " to " + after + ". Continue?",
                            "Confirm status change", JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (choice != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                controller.updateUnusedBloodUnitDonationDate(unit.getId(), corrected);
                dialog.dispose();
                onDataChanged.run();
                status.accept(UiNotice.success("Blood unit dates corrected."));
            } catch (DateTimeParseException exception) {
                showValidation(feedback, save,
                        "Use yyyy-MM-dd for the donation date.", false);
            } catch (IllegalArgumentException | IOException exception) {
                showValidation(feedback, save, exception.getMessage(), false);
            }
        });
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent event) {
                donation.requestFocusInWindow();
            }
        });
        finishDialog(dialog, "Correct donation date",
                "Identity, donor, blood type, and used units remain locked.",
                form, feedback, save, cancel, 570);
    }

    private void finishDialog(JDialog dialog, String title, String subtitle,
                              JPanel form, JLabel feedback, JButton save,
                              JButton cancel, int height) {
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading(title));
        header.add(Box.createVerticalStrut(5));
        header.add(UiComponents.muted(subtitle));
        content.add(header, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);
        JPanel footer = new JPanel(new BorderLayout(14, 0));
        footer.setOpaque(false);
        footer.add(feedback, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        UiComponents.configureDialogKeys(dialog, save, cancel);
        dialog.setSize(680, height);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
    }

    private ArrayList<DonorOption> donorOptions(LifeFlowState snapshot,
                                                 LocalDate date) {
        ArrayList<DonorOption> options = new ArrayList<>();
        for (Donor donor : snapshot.getDonors()) {
            EligibilityResult result = controller.checkDonorEligibility(
                    donor.getId(), date);
            options.add(new DonorOption(donor.getId(), donor.getName(), result));
        }
        options.sort(Comparator.comparing((DonorOption option) -> !option.eligible())
                .thenComparing(option -> option.name, String.CASE_INSENSITIVE_ORDER));
        return options;
    }

    private static int firstEligibleIndex(ArrayList<DonorOption> options) {
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).eligible()) {
                return index;
            }
        }
        return 0;
    }

    private void updateAddFormState(JTextField id, JComboBox<DonorOption> donor,
                                    JTextField donation, JTextField expiry,
                                    JLabel feedback, JButton save) {
        DonorOption selected = (DonorOption) donor.getSelectedItem();
        if (selected == null) {
            showValidation(feedback, save, "Select a registered donor.", false);
            return;
        }
        LocalDate donationDate;
        try {
            donationDate = LocalDate.parse(donation.getText().trim());
        } catch (DateTimeParseException exception) {
            showValidation(feedback, save,
                    "Use yyyy-MM-dd for the donation date.", false);
            return;
        }
        expiry.setText(donationDate.plusDays(
                DonationPolicy.UNIT_SHELF_LIFE_DAYS).toString());
        EligibilityResult eligibility;
        try {
            eligibility = controller.checkDonorEligibility(selected.id, donationDate);
        } catch (IllegalArgumentException exception) {
            showValidation(feedback, save, exception.getMessage(), false);
            return;
        }
        if (!eligibility.eligible()) {
            showValidation(feedback, save, eligibility.message(), false);
            return;
        }
        if (id.getText().trim().isEmpty()) {
            showValidation(feedback, save, "Automatic Unit ID is unavailable.", false);
            return;
        }
        String detail = eligibility.lastDonationDate() == null
                ? "First recorded donation; donor is eligible on " + donationDate + "."
                : "Last donation: " + eligibility.lastDonationDate()
                        + ". Donor is eligible on " + donationDate + ".";
        showValidation(feedback, save, detail, true);
    }

    public void refreshData() {
        LifeFlowState snapshot = controller.getStateSnapshot();
        LocalDate today = controller.today();
        Map<String, Donor> donors = new HashMap<>();
        for (Donor donor : snapshot.getDonors()) {
            donors.put(donor.getId().toLowerCase(java.util.Locale.ROOT), donor);
        }
        model.setRowCount(0);
        snapshot.getUnits().stream()
                .sorted(Comparator.comparing(BloodUnit::getDonationDate).reversed()
                        .thenComparing(BloodUnit::getId,
                                String.CASE_INSENSITIVE_ORDER))
                .forEach(unit -> {
                    Donor donor = donors.get(unit.getDonorId()
                            .toLowerCase(java.util.Locale.ROOT));
                    String donorName = donor == null ? unit.getDonorId()
                            : donor.getName() + " (" + donor.getId() + ")";
                    InventoryState inventoryState = unit.getInventoryState(today);
                    model.addRow(new Object[]{unit.getId(), donorName,
                            DashboardPanel.displayType(unit.getBloodType()),
                            unit.getDonationDate(), unit.getExpiryDate(),
                            daysLeft(unit, inventoryState, today), inventoryState.name()});
                });
        updateFilter();
        centerLayout.show(center, model.getRowCount() == 0 ? "empty" : "table");
    }

    private String donorDisplay(BloodUnit unit) {
        Donor donor = controller.getStateSnapshot().getDonors().stream()
                .filter(item -> item.getId().equalsIgnoreCase(unit.getDonorId()))
                .findFirst().orElse(null);
        return donor == null ? unit.getDonorId()
                : donor.getName() + " (" + donor.getId() + ")";
    }

    private static Object daysLeft(BloodUnit unit, InventoryState state,
                                   LocalDate today) {
        if (state == InventoryState.USED) {
            return "—";
        }
        long days = ChronoUnit.DAYS.between(today, unit.getExpiryDate());
        return state == InventoryState.EXPIRED
                ? "Expired " + Math.abs(days) + " days ago" : days;
    }

    private static InventoryState derivedState(LocalDate donation,
                                               LocalDate today) {
        BloodUnit temporary = new BloodUnit("preview", "preview", BloodType.O_POS,
                donation, donation.plusDays(DonationPolicy.UNIT_SHELF_LIFE_DAYS),
                UnitStatus.AVAILABLE);
        return temporary.getInventoryState(today);
    }

    private static JPanel formPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        return form;
    }

    private static JLabel feedbackLabel() {
        JLabel feedback = new JLabel(" ");
        feedback.setFont(UiTheme.SMALL);
        feedback.setVerticalAlignment(JLabel.TOP);
        feedback.setPreferredSize(new java.awt.Dimension(330, 48));
        return feedback;
    }

    private static void showValidation(JLabel feedback, JButton save,
                                       String message, boolean valid) {
        feedback.setForeground(valid ? UiTheme.SUCCESS : UiTheme.DANGER);
        feedback.setText("<html><body style='width:310px'>" + escape(message)
                + "</body></html>");
        feedback.setToolTipText(message);
        save.setEnabled(valid);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void installValidation(JTextField field, Runnable validation) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { validation.run(); }
            @Override public void removeUpdate(DocumentEvent event) { validation.run(); }
            @Override public void changedUpdate(DocumentEvent event) { validation.run(); }
        });
    }

    private static void addFormRow(JPanel form, int row, String label,
                                   java.awt.Component input) {
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

    private static final class DonorOption {
        private final String id;
        private final String name;
        private final EligibilityResult eligibility;

        private DonorOption(String id, String name,
                            EligibilityResult eligibility) {
            this.id = id;
            this.name = name;
            this.eligibility = eligibility;
        }

        private boolean eligible() {
            return eligibility.eligible();
        }

        @Override
        public String toString() {
            if (eligibility.eligible()) {
                return name + " · " + id + " · ELIGIBLE";
            }
            if (eligibility.nextEligibleDate() != null) {
                return name + " · " + id + " · Next: "
                        + eligibility.nextEligibleDate();
            }
            return name + " · " + id + " · NOT ELIGIBLE";
        }
    }
}
