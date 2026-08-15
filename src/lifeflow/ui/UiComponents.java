package lifeflow.ui;

import java.awt.BasicStroke;
import java.awt.event.ActionEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
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
        RoundedPanel panel = new RoundedPanel(layout, 18);
        panel.setBackground(UiTheme.SURFACE);
        panel.setBorder(new EmptyBorder(18, 18, 18, 18));
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
        button.setPreferredSize(new Dimension(196, 43));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        return button;
    }

    public static void setNavActive(JButton button, boolean active) {
        button.putClientProperty("navActive", active);
        button.setForeground(active ? Color.WHITE : UiTheme.SIDEBAR_TEXT);
        button.repaint();
    }

    public static JTextField searchField(String tooltip) {
        JTextField field = new JTextField();
        field.setToolTipText(tooltip);
        field.setPreferredSize(new Dimension(230, 38));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(7, 11, 7, 11)));
        return field;
    }

    public static void styleInput(JComponent input) {
        input.setPreferredSize(new Dimension(260, 38));
        input.setFont(UiTheme.BODY);
        if (input instanceof JTextField textField) {
            textField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.BORDER),
                    new EmptyBorder(7, 10, 7, 10)));
        } else if (input instanceof JComboBox<?>) {
            input.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        }
    }

    public static JScrollPane tableScroll(JTable table) {
        configureTable(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
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
        table.setRowHeight(42);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(UiTheme.CORAL_LIGHT);
        table.setSelectionForeground(UiTheme.NAVY);
        table.setBackground(UiTheme.SURFACE);
        table.setForeground(UiTheme.NAVY);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.getTableHeader().setBackground(new Color(0xF8F9FC));
        table.getTableHeader().setForeground(UiTheme.MUTED);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(
                0, 0, 1, 0, UiTheme.BORDER));
    }

    public static DefaultTableCellRenderer statusRenderer() {
        return new StatusRenderer();
    }

    private static final class RoundedPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final int radius;

        private RoundedPanel(LayoutManager layout, int radius) {
            super(layout);
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
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
            setPreferredSize(new Dimension(136, 40));
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
                color = UiTheme.BORDER;
            }
            if (getModel().isPressed()) {
                color = color.darker();
            }
            copy.setColor(color);
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

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        private StatusRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(new EmptyBorder(6, 8, 6, 8));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            if (selected) {
                return this;
            }
            String status = value == null ? "" : value.toString();
            setOpaque(true);
            if (status.contains("AVAILABLE") || status.contains("FULFILLED")) {
                setBackground(UiTheme.SUCCESS_LIGHT);
                setForeground(UiTheme.SUCCESS);
            } else if (status.contains("EMERGENCY") || status.contains("USED")) {
                setBackground(UiTheme.DANGER_LIGHT);
                setForeground(UiTheme.DANGER);
            } else {
                setBackground(UiTheme.WARNING_LIGHT);
                setForeground(UiTheme.WARNING);
            }
            return this;
        }
    }
}
