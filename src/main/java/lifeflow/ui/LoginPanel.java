package lifeflow.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import lifeflow.model.BloodType;
import lifeflow.model.DonorAccount;
import lifeflow.model.Hospital;
import lifeflow.model.exception.LifeFlowException;
import lifeflow.service.AdminAuth;
import lifeflow.service.DonorRegistry;
import lifeflow.service.DonorSignupService;
import lifeflow.service.HospitalRegistry;

/** Sign-in screen for administrators, hospitals, and self-registered donors. */
@SuppressWarnings("serial")
public final class LoginPanel extends JPanel {
    public static final Color BRAND = new Color(0x172033);
    private static final Color BRAND_ACCENT = new Color(0x24314A);

    /** The three account roles offered on the sign-in screen. */
    public enum Role {
        ADMIN("Admin"), HOSPITAL("Hospital"), DONOR("Donor");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final JTextField usernameField = new RoundedField();
    private final JPasswordField passwordField = new RoundedSecretField();
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton togglePassword = new JButton("Show");
    private final JButton signInButton = UiComponents.primaryButton("Sign in");
    private final JLabel heading = new JLabel("Admin Sign In");
    private final JLabel subtitle = new JLabel(
            "Welcome back — enter your credentials to continue.");
    private final JLabel hint = new JLabel(
            "Default credentials — admin / admin123");
    private final JButton adminTab = new JButton("Admin");
    private final JButton hospitalTab = new JButton("Hospital");
    private final JButton donorTab = new JButton("Donor");
    private final JButton createAccountLink = new JButton("Create an account");
    private final JLabel accessLabel = new JLabel("ADMINISTRATOR ACCESS");
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final HospitalRegistry registry;
    private final DonorRegistry donorRegistry;
    private final DonorSignupService signupService;
    private final Consumer<LoginResult> onSuccess;
    private Consumer<String> roleChangeListener = ignored -> { };
    private Role role = Role.ADMIN;

    public LoginPanel(HospitalRegistry registry, DonorRegistry donorRegistry,
                      DonorSignupService signupService,
                      Consumer<LoginResult> onSuccess) {
        super(new BorderLayout());
        this.registry = registry;
        this.donorRegistry = donorRegistry;
        this.signupService = signupService;
        this.onSuccess = onSuccess;
        setOpaque(false);
        setPreferredSize(new Dimension(840, 560));

        BrandPanel brand = new BrandPanel(accessLabel);
        brand.setPreferredSize(new Dimension(280, 560));
        add(brand, BorderLayout.WEST);
        add(buildShell(), BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setColor(UiTheme.SHADOW);
        copy.fillRoundRect(0, 6, getWidth() - 1, getHeight() - 6, 22, 22);
        java.awt.GradientPaint sheen = new java.awt.GradientPaint(
                0, 0, new Color(255, 255, 255, 152),
                0, getHeight(), new Color(245, 248, 253, 128));
        copy.setPaint(sheen);
        copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
        copy.setColor(new Color(229, 234, 242, 190));
        copy.setStroke(new BasicStroke(1f));
        copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
        copy.dispose();
        super.paintComponent(graphics);
    }

    private JPanel buildShell() {
        cardHost.setOpaque(false);
        cardHost.add(buildLoginCard(), "login");
        cardHost.add(buildRegisterCard(), "register");
        cardHost.add(buildDonorRegisterCard(), "registerDonor");

        /* Lock the card host size so switching cards never triggers a resize. */
        Dimension locked = cardHost.getPreferredSize();
        cardHost.setPreferredSize(locked);
        cardHost.setMinimumSize(locked);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.add(Box.createGlue(), BorderLayout.CENTER);
        headerRow.add(new CloseButton(), BorderLayout.EAST);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(headerRow, BorderLayout.NORTH);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(top, BorderLayout.NORTH);
        wrapper.add(cardHost, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildLoginCard() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(28, 48, 24, 48));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        heading.setForeground(UiTheme.NAVY);
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(heading, gbc);

        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        subtitle.setForeground(UiTheme.MUTED);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 20, 0);
        form.add(subtitle, gbc);

        JPanel tabs = new JPanel(new GridLayout(1, 3));
        tabs.setOpaque(false);
        tabs.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER, 1, true));
        tabs.setPreferredSize(new Dimension(300, 44));
        adminTab.setName("adminRoleTab");
        adminTab.setFont(UiTheme.BODY_BOLD);
        adminTab.setForeground(Color.WHITE);
        adminTab.setBackground(UiTheme.NAVY);
        adminTab.setFocusPainted(false);
        adminTab.setContentAreaFilled(true);
        adminTab.setBorder(BorderFactory.createEmptyBorder(9, 0, 9, 0));
        adminTab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        adminTab.addActionListener(event -> setRole(Role.ADMIN));
        hospitalTab.setName("hospitalRoleTab");
        hospitalTab.setFont(UiTheme.BODY_BOLD);
        hospitalTab.setForeground(UiTheme.NAVY);
        hospitalTab.setBackground(Color.WHITE);
        hospitalTab.setFocusPainted(false);
        hospitalTab.setContentAreaFilled(true);
        hospitalTab.setBorder(BorderFactory.createEmptyBorder(9, 0, 9, 0));
        hospitalTab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hospitalTab.addActionListener(event -> setRole(Role.HOSPITAL));
        donorTab.setName("donorRoleTab");
        donorTab.setFont(UiTheme.BODY_BOLD);
        donorTab.setForeground(UiTheme.NAVY);
        donorTab.setBackground(Color.WHITE);
        donorTab.setFocusPainted(false);
        donorTab.setContentAreaFilled(true);
        donorTab.setBorder(BorderFactory.createEmptyBorder(9, 0, 9, 0));
        donorTab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        donorTab.addActionListener(event -> setRole(Role.DONOR));
        tabs.add(adminTab);
        tabs.add(hospitalTab);
        tabs.add(donorTab);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 16, 0);
        form.add(tabs, gbc);

        usernameField.setName("loginUsername");
        usernameField.addActionListener(event -> signIn());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        form.add(fieldLabel("USERNAME"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 13, 0);
        form.add(usernameField, gbc);

        passwordField.setName("loginPassword");
        passwordField.addActionListener(event -> signIn());
        JPanel passwordRow = new JPanel(new BorderLayout(0, 0));
        passwordRow.setOpaque(false);
        togglePassword.setName("togglePassword");
        togglePassword.setFont(UiTheme.BODY_BOLD);
        togglePassword.setForeground(UiTheme.NAVY);
        togglePassword.setBackground(UiTheme.SURFACE);
        togglePassword.setFocusPainted(false);
        togglePassword.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        togglePassword.setPreferredSize(new Dimension(72, 44));
        togglePassword.setContentAreaFilled(false);
        togglePassword.setOpaque(false);
        togglePassword.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 14));
        togglePassword.addActionListener(event -> {
            boolean hidden = passwordField.getEchoChar() != 0;
            passwordField.setEchoChar(hidden ? (char) 0 : '\u2022');
            togglePassword.setText(hidden ? "Hide" : "Show");
        });
        passwordRow.add(passwordField, BorderLayout.CENTER);
        passwordRow.add(togglePassword, BorderLayout.EAST);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        form.add(fieldLabel("PASSWORD"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(passwordRow, gbc);

        errorLabel.setName("loginError");
        errorLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        errorLabel.setForeground(UiTheme.DANGER);
        errorLabel.setPreferredSize(new Dimension(300, 18));
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(errorLabel, gbc);

        signInButton.setName("signInButton");
        signInButton.putClientProperty("hero", true);
        signInButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        signInButton.setPreferredSize(new Dimension(300, 46));
        signInButton.addActionListener(event -> signIn());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(signInButton, gbc);

        createAccountLink.setName("createAccountLink");
        createAccountLink.setFont(UiTheme.BODY_BOLD);
        createAccountLink.setForeground(UiTheme.CORAL_DARK);
        createAccountLink.setContentAreaFilled(false);
        createAccountLink.setOpaque(false);
        createAccountLink.setFocusPainted(false);
        createAccountLink.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        createAccountLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        createAccountLink.addActionListener(event -> cards.show(cardHost,
                role == Role.HOSPITAL ? "register" : "registerDonor"));
        JPanel linkRow = new JPanel(new FlowLayoutLeft());
        linkRow.setOpaque(false);
        linkRow.add(createAccountLink);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        form.add(linkRow, gbc);

        hint.setName("loginHint");
        hint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        hint.setForeground(new Color(0x98A1B2));
        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 0, 0);
        form.add(hint, gbc);
        return form;
    }

    private JPanel buildRegisterCard() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(20, 48, 16, 48));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel registerHeading = new JLabel("Hospital Registration");
        registerHeading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        registerHeading.setForeground(UiTheme.NAVY);
        gbc.insets = new Insets(0, 0, 4, 0);
        form.add(registerHeading, gbc);

        JLabel registerSubtitle = new JLabel(
                "Create an account to place blood requests.");
        registerSubtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        registerSubtitle.setForeground(UiTheme.MUTED);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 16, 0);
        form.add(registerSubtitle, gbc);

        JTextField registerName = new RoundedField();
        registerName.setName("registerName");
        registerName.setPreferredSize(new Dimension(300, 40));
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("HOSPITAL NAME"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(registerName, gbc);

        JTextField registerUsername = new RoundedField();
        registerUsername.setName("registerUsername");
        registerUsername.setPreferredSize(new Dimension(300, 40));
        registerUsername.addActionListener(event -> submitRegistration());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("USERNAME"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(registerUsername, gbc);

        JPasswordField registerPassword = new RoundedSecretField();
        registerPassword.setName("registerPassword");
        registerPassword.setPreferredSize(new Dimension(300, 40));
        registerPassword.addActionListener(event -> submitRegistration());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("PASSWORD (MIN 4 CHARACTERS)"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(registerPassword, gbc);

        JPasswordField registerConfirm = new RoundedSecretField();
        registerConfirm.setName("registerConfirm");
        registerConfirm.setPreferredSize(new Dimension(300, 40));
        registerConfirm.addActionListener(event -> submitRegistration());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("CONFIRM PASSWORD"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(registerConfirm, gbc);

        JLabel registerError = new JLabel(" ");
        registerError.setName("registerError");
        registerError.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        registerError.setForeground(UiTheme.DANGER);
        registerError.setPreferredSize(new Dimension(300, 16));
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(registerError, gbc);

        JButton registerButton = UiComponents.primaryButton("Create account");
        registerButton.setName("registerButton");
        registerButton.putClientProperty("hero", true);
        registerButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        registerButton.setPreferredSize(new Dimension(300, 42));
        registerButton.addActionListener(event -> submitRegistration());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        form.add(registerButton, gbc);

        JButton backButton = UiComponents.secondaryButton("Back to sign in");
        backButton.setName("backToLoginButton");
        backButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        backButton.setPreferredSize(new Dimension(300, 40));
        backButton.addActionListener(event -> cards.show(cardHost, "login"));
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        form.add(backButton, gbc);

        Runnable clear = () -> registerError.setText(" ");
        registerName.getDocument().addDocumentListener(new DocumentChangeAdapter(clear));
        registerUsername.getDocument().addDocumentListener(new DocumentChangeAdapter(clear));
        return form;
    }

    private JPanel buildDonorRegisterCard() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(16, 48, 12, 48));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel registerHeading = new JLabel("Donor Registration");
        registerHeading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        registerHeading.setForeground(UiTheme.NAVY);
        gbc.insets = new Insets(0, 0, 4, 0);
        form.add(registerHeading, gbc);

        JLabel registerSubtitle = new JLabel(
                "Create an account to track your donation status.");
        registerSubtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        registerSubtitle.setForeground(UiTheme.MUTED);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 14, 0);
        form.add(registerSubtitle, gbc);

        JTextField donorName = new RoundedField();
        donorName.setName("donorName");
        donorName.setPreferredSize(new Dimension(300, 40));
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("FULL NAME"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(donorName, gbc);

        JTextField donorAge = new RoundedField();
        donorAge.setName("donorAge");
        donorAge.setPreferredSize(new Dimension(138, 40));
        JTextField donorWeight = new RoundedField();
        donorWeight.setName("donorWeight");
        donorWeight.setPreferredSize(new Dimension(138, 40));
        JPanel measureRow = new JPanel(new GridLayout(1, 2, 10, 0));
        measureRow.setOpaque(false);
        measureRow.add(donorAge);
        measureRow.add(donorWeight);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("AGE / WEIGHT (KG)"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(measureRow, gbc);

        JComboBox<BloodType> donorType = new JComboBox<>(BloodType.values());
        donorType.setName("donorBloodType");
        UiComponents.styleInput(donorType);
        donorType.setPreferredSize(new Dimension(300, 40));
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("BLOOD TYPE"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(donorType, gbc);

        JTextField donorUsername = new RoundedField();
        donorUsername.setName("donorUsername");
        donorUsername.setPreferredSize(new Dimension(300, 40));
        donorUsername.addActionListener(event -> submitDonorRegistration());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("USERNAME"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(donorUsername, gbc);

        JPasswordField donorPassword = new RoundedSecretField();
        donorPassword.setName("donorPassword");
        donorPassword.setPreferredSize(new Dimension(300, 40));
        donorPassword.addActionListener(event -> submitDonorRegistration());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("PASSWORD (MIN 4 CHARACTERS)"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(donorPassword, gbc);

        JPasswordField donorConfirm = new RoundedSecretField();
        donorConfirm.setName("donorConfirm");
        donorConfirm.setPreferredSize(new Dimension(300, 40));
        donorConfirm.addActionListener(event -> submitDonorRegistration());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("CONFIRM PASSWORD"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(donorConfirm, gbc);

        JLabel donorError = new JLabel(" ");
        donorError.setName("donorError");
        donorError.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        donorError.setForeground(UiTheme.DANGER);
        donorError.setPreferredSize(new Dimension(300, 16));
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(donorError, gbc);

        JButton donorRegisterButton = UiComponents.primaryButton("Create account");
        donorRegisterButton.setName("donorRegisterButton");
        donorRegisterButton.putClientProperty("hero", true);
        donorRegisterButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        donorRegisterButton.setPreferredSize(new Dimension(300, 42));
        donorRegisterButton.addActionListener(event -> submitDonorRegistration());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        form.add(donorRegisterButton, gbc);

        JButton donorBackButton = UiComponents.secondaryButton("Back to sign in");
        donorBackButton.setName("donorBackButton");
        donorBackButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        donorBackButton.setPreferredSize(new Dimension(300, 40));
        donorBackButton.addActionListener(event -> cards.show(cardHost, "login"));
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        form.add(donorBackButton, gbc);

        Runnable clear = () -> donorError.setText(" ");
        donorName.getDocument().addDocumentListener(new DocumentChangeAdapter(clear));
        donorUsername.getDocument().addDocumentListener(new DocumentChangeAdapter(clear));
        return form;
    }

    private void submitRegistration() {
        JTextField name = findField("registerName");
        JTextField username = findField("registerUsername");
        JPasswordField password = findFieldPassword("registerPassword");
        JPasswordField confirm = findFieldPassword("registerConfirm");
        JLabel error = findLabel("registerError");
        try {
            if (!new String(password.getPassword())
                    .equals(new String(confirm.getPassword()))) {
                error.setText("Passwords do not match.");
                return;
            }
            Hospital hospital = registry.register(name.getText(),
                    username.getText(), new String(password.getPassword()));
            error.setText(" ");
            onSuccess.accept(LoginResult.hospitalSession(hospital));
        } catch (LifeFlowException | IOException exception) {
            error.setText(exception.getMessage());
        }
    }

    private void submitDonorRegistration() {
        JTextField name = findField("donorName");
        JTextField age = findField("donorAge");
        JTextField weight = findField("donorWeight");
        JComboBox<?> type = (JComboBox<?>) findComponent("donorBloodType");
        JTextField username = findField("donorUsername");
        JPasswordField password = findFieldPassword("donorPassword");
        JPasswordField confirm = findFieldPassword("donorConfirm");
        JLabel error = findLabel("donorError");
        try {
            if (!new String(password.getPassword())
                    .equals(new String(confirm.getPassword()))) {
                error.setText("Passwords do not match.");
                return;
            }
            int donorAge = Integer.parseInt(age.getText().trim());
            double donorWeight = Double.parseDouble(weight.getText().trim());
            if (name.getText().trim().isEmpty()) {
                error.setText("Full name is required.");
                return;
            }
            DonorAccount account = signupService.signup(name.getText(),
                    donorAge, donorWeight, (BloodType) type.getSelectedItem(),
                    username.getText(), new String(password.getPassword()));
            error.setText(" ");
            onSuccess.accept(LoginResult.donorSession(account));
        } catch (NumberFormatException exception) {
            error.setText("Age and weight must be valid numbers.");
        } catch (LifeFlowException | IOException exception) {
            error.setText(exception.getMessage());
        }
    }

    private JTextField findField(String name) {
        return (JTextField) findComponent(name);
    }

    private JPasswordField findFieldPassword(String name) {
        return (JPasswordField) findComponent(name);
    }

    private JLabel findLabel(String name) {
        return (JLabel) findComponent(name);
    }

    private java.awt.Component findComponent(String name) {
        return findIn(cardHost, name);
    }

    private static java.awt.Component findIn(java.awt.Component component,
                                             String name) {
        if (name.equals(component.getName())) {
            return component;
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                java.awt.Component match = findIn(child, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    /** Legacy switch between the admin and hospital role views. */
    public void setRole(boolean admin) {
        setRole(admin ? Role.ADMIN : Role.HOSPITAL);
    }

    /** Switches the sign-in screen to the given role view. */
    public void setRole(Role selected) {
        role = selected;
        adminTab.setBackground(selected == Role.ADMIN ? UiTheme.NAVY : Color.WHITE);
        adminTab.setForeground(selected == Role.ADMIN ? Color.WHITE : UiTheme.NAVY);
        hospitalTab.setBackground(selected == Role.HOSPITAL ? UiTheme.NAVY : Color.WHITE);
        hospitalTab.setForeground(selected == Role.HOSPITAL ? Color.WHITE : UiTheme.NAVY);
        donorTab.setBackground(selected == Role.DONOR ? UiTheme.NAVY : Color.WHITE);
        donorTab.setForeground(selected == Role.DONOR ? Color.WHITE : UiTheme.NAVY);
        heading.setText(selected == Role.ADMIN ? "Admin Sign In"
                : selected == Role.HOSPITAL ? "Hospital Sign In" : "Donor Sign In");
        subtitle.setText(selected == Role.ADMIN
                ? "Welcome back — enter your credentials to continue."
                : selected == Role.HOSPITAL
                ? "Sign in with your hospital account to place requests."
                : "Sign in to view your donation status and history.");
        hint.setText(selected == Role.ADMIN
                ? "Default credentials — admin / admin123"
                : selected == Role.HOSPITAL
                ? "New here? Create your hospital account below."
                : "New here? Create your donor account below.");
        accessLabel.setText(selected == Role.ADMIN ? "ADMINISTRATOR ACCESS"
                : selected == Role.HOSPITAL ? "HOSPITAL PORTAL" : "DONOR PORTAL");
        createAccountLink.setForeground(selected == Role.ADMIN
                ? new Color(0, 0, 0, 0) : UiTheme.CORAL_DARK);
        createAccountLink.setEnabled(selected != Role.ADMIN);
        errorLabel.setText(" ");
        passwordField.setText("");
        roleChangeListener.accept(selected.label());
    }

    public void setDonorRole() {
        setRole(Role.DONOR);
    }

    public Role getRole() {
        return role;
    }

    public void setRoleChangeListener(Consumer<String> listener) {
        roleChangeListener = listener == null ? ignored -> { } : listener;
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        label.setForeground(UiTheme.MUTED);
        return label;
    }

    /** Validates the fields; on success runs the callback, otherwise shows an error. */
    public void signIn() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        boolean ok;
        if (role == Role.ADMIN) {
            ok = AdminAuth.authenticate(username, password);
        } else if (role == Role.HOSPITAL) {
            ok = registry.authenticate(username, password) != null;
        } else {
            ok = donorRegistry.authenticate(username, password) != null;
        }
        if (ok) {
            errorLabel.setText(" ");
            LoginResult outcome;
            if (role == Role.ADMIN) {
                outcome = LoginResult.adminSession();
            } else if (role == Role.HOSPITAL) {
                outcome = LoginResult.hospitalSession(
                        registry.authenticate(username, password));
            } else {
                outcome = LoginResult.donorSession(
                        donorRegistry.authenticate(username, password));
            }
            onSuccess.accept(outcome);
        } else {
            errorLabel.setText("Invalid username or password. Please try again.");
            passwordField.setText("");
            passwordField.requestFocusInWindow();
        }
    }

    public JTextField getUsernameField() {
        return usernameField;
    }

    public JPasswordField getPasswordField() {
        return passwordField;
    }

    public JButton getSignInButton() {
        return signInButton;
    }

    public JLabel getErrorLabel() {
        return errorLabel;
    }

    public boolean isAdminRole() {
        return role == Role.ADMIN;
    }

    private static final class FlowLayoutLeft extends java.awt.FlowLayout {
        private static final long serialVersionUID = 1L;

        private FlowLayoutLeft() {
            super(java.awt.FlowLayout.LEFT, 0, 0);
        }
    }

    /** Rounded text field with a soft border that reacts to hover and focus. */
    private static class RoundedField extends JTextField {
        private static final long serialVersionUID = 1L;

        private RoundedField() {
            setFont(UiTheme.BODY);
            setForeground(UiTheme.NAVY);
            setCaretColor(UiTheme.CORAL_DARK);
            setPreferredSize(new Dimension(300, 44));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
            setSelectionColor(new Color(239, 71, 111, 90));
            setSelectedTextColor(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focused = hasFocus();
            boolean hovered = getMousePosition() != null;
            copy.setColor(new Color(255, 255, 255, 235));
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            copy.setColor(focused ? UiTheme.CORAL
                    : hovered ? new Color(0xB9C4D6) : UiTheme.BORDER);
            copy.setStroke(new BasicStroke(focused ? 1.6f : 1f));
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            copy.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundedSecretField extends JPasswordField {
        private static final long serialVersionUID = 1L;

        private RoundedSecretField() {
            setFont(UiTheme.BODY);
            setForeground(UiTheme.NAVY);
            setCaretColor(UiTheme.CORAL_DARK);
            setPreferredSize(new Dimension(300, 44));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
            setSelectionColor(new Color(239, 71, 111, 90));
            setSelectedTextColor(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focused = hasFocus();
            boolean hovered = getMousePosition() != null;
            copy.setColor(new Color(255, 255, 255, 235));
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            copy.setColor(focused ? UiTheme.CORAL
                    : hovered ? new Color(0xB9C4D6) : UiTheme.BORDER);
            copy.setStroke(new BasicStroke(focused ? 1.6f : 1f));
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            copy.dispose();
            super.paintComponent(graphics);
        }
    }

    /** Minimal document listener that reuses a single runnable. */
    private static final class DocumentChangeAdapter
            implements javax.swing.event.DocumentListener {
        private final Runnable onChange;

        private DocumentChangeAdapter(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }
    }

    private static final class BrandPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private BrandPanel(JLabel accessLabel) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            add(Box.createVerticalStrut(54));
            JLabel badge = new JLabel();
            badge.setPreferredSize(new Dimension(68, 68));
            badge.setMaximumSize(new Dimension(68, 68));
            badge.setAlignmentX(CENTER_ALIGNMENT);
            add(badge);
            add(Box.createVerticalStrut(18));
            JLabel name = new JLabel("LifeFlow");
            name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            name.setForeground(Color.WHITE);
            name.setAlignmentX(CENTER_ALIGNMENT);
            add(name);
            add(Box.createVerticalStrut(6));
            JLabel tagline = new JLabel("<html><div style='text-align:center'>"
                    + "Blood Donation &amp;<br>Emergency Matching</div></html>");
            tagline.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            tagline.setForeground(UiTheme.SIDEBAR_MUTED);
            tagline.setAlignmentX(CENTER_ALIGNMENT);
            add(tagline);
            add(Box.createVerticalGlue());
            accessLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
            accessLabel.setForeground(new Color(0x8D98AD));
            accessLabel.setHorizontalAlignment(SwingConstants.CENTER);
            accessLabel.setAlignmentX(CENTER_ALIGNMENT);
            add(accessLabel);
            add(Box.createVerticalStrut(30));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            Path2D shape = new Path2D.Double();
            shape.moveTo(24, 0);
            shape.lineTo(getWidth(), 0);
            shape.lineTo(getWidth(), getHeight());
            shape.lineTo(24, getHeight());
            shape.quadTo(0, getHeight() - 24, 0, getHeight() - 24);
            shape.lineTo(0, 24);
            shape.quadTo(0, 0, 24, 0);
            shape.closePath();
            copy.setColor(BRAND);
            copy.fill(shape);

            copy.setColor(new Color(0x2B3A55));
            copy.setStroke(new BasicStroke(1.2f));
            copy.draw(new java.awt.geom.Ellipse2D.Double(
                    getWidth() - 74, getHeight() - 150, 150, 150));
            copy.draw(new java.awt.geom.Ellipse2D.Double(
                    getWidth() - 128, getHeight() - 70, 110, 110));
            copy.setColor(new Color(239, 71, 111, 90));
            copy.setStroke(new BasicStroke(2.2f));
            copy.draw(new java.awt.geom.Ellipse2D.Double(
                    getWidth() - 52, getHeight() - 116, 46, 46));

            paintDrop(copy, getWidth() / 2, 88, 17, UiTheme.CORAL);
            copy.dispose();
            super.paintComponent(graphics);
        }

        private static void paintDrop(Graphics2D graphics, int cx, int cy, int r,
                                      Color color) {
            Path2D drop = new Path2D.Double();
            drop.moveTo(cx, cy - r);
            drop.curveTo(cx + r, cy - r * 0.35, cx + r * 0.55, cy + r,
                    cx, cy + r);
            drop.curveTo(cx - r * 0.55, cy + r, cx - r, cy - r * 0.35,
                    cx, cy - r);
            drop.closePath();
            graphics.setColor(color);
            graphics.fill(drop);
        }
    }

    private static final class CloseButton extends JButton {
        private static final long serialVersionUID = 1L;

        private CloseButton() {
            setContentAreaFilled(false);
            setOpaque(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            setPreferredSize(new Dimension(28, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Cancel");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isRollover()) {
                copy.setColor(UiTheme.CORAL_LIGHT);
                copy.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            }
            copy.setColor(getModel().isRollover() ? UiTheme.DANGER
                    : new Color(0x98A1B2));
            copy.setStroke(new BasicStroke(1.8f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int m = 9;
            copy.drawLine(m, m, getWidth() - m, getHeight() - m);
            copy.drawLine(getWidth() - m, m, m, getHeight() - m);
            copy.dispose();
        }
    }
}