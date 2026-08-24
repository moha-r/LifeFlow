package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Compact, grouped sidebar with one unambiguous active destination. */
@SuppressWarnings("serial")
public final class SidebarPanel extends JPanel {
    private static final String[] PAGES = {
        "Dashboard", "Donors", "Blood Inventory", "Blood Requests", "Matching",
        "Reports", "Appointments", "Donation Centers"
    };
    private static final String[] LABELS = {
        "Overview", "Donors", "Inventory", "Requests", "Matching", "Reports",
        "Appointments", "Donation Centers"
    };
    private static final int H_GAP = 16;
    private static final int[] OPERATIONS = {0, 2, 3, 4};
    private static final int[] MANAGEMENT = {1, 6, 7, 5};

    private final Map<String, JButton> navigation = new LinkedHashMap<>();
    private Runnable signOutHandler = () -> { };

    public SidebarPanel(Consumer<String> navigationHandler) {
        super(new BorderLayout());
        setName("sidebar");
        setBackground(UiTheme.SIDEBAR);
        setPreferredSize(new Dimension(UiTheme.SIDEBAR_WIDTH, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1,
                new Color(0x2D374B)));
        add(buildNavigation(navigationHandler), BorderLayout.NORTH);
        add(buildNotice(), BorderLayout.SOUTH);
        showActive("Dashboard");
    }

    private JPanel buildNavigation(Consumer<String> handler) {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(16, H_GAP, 12, H_GAP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel brand = new JPanel(new BorderLayout(9, 0));
        brand.setOpaque(false);
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 22, 0);
        content.add(brand, gbc);

        JLabel drop = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D copy = (Graphics2D) g.create();
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int r = 10;
                Path2D shape = new Path2D.Double();
                shape.moveTo(cx, cy - r);
                shape.curveTo(cx + r, cy - r * 0.35, cx + r * 0.55, cy + r,
                        cx, cy + r);
                shape.curveTo(cx - r * 0.55, cy + r, cx - r, cy - r * 0.35,
                        cx, cy - r);
                shape.closePath();
                copy.setColor(UiTheme.CORAL);
                copy.fill(shape);
                copy.dispose();
            }
        };
        drop.setPreferredSize(new Dimension(26, 26));
        brand.add(drop, BorderLayout.WEST);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new javax.swing.BoxLayout(titles,
                javax.swing.BoxLayout.Y_AXIS));
        JLabel name = new JLabel("LifeFlow");
        name.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 18));
        name.setForeground(Color.WHITE);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Blood operations console");
        subtitle.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.PLAIN, 11));
        subtitle.setForeground(new Color(0xFF6E90));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(name);
        titles.add(javax.swing.Box.createVerticalStrut(2));
        titles.add(subtitle);
        brand.add(titles, BorderLayout.CENTER);

        JPanel operations = buildNavigationGroup("OPERATIONS",
                "operationsNavigation", OPERATIONS, handler);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 20, 0);
        content.add(operations, gbc);

        JPanel management = buildNavigationGroup("MANAGEMENT",
                "managementNavigation", MANAGEMENT, handler);
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 0, 0);
        content.add(management, gbc);
        return content;
    }

    private JPanel buildNavigationGroup(String title, String name,
                                        int[] indexes,
                                        Consumer<String> handler) {
        JPanel group = new JPanel(new GridBagLayout());
        group.setName(name);
        group.setOpaque(false);
        GridBagConstraints item = new GridBagConstraints();
        item.gridx = 0;
        item.weightx = 1;
        item.fill = GridBagConstraints.HORIZONTAL;

        JLabel section = new JLabel(title);
        section.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 10));
        section.setForeground(UiTheme.SIDEBAR_MUTED);
        item.gridy = 0;
        item.insets = new Insets(0, 8, 7, 8);
        group.add(section, item);

        for (int position = 0; position < indexes.length; position++) {
            int index = indexes[position];
            String page = PAGES[index];
            JButton button = UiComponents.navButton(LABELS[index]);
            button.setIcon(new NavigationIcon(page, UiTheme.SIDEBAR_MUTED));
            button.setIconTextGap(11);
            button.addActionListener(event -> handler.accept(page));
            navigation.put(page, button);
            item.gridy = position + 1;
            item.insets = new Insets(0, 0, 3, 0);
            group.add(button, item);
        }
        return group;
    }

    private JPanel buildNotice() {
        JPanel notice = new JPanel(new BorderLayout(0, 10));
        notice.setOpaque(false);
        notice.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        new Color(0x2D374B)),
                BorderFactory.createEmptyBorder(12, H_GAP, 14, H_GAP)));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new javax.swing.BoxLayout(copy,
                javax.swing.BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Educational simulation");
        title.setFont(UiTheme.SMALL);
        title.setForeground(new Color(0xFF819D));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel detail = new JLabel("Local data · non-clinical");
        detail.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.PLAIN, 10));
        detail.setForeground(UiTheme.SIDEBAR_MUTED);
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.add(title);
        copy.add(javax.swing.Box.createVerticalStrut(4));
        copy.add(detail);
        notice.add(copy, BorderLayout.CENTER);

        JButton signOut = UiComponents.signOutButton("Sign out");
        signOut.setName("signOutButton");
        signOut.setIcon(new LogoutIcon(UiTheme.SIDEBAR_MUTED));
        signOut.setIconTextGap(12);
        signOut.addActionListener(event -> signOutHandler.run());
        signOut.setPreferredSize(new Dimension(200, 38));
        notice.add(signOut, BorderLayout.SOUTH);
        return notice;
    }

    public void setSignOutHandler(Runnable handler) {
        this.signOutHandler = handler;
    }

    public void showActive(String page) {
        for (Map.Entry<String, JButton> entry : navigation.entrySet()) {
            boolean active = entry.getKey().equals(page);
            JButton button = entry.getValue();
            UiComponents.setNavActive(button, active);
            button.setIcon(new NavigationIcon(entry.getKey(), active
                    ? Color.WHITE : UiTheme.SIDEBAR_MUTED));
        }
    }

    public int getActiveCount() {
        int count = 0;
        for (JButton button : navigation.values()) {
            if (Boolean.TRUE.equals(button.getClientProperty("navActive"))) {
                count++;
            }
        }
        return count;
    }

    public boolean isActive(String page) {
        JButton button = navigation.get(page);
        return button != null
                && Boolean.TRUE.equals(button.getClientProperty("navActive"));
    }

    private static final class LogoutIcon implements javax.swing.Icon {
        private final java.awt.Color color;

        private LogoutIcon(java.awt.Color color) {
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

        @Override
        public void paintIcon(java.awt.Component component, java.awt.Graphics graphics,
                              int x, int y) {
            java.awt.Graphics2D copy = (java.awt.Graphics2D) graphics.create();
            copy.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(color);
            copy.setStroke(new java.awt.BasicStroke(1.6f,
                    java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            copy.drawLine(x + 2, y + 4, x + 2, y + 12);
            copy.drawLine(x + 2, y + 4, x + 12, y + 4);
            copy.drawLine(x + 2, y + 12, x + 12, y + 12);
            copy.drawLine(x + 5, y + 8, x + 13, y + 8);
            copy.drawLine(x + 10, y + 5, x + 13, y + 8);
            copy.drawLine(x + 10, y + 11, x + 13, y + 8);
            copy.dispose();
        }
    }
}
