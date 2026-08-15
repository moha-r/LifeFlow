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
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.RequestStatus;
import lifeflow.service.LifeFlowController;

/** Searchable regular and emergency request workspace. */
@SuppressWarnings("serial")
public final class RequestsPanel extends JPanel {
    private final LifeFlowController controller;
    private final Runnable onDataChanged;
    private final Consumer<String> status;
    private final DefaultTableModel model = UiComponents.readOnlyModel(
            "Request ID", "Kind", "Requester", "Blood Type", "Quantity",
            "Date", "Priority", "Status");
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = UiComponents.searchField("Search requests");
    private final JComboBox<String> statusFilter = new JComboBox<>(
            new String[]{"All statuses", "PENDING", "FULFILLED"});
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel center = new JPanel(centerLayout);

    public RequestsPanel(LifeFlowController controller, Runnable onDataChanged,
                         Consumer<String> status) {
        super(new BorderLayout(0, 18));
        this.controller = controller;
        this.onDataChanged = onDataChanged;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(28, 30, 28, 30));
        search.setPreferredSize(new java.awt.Dimension(180, 38));
        UiComponents.styleInput(statusFilter);
        statusFilter.setPreferredSize(new java.awt.Dimension(135, 38));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(1).setCellRenderer(UiComponents.statusRenderer());
        table.getColumnModel().getColumn(7).setCellRenderer(UiComponents.statusRenderer());
        int[] widths = {90, 95, 175, 100, 75, 110, 70, 100};
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

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setOpaque(false);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(UiComponents.title("Blood Requests"));
        copy.add(Box.createVerticalStrut(5));
        copy.add(UiComponents.muted("Emergency requests are always prioritised before regular requests."));
        header.add(copy, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 4));
        actions.setOpaque(false);
        JButton edit = UiComponents.secondaryButton("Edit selected");
        JButton add = UiComponents.primaryButton("+ New request");
        add.setPreferredSize(new java.awt.Dimension(166, 40));
        edit.addActionListener(event -> showEditDialog());
        add.addActionListener(event -> showAddDialog());
        actions.add(search);
        actions.add(statusFilter);
        actions.add(edit);
        actions.add(add);
        header.add(actions, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildCenter() {
        center.setOpaque(false);
        JPanel tableCard = UiComponents.card(new BorderLayout());
        tableCard.add(UiComponents.tableScroll(table), BorderLayout.CENTER);
        center.add(tableCard, "table");
        JPanel empty = UiComponents.card(new GridBagLayout());
        JPanel message = new JPanel();
        message.setOpaque(false);
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        JLabel title = UiComponents.heading("No blood requests yet");
        title.setAlignmentX(CENTER_ALIGNMENT);
        JLabel copy = UiComponents.muted("Create a regular or emergency request to begin matching.");
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
    }

    private void updateFilter() {
        String query = search.getText().trim();
        String selected = statusFilter.getSelectedItem().toString();
        java.util.ArrayList<RowFilter<Object, Object>> filters = new java.util.ArrayList<>();
        if (!query.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(query)));
        }
        if (!selected.equals("All statuses")) {
            filters.add(RowFilter.regexFilter("^" + selected + "$", 7));
        }
        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    public void showAddDialog() {
        showRequestDialog(null);
    }

    private void showEditDialog() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            status.accept("Select a pending request to edit.");
            return;
        }
        String id = model.getValueAt(table.convertRowIndexToModel(viewRow), 0).toString();
        for (BloodRequest request : controller.getRequests()) {
            if (request.getId().equals(id)) {
                if (request.getStatus() == RequestStatus.FULFILLED) {
                    status.accept("Fulfilled requests cannot be edited.");
                    return;
                }
                showRequestDialog(request);
                return;
            }
        }
    }

    private void showRequestDialog(BloodRequest request) {
        boolean editing = request != null;
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, editing ? "Edit request" : "New blood request",
                Dialog.ModalityType.APPLICATION_MODAL);
        JTextField id = new JTextField(editing ? request.getId() : "");
        JTextField requester = new JTextField(editing ? request.getRequesterName() : "");
        JComboBox<BloodType> type = new JComboBox<>(BloodType.values());
        JTextField quantity = new JTextField(editing
                ? Integer.toString(request.getQuantity()) : "1");
        JComboBox<String> kind = new JComboBox<>(new String[]{"REGULAR", "EMERGENCY"});
        if (editing) {
            id.setEditable(false);
            type.setSelectedItem(request.getBloodType());
            kind.setSelectedItem(request.getKind());
            kind.setEnabled(false);
        }
        UiComponents.styleInput(id);
        UiComponents.styleInput(requester);
        UiComponents.styleInput(type);
        UiComponents.styleInput(quantity);
        UiComponents.styleInput(kind);
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addFormRow(form, 0, "Request ID", id);
        addFormRow(form, 1, "Requester", requester);
        addFormRow(form, 2, "Blood type", type);
        addFormRow(form, 3, "Quantity", quantity);
        addFormRow(form, 4, "Request kind", kind);
        JLabel error = new JLabel(" ");
        error.setForeground(UiTheme.DANGER);
        error.setFont(UiTheme.SMALL);
        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton save = UiComponents.primaryButton(editing ? "Save changes" : "Create request");
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                int amount = Integer.parseInt(quantity.getText().trim());
                if (editing) {
                    controller.updatePendingRequest(request.getId(), requester.getText(),
                            (BloodType) type.getSelectedItem(), amount);
                } else {
                    controller.addRequest(id.getText(), requester.getText(),
                            (BloodType) type.getSelectedItem(), amount,
                            "EMERGENCY".equals(kind.getSelectedItem()));
                }
                dialog.dispose();
                onDataChanged.run();
                status.accept(editing ? "Blood request updated."
                        : "Blood request created successfully.");
            } catch (NumberFormatException exception) {
                error.setText("Quantity must be a valid whole number.");
            } catch (IllegalArgumentException | IOException exception) {
                error.setText(exception.getMessage());
            }
        });
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading(editing ? "Edit pending request" : "Create blood request"));
        header.add(Box.createVerticalStrut(5));
        header.add(UiComponents.muted(editing
                ? "Request type, ID, date, and status remain locked."
                : "Emergency requests receive the highest matching priority."));
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
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(560, 470);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
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
        for (BloodRequest request : controller.getRequests()) {
            model.addRow(new Object[]{request.getId(), request.getKind(),
                    request.getRequesterName(), DashboardPanel.displayType(request.getBloodType()),
                    request.getQuantity(), request.getRequestDate(), request.getPriority(),
                    request.getStatus()});
        }
        centerLayout.show(center, model.getRowCount() == 0 ? "empty" : "table");
    }
}
