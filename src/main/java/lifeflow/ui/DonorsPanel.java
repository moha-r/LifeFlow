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
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
import lifeflow.model.EligibilityReason;
import lifeflow.model.EligibilityResult;
import lifeflow.model.LifeFlowState;
import lifeflow.service.DonationPolicy;
import lifeflow.service.LifeFlowController;

/** Donor workspace that keeps profiles separate from donation events. */
@SuppressWarnings("serial")
public final class DonorsPanel extends JPanel {
    private static final String TABLE_VIEW = "table";
    private static final String EMPTY_VIEW = "empty";

    private final LifeFlowController controller;
    private final Runnable onDataChanged;
    private final Consumer<UiNotice> status;
    private final DefaultTableModel model = UiComponents.readOnlyModel(
            "ID", "Name", "Age", "Weight (kg)", "Blood Type", "Last Donation",
            "Eligibility", "Units");
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = UiComponents.searchField("Search donors");
    private final JComboBox<String> eligibilityFilter = new JComboBox<>(new String[]{
            "All eligibility", "ELIGIBLE", "DEFERRED", "NOT ELIGIBLE"});
    private final JComboBox<String> bloodTypeFilter = new JComboBox<>();
    private final JLabel recordCount = UiComponents.muted("0 RECORDS");
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel center = new JPanel(centerLayout);

    public DonorsPanel(LifeFlowController controller, Runnable onDataChanged,
                       Consumer<UiNotice> status) {
        super(new BorderLayout());
        this.controller = controller;
        this.onDataChanged = onDataChanged;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);
        configureFilters();
        PageShell shell = new PageShell("Donor registry",
                "Register donor profiles and review current donation eligibility.");
        shell.setActions(buildPageActions());
        shell.setToolbar(buildToolbar());
        shell.setBody(buildCenter());
        add(shell, BorderLayout.CENTER);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(6)
                .setCellRenderer(UiComponents.statusRenderer());
        int[] widths = {95, 190, 55, 85, 85, 115, 110, 55};
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
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
        eligibilityFilter.setName("eligibilityFilter");
        bloodTypeFilter.setName("donorBloodTypeFilter");
        bloodTypeFilter.addItem("All blood types");
        for (BloodType type : BloodType.values()) {
            bloodTypeFilter.addItem(DashboardPanel.displayType(type));
        }
        UiComponents.styleInput(eligibilityFilter);
        UiComponents.styleInput(bloodTypeFilter);
        eligibilityFilter.setPreferredSize(new java.awt.Dimension(145, 34));
        bloodTypeFilter.setPreferredSize(new java.awt.Dimension(135, 34));
        search.setPreferredSize(new java.awt.Dimension(205, 34));
    }

    private JPanel buildPageActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        JButton add = UiComponents.primaryButton("+ Add donor");
        add.setPreferredSize(new java.awt.Dimension(140, 34));
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
        filters.add(eligibilityFilter);
        filters.add(bloodTypeFilter);
        toolbar.add(filters, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        actions.setOpaque(false);
        JButton edit = UiComponents.secondaryButton("Edit selected");
        edit.setPreferredSize(new java.awt.Dimension(132, 34));
        edit.addActionListener(event -> showEditDialog());
        actions.add(recordCount);
        actions.add(edit);
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

        JPanel empty = UiComponents.densePanel(new GridBagLayout());
        empty.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        JPanel message = new JPanel();
        message.setOpaque(false);
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        JLabel title = UiComponents.heading("No donors registered yet");
        title.setAlignmentX(CENTER_ALIGNMENT);
        JLabel subtitle = UiComponents.muted("Use Add donor to create the first profile.");
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        message.add(title);
        message.add(Box.createVerticalStrut(7));
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
        eligibilityFilter.addActionListener(event -> updateFilter());
        bloodTypeFilter.addActionListener(event -> updateFilter());
    }

    private void updateFilter() {
        ArrayList<RowFilter<Object, Object>> filters = new ArrayList<>();
        String text = UiComponents.searchValue(search);
        if (!text.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
        String eligibility = String.valueOf(eligibilityFilter.getSelectedItem());
        if (!eligibility.startsWith("All")) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(eligibility) + "$", 6));
        }
        String type = String.valueOf(bloodTypeFilter.getSelectedItem());
        if (!type.startsWith("All")) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(type) + "$", 4));
        }
        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        recordCount.setText(sorter.getViewRowCount() + " RECORDS");
    }

    public void showAddDialog() {
        showDonorDialog(null);
    }

    private void showEditDialog() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            status.accept(UiNotice.info("Select a donor to edit."));
            return;
        }
        String id = model.getValueAt(table.convertRowIndexToModel(viewRow), 0).toString();
        for (Donor donor : controller.getStateSnapshot().getDonors()) {
            if (donor.getId().equalsIgnoreCase(id)) {
                showDonorDialog(donor);
                return;
            }
        }
    }

    private void showDonorDialog(Donor donor) {
        boolean editing = donor != null;
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, editing ? "Edit donor" : "Register donor",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextField id = new JTextField(editing ? donor.getId()
                : controller.getNextDonorId());
        id.setEditable(false);
        JTextField name = new JTextField(editing ? donor.getName() : "");
        JTextField age = new JTextField(editing ? Integer.toString(donor.getAge()) : "");
        JTextField weight = new JTextField(editing
                ? Double.toString(donor.getWeightKg()) : "");
        JComboBox<BloodType> type = new JComboBox<>(BloodType.values());
        JCheckBox hasExternal = new JCheckBox("Has donated outside LifeFlow?");
        hasExternal.setOpaque(false);
        hasExternal.setFont(UiTheme.BODY);
        LocalDate external = editing ? donor.getExternalLastDonationDate() : null;
        hasExternal.setSelected(external != null);
        JTextField externalDate = new JTextField(external == null ? "" : external.toString());
        JPanel externalRow = new JPanel(new BorderLayout());
        externalRow.setOpaque(false);
        externalRow.add(externalDate, BorderLayout.CENTER);
        externalRow.setVisible(hasExternal.isSelected());
        if (editing) {
            type.setSelectedItem(donor.getBloodType());
            type.setEnabled(!hasUnits(donor.getId()));
            if (!type.isEnabled()) {
                type.setToolTipText("Blood type is locked because units exist.");
            }
        }
        UiComponents.styleInput(id);
        UiComponents.styleInput(name);
        UiComponents.styleInput(age);
        UiComponents.styleInput(weight);
        UiComponents.styleInput(type);
        UiComponents.styleInput(externalDate);

        JLabel helper = new JLabel("<html>Leave this empty for a first-time donor.<br>"
                + "Do not enter the donation you are about to record in Inventory.</html>");
        helper.setFont(UiTheme.SMALL);
        helper.setForeground(UiTheme.MUTED);
        JLabel feedback = new JLabel(" ");
        feedback.setFont(UiTheme.SMALL);
        feedback.setForeground(UiTheme.DANGER);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
        addFormRow(form, 0, editing ? "Donor ID" : "Donor ID (auto)", id);
        addFormRow(form, 1, "Full name", name);
        addFormRow(form, 2, "Age", age);
        addFormRow(form, 3, "Weight (kg)", weight);
        addFormRow(form, 4, "Blood type", type);
        addFormRow(form, 5, "External history", hasExternal);
        addFormRow(form, 6, "Latest external date", externalRow);
        addFormRow(form, 7, "", helper);

        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton(editing ? "Save changes" : "Add donor");
        Runnable validate = () -> validateForm(name, age, weight, hasExternal,
                externalDate, feedback, save);
        installValidation(name, validate);
        installValidation(age, validate);
        installValidation(weight, validate);
        installValidation(externalDate, validate);
        hasExternal.addActionListener(event -> {
            externalRow.setVisible(hasExternal.isSelected());
            dialog.pack();
            dialog.setSize(650, hasExternal.isSelected() ? 610 : 560);
            validate.run();
        });
        validate.run();
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                int donorAge = Integer.parseInt(age.getText().trim());
                double donorWeight = Double.parseDouble(weight.getText().trim());
                LocalDate externalDonation = hasExternal.isSelected()
                        ? LocalDate.parse(externalDate.getText().trim()) : null;
                if (editing) {
                    controller.updateDonor(donor.getId(), name.getText(), donorAge,
                            donorWeight, (BloodType) type.getSelectedItem(),
                            externalDonation);
                } else {
                    controller.addDonor(id.getText(), name.getText(), donorAge,
                            donorWeight, (BloodType) type.getSelectedItem(),
                            externalDonation);
                }
                EligibilityResult result = controller.checkDonorEligibility(
                        editing ? donor.getId() : id.getText(), controller.today());
                dialog.dispose();
                onDataChanged.run();
                status.accept(donorNotice(editing, result));
            } catch (DateTimeParseException exception) {
                setFeedback(feedback, "Use yyyy-MM-dd for the external date.",
                        UiTheme.DANGER);
            } catch (NumberFormatException exception) {
                setFeedback(feedback, "Age and weight must be valid numbers.",
                        UiTheme.DANGER);
            } catch (IllegalArgumentException | IOException exception) {
                setFeedback(feedback, exception.getMessage(), UiTheme.DANGER);
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading(editing ? "Edit donor profile" : "Register a donor"));
        header.add(Box.createVerticalStrut(5));
        header.add(UiComponents.muted(
                "The profile can be saved even when the donor is not currently eligible."));
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
        dialog.setSize(650, hasExternal.isSelected() ? 610 : 560);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(name::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private UiNotice donorNotice(boolean editing, EligibilityResult result) {
        if (editing) {
            return UiNotice.success("Donor profile updated.");
        }
        if (result.eligible()) {
            return UiNotice.success("Donor registered and currently eligible.");
        }
        if (result.reason() == EligibilityReason.WAITING_PERIOD) {
            return UiNotice.warning("Donor registered but currently deferred until "
                    + result.nextEligibleDate() + ".");
        }
        return UiNotice.warning(
                "Donor registered but does not meet the simulation requirements.");
    }

    private void validateForm(JTextField name, JTextField age, JTextField weight,
                              JCheckBox hasExternal, JTextField external,
                              JLabel feedback, JButton save) {
        try {
            if (name.getText().trim().isEmpty()) {
                throw new IllegalArgumentException("Full name is required.");
            }
            int parsedAge = Integer.parseInt(age.getText().trim());
            double parsedWeight = Double.parseDouble(weight.getText().trim());
            if (parsedAge < 1 || parsedAge > 120) {
                throw new IllegalArgumentException("Age must be between 1 and 120.");
            }
            if (!Double.isFinite(parsedWeight) || parsedWeight <= 0
                    || parsedWeight > 500) {
                throw new IllegalArgumentException(
                        "Weight must be greater than 0 and no more than 500 kg.");
            }
            if (hasExternal.isSelected()) {
                LocalDate value = LocalDate.parse(external.getText().trim());
                if (value.isAfter(controller.today())) {
                    throw new IllegalArgumentException(
                            "External donation date cannot be in the future.");
                }
            }
            setFeedback(feedback,
                    "Profile data is ready. Eligibility is checked separately.",
                    UiTheme.SUCCESS);
            save.setEnabled(true);
        } catch (DateTimeParseException exception) {
            setFeedback(feedback, "Use yyyy-MM-dd for the external date.",
                    UiTheme.DANGER);
            save.setEnabled(false);
        } catch (NumberFormatException exception) {
            setFeedback(feedback, "Age and weight must be valid numbers.",
                    UiTheme.DANGER);
            save.setEnabled(false);
        } catch (IllegalArgumentException exception) {
            setFeedback(feedback, exception.getMessage(), UiTheme.DANGER);
            save.setEnabled(false);
        }
    }

    private boolean hasUnits(String donorId) {
        return controller.getStateSnapshot().getUnits().stream().anyMatch(unit ->
                unit.getDonorId().equalsIgnoreCase(donorId));
    }

    public void refreshData() {
        LifeFlowState snapshot = controller.getStateSnapshot();
        LocalDate today = controller.today();
        Map<String, Integer> unitCounts = new HashMap<>();
        Map<String, LocalDate> latestDates = new HashMap<>();
        for (BloodUnit unit : snapshot.getUnits()) {
            String key = unit.getDonorId().toLowerCase(java.util.Locale.ROOT);
            unitCounts.merge(key, 1, Integer::sum);
            latestDates.merge(key, unit.getDonationDate(),
                    (first, second) -> first.isAfter(second) ? first : second);
        }
        DonationPolicy policy = policyFor(today);
        model.setRowCount(0);
        snapshot.getDonors().stream()
                .sorted(Comparator.comparing(Donor::getName,
                        String.CASE_INSENSITIVE_ORDER))
                .forEach(donor -> {
                    String key = donor.getId().toLowerCase(java.util.Locale.ROOT);
                    LocalDate effective = donor.getExternalLastDonationDate();
                    LocalDate internal = latestDates.get(key);
                    if (internal != null && (effective == null
                            || internal.isAfter(effective))) {
                        effective = internal;
                    }
                    EligibilityResult result = policy.evaluate(donor, today, effective);
                    model.addRow(new Object[]{donor.getId(), donor.getName(), donor.getAge(),
                            donor.getWeightKg(), DashboardPanel.displayType(donor.getBloodType()),
                            effective == null ? "—" : effective,
                            displayEligibility(result), unitCounts.getOrDefault(key, 0)});
                });
        updateFilter();
        centerLayout.show(center, model.getRowCount() == 0 ? EMPTY_VIEW : TABLE_VIEW);
    }

    private static DonationPolicy policyFor(LocalDate date) {
        ZoneId zone = ZoneId.systemDefault();
        Clock fixed = Clock.fixed(date.atStartOfDay(zone).toInstant(), zone);
        return new DonationPolicy(fixed);
    }

    private static String displayEligibility(EligibilityResult result) {
        if (result.eligible()) {
            return "ELIGIBLE";
        }
        return result.reason() == EligibilityReason.WAITING_PERIOD
                ? "DEFERRED" : "NOT ELIGIBLE";
    }

    private static void installValidation(JTextField field, Runnable validation) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { validation.run(); }
            @Override public void removeUpdate(DocumentEvent event) { validation.run(); }
            @Override public void changedUpdate(DocumentEvent event) { validation.run(); }
        });
    }

    private static void setFeedback(JLabel label, String message,
                                    java.awt.Color color) {
        label.setText("<html>" + escape(message) + "</html>");
        label.setToolTipText(message);
        label.setForeground(color);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
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
}
