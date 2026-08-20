package lifeflow.ui;

import java.awt.BasicStroke;
import java.awt.event.ActionEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Small reusable Swing factories that keep pages visually consistent. */
public final class UiComponents {
    private UiComponents() {
    }

    public static JPanel card(LayoutManager layout) {
        RoundedPanel panel = new RoundedPanel(layout, 16);
        panel.setBackground(UiTheme.SURFACE);
        panel.setShadow(true);
        panel.setBorder(new EmptyBorder(18, 18, 18, 18));
        return panel;
    }

    public static JPanel densePanel(LayoutManager layout) {
        RoundedPanel panel = new RoundedPanel(layout, 12);
        panel.setBackground(UiTheme.SURFACE);
        panel.setShadow(true);
        panel.setBorder(new EmptyBorder(0, 0, 0, 0));
        return panel;
    }

    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.TITLE);
        label.setForeground(UiTheme.NAVY);
        return label;
    }

    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.HEADING);
        label.setForeground(UiTheme.NAVY);
        return label;
    }

    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.SMALL);
        label.setForeground(UiTheme.MUTED);
        return label;
    }

    public static JButton primaryButton(String text) {
        return new ModernButton(text, UiTheme.CORAL, Color.WHITE, UiTheme.CORAL_DARK);
    }

    public static JButton secondaryButton(String text) {
        ModernButton button = new ModernButton(text, UiTheme.SURFACE,
                UiTheme.NAVY, new Color(0xF1F3F8));
        button.putClientProperty("outlined", true);
        return button;
    }

    public static JButton navButton(String text) {
        ModernButton button = new ModernButton(text, UiTheme.SIDEBAR,
                UiTheme.SIDEBAR_TEXT, UiTheme.SIDEBAR_HOVER);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(new EmptyBorder(0, 10, 0, 10));
        button.setPreferredSize(new Dimension(244, 42));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return button;
    }

    public static JButton signOutButton(String text) {
        ModernButton button = new ModernButton(text, UiTheme.SIDEBAR,
                UiTheme.SIDEBAR_TEXT, UiTheme.CORAL_DARK);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(new EmptyBorder(0, 10, 0, 10));
        button.setPreferredSize(new Dimension(244, 42));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return button;
    }

    public static void setNavActive(JButton button, boolean active) {
        button.putClientProperty("navActive", active);
        button.setForeground(active ? Color.WHITE : UiTheme.SIDEBAR_TEXT);
        button.repaint();
    }

    public static JTextField searchField(String tooltip) {
        JTextField field = new JTextField();
        field.setName("searchField");
        field.setToolTipText(tooltip);
        field.setPreferredSize(new Dimension(250, 34));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(7, 12, 7, 12)));
        installPlaceholder(field, "Search " + tooltip.toLowerCase()
                .replace("search ", "") + "…");
        return field;
    }

    public static String searchValue(JTextField field) {
        return Boolean.TRUE.equals(field.getClientProperty("placeholderVisible"))
                ? "" : field.getText().trim();
    }

    private static void installPlaceholder(JTextField field, String placeholder) {
        Runnable show = () -> {
            field.setText(placeholder);
            field.setForeground(new Color(0x9AA4B6));
            field.putClientProperty("placeholderVisible", true);
        };
        show.run();
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                if (Boolean.TRUE.equals(field.getClientProperty("placeholderVisible"))) {
                    field.setText("");
                    field.setForeground(UiTheme.NAVY);
                    field.putClientProperty("placeholderVisible", false);
                }
            }

            @Override
            public void focusLost(FocusEvent event) {
                if (field.getText().isBlank()) {
                    show.run();
                }
            }
        });
    }

    public static void styleInput(JComponent input) {
        input.setPreferredSize(new Dimension(260, 38));
        input.setFont(UiTheme.BODY);
        if (input instanceof JTextField textField) {
            textField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.BORDER),
                    new EmptyBorder(7, 11, 7, 11)));
        } else if (input instanceof JComboBox<?>) {
            input.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        }
    }

    public static JScrollPane tableScroll(JTable table) {
        configureTable(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(0, 0, 0, 0)));
        scroll.setCorner(JScrollPane.UPPER_RIGHT_CORNER,
                new HeaderCorner());
        scroll.getViewport().setBackground(UiTheme.SURFACE);
        return scroll;
    }

    public static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public static void configureDialogKeys(JDialog dialog, JButton save,
                                           JButton cancel) {
        dialog.getRootPane().setDefaultButton(save);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "close-dialog");
        dialog.getRootPane().getActionMap().put("close-dialog", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                cancel.doClick();
            }
        });
    }

    public static void configureTable(JTable table) {
        table.setRowHeight(40);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(true);
        table.setGridColor(UiTheme.BORDER);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(UiTheme.CORAL_LIGHT);
        table.setSelectionForeground(UiTheme.NAVY);
        table.setBackground(UiTheme.SURFACE);
        table.setForeground(UiTheme.NAVY);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setDefaultRenderer(Object.class, new DenseCellRenderer());
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));
        table.getTableHeader().setBackground(UiTheme.HEADER_FILL);
        table.getTableHeader().setForeground(UiTheme.MUTED);
        table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(
                0, 0, 1, 0, UiTheme.BORDER));
    }

    public static DefaultTableCellRenderer statusRenderer() {
        return new StatusRenderer();
    }

    private static final class RoundedPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final int radius;
        private boolean shadow;

        private RoundedPanel(LayoutManager layout, int radius) {
            super(layout);
            this.radius = radius;
            setOpaque(false);
        }

        private void setShadow(boolean shadow) {
            this.shadow = shadow;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            if (shadow) {
                copy.setColor(UiTheme.SHADOW);
                copy.fillRoundRect(0, 3, getWidth() - 1, getHeight() - 4,
                        radius, radius);
            }
            copy.setColor(getBackground());
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            copy.setColor(UiTheme.BORDER);
            copy.setStroke(new BasicStroke(1f));
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            copy.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ModernButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final Color fill;
        private final Color hover;

        private ModernButton(String text, Color fill, Color foreground, Color hover) {
            super(text);
            this.fill = fill;
            this.hover = hover;
            setForeground(foreground);
            setFont(UiTheme.BODY_BOLD);
            setBorder(new EmptyBorder(0, 16, 0, 16));
            int labelWidth = getFontMetrics(getFont()).stringWidth(text);
            setPreferredSize(new Dimension(Math.max(108, labelWidth + 48), 40));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(0, 0, 0, 0));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = getModel().isRollover() ? hover : fill;
            if (Boolean.TRUE.equals(getClientProperty("navActive"))) {
                color = UiTheme.CORAL;
            }
            if (!isEnabled()) {
                color = new Color(0xE3E7EF);
            }
            if (getModel().isPressed()) {
                color = color.darker();
            }
            boolean solid = !Boolean.TRUE.equals(getClientProperty("outlined"));
            if (solid && isEnabled()) {
                copy.setColor(UiTheme.SHADOW);
                copy.fillRoundRect(0, 2, getWidth(), getHeight() - 2, 12, 12);
            }
            if (Boolean.TRUE.equals(getClientProperty("hero")) && isEnabled()) {
                copy.setPaint(new java.awt.GradientPaint(0, 0, fill,
                        getWidth(), getHeight(), hover));
            } else {
                copy.setColor(color);
            }
            copy.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            if (Boolean.TRUE.equals(getClientProperty("outlined"))) {
                copy.setColor(UiTheme.BORDER);
                copy.setStroke(new BasicStroke(1f));
                copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
            copy.dispose();
            super.paintComponent(graphics);
        }
    }

    /** Renders status values as soft rounded chips. */
    private static final class StatusRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private Color chipBackground;
        private Color chipForeground;

        private StatusRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(new EmptyBorder(0, 0, 0, 0));
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            if (selected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
                setOpaque(true);
                return this;
            }
            setOpaque(false);
            String status = value == null ? "" : value.toString();
            if (status.contains("CANCELLED")) {
                chipBackground = new Color(0xEEF0F4);
                chipForeground = UiTheme.MUTED;
            } else if (status.contains("NOT ELIGIBLE") || status.contains("EXPIRED")
                    || status.contains("EMERGENCY") || status.contains("USED")
                    || status.contains("EMPTY")) {
                chipBackground = UiTheme.DANGER_LIGHT;
                chipForeground = UiTheme.DANGER;
            } else if (status.contains("AVAILABLE") || status.contains("FULFILLED")
                    || status.equals("ELIGIBLE") || status.contains("READY")) {
                chipBackground = UiTheme.SUCCESS_LIGHT;
                chipForeground = UiTheme.SUCCESS;
            } else {
                chipBackground = UiTheme.WARNING_LIGHT;
                chipForeground = UiTheme.WARNING;
            }
            setForeground(chipForeground);
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            if (getBackground() != null && isOpaque()) {
                copy.setColor(getBackground());
                copy.fillRect(0, 0, getWidth(), getHeight());
            }
            if (chipBackground != null) {
                int textWidth = getFontMetrics(getFont()).stringWidth(getText());
                int chipWidth = Math.min(getWidth() - 12, textWidth + 22);
                int x = (getWidth() - chipWidth) / 2;
                int chipHeight = Math.min(getHeight() - 8, 24);
                int y = (getHeight() - chipHeight) / 2;
                copy.setColor(chipBackground);
                copy.fillRoundRect(x, y, chipWidth, chipHeight,
                        chipHeight, chipHeight);
            }
            copy.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class DenseCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        private DenseCellRenderer() {
            setBorder(new EmptyBorder(0, 12, 0, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus,
                    row, column);
            setForeground(UiTheme.NAVY);
            setBackground(selected ? UiTheme.CORAL_LIGHT
                    : row % 2 == 0 ? UiTheme.SURFACE : UiTheme.ROW_ALT);
            return this;
        }
    }

    private static final class HeaderCorner extends JPanel {
        private static final long serialVersionUID = 1L;

        private HeaderCorner() {
            setBackground(UiTheme.HEADER_FILL);
        }
    }
}