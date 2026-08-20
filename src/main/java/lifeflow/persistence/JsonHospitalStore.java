package lifeflow.persistence;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import lifeflow.model.Hospital;

/** Atomic JSON persistence for the hospital registry. */
public final class JsonHospitalStore implements AutoCloseable {
    private final Path dataFile;
    private final ObjectMapper mapper;

    public JsonHospitalStore(Path directory) {
        Path dir = directory.toAbsolutePath().normalize();
        dataFile = dir.resolve("hospitals.json");
        mapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
    }

    public List<Hospital> load() throws IOException {
        if (!Files.exists(dataFile)) {
            return new ArrayList<>();
        }
        List<Hospital> hospitals = new ArrayList<>();
        var root = mapper.readTree(Files.readAllBytes(dataFile));
        for (var node : root) {
            hospitals.add(new Hospital(
                    node.get("id").asText(),
                    node.get("name").asText(),
                    node.get("username").asText(),
                    node.get("password").asText(),
                    java.time.LocalDate.parse(node.get("registrationDate").asText())));
        }
        return hospitals;
    }

    public void save(List<Hospital> hospitals) throws IOException {
        ArrayNode root = mapper.createArrayNode();
        for (Hospital hospital : hospitals) {
            ObjectNode node = root.addObject();
            node.put("id", hospital.getId());
            node.put("name", hospital.getName());
            node.put("username", hospital.getUsername());
            node.put("password", hospital.getPassword());
            node.put("registrationDate", hospital.getRegistrationDate().toString());
        }
        Files.createDirectories(dataFile.getParent());
        Path temp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        Files.write(temp, mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(root));
        try {
            Files.move(temp, dataFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void close() {
        // Nothing to release; kept for AutoCloseable symmetry with the main store.
    }
}