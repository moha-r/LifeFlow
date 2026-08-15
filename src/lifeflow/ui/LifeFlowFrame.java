package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.persistence.FileManager;
import lifeflow.service.BloodInventory;
import lifeflow.service.MatchingService;

/** Main Swing window for the LifeFlow educational simulation. */
@SuppressWarnings({"serial", "this-escape"})
public class LifeFlowFrame extends JFrame {
    private final ArrayList<Donor> donors;
    private final ArrayList<BloodRequest> requests;
    private final BloodInventory inventory = new BloodInventory();
    private final MatchingService matchingService;
    private final FileManager fileManager;

    private final DefaultTableModel donorTableModel = readOnlyModel(
            "ID", "Name", "Age", "Weight (kg)", "Blood Type", "Last Donation");
    private final DefaultTableModel unitTableModel = readOnlyModel(
            "Unit ID", "Donor ID", "Blood Type", "Donation Date", "Expiry Date", "Status");
    private final DefaultTableModel requestTableModel = readOnlyModel(
            "Request ID", "Kind", "Requester", "Blood Type", "Quantity", "Date", "Priority", "Status");
    private final DefaultTableModel stockTableModel = readOnlyModel(
            "Blood Type", "Available Units");

    private final JComboBox<String> donorSelection = new JComboBox<>();
    private final JTextArea matchingOutput = new JTextArea();

    public LifeFlowFrame(ArrayList<Donor> donors, ArrayList<BloodUnit> units,
                         ArrayList<BloodRequest> requests,
                         FileManager fileManager) {
        super("LifeFlow - Blood Donation and Emergency Matching Simulator");
        this.donors = donors;
        this.requests = requests;
        this.fileManager = fileManager;
        for (BloodUnit unit : units) {
            inventory.addUnit(unit);
        }
        matchingService = new MatchingService(inventory);

        configureWindow();
        buildContent();
        refreshAll();
    }

    private void configureWindow() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                try {
                    saveAll();
                    dispose();
                } catch (IOException exception) {
                    showError("Could not save data. The application will remain open.", exception);
                }
            }
        });
    }

    private void buildContent() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel notice = new JLabel(
                "Educational simulation only - not for medical or transfusion decisions",
                SwingConstants.CENTER);
        notice.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        root.add(notice, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Donors", buildDonorTab());
        tabs.addTab("Blood Inventory", buildInventoryTab());
        tabs.addTab("Blood Requests", buildRequestTab());
        tabs.addTab("Matching", buildMatchingTab());
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel buildDonorTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JTextField idField = new JTextField();
        JTextField nameField = new JTextField();
        JTextField ageField = new JTextField();
        JTextField weightField = new JTextField();
        JComboBox<BloodType> bloodTypeField = new JComboBox<>(BloodType.values());
        JTextField lastDonationField = new JTextField();

        JPanel fields = formPanel();
        addField(fields, "Donor ID", idField);
        addField(fields, "Name", nameField);
        addField(fields, "Age", ageField);
        addField(fields, "Weight (kg)", weightField);
        addField(fields, "Blood Type", bloodTypeField);
        addField(fields, "Last Donation (yyyy-MM-dd, optional)", lastDonationField);

        JButton addButton = new JButton("Add Donor");
        addButton.addActionListener(event -> {
            try {
                String id = required(idField.getText(), "Donor ID");
                String name = safeText(nameField.getText(), "Name");
                if (containsDonorId(id)) {
                    throw new IllegalArgumentException("Donor ID already exists.");
                }
                int age = Integer.parseInt(required(ageField.getText(), "Age"));
                double weight = Double.parseDouble(required(weightField.getText(), "Weight"));
                if (age <= 0 || weight <= 0) {
                    throw new IllegalArgumentException("Age and weight must be positive numbers.");
                }
                LocalDate lastDonation = optionalDate(lastDonationField.getText());
                donors.add(new Donor(id, name, age, weight,
                        (BloodType) bloodTypeField.getSelectedItem(), lastDonation));
                fileManager.saveDonors(donors);
                refreshAll();
                clear(idField, nameField, ageField, weightField, lastDonationField);
                showInfo("Donor added successfully.");
            } catch (NumberFormatException exception) {
                showError("Age and weight must be valid numbers.", exception);
            } catch (DateTimeParseException exception) {
                showError("Use yyyy-MM-dd for the last donation date.", exception);
            } catch (IllegalArgumentException | IOException exception) {
                showError(exception.getMessage(), exception);
            }
        });

        panel.add(topSection(fields, addButton), BorderLayout.NORTH);
        panel.add(tableScroll(donorTableModel), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInventoryTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JTextField unitIdField = new JTextField();
        JTextField donationDateField = new JTextField(LocalDate.now().toString());
        JTextField expiryDateField = new JTextField();

        JPanel fields = formPanel();
        addField(fields, "Unit ID", unitIdField);
        addField(fields, "Donor", donorSelection);
        addField(fields, "Donation Date (yyyy-MM-dd)", donationDateField);
        addField(fields, "Expiry Date (yyyy-MM-dd)", expiryDateField);

        JButton addButton = new JButton("Add Blood Unit");
        addButton.addActionListener(event -> {
            try {
                String unitId = required(unitIdField.getText(), "Unit ID");
                if (inventory.containsId(unitId)) {
                    throw new IllegalArgumentException("Unit ID already exists.");
                }
                int donorIndex = donorSelection.getSelectedIndex();
                if (donorIndex < 0 || donorIndex >= donors.size()) {
                    throw new IllegalArgumentException("Register and select a donor first.");
                }
                Donor donor = donors.get(donorIndex);
                LocalDate donationDate = LocalDate.parse(
                        required(donationDateField.getText(), "Donation date"));
                LocalDate expiryDate = LocalDate.parse(
                        required(expiryDateField.getText(), "Expiry date"));
                if (!donor.isEligible(donationDate)) {
                    throw new IllegalArgumentException(
                            "Donor is not eligible. Check age 18-60, weight at least 45 kg, "
                                    + "the three-month gap, and that the date is not in the future.");
                }
                if (expiryDate.isBefore(donationDate)) {
                    throw new IllegalArgumentException(
                            "Expiry date cannot be before the donation date.");
                }

                inventory.addUnit(new BloodUnit(unitId, donor.getId(),
                        donor.getBloodType(), donationDate, expiryDate,
                        UnitStatus.AVAILABLE));
                donor.recordDonation(donationDate);
                saveAll();
                refreshAll();
                clear(unitIdField, expiryDateField);
                showInfo("Blood unit added successfully.");
            } catch (DateTimeParseException exception) {
                showError("Use yyyy-MM-dd for donation and expiry dates.", exception);
            } catch (IllegalArgumentException | IOException exception) {
                showError(exception.getMessage(), exception);
            }
        });

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(tableScroll(unitTableModel), BorderLayout.CENTER);
        JScrollPane stockScroll = tableScroll(stockTableModel);
        stockScroll.setPreferredSize(new Dimension(230, 100));
        center.add(stockScroll, BorderLayout.EAST);

        panel.add(topSection(fields, addButton), BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRequestTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JTextField requestIdField = new JTextField();
        JTextField requesterField = new JTextField();
        JComboBox<BloodType> bloodTypeField = new JComboBox<>(BloodType.values());
        JTextField quantityField = new JTextField();
        JComboBox<String> kindField = new JComboBox<>(
                new String[]{"REGULAR", "EMERGENCY"});

        JPanel fields = formPanel();
        addField(fields, "Request ID", requestIdField);
        addField(fields, "Requester", requesterField);
        addField(fields, "Blood Type", bloodTypeField);
        addField(fields, "Quantity", quantityField);
        addField(fields, "Request Kind", kindField);

        JButton addButton = new JButton("Add Request");
        addButton.addActionListener(event -> {
            try {
                String id = required(requestIdField.getText(), "Request ID");
                String requester = safeText(requesterField.getText(), "Requester");
                if (containsRequestId(id)) {
                    throw new IllegalArgumentException("Request ID already exists.");
                }
                int quantity = Integer.parseInt(
                        required(quantityField.getText(), "Quantity"));
                if (quantity <= 0) {
                    throw new IllegalArgumentException("Quantity must be greater than zero.");
                }
                BloodType type = (BloodType) bloodTypeField.getSelectedItem();
                BloodRequest request;
                if ("EMERGENCY".equals(kindField.getSelectedItem())) {
                    request = new EmergencyRequest(id, requester, type, quantity,
                            LocalDate.now(), RequestStatus.PENDING);
                } else {
                    request = new RegularRequest(id, requester, type, quantity,
                            LocalDate.now(), RequestStatus.PENDING);
                }
                requests.add(request);
                fileManager.saveRequests(requests);
                refreshAll();
                clear(requestIdField, requesterField, quantityField);
                showInfo("Blood request added successfully.");
            } catch (NumberFormatException exception) {
                showError("Quantity must be a valid whole number.", exception);
            } catch (IllegalArgumentException | IOException exception) {
                showError(exception.getMessage(), exception);
            }
        });

        panel.add(topSection(fields, addButton), BorderLayout.NORTH);
        panel.add(tableScroll(requestTableModel), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildMatchingTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JButton processButton = new JButton("Process Next Request");
        processButton.addActionListener(event -> processNextRequest());

        matchingOutput.setEditable(false);
        matchingOutput.setLineWrap(true);
        matchingOutput.setWrapStyleWord(true);
        matchingOutput.setText(
                "Press 'Process Next Request' to process the highest-priority pending request.");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(processButton);
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(matchingOutput), BorderLayout.CENTER);
        return panel;
    }

    private void processNextRequest() {
        BloodRequest request = matchingService.findNextPending(requests);
        if (request == null) {
            showInfo("There are no pending requests.");
            return;
        }

        ArrayList<BloodUnit> matched = matchingService.match(request, LocalDate.now());
        if (matched.isEmpty()) {
            int available = inventory.getAvailableUnits(
                    request.getBloodType(), LocalDate.now()).size();
            matchingOutput.setText("Request " + request.getId() + " ("
                    + request.getKind() + ") needs " + request.getQuantity()
                    + " unit(s) of " + request.getBloodType() + ", but only "
                    + available + " are available. The request remains pending.");
            return;
        }

        try {
            saveAll();
            StringBuilder unitIds = new StringBuilder();
            for (BloodUnit unit : matched) {
                if (!unitIds.isEmpty()) {
                    unitIds.append(", ");
                }
                unitIds.append(unit.getId());
            }
            matchingOutput.setText("Request " + request.getId()
                    + " was fulfilled successfully.\nMatched unit(s): " + unitIds);
            refreshAll();
        } catch (IOException exception) {
            showError("The match succeeded in memory, but data could not be saved.", exception);
        }
    }

    private void refreshAll() {
        donorTableModel.setRowCount(0);
        donorSelection.removeAllItems();
        for (Donor donor : donors) {
            donorTableModel.addRow(new Object[]{donor.getId(), donor.getName(),
                    donor.getAge(), donor.getWeightKg(), donor.getBloodType(),
                    donor.getLastDonationDate() == null
                            ? "" : donor.getLastDonationDate()});
            donorSelection.addItem(donor.getId() + " - " + donor.getName());
        }

        unitTableModel.setRowCount(0);
        for (BloodUnit unit : inventory.getUnits()) {
            unitTableModel.addRow(new Object[]{unit.getId(), unit.getDonorId(),
                    unit.getBloodType(), unit.getDonationDate(), unit.getExpiryDate(),
                    unit.getStatus()});
        }

        requestTableModel.setRowCount(0);
        for (BloodRequest request : requests) {
            requestTableModel.addRow(new Object[]{request.getId(), request.getKind(),
                    request.getRequesterName(), request.getBloodType(),
                    request.getQuantity(), request.getRequestDate(),
                    request.getPriority(), request.getStatus()});
        }

        stockTableModel.setRowCount(0);
        HashMap<BloodType, Integer> counts = inventory.getStockCounts(LocalDate.now());
        for (BloodType type : BloodType.values()) {
            stockTableModel.addRow(new Object[]{type, counts.get(type)});
        }
    }

    private void saveAll() throws IOException {
        fileManager.saveDonors(donors);
        fileManager.saveUnits(inventory.getUnits());
        fileManager.saveRequests(requests);
    }

    private boolean containsDonorId(String id) {
        for (Donor donor : donors) {
            if (donor.getId().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsRequestId(String id) {
        for (BloodRequest request : requests) {
            if (request.getId().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    private String required(String value, String fieldName) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return trimmed;
    }

    private String safeText(String value, String fieldName) {
        String trimmed = required(value, fieldName);
        if (trimmed.contains("|")) {
            throw new IllegalArgumentException(fieldName + " cannot contain the | character.");
        }
        return trimmed;
    }

    private LocalDate optionalDate(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : LocalDate.parse(trimmed);
    }

    private static JPanel formPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 4, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return panel;
    }

    private static void addField(JPanel panel, String label, java.awt.Component field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private static JPanel topSection(JPanel fields, JButton button) {
        JPanel section = new JPanel(new BorderLayout());
        section.add(fields, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(button);
        section.add(buttonPanel, BorderLayout.SOUTH);
        return section;
    }

    private static JScrollPane tableScroll(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        return new JScrollPane(table);
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static void clear(JTextField... fields) {
        for (JTextField field : fields) {
            field.setText("");
        }
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "LifeFlow",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message, Exception exception) {
        String detail = message == null || message.isBlank()
                ? exception.getClass().getSimpleName() : message;
        JOptionPane.showMessageDialog(this, detail, "LifeFlow Error",
                JOptionPane.ERROR_MESSAGE);
    }
}
