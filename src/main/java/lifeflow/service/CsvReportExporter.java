package lifeflow.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.LifeFlowState;
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
}
