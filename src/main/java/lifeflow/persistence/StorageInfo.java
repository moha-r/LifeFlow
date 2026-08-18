package lifeflow.persistence;

import java.nio.file.Path;

/** Current health and location of local JSON persistence. */
public record StorageInfo(Path dataFile, boolean backupCurrent,
                          boolean recoveryRequired, String detail) {
}
