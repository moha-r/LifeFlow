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
import lifeflow.model.exception.LifeFlowException;
import lifeflow.service.LifeFlowController;

/** Searchable regular and emergency request workspace. */
@SuppressWarnings("serial")
public final class RequestsPanel extends JPanel implements lifeflow.service.StateObserver {
    private final LifeFlowController controller;
    private final Runnable onDataChanged;
    private final Consumer<UiNotice> status;
    private final DefaultTableModel model = UiComponents.readOnlyModel(
            "Request ID", "Kind", "Requester", "Blood Type", "Quantity",
            "Date", "Priority", "Status", "Volunteers");
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = UiComponents.searchField("Search requests");
    private final JComboBox<String> statusFilter = new JComboBox<>(
            new String[]{"All statuses", "PENDING", "FULFILLED", "CANCELLED"});
    private final JLabel recordCount = UiComponents.muted("0 RECORDS");
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel center = new JPanel(centerLayout);

    public RequestsPanel(LifeFlowController controller, Runnable onDataChanged,
                         Consumer<UiNotice> status) {
        super(new BorderLayout());
        this.controller = controller;
        this.onDataChanged = onDataChanged;
        this.status = status;
        setBackground(UiTheme.BACKGROUND);
        search.setPreferredSize(new java.awt.Dimension(220, 34));
        UiComponents.styleInput(statusFilter);
        statusFilter.setPreferredSize(new java.awt.Dimension(135, 34));
        PageShell shell = new PageShell("Request queue",
                "Emergency requests are prioritised before regular requests.");
        shell.setActions(buildPageActions());
        shell.setToolbar(buildToolbar());
        shell.setBody(buildCenter());
        add(shell, BorderLayout.CENTER);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(1).setCellRenderer(UiComponents.statusRenderer());
        table.getColumnModel().getColumn(7).setCellRenderer(UiComponents.statusRenderer());
        int[] widths = {90, 95, 175, 100, 75, 110, 70, 100, 80};
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    showEditDialog();
                }
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showContextMenu(event);
                }
            }
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showContextMenu(event);
                }
            }
            private void showContextMenu(java.awt.event.MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                if (row >= 0 && !table.isRowSelected(row)) {
                    table.setRowSelectionInterval(row, row);
                }
                javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
                javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit Request...");
                editItem.addActionListener(e -> showEditDialog());
                javax.swing.JMenuItem copyIdItem = new javax.swing.JMenuItem("Copy Request ID");
                copyIdItem.addActionListener(e -> {
                    if (table.getSelectedRow() >= 0) {
                        String id = model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()), 0).toString();
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                                new java.awt.datatransfer.StringSelection(id), null);
                        status.accept(UiNotice.info("Request ID copied to clipboard."));
                    }
                });
                popup.add(editItem);
                popup.add(copyIdItem);
                popup.show(event.getComponent(), event.getX(), event.getY());
            }
        });
        installFilters();
        refreshData();
    }

    private JPanel buildPageActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        JButton add = UiComponents.primaryButton("+ New request");
        add.setPreferredSize(new java.awt.Dimension(154, 34));
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
        toolbar.add(filters, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        actions.setOpaque(false);
        JButton edit = UiComponents.secondaryButton("Edit selected");
        edit.setPreferredSize(new java.awt.Dimension(132, 34));
        edit.addActionListener(event -> showEditDialog());
        JButton decline = UiComponents.secondaryButton("Decline");
        decline.setName("declineRequestButton");
        decline.setPreferredSize(new java.awt.Dimension(92, 34));
        decline.addActionListener(event -> showDeclineDialog());
        actions.add(recordCount);
        actions.add(edit);
        actions.add(decline);
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
        String query = UiComponents.searchValue(search);
        String selected = statusFilter.getSelectedItem().toString();
        java.util.ArrayList<RowFilter<Object, Object>> filters = new java.util.ArrayList<>();
        if (!query.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(query)));
        }
        if (!selected.equals("All statuses")) {
            filters.add(RowFilter.regexFilter("^" + selected + "$", 7));
        }
        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        recordCount.setText(sorter.getViewRowCount() + " RECORDS");
    }

    public void showAddDialog() {
        showRequestDialog(null);
    }

    private void showEditDialog() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            status.accept(UiNotice.info("Select a pending request to edit."));
            return;
        }
        String id = model.getValueAt(table.convertRowIndexToModel(viewRow), 0).toString();
        for (BloodRequest request : controller.getRequests()) {
            if (request.getId().equals(id)) {
                if (request.getStatus() == RequestStatus.FULFILLED) {
                    status.accept(UiNotice.warning(
                            "Fulfilled requests cannot be edited."));
                    return;
                }
                if (request.getStatus() == RequestStatus.CANCELLED) {
                    status.accept(UiNotice.warning(
                            "Cancelled requests cannot be edited."));
                    return;
                }
                showRequestDialog(request);
                return;
            }
        }
    }

    private void showDeclineDialog() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            status.accept(UiNotice.info("Select a pending request to decline."));
            return;
        }
        String id = model.getValueAt(table.convertRowIndexToModel(viewRow), 0).toString();
        BloodRequest selected = null;
        for (BloodRequest request : controller.getRequests()) {
            if (request.getId().equals(id)) {
                selected = request;
                break;
            }
        }
        if (selected == null) {
            return;
        }
        if (selected.getStatus() != RequestStatus.PENDING) {
            status.accept(UiNotice.warning(
                    "Only pending requests can be declined."));
            return;
        }
        BloodRequest target = selected;
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Decline request " + target.getId(),
                Dialog.ModalityType.APPLICATION_MODAL);
        JTextField reason = new JTextField();
        UiComponents.styleInput(reason);
        JLabel error = new JLabel(" ");
        error.setForeground(UiTheme.DANGER);
        error.setFont(UiTheme.SMALL);
        JButton cancel = UiComponents.secondaryButton("Cancel");
        JButton confirm = UiComponents.primaryButton("Decline request");
        cancel.addActionListener(event -> dialog.dispose());
        confirm.addActionListener(event -> {
            try {
                controller.declineRequest(target.getId(), reason.getText());
                dialog.dispose();
                status.accept(UiNotice.warning(
                        "Request " + target.getId() + " declined."));
                onDataChanged.run();
            } catch (LifeFlowException | IOException exception) {
                error.setText(exception.getMessage());
            }
        });
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiComponents.heading("Decline pending request"));
        header.add(Box.createVerticalStrut(5));
        header.add(UiComponents.muted(target.getKind() + " · "
                + DashboardPanel.displayType(target.getBloodType()) + " · "
                + target.getQuantity() + " unit(s)"));
        content.add(header, BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addFormRow(form, 0, "Reason", reason);
        content.add(form, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(confirm);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(error, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        UiComponents.configureDialogKeys(dialog, confirm, cancel);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(520, 240);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private void showRequestDialog(BloodRequest request) {
        boolean editing = request != null;
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, editing ? "Edit request" : "New blood request",
                Dialog.ModalityType.APPLICATION_MODAL);
        JTextField id = new JTextField(editing ? request.getId()
                : controller.getNextRequestId());
        id.setEditable(false);
        JTextField requester = new JTextField(editing ? request.getRequesterName() : "");
        JComboBox<BloodType> type = new JComboBox<>(BloodType.values());
        JTextField quantity = new JTextField(editing
                ? Integer.toString(request.getQuantity()) : "1");
        JComboBox<String> kind = new JComboBox<>(new String[]{"REGULAR", "EMERGENCY"});
        if (editing) {
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
        addFormRow(form, 0, editing ? "Request ID" : "Request ID (auto)", id);
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
                status.accept(UiNotice.success(editing ? "Blood request updated."
                        : "Blood request created successfully."));
                onDataChanged.run();
            } catch (NumberFormatException exception) {
                error.setText("Quantity must be a valid whole number.");
            } catch (LifeFlowException | IOException exception) {
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

    public void onStateChanged() { refreshData(); }

    public void refreshData() {
        model.setRowCount(0);
        for (BloodRequest request : controller.getRequests()) {
            model.addRow(new Object[]{request.getId(), request.getKind(),
                    request.getRequesterName(), DashboardPanel.displayType(request.getBloodType()),
                    request.getQuantity(), request.getRequestDate(), request.getPriority(),
                    request.getStatus(),
                    controller.getVolunteerCountForRequest(request.getId())});
        }
        recordCount.setText(sorter.getViewRowCount() + " RECORDS");
        centerLayout.show(center, model.getRowCount() == 0 ? "empty" : "table");
    }
}
