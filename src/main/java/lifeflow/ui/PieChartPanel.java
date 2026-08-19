package lifeflow.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import lifeflow.model.BloodType;

public class PieChartPanel extends JPanel {
    private HashMap<BloodType, Integer> data = new HashMap<>();
    private final Map<BloodType, Color> colors = new HashMap<>();

    public PieChartPanel() {
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(200, 200));
        colors.put(BloodType.O_POS, new Color(229, 57, 53));
        colors.put(BloodType.O_NEG, new Color(211, 47, 47));
        colors.put(BloodType.A_POS, new Color(198, 40, 40));
        colors.put(BloodType.A_NEG, new Color(183, 28, 28));
        colors.put(BloodType.B_POS, new Color(239, 83, 80));
        colors.put(BloodType.B_NEG, new Color(244, 67, 54));
        colors.put(BloodType.AB_POS, new Color(255, 138, 101));
        colors.put(BloodType.AB_NEG, new Color(255, 112, 67));
    }

    public void updateData(HashMap<BloodType, Integer> newData) {
        this.data = newData;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int total = data.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            g2.setColor(new Color(224, 224, 224));
            g2.fillOval(10, 10, getWidth() - 20, getHeight() - 20);
            g2.dispose();
            return;
        }

        double startAngle = 0;
        for (BloodType type : BloodType.values()) {
            int value = data.getOrDefault(type, 0);
            if (value > 0) {
                double extent = (value / (double) total) * 360.0;
                g2.setColor(colors.get(type));
                g2.fill(new Arc2D.Double(10, 10, getWidth() - 20, getHeight() - 20, startAngle, extent, Arc2D.PIE));
                startAngle += extent;
            }
        }
        
        // Inner circle to make it a donut chart (looks better!)
        g2.setColor(getBackground()); // or UiTheme.BACKGROUND if opaque
        g2.fillOval(50, 50, getWidth() - 100, getHeight() - 100);

        g2.dispose();
    }
}
