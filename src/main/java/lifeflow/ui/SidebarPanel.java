package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Dense sidebar with one unambiguous active destination. */
@SuppressWarnings("serial")
public final class SidebarPanel extends JPanel {
    private static final String[] PAGES = {
        "Dashboard", "Donors", "Blood Inventory", "Blood Requests", "Matching", "Reports"
    };
    private static final String[] LABELS = {
        "Overview", "Donors", "Inventory", "Requests", "Matching", "Reports"
    };

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
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(20, 8, 12, 8));

        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 4, 16, 4));
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
        drop.setPreferredSize(new Dimension(30, 30));
        brand.add(drop, BorderLayout.WEST);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("LifeFlow");
        name.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 20));
        name.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Blood operations console");
        subtitle.setFont(UiTheme.SMALL);
        subtitle.setForeground(new Color(0xFF6E90));
        titles.add(name);
        titles.add(Box.createVerticalStrut(2));
        titles.add(subtitle);
        brand.add(titles, BorderLayout.CENTER);
        content.add(brand);

        JLabel section = new JLabel("WORKSPACE");
        section.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 10));
        section.setForeground(UiTheme.SIDEBAR_MUTED);
        section.setBorder(BorderFactory.createEmptyBorder(15, 6, 8, 0));
        content.add(section);

        for (int index = 0; index < PAGES.length; index++) {
            String page = PAGES[index];
            JButton button = UiComponents.navButton(LABELS[index]);
            button.setIcon(new NavigationIcon(page, UiTheme.SIDEBAR_MUTED));
            button.setIconTextGap(10);
            button.addActionListener(event -> handler.accept(page));
            navigation.put(page, button);
            content.add(button);
            content.add(Box.createVerticalStrut(3));
        }
        return content;
    }

    private JPanel buildNotice() {
        JPanel notice = new JPanel();
        notice.setOpaque(false);
        notice.setLayout(new BoxLayout(notice, BoxLayout.Y_AXIS));
        notice.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        new Color(0x2D374B)),
                BorderFactory.createEmptyBorder(14, 12, 16, 12)));
        JLabel title = new JLabel("Educational simulation");
        title.setFont(UiTheme.SMALL);
        title.setForeground(new Color(0xFF819D));
        JLabel detail = new JLabel("Local data · non-clinical");
        detail.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.PLAIN, 10));
        detail.setForeground(UiTheme.SIDEBAR_MUTED);
        notice.add(title);
        notice.add(Box.createVerticalStrut(4));
        notice.add(detail);
        notice.add(Box.createVerticalStrut(12));

        JButton signOut = UiComponents.signOutButton("Sign out");
        signOut.setName("signOutButton");
        signOut.setIcon(new LogoutIcon(UiTheme.SIDEBAR_MUTED));
        signOut.setIconTextGap(10);
        signOut.addActionListener(event -> signOutHandler.run());
        notice.add(signOut);
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
                    ? java.awt.Color.WHITE : UiTheme.SIDEBAR_MUTED));
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
