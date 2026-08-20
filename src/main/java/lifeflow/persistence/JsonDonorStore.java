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
import lifeflow.model.DonorAccount;

/** Atomic JSON persistence for the donor registry. */
public final class JsonDonorStore implements AutoCloseable {
    private final Path dataFile;
    private final ObjectMapper mapper;

    public JsonDonorStore(Path directory) {
        Path dir = directory.toAbsolutePath().normalize();
        dataFile = dir.resolve("donors.json");
        mapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
    }

    public List<DonorAccount> load() throws IOException {
        if (!Files.exists(dataFile)) {
            return new ArrayList<>();
        }
        List<DonorAccount> accounts = new ArrayList<>();
        var root = mapper.readTree(Files.readAllBytes(dataFile));
        for (var node : root) {
            accounts.add(new DonorAccount(
                    node.get("id").asText(),
                    node.get("donorId").asText(),
                    node.get("username").asText(),
                    node.get("password").asText(),
                    java.time.LocalDate.parse(node.get("registrationDate").asText())));
        }
        return accounts;
    }

    public void save(List<DonorAccount> accounts) throws IOException {
        ArrayNode root = mapper.createArrayNode();
        for (DonorAccount account : accounts) {
            ObjectNode node = root.addObject();
            node.put("id", account.getId());
            node.put("donorId", account.getDonorId());
            node.put("username", account.getUsername());
            node.put("password", account.getPassword());
            node.put("registrationDate", account.getRegistrationDate().toString());
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