package lifeflow.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EligibilityResult;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RequestStatus;
import java.time.LocalDate;

public class CsvReportExporter {

    public static void exportInventory(Path path, LifeFlowState state, LocalDate today) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            // Write BOM for Excel to recognize UTF-8
            writer.write('\ufeff');
            writer.write("Unit ID,Donor ID,Blood Type,Donation Date,Expiry Date,Status\n");
            for (BloodUnit unit : state.getUnits()) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s\n",
                        unit.getId(),
                        unit.getDonorId(),
                        unit.getBloodType().name(),
                        unit.getDonationDate(),
                        unit.getExpiryDate(),
                        unit.getInventoryState(today).name()
                ));
            }
        }
    }

    public static void exportDonors(Path path, LifeFlowState state, LocalDate today)
            throws IOException {
        DonationPolicy policy = new DonationPolicy();
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writer.write("Donor ID,Name,Age,Weight (kg),Blood Type,Last Donation,"
                    + "Eligibility,Next Eligible Date\n");
            for (Donor donor : state.getDonors()) {
                LocalDate last = effectiveLastDonation(state, donor.getId());
                EligibilityResult result = policy.evaluate(donor, today, last);
                writer.write(String.format("%s,%s,%d,%.1f,%s,%s,%s,%s\n",
                        donor.getId(),
                        csv(donor.getName()),
                        donor.getAge(),
                        donor.getWeightKg(),
                        donor.getBloodType().name(),
                        last == null ? "" : last.toString(),
                        result.eligible() ? "ELIGIBLE" : result.reason().name(),
                        result.nextEligibleDate() == null ? ""
                                : result.nextEligibleDate().toString()));
            }
        }
    }

    public static void exportRequests(Path path, LifeFlowState state) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writer.write("Request ID,Kind,Requester,Blood Type,Quantity,Request Date,"
                    + "Priority,Status\n");
            for (BloodRequest request : state.getRequests()) {
                writer.write(String.format("%s,%s,%s,%s,%d,%s,%d,%s\n",
                        request.getId(),
                        request.getKind(),
                        csv(request.getRequesterName()),
                        request.getBloodType().name(),
                        request.getQuantity(),
                        request.getRequestDate(),
                        request.getPriority(),
                        request.getStatus().name()));
            }
        }
    }

    public static void exportAppointments(Path path, LifeFlowState state)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writer.write("Appointment ID,Donor ID,Hospital ID,Appointment Date,"
                    + "Linked Request,Status\n");
            for (lifeflow.model.DonationAppointment appointment
                    : state.getAppointments()) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s\n",
                        appointment.getId(),
                        appointment.getDonorId(),
                        appointment.getHospitalId(),
                        appointment.getAppointmentDate(),
                        appointment.getLinkedRequestId() == null ? ""
                                : appointment.getLinkedRequestId(),
                        appointment.getStatus().name()));
            }
        }
    }

    public static void exportAudit(Path path, LifeFlowState state) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writer.write("Fulfilment History\n");
            writer.write("Request ID,Processed On,Units\n");
            for (FulfilmentRecord record : completedRecords(state)) {
                writer.write(String.format("%s,%s,\"%s\"\n",
                        record.requestId(), record.processedDate(),
                        String.join(" | ", record.unitIds())));
            }
            writer.write("\nOperation Log\n");
            writer.write("Timestamp,Operation\n");
            for (String log : state.getLogs()) {
                String timestamp = "";
                String message = log;
                if (log.startsWith("[") && log.contains("] ")) {
                    int end = log.indexOf("] ");
                    timestamp = log.substring(1, end);
                    message = log.substring(end + 2);
                }
                writer.write(String.format("%s,%s\n", timestamp,
                        message.replace("\"", "'").replace("\n", " ")));
            }
        }
    }

    private static java.util.List<FulfilmentRecord> completedRecords(LifeFlowState state) {
        return state.getFulfilments().stream()
                .filter(record -> state.getRequests().stream()
                        .anyMatch(request -> request.getId()
                                .equalsIgnoreCase(record.requestId())
                                && request.getStatus() == RequestStatus.FULFILLED))
                .toList();
    }

    private static LocalDate effectiveLastDonation(LifeFlowState state, String donorId) {
        LocalDate latest = null;
        for (Donor donor : state.getDonors()) {
            if (donor.getId().equalsIgnoreCase(donorId)) {
                latest = donor.getExternalLastDonationDate();
                break;
            }
        }
        for (BloodUnit unit : state.getUnits()) {
            if (unit.getDonorId().equalsIgnoreCase(donorId)
                    && (latest == null || unit.getDonationDate().isAfter(latest))) {
                latest = unit.getDonationDate();
            }
        }
        return latest;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}