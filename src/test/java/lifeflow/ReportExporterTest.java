package lifeflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.service.CsvReportExporter;
import lifeflow.service.HtmlReportExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReportExporterTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @TempDir Path directory;

    @Test
    void donorsReportIncludesEligibility() throws Exception {
        Path file = directory.resolve("donors.csv");
        CsvReportExporter.exportDonors(file, state(), TODAY);
        String content = Files.readString(file);
        assertTrue(content.contains("Donor ID,Name,Age,Weight (kg),Blood Type,"
                + "Last Donation,Eligibility,Next Eligible Date"));
        assertTrue(content.contains("D000001"));
        assertTrue(content.contains("WAITING_PERIOD"));
    }

    @Test
    void donorsReportEscapesNamesWithCommas() throws Exception {
        Path file = directory.resolve("donors.csv");
        LifeFlowState state = state();
        Donor withComma = new Donor("D000002", "Maya, Alia", 30, 60.0,
                BloodType.O_NEG, null);
        ArrayList<Donor> donors = new ArrayList<>(List.of(withComma));
        donors.addAll(state.getDonors());
        CsvReportExporter.exportDonors(file, new LifeFlowState(1, donors,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>()), TODAY);
        String content = Files.readString(file);
        assertTrue(content.contains("\"Maya, Alia\""));
    }

    @Test
    void requestsReportIncludesKindAndPriority() throws Exception {
        Path file = directory.resolve("requests.csv");
        CsvReportExporter.exportRequests(file, state());
        String content = Files.readString(file);
        assertTrue(content.contains("Request ID,Kind,Requester,Blood Type,Quantity,"
                + "Request Date,Priority,Status"));
        assertTrue(content.contains("R000001"));
        assertTrue(content.contains("EMERGENCY"));
        assertTrue(content.contains("REGULAR"));
    }

    @Test
    void auditReportContainsBothSections() throws Exception {
        Path file = directory.resolve("audit.csv");
        CsvReportExporter.exportAudit(file, state());
        String content = Files.readString(file);
        assertTrue(content.contains("Fulfilment History"));
        assertTrue(content.contains("Operation Log"));
        assertTrue(content.contains("Added donor"));
    }

    @Test
    void summaryHtmlContainsMetricsAndEscapesMarkup() throws Exception {
        Path file = directory.resolve("summary.html");
        LifeFlowState state = state();
        Donor hostile = new Donor("D000003", "<script>alert('x')</script>", 25,
                55.0, BloodType.A_POS, null);
        ArrayList<Donor> donors = new ArrayList<>(List.of(hostile));
        donors.addAll(state.getDonors());
        LifeFlowState hostileState = new LifeFlowState(1, donors,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        HtmlReportExporter.exportSummary(file, hostileState, TODAY);
        String content = Files.readString(file);
        assertTrue(content.contains("<title>LifeFlow Summary Report</title>"));
        assertTrue(content.contains("Registered Donors"));
        assertFalse(content.contains("<script>alert"));
        assertTrue(content.contains("&lt;script&gt;alert"));
    }

    @Test
    void appointmentsReportIncludesStatusAndLinkedRequests() throws Exception {
        Path file = directory.resolve("appointments.csv");
        CsvReportExporter.exportAppointments(file, state());
        String content = Files.readString(file);
        assertTrue(content.contains("Appointment ID,Donor ID,Hospital ID,"
                + "Appointment Date,Linked Request,Status"));
        assertTrue(content.contains("A000001"));
        assertTrue(content.contains("R000001"));
        assertTrue(content.contains("BOOKED"));
    }

    @Test
    void summaryHtmlContainsAppointmentsSection() throws Exception {
        Path file = directory.resolve("summary.html");
        HtmlReportExporter.exportSummary(file, state(), TODAY);
        String content = Files.readString(file);
        assertTrue(content.contains("Donation Appointments"));
        assertTrue(content.contains("Upcoming Appointments"));
        assertTrue(content.contains("A000001"));
    }

    @Test
    void reportsExcludePartialFulfilmentRecords() throws Exception {
        LifeFlowState partial = new LifeFlowState(4,
                new ArrayList<>(List.of(new Donor("D000001", "Aisha", 25, 55.0,
                        BloodType.A_POS, null))),
                new ArrayList<>(List.of(
                        new BloodUnit("U000001", "D000001", BloodType.A_POS,
                                TODAY.minusDays(2), TODAY.plusDays(33),
                                UnitStatus.RESERVED),
                        new BloodUnit("U000002", "D000001", BloodType.A_POS,
                                TODAY.minusDays(1), TODAY.plusDays(34),
                                UnitStatus.USED))),
                new ArrayList<>(List.of(
                        new RegularRequest("R000001", "Clinic",
                                BloodType.A_POS, 2, TODAY.minusDays(1),
                                RequestStatus.FULFILLED),
                        new RegularRequest("R000002", "Clinic",
                                BloodType.A_POS, 2, TODAY,
                                RequestStatus.PENDING))),
                new ArrayList<>(List.of(
                        new FulfilmentRecord("R000001", TODAY.minusDays(1),
                                List.of("U000002")),
                        new FulfilmentRecord("R000002", TODAY,
                                List.of("U000001")))),
                new ArrayList<>(),
                new ArrayList<>());

        Path csv = directory.resolve("audit.csv");
        CsvReportExporter.exportAudit(csv, partial);
        String content = Files.readString(csv);
        assertTrue(content.contains("R000001"));
        assertFalse(content.contains("R000002"));

        Path html = directory.resolve("summary.html");
        HtmlReportExporter.exportSummary(html, partial, TODAY);
        String htmlContent = Files.readString(html);
        assertTrue(htmlContent.contains("R000001"));
        String fulfilmentSection = htmlContent.substring(
                htmlContent.indexOf("Fulfilment History"));
        assertFalse(fulfilmentSection.contains("R000002"));
    }

    private static LifeFlowState state() {
        Donor donor = new Donor("D000001", "Aisha", 25, 55.0, BloodType.A_POS,
                null);
        BloodUnit unit = new BloodUnit("U000001", "D000001", BloodType.A_POS,
                TODAY.minusDays(2), TODAY.plusDays(33), UnitStatus.AVAILABLE);
        RegularRequest regular = new RegularRequest("R000001", "Clinic",
                BloodType.A_POS, 1, TODAY.minusDays(1), RequestStatus.FULFILLED);
        EmergencyRequest emergency = new EmergencyRequest("R000002", "ER",
                BloodType.O_NEG, 2, TODAY, RequestStatus.PENDING);
        FulfilmentRecord record = new FulfilmentRecord("R000001",
                TODAY.minusDays(1), List.of("U000001"));
        lifeflow.model.DonationAppointment completed =
                new lifeflow.model.DonationAppointment("A000001", "D000001",
                        "H1", TODAY.minusDays(3), "R000001",
                        lifeflow.model.AppointmentStatus.COMPLETED);
        lifeflow.model.DonationAppointment upcoming =
                new lifeflow.model.DonationAppointment("A000002", "D000001",
                        "H1", TODAY.plusDays(4), "R000002",
                        lifeflow.model.AppointmentStatus.BOOKED);
        ArrayList<String> logs = new ArrayList<>(List.of(
                "[2026-08-20 10:00:00] Added donor D000001 (A_POS)",
                "[2026-08-20 10:05:00] Fulfilled request R000001 (1 units)"));
        return new LifeFlowState(3,
                new ArrayList<>(List.of(donor)),
                new ArrayList<>(List.of(unit)),
                new ArrayList<>(List.of(regular, emergency)),
                new ArrayList<>(List.of(record)),
                new ArrayList<>(List.of(completed, upcoming)),
                logs);
    }
}