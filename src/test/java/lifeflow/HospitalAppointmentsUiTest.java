package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import lifeflow.model.AppointmentStatus;
import lifeflow.model.BloodType;
import lifeflow.model.Donor;
import lifeflow.model.DonationAppointment;
import lifeflow.model.Hospital;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.persistence.JsonHospitalStore;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.service.HospitalRegistry;
import lifeflow.ui.HospitalPortalFrame;
import org.junit.jupiter.api.Test;

final class HospitalAppointmentsUiTest {
    private static final LocalDate TODAY = LocalDate.now();

    @Test
    void hospitalPortalUsesBoundedTaskFirstLayout() throws Exception {
        Hospital hospital = new Hospital("H1", "City Hospital", "city",
                "pass123", TODAY);
        HospitalPortalFrame frame = frame(state(hospital, List.of()), hospital);

        JScrollPane scroll = (JScrollPane) findIn(frame, "hospitalPortalScroll");
        JPanel content = (JPanel) findIn(frame, "hospitalPortalContent");
        JPanel overview = (JPanel) findIn(frame, "hospitalOverviewPanel");
        JPanel workspace = (JPanel) findIn(frame, "hospitalRequestWorkspace");
        JPanel composer = (JPanel) findIn(frame, "hospitalRequestComposer");
        JPanel requests = (JPanel) findIn(frame, "hospitalRequestsPanel");
        JPanel appointments = (JPanel) findIn(frame, "hospitalAppointmentsPanel");
        JPanel brand = (JPanel) findIn(frame, "hospitalBrandPanel");
        JButton signOut = findButton(frame, "hospitalSignOutButton");

        assertNotNull(scroll);
        assertEquals(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                scroll.getVerticalScrollBarPolicy());
        assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
                scroll.getHorizontalScrollBarPolicy());
        assertNotNull(content);
        assertTrue(content.getPreferredSize().width <= 1280);
        assertNotNull(overview);
        assertTrue(overview.getPreferredSize().height <= 150);
        assertNotNull(workspace);
        assertTrue(workspace.getLayout() instanceof java.awt.BorderLayout);
        assertNotNull(composer);
        assertNotNull(requests);
        assertNotNull(appointments);
        assertNotNull(brand);
        assertTrue(brand.getLayout() instanceof java.awt.GridBagLayout);
        assertNotNull(signOut);
        assertTrue(signOut.getPreferredSize().width <= 120);
        assertEquals(javax.swing.SwingConstants.CENTER,
                signOut.getHorizontalAlignment());

        JComboBox<?> bloodType = (JComboBox<?>) findIn(frame,
                "hospitalBloodTypePicker");
        JComboBox<?> requestKind = (JComboBox<?>) findIn(frame,
                "hospitalRequestKindPicker");
        assertEquals("A+", renderedText(bloodType, BloodType.A_POS));
        assertEquals("Emergency", renderedText(requestKind, "EMERGENCY"));
        frame.dispose();
    }

    @Test
    void hospitalSeesItsAppointmentsAndVolunteers() throws Exception {
        Hospital hospital = new Hospital("H1", "City Hospital", "city",
                "pass123", TODAY);
        DonationAppointment mine = new DonationAppointment("A000001", "D1",
                "H1", TODAY.plusDays(2), "R1", AppointmentStatus.BOOKED);
        DonationAppointment others = new DonationAppointment("A000002", "D2",
                "H2", TODAY.plusDays(2), null, AppointmentStatus.BOOKED);
        DonationAppointment completed = new DonationAppointment("A000003",
                "D1", "H1", TODAY.minusDays(3), null,
                AppointmentStatus.COMPLETED);
        HospitalPortalFrame frame = frame(state(hospital,
                List.of(mine, others, completed)), hospital);

        JTable appointments = findTable(frame, "hospitalAppointmentsTable");
        assertEquals(2, appointments.getRowCount());
        assertEquals("A000003", appointments.getValueAt(0, 0));
        assertEquals("A000001", appointments.getValueAt(1, 0));
        assertEquals("Sara Ali", appointments.getValueAt(1, 1));

        JTable requests = findTable(frame, "portalRequestsTable");
        assertEquals(1, requests.getRowCount());
        assertEquals(1, requests.getValueAt(0, 6));
        frame.dispose();
    }

    @Test
    void recordButtonEnablesOnlyForCompletableBooking() throws Exception {
        Hospital hospital = new Hospital("H1", "City Hospital", "city",
                "pass123", TODAY);
        DonationAppointment today = new DonationAppointment("A000001", "D1",
                "H1", TODAY, null, AppointmentStatus.BOOKED);
        DonationAppointment future = new DonationAppointment("A000002", "D2",
                "H1", TODAY.plusDays(5), null, AppointmentStatus.BOOKED);
        HospitalPortalFrame frame = frame(state(hospital,
                List.of(today, future)), hospital);

        JTable appointments = findTable(frame, "hospitalAppointmentsTable");
        JButton record = findButton(frame, "recordDonationButton");
        assertFalse(record.isEnabled());
        appointments.setRowSelectionInterval(0, 0);
        assertTrue(record.isEnabled());
        appointments.setRowSelectionInterval(1, 1);
        assertFalse(record.isEnabled());
        frame.dispose();
    }

    @Test
    void hospitalRequestsSurviveHospitalRename() throws Exception {
        Hospital renamed = new Hospital("H1", "Renamed Central", "city",
                "pass123", TODAY);
        ArrayList<lifeflow.model.BloodRequest> requests = new ArrayList<>();
        requests.add(new RegularRequest("R1", "Old Name",
                BloodType.O_POS, 1, TODAY, RequestStatus.PENDING, "H1"));
        HospitalPortalFrame frame = frame(state(renamed,
                List.of(), requests), renamed);

        JTable table = findTable(frame, "portalRequestsTable");
        assertEquals(1, table.getRowCount());
        assertEquals("R1", table.getValueAt(0, 0));
        frame.dispose();
    }

    @Test
    void hospitalCanCancelItsOwnPendingRequest() throws Exception {
        Hospital hospital = new Hospital("H1", "City Hospital", "city",
                "pass123", TODAY);
        DonationAppointment volunteer = new DonationAppointment("A000001",
                "D1", "H1", TODAY.plusDays(2), "R1", AppointmentStatus.BOOKED);
        ArrayList<lifeflow.model.BloodRequest> requests = new ArrayList<>();
        requests.add(new RegularRequest("R1", hospital.getName(),
                BloodType.O_POS, 1, TODAY, RequestStatus.PENDING, "H1"));
        HospitalPortalFrame frame = frame(state(hospital,
                List.of(volunteer), requests), hospital);

        JTable requestsTable = findTable(frame, "portalRequestsTable");
        JButton cancel = findButton(frame, "cancelRequestButton");
        assertFalse(cancel.isEnabled());
        requestsTable.setRowSelectionInterval(0, 0);
        assertTrue(cancel.isEnabled());

        cancel.doClick();

        assertEquals("CANCELLED", requestsTable.getValueAt(0, 5));
        JTable appointments = findTable(frame, "hospitalAppointmentsTable");
        assertEquals("CANCELLED", appointments.getValueAt(0, 3));
        frame.dispose();
    }

    @Test
    void fulfilledRequestShowsUnitDetails() throws Exception {
        Hospital hospital = new Hospital("H1", "City Hospital", "city",
                "pass123", TODAY);
        ArrayList<lifeflow.model.BloodRequest> requests = new ArrayList<>();
        requests.add(new RegularRequest("R1", hospital.getName(),
                BloodType.O_POS, 2, TODAY.minusDays(1),
                RequestStatus.FULFILLED, "H1"));
        ArrayList<lifeflow.model.FulfilmentRecord> records = new ArrayList<>();
        records.add(new lifeflow.model.FulfilmentRecord("R1",
                TODAY.minusDays(1), List.of("U000001", "U000002")));
        HospitalPortalFrame frame = frame(state(hospital, List.of(), requests,
                records, units()), hospital);

        JTable table = findTable(frame, "portalRequestsTable");
        assertEquals(1, table.getRowCount());
        assertEquals("2 units (U000001, U000002)", table.getValueAt(0, 7));
        frame.dispose();
    }

    private static ArrayList<lifeflow.model.BloodUnit> units() {
        ArrayList<lifeflow.model.BloodUnit> units = new ArrayList<>();
        units.add(new lifeflow.model.BloodUnit("U000001", "D1", BloodType.O_POS,
                TODAY.minusDays(1), TODAY.plusDays(34),
                lifeflow.model.UnitStatus.USED));
        units.add(new lifeflow.model.BloodUnit("U000002", "D3", BloodType.O_POS,
                TODAY.minusDays(1), TODAY.plusDays(34),
                lifeflow.model.UnitStatus.USED));
        return units;
    }

    private static LifeFlowState state(Hospital hospital,
                                       java.util.List<DonationAppointment> appointments) {
        return state(hospital, appointments,
                requestsFor(hospital));
    }

    private static LifeFlowState state(Hospital hospital,
                                       java.util.List<DonationAppointment> appointments,
                                       java.util.List<lifeflow.model.BloodRequest> requests) {
        return state(hospital, appointments, requests, new ArrayList<>());
    }

    private static LifeFlowState state(Hospital hospital,
                                       java.util.List<DonationAppointment> appointments,
                                       java.util.List<lifeflow.model.BloodRequest> requests,
                                       java.util.List<lifeflow.model.FulfilmentRecord> records) {
        return state(hospital, appointments, requests, records, new ArrayList<>());
    }

    private static LifeFlowState state(Hospital hospital,
                                       java.util.List<DonationAppointment> appointments,
                                       java.util.List<lifeflow.model.BloodRequest> requests,
                                       java.util.List<lifeflow.model.FulfilmentRecord> records,
                                       ArrayList<lifeflow.model.BloodUnit> units) {
        ArrayList<Donor> donors = new ArrayList<>();
        donors.add(new Donor("D1", "Sara Ali", 25, 62, BloodType.O_POS, null));
        donors.add(new Donor("D2", "Omar Khan", 30, 80, BloodType.B_POS, null));
        donors.add(new Donor("D3", "Extra Donor", 28, 70, BloodType.O_POS,
                null));
        return new LifeFlowState(1, donors, units,
                new ArrayList<>(requests), new ArrayList<>(records),
                new ArrayList<>(appointments), new ArrayList<>());
    }

    private static java.util.List<lifeflow.model.BloodRequest> requestsFor(
            Hospital hospital) {
        ArrayList<lifeflow.model.BloodRequest> requests = new ArrayList<>();
        requests.add(new RegularRequest("R1", hospital.getName(),
                BloodType.O_POS, 1, TODAY, RequestStatus.PENDING));
        return requests;
    }

    private static HospitalPortalFrame frame(LifeFlowState state,
                                             Hospital hospital)
            throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-hosp-");
        HospitalRegistry registry = new HospitalRegistry(new ArrayList<>(),
                new JsonHospitalStore(dir));
        JsonLifeFlowStore store = new JsonLifeFlowStore(dir);
        HospitalPortalFrame[] holder = new HospitalPortalFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new HospitalPortalFrame(state, store, hospital,
                    registry, new NoOpSwitcher());
        });
        return holder[0];
    }

    private static JLabel findLabel(java.awt.Component component, String name) {
        return (JLabel) findIn(component, name);
    }

    private static JTable findTable(java.awt.Component component, String name) {
        return (JTable) findIn(component, name);
    }

    private static JButton findButton(java.awt.Component component, String name) {
        return (JButton) findIn(component, name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String renderedText(JComboBox<?> combo, Object value) {
        javax.swing.ListCellRenderer renderer = combo.getRenderer();
        java.awt.Component rendered = renderer.getListCellRendererComponent(
                new javax.swing.JList<>(), value, 0, false, false);
        return ((JLabel) rendered).getText();
    }

    private static java.awt.Component findIn(java.awt.Component component,
                                             String name) {
        if (name.equals(component.getName())) {
            return component;
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                java.awt.Component match = findIn(child, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static final class NoOpSwitcher implements lifeflow.ui.SessionSwitcher {
        @Override
        public void exitApplication() {
        }

        @Override
        public void openHospitalPortal(Hospital other) {
        }

        @Override
        public void openDonorPortal(lifeflow.model.DonorAccount donor) {
        }

        @Override
        public void openAdminWorkspace() {
        }

        @Override
        public void showLogin() {
        }
    }
}
