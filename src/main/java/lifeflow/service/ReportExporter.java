package lifeflow.service;

import java.io.IOException;
import java.nio.file.Path;
import lifeflow.model.LifeFlowState;

/**
 * Strategy interface for exporting LifeFlow data to external formats.
 */
public interface ReportExporter {

    /**
     * Exports the provided state to the given destination file.
     *
     * @param state the current LifeFlow state to export
     * @param destination the path where the report should be saved
     * @throws IOException if the export fails
     */
    void export(LifeFlowState state, Path destination) throws IOException;
}
