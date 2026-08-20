package lifeflow;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lifeflow.model.Hospital;
import lifeflow.model.LifeFlowState;
import lifeflow.persistence.JsonHospitalStore;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.persistence.StoragePaths;
import lifeflow.service.HospitalRegistry;
import lifeflow.ui.HospitalPortalFrame;
import lifeflow.ui.LifeFlowFrame;
import lifeflow.ui.LoginDialog;
import lifeflow.ui.LoginResult;
import lifeflow.ui.SessionSwitcher;
import lifeflow.ui.UiTheme;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        CountDownLatch appEnded = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            HospitalRegistry registry = createRegistry();
            if (registry == null) {
                appEnded.countDown();
                return;
            }
            SessionSwitcher switcher = new SessionSwitcher() {
                @Override
                public void exitApplication() {
                    appEnded.countDown();
                }

                @Override
                public void openHospitalPortal(Hospital hospital) {
                    openHospitalSession(registry, hospital, appEnded);
                }

                @Override
                public void openAdminWorkspace() {
                    openAdminSession(registry, appEnded);
                }
            };
            LoginResult result = LoginDialog.showAndAuthenticate(registry);
            if (result == null) {
                appEnded.countDown();
                return;
            }
            if (result.admin()) {
                openAdminSession(registry, appEnded);
            } else {
                openHospitalSession(registry, result.hospital(), appEnded);
            }
        });
        try {
            appEnded.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }

    private static HospitalRegistry createRegistry() {
        try {
            JsonHospitalStore store = new JsonHospitalStore(StoragePaths.resolve());
            return new HospitalRegistry(store.load(), store);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(null,
                    "LifeFlow could not read its hospital accounts.\n"
                            + exception.getMessage(),
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private static void openAdminSession(HospitalRegistry registry,
                                     CountDownLatch appEnded) {
        JsonLifeFlowStore store = null;
        try {
            store = new JsonLifeFlowStore(StoragePaths.resolve());
            LifeFlowState state = loadState(store);
            if (state == null) {
                store.close();
                return;
            }
            LifeFlowFrame frame = new LifeFlowFrame(state, store, registry,
                    new SessionSwitcher() {
                        @Override
                        public void exitApplication() {
                            appEnded.countDown();
                        }

                        @Override
                        public void openHospitalPortal(Hospital hospital) {
                            openHospitalSession(registry, hospital, appEnded);
                        }

                        @Override
                        public void openAdminWorkspace() {
                            // Already on the admin workspace.
                        }
                    });
            frame.setVisible(true);
        } catch (IOException exception) {
            if (store != null) {
                try {
                    store.close();
                } catch (IOException ignored) {
                    // The original startup error is the useful message.
                }
            }
            JOptionPane.showMessageDialog(null,
                    "LifeFlow could not load its data.\n" + exception.getMessage(),
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void openHospitalSession(HospitalRegistry registry,
                                        Hospital hospital,
                                        CountDownLatch appEnded) {
        JsonLifeFlowStore store = null;
        try {
            store = new JsonLifeFlowStore(StoragePaths.resolve());
            LifeFlowState state = loadState(store);
            if (state == null) {
                store.close();
                return;
            }
            HospitalPortalFrame frame = new HospitalPortalFrame(state, store,
                    hospital, registry, new SessionSwitcher() {
                        @Override
                        public void exitApplication() {
                            appEnded.countDown();
                        }

                        @Override
                        public void openHospitalPortal(Hospital other) {
                            openHospitalSession(registry, other, appEnded);
                        }

                        @Override
                        public void openAdminWorkspace() {
                            openAdminSession(registry, appEnded);
                        }
                    });
            frame.setVisible(true);
        } catch (IOException exception) {
            if (store != null) {
                try {
                    store.close();
                } catch (IOException ignored) {
                    // The original startup error is the useful message.
                }
            }
            JOptionPane.showMessageDialog(null,
                    "LifeFlow could not load its data.\n" + exception.getMessage(),
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Loads the snapshot with the existing recovery and migration handling. */
    private static LifeFlowState loadState(JsonLifeFlowStore store) {
        try {
            return store.load();
        } catch (IOException loadFailure) {
            if (store.getStorageInfo().detail().startsWith("Migration failed")) {
                JOptionPane.showMessageDialog(null,
                        "LifeFlow could not migrate the previous JSON format.\n"
                                + "The original file was preserved and archived.\n\n"
                                + loadFailure.getMessage(),
                        "Migration Failed", JOptionPane.ERROR_MESSAGE);
                return null;
            }
            int choice = JOptionPane.showConfirmDialog(null,
                    "LifeFlow found damaged local data.\n"
                            + "Restore the latest verified JSON backup?\n\n"
                            + loadFailure.getMessage(),
                    "Recovery Required", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return null;
            }
            try {
                return store.restoreLatestBackup();
            } catch (IOException restoreFailure) {
                JOptionPane.showMessageDialog(null,
                        "LifeFlow could not restore the backup.\n"
                                + restoreFailure.getMessage(),
                        "Recovery Failed", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }
    }

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (ReflectiveOperationException
                 | javax.swing.UnsupportedLookAndFeelException ignored) {
            // The platform default remains available when the cross-platform theme fails.
        }
        UiTheme.install();
    }
}