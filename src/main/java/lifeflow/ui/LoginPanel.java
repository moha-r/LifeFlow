package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import lifeflow.model.Hospital;
import lifeflow.service.AdminAuth;
import lifeflow.service.HospitalRegistry;
import lifeflow.model.exception.LifeFlowException;

/** Sign-in screen for administrators and self-registered hospitals. */
@SuppressWarnings("serial")
public final class LoginPanel extends JPanel {
    public static final Color BRAND = new Color(0x172033);
    private static final Color BRAND_ACCENT = new Color(0x24314A);

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
    private final JButton createAccountLink = new JButton("Create an account");
    private final JLabel accessLabel = new JLabel("ADMINISTRATOR ACCESS");
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final HospitalRegistry registry;
    private final Consumer<LoginResult> onSuccess;
    private Consumer<String> roleChangeListener = ignored -> { };
    private boolean adminRole = true;

    public LoginPanel(HospitalRegistry registry, Consumer<LoginResult> onSuccess) {
        super(new BorderLayout());
        this.registry = registry;
        this.onSuccess = onSuccess;
        setOpaque(false);
        setPreferredSize(new Dimension(840, 530));

        BrandPanel brand = new BrandPanel(accessLabel);
        brand.setPreferredSize(new Dimension(280, 530));
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

        JPanel tabs = new JPanel(new java.awt.GridLayout(1, 2));
        tabs.setOpaque(false);
        tabs.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER, 1, true));
        tabs.setPreferredSize(new Dimension(300, 42));
        adminTab.setName("adminRoleTab");
        adminTab.setFont(UiTheme.BODY_BOLD);
        adminTab.setForeground(Color.WHITE);
        adminTab.setBackground(UiTheme.NAVY);
        adminTab.setFocusPainted(false);
        adminTab.setContentAreaFilled(true);
        adminTab.setBorder(BorderFactory.createEmptyBorder(9, 0, 9, 0));
        adminTab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        adminTab.addActionListener(event -> setRole(true));
        hospitalTab.setName("hospitalRoleTab");
        hospitalTab.setFont(UiTheme.BODY_BOLD);
        hospitalTab.setForeground(UiTheme.NAVY);
        hospitalTab.setBackground(Color.WHITE);
        hospitalTab.setFocusPainted(false);
        hospitalTab.setContentAreaFilled(true);
        hospitalTab.setBorder(BorderFactory.createEmptyBorder(9, 0, 9, 0));
        hospitalTab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hospitalTab.addActionListener(event -> setRole(false));
        tabs.add(adminTab);
        tabs.add(hospitalTab);
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
        createAccountLink.addActionListener(event -> cards.show(cardHost, "register"));
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
        registerName.setPreferredSize(new Dimension(300, 38));
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("HOSPITAL NAME"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(registerName, gbc);

        JTextField registerUsername = new RoundedField();
        registerUsername.setName("registerUsername");
        registerUsername.setPreferredSize(new Dimension(300, 38));
        registerUsername.addActionListener(event -> submitRegistration());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("USERNAME"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(registerUsername, gbc);

        JPasswordField registerPassword = new RoundedSecretField();
        registerPassword.setName("registerPassword");
        registerPassword.setPreferredSize(new Dimension(300, 38));
        registerPassword.addActionListener(event -> submitRegistration());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 3, 0);
        form.add(fieldLabel("PASSWORD (MIN 4 CHARACTERS)"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        form.add(registerPassword, gbc);

        JPasswordField registerConfirm = new RoundedSecretField();
        registerConfirm.setName("registerConfirm");
        registerConfirm.setPreferredSize(new Dimension(300, 38));
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

    /** Switches between the admin and hospital role views. */
    public void setRole(boolean admin) {
        adminRole = admin;
        adminTab.setBackground(admin ? UiTheme.NAVY : Color.WHITE);
        adminTab.setForeground(admin ? Color.WHITE : UiTheme.NAVY);
        hospitalTab.setBackground(admin ? Color.WHITE : UiTheme.NAVY);
        hospitalTab.setForeground(admin ? UiTheme.NAVY : Color.WHITE);
        heading.setText(admin ? "Admin Sign In" : "Hospital Sign In");
        subtitle.setText(admin
                ? "Welcome back — enter your credentials to continue."
                : "Sign in with your hospital account to place requests.");
        hint.setText(admin ? "Default credentials — admin / admin123"
                : "New here? Create your hospital account below.");
        accessLabel.setText(admin ? "ADMINISTRATOR ACCESS" : "HOSPITAL PORTAL");
        createAccountLink.setForeground(admin ? new Color(0, 0, 0, 0) : UiTheme.CORAL_DARK);
        createAccountLink.setEnabled(!admin);
        errorLabel.setText(" ");
        passwordField.setText("");
        roleChangeListener.accept(admin ? "Admin" : "Hospital");
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
        boolean ok = adminRole
                ? AdminAuth.authenticate(username, password)
                : registry.authenticate(username, password) != null;
        if (ok) {
            errorLabel.setText(" ");
            if (adminRole) {
                onSuccess.accept(LoginResult.adminSession());
            } else {
                onSuccess.accept(LoginResult.hospitalSession(
                        registry.authenticate(username, password)));
            }
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
        return adminRole;
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