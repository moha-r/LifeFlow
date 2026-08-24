package lifeflow.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import lifeflow.model.BloodType;

@SuppressWarnings("serial")
public final class PieChartPanel extends JPanel {
    private HashMap<BloodType, Integer> data = new HashMap<>();
    private static final Map<BloodType, Color> COLORS = Map.of(
            BloodType.O_POS, new Color(0xE53935),
            BloodType.O_NEG, new Color(0x1E88E5),
            BloodType.A_POS, new Color(0x43A047),
            BloodType.A_NEG, new Color(0x8E24AA),
            BloodType.B_POS, new Color(0xFB8C00),
            BloodType.B_NEG, new Color(0x00ACC1),
            BloodType.AB_POS, new Color(0x3949AB),
            BloodType.AB_NEG, new Color(0x6D4C41)
    );
    private static final String[] LABELS = {"O+", "O−", "A+", "A−", "B+", "B−", "AB+", "AB−"};

    public PieChartPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(300, 300));
    }

    public void updateData(HashMap<BloodType, Integer> newData) {
        this.data = newData == null ? new HashMap<>() : newData;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int total = data.values().stream().mapToInt(Integer::intValue).sum();

        // Calculate square chart area
        int chartSize = Math.min(getWidth() - 140, getHeight() - 20);
        if (chartSize < 60) chartSize = 60;
        int cx = 10;
        int cy = (getHeight() - chartSize) / 2;

        if (total == 0) {
            g2.setColor(new Color(0xE0E0E0));
            g2.fillOval(cx, cy, chartSize, chartSize);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g2.setColor(new Color(0x999999));
            FontMetrics fm = g2.getFontMetrics();
            String msg = "No Stock";
            g2.drawString(msg, cx + (chartSize - fm.stringWidth(msg)) / 2,
                    cy + chartSize / 2 + fm.getAscent() / 2);
            g2.dispose();
            return;
        }

        // Draw pie slices (square)
        double startAngle = 90;
        BloodType[] types = BloodType.values();
        for (BloodType type : types) {
            int value = data.getOrDefault(type, 0);
            if (value > 0) {
                double extent = (value / (double) total) * 360.0;
                g2.setColor(COLORS.get(type));
                g2.fill(new Arc2D.Double(cx, cy, chartSize, chartSize,
                        startAngle, extent, Arc2D.PIE));
                startAngle += extent;
            }
        }

        // Donut hole
        int holeSize = (int) (chartSize * 0.45);
        int hx = cx + (chartSize - holeSize) / 2;
        int hy = cy + (chartSize - holeSize) / 2;
        g2.setColor(UiTheme.SURFACE);
        g2.fillOval(hx, hy, holeSize, holeSize);

        // Center total
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g2.setColor(UiTheme.NAVY);
        FontMetrics fm = g2.getFontMetrics();
        String totalStr = String.valueOf(total);
        g2.drawString(totalStr, hx + (holeSize - fm.stringWidth(totalStr)) / 2,
                hy + holeSize / 2 + 6);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g2.setColor(UiTheme.MUTED);
        fm = g2.getFontMetrics();
        String label = "units";
        g2.drawString(label, hx + (holeSize - fm.stringWidth(label)) / 2,
                hy + holeSize / 2 + 20);

        // Legend (right side)
        int legendX = cx + chartSize + 16;
        int legendY = cy + 10;
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        fm = g2.getFontMetrics();
        for (int i = 0; i < types.length; i++) {
            int value = data.getOrDefault(types[i], 0);
            g2.setColor(COLORS.get(types[i]));
            g2.fillRoundRect(legendX, legendY + i * 22, 12, 12, 3, 3);
            g2.setColor(value > 0 ? UiTheme.NAVY : UiTheme.MUTED);
            String entry = LABELS[i] + "  " + value;
            g2.drawString(entry, legendX + 18, legendY + i * 22 + fm.getAscent());
        }

        g2.dispose();
    }
}
