package jacross.plaf;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicComboBoxUI;

import jacross.ColorRole;
import jacross.Tokens;

/** Combo box with a flat chevron arrow and the shared field border. */
public class JaCrossComboBoxUI extends BasicComboBoxUI {
    public static ComponentUI createUI(JComponent c) {
        return new JaCrossComboBoxUI();
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        Tokens t = (Tokens) UIManager.get("jacross.tokens");
        if (t != null) {
            comboBox.setBorder(new JaCrossBorders.Field(t));
        }
    }

    @Override
    protected JButton createArrowButton() {
        JButton button = new JButton() {
            @Override
            public void paintComponent(Graphics g) {
                Tokens t = (Tokens) UIManager.get("jacross.tokens");
                if (t == null) {
                    super.paintComponent(g);
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getParent().getBackground());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(t.color(ColorRole.ON_SURFACE_VARIANT));
                    g2.setStroke(new java.awt.BasicStroke(1.6f,
                            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    double cx = getWidth() / 2.0;
                    double cy = getHeight() / 2.0;
                    Path2D.Double chevron = new Path2D.Double();
                    chevron.moveTo(cx - 4, cy - 2);
                    chevron.lineTo(cx, cy + 2.5);
                    chevron.lineTo(cx + 4, cy - 2);
                    g2.draw(chevron);
                } finally {
                    g2.dispose();
                }
            }
        };
        button.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false);
        button.setFocusable(false);
        return button;
    }
}
