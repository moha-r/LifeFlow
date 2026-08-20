package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import lifeflow.model.AppointmentStatus;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.DonorAccount;
import lifeflow.model.DonationAppointment;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.persistence.JsonDonorStore;
import lifeflow.persistence.JsonHospitalStore;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.service.DonorRegistry;
import lifeflow.service.HospitalRegistry;
import lifeflow.ui.DonorPortalFrame;
import org.junit.jupiter.api.Test;

final class DonorPortalUiTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Test
    void eligibleDonorSeesEligibleChipAndEmptyHistory() throws Exception {
        Donor donor = donor("D1", "Sara Ali", 25, 62, null);
        DonorAccount account = account("DA1", "D1");
        DonorPortalFrame frame = frame(state(donor, new ArrayList<>()), account);

        JLabel chip = findLabel(frame, "donorStatusChip");
        JLabel count = findLabel(frame, "donorDonationCount");
        JTable donations = findTable(frame, "donorDonationsTable");

        assertEquals("ELIGIBLE TO DONATE", chip.getText());
        assertEquals("0 donation(s)", count.getText());
        assertEquals(0, donations.getRowCount());
        assertTrue(findButton(frame, "donorEditProfileButton").isEnabled());
        frame.dispose();
    }

    @Test
    void donorWithUnitsSeesHistoryAndDeferredStatus() throws Exception {
        Donor donor = donor("D1", "Sara Ali", 25, 62, null);
        BloodUnit unit = new BloodUnit("U000001", "D1", BloodType.O_POS,
                TODAY.minusDays(40), TODAY.minusDays(5), UnitStatus.AVAILABLE);
        DonorAccount account = account("DA1", "D1");
        DonorPortalFrame frame = frame(state(donor, List.of(unit)), account);

        JLabel chip = findLabel(frame, "donorStatusChip");
        JTable donations = findTable(frame, "donorDonationsTable");

        assertEquals("DEFERRED", chip.getText());
        assertEquals(1, donations.getRowCount());
        assertEquals("U000001", donations.getValueAt(0, 0));
        assertEquals("O+", donations.getValueAt(0, 2));
        frame.dispose();
    }

    @Test
    void otherDonorsUnitsAreExcludedFromHistory() throws Exception {
        Donor donor = donor("D1", "Sara Ali", 25, 62, null);
        BloodUnit mine = new BloodUnit("U000001", "D1", BloodType.O_POS,
                TODAY.minusDays(40), TODAY.minusDays(5), UnitStatus.AVAILABLE);
        BloodUnit other = new BloodUnit("U000002", "D2", BloodType.B_POS,
                TODAY.minusDays(10), TODAY.plusDays(25), UnitStatus.AVAILABLE);
        DonorAccount account = account("DA1", "D1");
        Donor otherDonor = new Donor("D2", "Omar Khan", 30, 80,
                BloodType.B_POS, null);
        DonorPortalFrame frame = frame(state(donor, List.of(mine, other),
                List.of(otherDonor)), account);

        JTable donations = findTable(frame, "donorDonationsTable");
        assertEquals(1, donations.getRowCount());
        assertEquals("U000001", donations.getValueAt(0, 0));
        frame.dispose();
    }

    @Test
    void missingProfileShowsRemovalNoticeAndLocksEditing() throws Exception {
        DonorAccount account = account("DA1", "D-MISSING");
        DonorPortalFrame frame = frame(state(null, new ArrayList<>()), account);

        JLabel chip = findLabel(frame, "donorStatusChip");
        assertEquals("PROFILE REMOVED", chip.getText());
        assertFalse(findButton(frame, "donorEditProfileButton").isEnabled());
        frame.dispose();
    }

    @Test
    void urgentNeedsShowOnlyCompatiblePendingRequests() throws Exception {
        Donor donor = donor("D1", "Sara Ali", 25, 62, null);
        BloodRequest compatible = new RegularRequest("R1", "City Hospital",
                BloodType.O_POS, 2, TODAY, RequestStatus.PENDING);
        BloodRequest incompatible = new RegularRequest("R2", "City Hospital",
                BloodType.O_NEG, 1, TODAY, RequestStatus.PENDING);
        BloodRequest notOpen = new RegularRequest("R3", "City Hospital",
                BloodType.B_POS, 1, TODAY, RequestStatus.CANCELLED);
        DonorAccount account = account("DA1", "D1");
        DonorPortalFrame frame = frame(state(donor, new ArrayList<>(),
                new ArrayList<>(), List.of(compatible, incompatible, notOpen)),
                account);

        JTable urgent = findTable(frame, "donorUrgentTable");
        assertEquals(1, urgent.getRowCount());
        assertEquals("R1", urgent.getValueAt(0, 0));
        frame.dispose();
    }

    @Test
    void appointmentsTableShowsBookedAppointments() throws Exception {
        Donor donor = donor("D1", "Sara Ali", 25, 62, null);
        DonationAppointment booked = new DonationAppointment("A000001", "D1",
                "H1", TODAY, null, AppointmentStatus.BOOKED);
        DonationAppointment completed = new DonationAppointment("A000002", "D1",
                "H1", TODAY.minusDays(10), null, AppointmentStatus.COMPLETED);
        DonorAccount account = account("DA1", "D1");
        DonorPortalFrame frame = frame(stateWithAppointments(donor,
                new ArrayList<>(), List.of(booked, completed)), account);

        JTable appointments = findTable(frame, "donorAppointmentsTable");
        assertEquals(2, appointments.getRowCount());
        assertEquals("A000002", appointments.getValueAt(0, 0));
        assertEquals("A000001", appointments.getValueAt(1, 0));
        assertFalse(findButton(frame, "donorBookButton").isEnabled());
        frame.dispose();
    }

    private static Donor donor(String id, String name, int age, double weight,
                               LocalDate external) {
        return new Donor(id, name, age, weight, BloodType.O_POS, external);
    }

    private static DonorAccount account(String id, String donorId) {
        return new DonorAccount(id, donorId, "sara.ali", "pass123", TODAY);
    }

    private static LifeFlowState state(Donor donor, List<BloodUnit> units) {
        return state(donor, units, new ArrayList<>());
    }

    private static LifeFlowState state(Donor donor, List<BloodUnit> units,
                                       List<Donor> extras) {
        ArrayList<Donor> donors = new ArrayList<>(extras);
        if (donor != null) {
            donors.add(donor);
        }
        return new LifeFlowState(1, donors, new ArrayList<>(units),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private static LifeFlowState stateWithAppointments(
            Donor donor, List<BloodUnit> units,
            List<DonationAppointment> appointments) {
        return state(donor, units, appointments, new ArrayList<>());
    }

    private static LifeFlowState state(Donor donor, List<BloodUnit> units,
                                       List<DonationAppointment> appointments,
                                       List<BloodRequest> requests) {
        ArrayList<Donor> donors = new ArrayList<>();
        if (donor != null) {
            donors.add(donor);
        }
        return new LifeFlowState(1, donors, new ArrayList<>(units),
                new ArrayList<>(requests), new ArrayList<>(),
                new ArrayList<>(appointments), new ArrayList<>());
    }

    private static DonorPortalFrame frame(LifeFlowState state, DonorAccount account)
            throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("lifeflow-portal-");
        DonorRegistry registry = new DonorRegistry(new ArrayList<>(),
                new JsonDonorStore(dir));
        HospitalRegistry hospitals = new HospitalRegistry(new ArrayList<>(),
                new JsonHospitalStore(dir));
        JsonLifeFlowStore store = new JsonLifeFlowStore(dir);
        DonorPortalFrame[] holder = new DonorPortalFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new DonorPortalFrame(state, store, account, registry,
                    hospitals, new NoOpSwitcher());
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
        public void openHospitalPortal(lifeflow.model.Hospital hospital) {
        }

        @Override
        public void openDonorPortal(DonorAccount donor) {
        }

        @Override
        public void openAdminWorkspace() {
        }

        @Override
        public void showLogin() {
        }
    }
}