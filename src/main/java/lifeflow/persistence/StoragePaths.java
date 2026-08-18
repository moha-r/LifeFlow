package lifeflow.persistence;

import java.nio.file.Path;
import java.util.Map;

/** Resolves one stable data directory independently from the working directory. */
public final class StoragePaths {
    private static final String ENVIRONMENT_KEY = "LIFEFLOW_DATA_DIR";

    private StoragePaths() {
    }

    public static Path resolve() {
        return resolve(System.getenv(), System.getProperty("user.home"));
    }

    public static Path resolve(Map<String, String> environment, String userHome) {
        String configured = environment.get(ENVIRONMENT_KEY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim()).toAbsolutePath().normalize();
        }
        return Path.of(userHome, ".lifeflow").toAbsolutePath().normalize();
    }
}
