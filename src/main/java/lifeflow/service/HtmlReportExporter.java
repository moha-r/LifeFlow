package lifeflow.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EligibilityResult;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RequestStatus;

/** Exports a single self-contained HTML summary, printable to PDF from a browser. */
public final class HtmlReportExporter {
    private HtmlReportExporter() {
    }

    public static void exportSummary(Path path, LifeFlowState state, LocalDate today)
            throws IOException {
        DonationPolicy policy = new DonationPolicy();
        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\">\n");
        html.append("<title>LifeFlow Summary Report</title>\n<style>\n");
        html.append(css());
        html.append("</style></head><body>\n");
        html.append("<div class=\"wrap\">\n");

        html.append("<header><h1>LifeFlow &mdash; Summary Report</h1>\n");
        html.append("<p class=\"meta\">Generated ")
                .append(LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append(" &middot; Data revision ").append(state.getRevision())
                .append("</p></header>\n");

        int availableUnits = (int) state.getUnits().stream()
                .filter(unit -> unit.isAvailable(today)).count();
        long pending = state.getRequests().stream()
                .filter(request -> request.getStatus()
                        == lifeflow.model.RequestStatus.PENDING).count();
        long fulfilled = state.getRequests().stream()
                .filter(request -> request.getStatus()
                        == lifeflow.model.RequestStatus.FULFILLED).count();
        long upcoming = state.getAppointments().stream()
                .filter(appointment -> appointment.isBooked()
                        && !appointment.isStale(today)).count();

        html.append("<div class=\"metrics\">\n");
        html.append(metric("Registered Donors", state.getDonors().size()));
        html.append(metric("Available Units", availableUnits));
        html.append(metric("Pending Requests", pending));
        html.append(metric("Fulfilled Requests", fulfilled));
        html.append(metric("Upcoming Appointments", upcoming));
        html.append("</div>\n");

        html.append("<section><h2>Inventory by Blood Type</h2>\n<table>");
        html.append("<tr><th>Blood Type</th><th>Available Units</th></tr>\n");
        java.util.HashMap<BloodType, Integer> stock = new java.util.HashMap<>();
        for (BloodUnit unit : state.getUnits()) {
            if (unit.isAvailable(today)) {
                stock.merge(unit.getBloodType(), 1, Integer::sum);
            }
        }
        for (BloodType type : BloodType.values()) {
            html.append("<tr><td>").append(type.name())
                    .append("</td><td>").append(stock.getOrDefault(type, 0))
                    .append("</td></tr>\n");
        }
        html.append("</table></section>\n");

        html.append("<section><h2>Donors</h2>\n<table>");
        html.append("<tr><th>ID</th><th>Name</th><th>Age</th><th>Weight</th>"
                + "<th>Blood Type</th><th>Eligibility</th></tr>\n");
        for (Donor donor : state.getDonors()) {
            LocalDate last = effectiveLastDonation(state, donor.getId());
            EligibilityResult result = policy.evaluate(donor, today, last);
            html.append("<tr><td>").append(escape(donor.getId()))
                    .append("</td><td>").append(escape(donor.getName()))
                    .append("</td><td>").append(donor.getAge())
                    .append("</td><td>").append(String.format("%.1f kg", donor.getWeightKg()))
                    .append("</td><td>").append(donor.getBloodType().name())
                    .append("</td><td>").append(result.eligible() ? "ELIGIBLE"
                            : result.reason().name())
                    .append("</td></tr>\n");
        }
        html.append("</table></section>\n");

        html.append("<section><h2>Blood Requests</h2>\n<table>");
        html.append("<tr><th>ID</th><th>Kind</th><th>Requester</th><th>Blood Type</th>"
                + "<th>Quantity</th><th>Requested On</th><th>Status</th></tr>\n");
        for (BloodRequest request : state.getRequests()) {
            html.append("<tr><td>").append(escape(request.getId()))
                    .append("</td><td>").append(request.getKind())
                    .append("</td><td>").append(escape(request.getRequesterName()))
                    .append("</td><td>").append(request.getBloodType().name())
                    .append("</td><td>").append(request.getQuantity())
                    .append("</td><td>").append(request.getRequestDate())
                    .append("</td><td>").append(request.getStatus().name())
                    .append("</td></tr>\n");
        }
        html.append("</table></section>\n");

        html.append("<section><h2>Donation Appointments</h2>\n<table>");
        html.append("<tr><th>ID</th><th>Donor</th><th>Hospital</th><th>Date</th>"
                + "<th>Linked Request</th><th>Status</th></tr>\n");
        for (lifeflow.model.DonationAppointment appointment
                : state.getAppointments()) {
            html.append("<tr><td>").append(escape(appointment.getId()))
                    .append("</td><td>").append(escape(appointment.getDonorId()))
                    .append("</td><td>").append(escape(appointment.getHospitalId()))
                    .append("</td><td>").append(appointment.getAppointmentDate())
                    .append("</td><td>")
                    .append(appointment.getLinkedRequestId() == null ? "&mdash;"
                            : escape(appointment.getLinkedRequestId()))
                    .append("</td><td>").append(appointment.getStatus().name())
                    .append("</td></tr>\n");
        }
        html.append("</table></section>\n");

        html.append("<section><h2>Fulfilment History</h2>\n<table>");
        html.append("<tr><th>Request ID</th><th>Processed On</th><th>Units</th></tr>\n");
        for (FulfilmentRecord record : completedRecords(state)) {
            html.append("<tr><td>").append(escape(record.requestId()))
                    .append("</td><td>").append(record.processedDate())
                    .append("</td><td>").append(escape(String.join(", ", record.unitIds())))
                    .append("</td></tr>\n");
        }
        html.append("</table></section>\n");

        html.append("<section><h2>Operation Log</h2>\n<table>");
        html.append("<tr><th>Timestamp</th><th>Operation</th></tr>\n");
        for (String log : state.getLogs()) {
            String timestamp = "";
            String message = log;
            if (log.startsWith("[") && log.contains("] ")) {
                int end = log.indexOf("] ");
                timestamp = log.substring(1, end);
                message = log.substring(end + 2);
            }
            html.append("<tr><td>").append(escape(timestamp))
                    .append("</td><td>").append(escape(message))
                    .append("</td></tr>\n");
        }
        html.append("</table></section>\n");

        html.append("<footer><p>LifeFlow educational simulation &mdash; "
                + "not for real medical use.</p></footer>\n");
        html.append("</div></body></html>\n");

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(html.toString());
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

    private static String metric(String label, long value) {
        return "<div class=\"metric\"><span class=\"label\">"
                + escape(label) + "</span><span class=\"value\">"
                + value + "</span></div>\n";
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

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String css() {
        return "body{font-family:Segoe UI,Arial,sans-serif;background:#F5F7FB;"
                + "color:#182033;margin:0;padding:24px;}"
                + ".wrap{max-width:960px;margin:0 auto;background:#fff;"
                + "border:1px solid #E5EAF2;border-radius:12px;padding:28px;}"
                + "header{border-bottom:3px solid #EF476F;padding-bottom:12px;}"
                + "h1{margin:0;font-size:24px;color:#182033;}"
                + ".meta{color:#6B7280;font-size:13px;margin:6px 0 0;}"
                + ".metrics{display:flex;gap:12px;margin:20px 0;}"
                + ".metric{flex:1;border-left:4px solid #EF476F;"
                + "border-top:1px solid #E5EAF2;border-right:1px solid #E5EAF2;"
                + "border-bottom:1px solid #E5EAF2;border-radius:4px;padding:10px 12px;}"
                + ".metric .label{display:block;font-size:10px;font-weight:700;"
                + "color:#6B7280;letter-spacing:.5px;}"
                + ".metric .value{display:block;font-size:26px;font-weight:700;"
                + "color:#182033;margin-top:4px;}"
                + "section{margin-top:24px;}h2{font-size:15px;color:#182033;"
                + "border-bottom:1px solid #E5EAF2;padding-bottom:6px;}"
                + "table{width:100%;border-collapse:collapse;font-size:13px;margin-top:8px;}"
                + "th{background:#F8F9FC;color:#6B7280;text-align:left;padding:8px 10px;"
                + "border:1px solid #E5EAF2;font-size:12px;}"
                + "td{padding:7px 10px;border:1px solid #E5EAF2;}"
                + "tr:nth-child(even) td{background:#FBFCFD;}"
                + "footer{margin-top:28px;border-top:1px solid #E5EAF2;padding-top:10px;"
                + "color:#6B7280;font-size:12px;}"
                + "@media print{body{background:#fff;padding:0;}"
                + ".wrap{border:none;box-shadow:none;}}";
    }
}