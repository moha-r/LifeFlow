package lifeflow.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import lifeflow.model.BloodType;
import lifeflow.model.DonorAccount;
import lifeflow.model.LifeFlowState;
import lifeflow.model.exception.LifeFlowException;
import lifeflow.persistence.JsonLifeFlowStore;

/**
 * Coordinates donor self-registration: the login account lives in the donor
 * registry while the medical profile lives in the main data store. The profile
 * is created only after the account succeeds, and the account is removed when
 * the profile cannot be saved.
 */
public final class DonorSignupService {
    private final DonorRegistry registry;
    private final Path dataDirectory;

    public DonorSignupService(DonorRegistry registry, Path dataDirectory) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.dataDirectory = Objects.requireNonNull(dataDirectory,
                "dataDirectory");
    }

    public DonorAccount signup(String name, int age, double weight,
                               BloodType bloodType, String username,
                               String password) throws IOException {
        JsonLifeFlowStore store = new JsonLifeFlowStore(dataDirectory);
        boolean storeReleased = false;
        try {
            LifeFlowState state = store.load();
            LifeFlowController controller = new LifeFlowController(state, store);
            try {
                String donorId = controller.getNextDonorId();
                DonorAccount account = registry.register(donorId, username, password);
                try {
                    controller.addDonor(donorId, name, age, weight, bloodType, null);
                    return account;
                } catch (LifeFlowException | IOException failure) {
                    try {
                        registry.remove(username);
                    } catch (IOException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                    throw failure;
                }
            } finally {
                controller.close();
                storeReleased = true;
            }
        } finally {
            if (!storeReleased) {
                try {
                    store.close();
                } catch (IOException ignored) {
                    // The original error carries the useful message.
                }
            }
        }
    }
}