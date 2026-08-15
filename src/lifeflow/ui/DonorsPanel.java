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
import lifeflow.service.LifeFlowController;

/** Searchable donor workspace with focused add and safe-edit dialogs. */
@SuppressWarnings("serial")
public final class DonorsPanel extends JPanel {
    private static final String TABLE_VIEW = "table";
    private static final String EMPTY_VIEW = "empty";

    private final LifeFlowController controller;
    private final Runnable onDataChanged;
    private final Consumer<String> status;
    private final DefaultTableModel model = UiComponents.readOnlyModel(
            "ID", "Name", "Age", "Weight (kg)", "Blood Type", "Last Donation");
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = UiComponents.searchField("Search donors");
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel center = new JPanel(centerLayout);

    public DonorsPanel(LifeFlowController controller, Runnable onDataChanged,
                       Consumer<String> status) {
        super(new BorderLayout(0, 18));
        this.controller = controller;
        this.onDataChanged = onDataChanged;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(28, 30, 28, 30));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        table.setRowSorter(sorter);
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

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setOpaque(false);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(UiComponents.title("Donors"));
        copy.add(Box.createVerticalStrut(5));
        copy.add(UiComponents.muted("Register donors and review donation eligibility details."));
        header.add(copy, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 4));
        actions.setOpaque(false);
        JButton edit = UiComponents.secondaryButton("Edit selected");
        JButton add = UiComponents.primaryButton("+ Add donor");
        edit.addActionListener(event -> showEditDialog());
        add.addActionListener(event -> showAddDialog());
        actions.add(search);
        actions.add(edit);
        actions.add(add);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenter() {
        center.setOpaque(false);
        JPanel card = UiComponents.card(new BorderLayout());
        JScrollPane scroll = UiComponents.tableScroll(table);
        card.add(scroll, BorderLayout.CENTER);
        center.add(card, TABLE_VIEW);

        JPanel empty = UiComponents.card(new GridBagLayout());
        JPanel message = new JPanel();
        message.setOpaque(false);
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        JLabel title = UiComponents.heading("No donors registered yet");
        title.setAlignmentX(CENTER_ALIGNMENT);
        JLabel subtitle = UiComponents.muted("Use Add donor to create the first record.");
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        message.add(title);
        message.add(Box.createVerticalStrut(7));
        message.add(subtitle);
        empty.add(message);
        center.add(empty, EMPTY_VIEW);
        return center;
    }

    private void installSearch() {
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateFilter();
            }
        });
    }

    private void updateFilter() {
        String text = search.getText().trim();
        sorter.setRowFilter(text.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    public void showAddDialog() {
        showDonorDialog(null);
    }

    private void showEditDialog() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            status.accept("Select a donor to edit.");
            return;
        }
        String id = model.getValueAt(table.convertRowIndexToModel(viewRow), 0).toString();
        for (Donor donor : controller.getDonors()) {
            if (donor.getId().equals(id)) {
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

        JTextField id = new JTextField(editing ? donor.getId() : "");
        JTextField name = new JTextField(editing ? donor.getName() : "");
        JTextField age = new JTextField(editing ? Integer.toString(donor.getAge()) : "");
        JTextField weight = new JTextField(editing
                ? Double.toString(donor.getWeightKg()) : "");
        JComboBox<BloodType> type = new JComboBox<>(BloodType.values());
        JTextField lastDonation = new JTextField(editing
                && donor.getLastDonationDate() != null
                ? donor.getLastDonationDate().toString() : "");
        if (editing) {
            type.setSelectedItem(donor.getBloodType());
            id.setEditable(false);
            boolean linked = hasUnits(donor.getId());
            type.setEnabled(!linked);
            lastDonation.setEditable(!linked);
        }
        UiComponents.styleInput(id);
        UiComponents.styleInput(name);
        UiComponents.styleInput(age);
        UiComponents.styleInput(weight);
        UiComponents.styleInput(type);
        UiComponents.styleInput(lastDonation);

        JLabel error = new JLabel(" ");
        error.setForeground(UiTheme.DANGER);
        error.setFont(UiTheme.SMALL);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
        addFormRow(form, 0, "Donor ID", id);
        addFormRow(form, 1, "Full name", name);
        addFormRow(form, 2, "Age", age);
        addFormRow(form, 3, "Weight (kg)", weight);
        addFormRow(form, 4, "Blood type", type);
        addFormRow(form, 5, "Last donation (yyyy-MM-dd)", lastDonation);

        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton(editing ? "Save changes" : "Add donor");
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                int donorAge = Integer.parseInt(age.getText().trim());
                double donorWeight = Double.parseDouble(weight.getText().trim());
                LocalDate donation = optionalDate(lastDonation.getText());
                if (editing) {
                    controller.updateDonor(donor.getId(), name.getText(), donorAge,
                            donorWeight, (BloodType) type.getSelectedItem(), donation);
                } else {
                    controller.addDonor(id.getText(), name.getText(), donorAge,
                            donorWeight, (BloodType) type.getSelectedItem(), donation);
                }
                dialog.dispose();
                onDataChanged.run();
                status.accept(editing ? "Donor updated successfully."
                        : "Donor registered successfully.");
            } catch (NumberFormatException exception) {
                error.setText("Age and weight must be valid numbers.");
            } catch (DateTimeParseException exception) {
                error.setText("Use yyyy-MM-dd for the last donation date.");
            } catch (IllegalArgumentException | IOException exception) {
                error.setText(exception.getMessage());
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(UiTheme.SURFACE);
        buttons.add(cancel);
        buttons.add(save);
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        JPanel dialogHeader = new JPanel();
        dialogHeader.setBackground(UiTheme.SURFACE);
        dialogHeader.setLayout(new BoxLayout(dialogHeader, BoxLayout.Y_AXIS));
        dialogHeader.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24));
        dialogHeader.add(UiComponents.heading(editing ? "Edit donor details" : "Register a new donor"));
        dialogHeader.add(Box.createVerticalStrut(5));
        dialogHeader.add(UiComponents.muted("Required fields are validated before saving."));
        content.add(dialogHeader, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(UiTheme.SURFACE);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24));
        footer.add(error, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        UiComponents.configureDialogKeys(dialog, save, cancel);
        dialog.setSize(560, 500);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private boolean hasUnits(String donorId) {
        for (BloodUnit unit : controller.getUnits()) {
            if (unit.getDonorId().equalsIgnoreCase(donorId)) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate optionalDate(String text) {
        String value = text.trim();
        return value.isEmpty() ? null : LocalDate.parse(value);
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
        for (Donor donor : controller.getDonors()) {
            model.addRow(new Object[]{donor.getId(), donor.getName(), donor.getAge(),
                    donor.getWeightKg(), DashboardPanel.displayType(donor.getBloodType()),
                    donor.getLastDonationDate() == null ? "—" : donor.getLastDonationDate()});
        }
        centerLayout.show(center, model.getRowCount() == 0 ? EMPTY_VIEW : TABLE_VIEW);
    }
}
