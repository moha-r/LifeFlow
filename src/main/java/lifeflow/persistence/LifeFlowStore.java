package lifeflow.persistence;

import java.io.IOException;
import lifeflow.model.LifeFlowState;

/** Loads and atomically saves complete application snapshots. */
public interface LifeFlowStore extends AutoCloseable {
    LifeFlowState load() throws IOException;

    void save(LifeFlowState state) throws IOException;

    StorageInfo getStorageInfo();

    @Override
    void close() throws IOException;
}
