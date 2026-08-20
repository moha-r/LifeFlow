package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.util.Comparator;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import lifeflow.model.Hospital;
import lifeflow.service.HospitalRegistry;
import lifeflow.service.LifeFlowController;

/** Donation-center workspace for managing the hospital registry. */
@SuppressWarnings("serial")
public final class HospitalsPanel extends JPanel {
    private final HospitalRegistry registry;
    private final LifeFlowController controller;
    private final Runnable onDataChanged;
    private final Consumer<UiNotice> status;
    private final DefaultTableModel model = UiComponents.readOnlyModel(
            "ID", "Name", "Username", "Registered");
    private final JTable table = new JTable(model);

    public HospitalsPanel(HospitalRegistry registry, LifeFlowController controller,
                          Runnable onDataChanged, Consumer<UiNotice> status) {
        super(new BorderLayout());
        this.registry = registry;
        this.controller = controller;
        this.onDataChanged = onDataChanged;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);
        PageShell shell = new PageShell("Donation centers",
                "Manage the centers where donors book and give blood.");
        shell.setActions(buildPageActions());
        shell.setBody(buildCenter());
        add(shell, BorderLayout.CENTER);
        int[] widths = {80, 260, 150, 110};
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
        refreshData();
    }

    private JPanel buildPageActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        JButton add = UiComponents.primaryButton("+ Add center");
        add.setPreferredSize(new java.awt.Dimension(140, 34));
        add.addActionListener(event -> showAddDialog());
        actions.add(add);
        return actions;
    }

    private JPanel buildCenter() {
        JPanel card = UiComponents.densePanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        JScrollPane scroll = UiComponents.tableScroll(table);
        card.add(scroll, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JButton edit = UiComponents.secondaryButton("Edit selected");
        edit.setPreferredSize(new java.awt.Dimension(132, 34));
        edit.addActionListener(event -> showEditDialog());
        JButton remove = UiComponents.secondaryButton("Remove");
        remove.setPreferredSize(new java.awt.Dimension(110, 34));
        remove.addActionListener(event -> removeSelected());
        toolbar.add(edit);
        toolbar.add(remove);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(card, BorderLayout.CENTER);
        body.add(toolbar, BorderLayout.SOUTH);
        return body;
    }

    public void refreshData() {
        model.setRowCount(0);
        registry.findAll().stream()
                .sorted(Comparator.comparing(Hospital::getName,
                        String.CASE_INSENSITIVE_ORDER))
                .forEach(hospital -> model.addRow(new Object[]{
                        hospital.getId(), hospital.getName(), hospital.getUsername(),
                        hospital.getRegistrationDate().toString()}));
    }

    private void showAddDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Add donation center",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextField name = new JTextField();
        JTextField username = new JTextField();
        JTextField password = new JTextField();
        UiComponents.styleInput(name);
        UiComponents.styleInput(username);
        UiComponents.styleInput(password);

        JLabel feedback = new JLabel(" ");
        feedback.setFont(UiTheme.SMALL);
        feedback.setForeground(UiTheme.DANGER);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
        addFormRow(form, 0, "Center name", name);
        addFormRow(form, 1, "Username", username);
        addFormRow(form, 2, "Password", password);

        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton("Add center");
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                registry.register(name.getText(), username.getText(),
                        password.getText());
                dialog.dispose();
                onDataChanged.run();
                status.accept(UiNotice.success("Donation center added."));
            } catch (lifeflow.model.exception.LifeFlowException exception) {
                setFeedback(feedback, exception.getMessage());
            } catch (IOException exception) {
                setFeedback(feedback, "Could not save: " + exception.getMessage());
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading("Register a donation center"));
        header.add(Box.createVerticalStrut(5));
        header.add(UiComponents.muted(
                "The center can then sign in through the hospital portal."));
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
        dialog.setSize(520, 420);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(name::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private void showEditDialog() {
        Hospital hospital = selected();
        if (hospital == null) {
            status.accept(UiNotice.info("Select a center to edit."));
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Edit donation center",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextField name = new JTextField(hospital.getName());
        UiComponents.styleInput(name);
        JTextField username = new JTextField(hospital.getUsername());
        username.setEditable(false);
        UiComponents.styleInput(username);

        JLabel feedback = new JLabel(" ");
        feedback.setFont(UiTheme.SMALL);
        feedback.setForeground(UiTheme.DANGER);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
        addFormRow(form, 0, "Center name", name);
        addFormRow(form, 1, "Username (fixed)", username);

        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton("Save changes");
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                Hospital updated = new Hospital(hospital.getId(),
                        name.getText(), hospital.getUsername(),
                        hospital.getPassword(), hospital.getRegistrationDate());
                registry.update(updated);
                dialog.dispose();
                onDataChanged.run();
                status.accept(UiNotice.success("Donation center updated."));
            } catch (lifeflow.model.exception.LifeFlowException exception) {
                setFeedback(feedback, exception.getMessage());
            } catch (IOException exception) {
                setFeedback(feedback, "Could not save: " + exception.getMessage());
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading("Edit donation center"));
        header.add(Box.createVerticalStrut(5));
        header.add(UiComponents.muted(
                "The username stays fixed because centers sign in with it."));
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
        dialog.setSize(520, 380);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(name::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private void removeSelected() {
        Hospital hospital = selected();
        if (hospital == null) {
            status.accept(UiNotice.info("Select a center to remove."));
            return;
        }
        boolean hasAppointments = controller.getStateSnapshot().getAppointments()
                .stream().anyMatch(appointment ->
                        appointment.getHospitalId().equalsIgnoreCase(hospital.getId()));
        if (hasAppointments) {
            status.accept(UiNotice.error(
                    "This center cannot be removed because appointments reference it."));
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Remove " + hospital.getName() + "? Centers sign in with this account.",
                "Remove donation center", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            registry.remove(hospital.getId());
            onDataChanged.run();
            status.accept(UiNotice.success("Donation center removed."));
        } catch (IOException exception) {
            status.accept(UiNotice.error("Could not save: " + exception.getMessage()));
        }
    }

    private Hospital selected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        String id = model.getValueAt(table.convertRowIndexToModel(viewRow), 0)
                .toString();
        return registry.findById(id);
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