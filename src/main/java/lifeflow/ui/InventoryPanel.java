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
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EligibilityResult;
import lifeflow.model.UnitStatus;
import lifeflow.service.LifeFlowController;

/** Blood-unit workspace with stock-friendly search and safe expiry editing. */
@SuppressWarnings("serial")
public final class InventoryPanel extends JPanel {
    private final LifeFlowController controller;
    private final Runnable onDataChanged;
    private final Consumer<String> status;
    private final DefaultTableModel model = UiComponents.readOnlyModel(
            "Unit ID", "Donor", "Blood Type", "Donation Date", "Expiry Date", "Status");
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = UiComponents.searchField("Search blood units");
    private final JLabel recordCount = UiComponents.muted("0 RECORDS");
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel center = new JPanel(centerLayout);

    public InventoryPanel(LifeFlowController controller, Runnable onDataChanged,
                          Consumer<String> status) {
        super(new BorderLayout());
        this.controller = controller;
        this.onDataChanged = onDataChanged;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);
        PageShell shell = new PageShell("Inventory registry",
                "Track availability, expiry dates, and unit usage.");
        shell.setActions(buildPageActions());
        shell.setToolbar(buildToolbar());
        shell.setBody(buildCenter());
        add(shell, BorderLayout.CENTER);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(5).setCellRenderer(UiComponents.statusRenderer());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    showEditDialog();
                }
            }
        });
        installSearch();
        refreshData();
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
        toolbar.add(search, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        actions.setOpaque(false);
        JButton edit = UiComponents.secondaryButton("Edit expiry");
        edit.setPreferredSize(new java.awt.Dimension(118, 34));
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
        JLabel copy = UiComponents.muted("Register an eligible donor before adding a unit.");
        copy.setAlignmentX(CENTER_ALIGNMENT);
        message.add(title);
        message.add(Box.createVerticalStrut(7));
        message.add(copy);
        empty.add(message);
        center.add(empty, "empty");
        return center;
    }

    private void installSearch() {
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void removeUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void changedUpdate(DocumentEvent event) { updateFilter(); }
        });
    }

    private void updateFilter() {
        String text = UiComponents.searchValue(search);
        sorter.setRowFilter(text.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        recordCount.setText(sorter.getViewRowCount() + " RECORDS");
    }

    public void showAddDialog() {
        if (controller.getDonors().isEmpty()) {
            status.accept("Register a donor before adding a blood unit.");
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Add blood unit",
                Dialog.ModalityType.APPLICATION_MODAL);
        JTextField id = new JTextField(controller.getNextUnitId());
        id.setEditable(false);
        JComboBox<DonorOption> donor = new JComboBox<>();
        for (Donor item : controller.getDonors()) {
            donor.addItem(new DonorOption(item.getId(), item.getName()));
        }
        JTextField donation = new JTextField(LocalDate.now().toString());
        JTextField expiry = new JTextField(LocalDate.now().plusDays(35).toString());
        UiComponents.styleInput(id);
        UiComponents.styleInput(donor);
        UiComponents.styleInput(donation);
        UiComponents.styleInput(expiry);

        JLabel error = errorLabel();
        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton("Add unit");
        JPanel form = formPanel();
        addFormRow(form, 0, "Unit ID (auto)", id);
        addFormRow(form, 1, "Donor", donor);
        addFormRow(form, 2, "Donation date", donation);
        addFormRow(form, 3, "Expiry date", expiry);
        Runnable validate = () -> updateAddFormState(id, donor, donation, expiry,
                error, save);
        installValidation(donation, validate);
        installValidation(expiry, validate);
        donor.addActionListener(event -> validate.run());
        validate.run();
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                DonorOption selected = (DonorOption) donor.getSelectedItem();
                if (selected == null) {
                    throw new IllegalArgumentException("Select a donor.");
                }
                controller.addBloodUnit(id.getText(), selected.id,
                        LocalDate.parse(donation.getText().trim()),
                        LocalDate.parse(expiry.getText().trim()));
                dialog.dispose();
                onDataChanged.run();
                status.accept("Blood unit added successfully.");
            } catch (DateTimeParseException exception) {
                error.setText("Use yyyy-MM-dd for donation and expiry dates.");
            } catch (IllegalArgumentException | IOException exception) {
                error.setText(exception.getMessage());
            }
        });
        finishDialog(dialog, "Add a blood unit",
                "The donor must be eligible on the donation date.", form, error, save, cancel, 430);
    }

    private void showEditDialog() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            status.accept("Select an available blood unit to edit.");
            return;
        }
        String id = model.getValueAt(table.convertRowIndexToModel(viewRow), 0).toString();
        BloodUnit selected = null;
        for (BloodUnit unit : controller.getUnits()) {
            if (unit.getId().equals(id)) {
                selected = unit;
                break;
            }
        }
        if (selected == null) {
            return;
        }
        if (selected.getStatus() == UnitStatus.USED) {
            status.accept("Used blood units cannot be edited.");
            return;
        }
        if (selected.isExpired(LocalDate.now())) {
            status.accept("Expired blood units cannot be edited.");
            return;
        }
        BloodUnit unit = selected;
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Edit blood unit",
                Dialog.ModalityType.APPLICATION_MODAL);
        JTextField idField = new JTextField(unit.getId());
        idField.setEditable(false);
        JTextField donation = new JTextField(unit.getDonationDate().toString());
        donation.setEditable(false);
        JTextField expiry = new JTextField(unit.getExpiryDate().toString());
        UiComponents.styleInput(idField);
        UiComponents.styleInput(donation);
        UiComponents.styleInput(expiry);
        JPanel form = formPanel();
        addFormRow(form, 0, "Unit ID", idField);
        addFormRow(form, 1, "Donation date", donation);
        addFormRow(form, 2, "New expiry date", expiry);
        JLabel error = errorLabel();
        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton("Save changes");
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                controller.updateBloodUnitExpiry(unit.getId(),
                        LocalDate.parse(expiry.getText().trim()));
                dialog.dispose();
                onDataChanged.run();
                status.accept("Blood unit expiry updated.");
            } catch (DateTimeParseException exception) {
                error.setText("Use yyyy-MM-dd for the expiry date.");
            } catch (IllegalArgumentException | IOException exception) {
                error.setText(exception.getMessage());
            }
        });
        finishDialog(dialog, "Edit blood unit expiry",
                "Used units and original donation data remain locked.",
                form, error, save, cancel, 390);
    }

    private void finishDialog(JDialog dialog, String title, String subtitle,
                              JPanel form, JLabel error, JButton save,
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
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(error, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        UiComponents.configureDialogKeys(dialog, save, cancel);
        dialog.setSize(560, height);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
    }

    private static JPanel formPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        return form;
    }

    private static JLabel errorLabel() {
        JLabel error = new JLabel(" ");
        error.setForeground(UiTheme.DANGER);
        error.setFont(UiTheme.SMALL);
        return error;
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

    public void refreshData() {
        model.setRowCount(0);
        for (BloodUnit unit : controller.getUnits()) {
            String donorName = unit.getDonorId();
            for (Donor donor : controller.getDonors()) {
                if (donor.getId().equalsIgnoreCase(unit.getDonorId())) {
                    donorName = donor.getName() + " (" + donor.getId() + ")";
                    break;
                }
            }
            model.addRow(new Object[]{unit.getId(), donorName,
                    DashboardPanel.displayType(unit.getBloodType()),
                    unit.getDonationDate(), unit.getExpiryDate(),
                    unit.isExpired(LocalDate.now()) ? "EXPIRED" : unit.getStatus()});
        }
        recordCount.setText(sorter.getViewRowCount() + " RECORDS");
        centerLayout.show(center, model.getRowCount() == 0 ? "empty" : "table");
    }

    private void updateAddFormState(JTextField id, JComboBox<DonorOption> donor,
                                    JTextField donation, JTextField expiry,
                                    JLabel feedback, JButton save) {
        DonorOption selected = (DonorOption) donor.getSelectedItem();
        if (selected == null) {
            showValidation(feedback, save, "Select a registered donor.");
            return;
        }
        LocalDate donationDate;
        LocalDate expiryDate;
        try {
            donationDate = LocalDate.parse(donation.getText().trim());
            expiryDate = LocalDate.parse(expiry.getText().trim());
        } catch (DateTimeParseException exception) {
            showValidation(feedback, save, "Use yyyy-MM-dd for both dates.");
            return;
        }
        if (expiryDate.isBefore(donationDate)) {
            showValidation(feedback, save,
                    "Expiry date cannot be before donation date.");
            return;
        }
        EligibilityResult eligibility;
        try {
            eligibility = controller.checkDonorEligibility(selected.id, donationDate);
        } catch (IllegalArgumentException exception) {
            showValidation(feedback, save, exception.getMessage());
            return;
        }
        if (!eligibility.eligible()) {
            showValidation(feedback, save, eligibility.message());
            return;
        }
        if (id.getText().trim().isEmpty()) {
            showValidation(feedback, save, "Enter a unique Unit ID.");
            return;
        }
        feedback.setForeground(UiTheme.SUCCESS);
        feedback.setText("Donor is eligible on " + donationDate + ".");
        save.setEnabled(true);
    }

    private static void showValidation(JLabel feedback, JButton save,
                                       String message) {
        feedback.setForeground(UiTheme.DANGER);
        feedback.setText(message);
        save.setEnabled(false);
    }

    private static void installValidation(JTextField field, Runnable validation) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { validation.run(); }
            @Override public void removeUpdate(DocumentEvent event) { validation.run(); }
            @Override public void changedUpdate(DocumentEvent event) { validation.run(); }
        });
    }

    private static final class DonorOption {
        private final String id;
        private final String name;

        private DonorOption(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + "  ·  " + id;
        }
    }
}
