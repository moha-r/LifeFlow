package lifeflow;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lifeflow.model.DonorAccount;
import lifeflow.model.Hospital;
import lifeflow.model.LifeFlowState;
import lifeflow.persistence.JsonDonorStore;
import lifeflow.persistence.JsonHospitalStore;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.persistence.StoragePaths;
import lifeflow.service.DonorRegistry;
import lifeflow.service.DonorSignupService;
import lifeflow.service.HospitalRegistry;
import lifeflow.ui.DonorPortalFrame;
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
            HospitalRegistry registry = createHospitalRegistry();
            DonorRegistry donorRegistry = createDonorRegistry();
            DonorSignupService signupService = createSignupService(donorRegistry);
            if (registry == null || donorRegistry == null || signupService == null) {
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
                    openHospitalSession(registry, donorRegistry, signupService,
                            hospital, appEnded);
                }

                @Override
                public void openDonorPortal(DonorAccount donor) {
                    openDonorSession(registry, donorRegistry, signupService,
                            donor, appEnded);
                }

                @Override
                public void openAdminWorkspace() {
                    openAdminSession(registry, donorRegistry, signupService,
                            appEnded);
                }

                @Override
                public void showLogin() {
                    route(registry, donorRegistry, signupService, appEnded);
                }
            };
            LoginResult result = LoginDialog.showAndAuthenticate(registry,
                    donorRegistry, signupService);
            if (result == null) {
                appEnded.countDown();
                return;
            }
            routeResult(registry, donorRegistry, signupService, result, appEnded);
        });
        try {
            appEnded.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }

    private static HospitalRegistry createHospitalRegistry() {
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

    private static DonorRegistry createDonorRegistry() {
        try {
            JsonDonorStore store = new JsonDonorStore(StoragePaths.resolve());
            return new DonorRegistry(store.load(), store);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(null,
                    "LifeFlow could not read its donor accounts.\n"
                            + exception.getMessage(),
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private static DonorSignupService createSignupService(DonorRegistry registry) {
        if (registry == null) {
            return null;
        }
        return new DonorSignupService(registry, StoragePaths.resolve());
    }

    /** Shows the sign-in dialog and routes; ends the app when cancelled. */
    private static void route(HospitalRegistry registry,
                              DonorRegistry donorRegistry,
                              DonorSignupService signupService,
                              CountDownLatch appEnded) {
        LoginResult outcome = LoginDialog.showAndAuthenticate(registry,
                donorRegistry, signupService);
        if (outcome == null) {
            appEnded.countDown();
            return;
        }
        routeResult(registry, donorRegistry, signupService, outcome, appEnded);
    }

    private static void routeResult(HospitalRegistry registry,
                                    DonorRegistry donorRegistry,
                                    DonorSignupService signupService,
                                    LoginResult outcome,
                                    CountDownLatch appEnded) {
        if (outcome.admin()) {
            openAdminSession(registry, donorRegistry, signupService, appEnded);
        } else if (outcome.hospital() != null) {
            openHospitalSession(registry, donorRegistry, signupService,
                    outcome.hospital(), appEnded);
        } else {
            openDonorSession(registry, donorRegistry, signupService,
                    outcome.donor(), appEnded);
        }
    }

    private static void openAdminSession(HospitalRegistry registry,
                                         DonorRegistry donorRegistry,
                                         DonorSignupService signupService,
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
                            openHospitalSession(registry, donorRegistry,
                                    signupService, hospital, appEnded);
                        }

                        @Override
                        public void openDonorPortal(DonorAccount donor) {
                            openDonorSession(registry, donorRegistry,
                                    signupService, donor, appEnded);
                        }

                        @Override
                        public void openAdminWorkspace() {
                            // Already on the admin workspace.
                        }

                        @Override
                        public void showLogin() {
                            route(registry, donorRegistry, signupService, appEnded);
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
                                         DonorRegistry donorRegistry,
                                         DonorSignupService signupService,
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
                            openHospitalSession(registry, donorRegistry,
                                    signupService, other, appEnded);
                        }

                        @Override
                        public void openDonorPortal(DonorAccount donor) {
                            openDonorSession(registry, donorRegistry,
                                    signupService, donor, appEnded);
                        }

                        @Override
                        public void openAdminWorkspace() {
                            openAdminSession(registry, donorRegistry,
                                    signupService, appEnded);
                        }

                        @Override
                        public void showLogin() {
                            route(registry, donorRegistry, signupService, appEnded);
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

    private static void openDonorSession(HospitalRegistry registry,
                                         DonorRegistry donorRegistry,
                                         DonorSignupService signupService,
                                         DonorAccount donor,
                                         CountDownLatch appEnded) {
        JsonLifeFlowStore store = null;
        try {
            store = new JsonLifeFlowStore(StoragePaths.resolve());
            LifeFlowState state = loadState(store);
            if (state == null) {
                store.close();
                return;
            }
            DonorPortalFrame frame = new DonorPortalFrame(state, store,
                    donor, donorRegistry, registry, new SessionSwitcher() {
                        @Override
                        public void exitApplication() {
                            appEnded.countDown();
                        }

                        @Override
                        public void openHospitalPortal(Hospital hospital) {
                            openHospitalSession(registry, donorRegistry,
                                    signupService, hospital, appEnded);
                        }

                        @Override
                        public void openDonorPortal(DonorAccount other) {
                            openDonorSession(registry, donorRegistry,
                                    signupService, other, appEnded);
                        }

                        @Override
                        public void openAdminWorkspace() {
                            openAdminSession(registry, donorRegistry,
                                    signupService, appEnded);
                        }

                        @Override
                        public void showLogin() {
                            route(registry, donorRegistry, signupService, appEnded);
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