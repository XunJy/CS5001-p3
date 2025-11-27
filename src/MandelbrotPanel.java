import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;

/**
 * Panel that displays the Mandelbrot image and supports drag-to-zoom interactions.
 */
public class MandelbrotPanel extends JPanel implements PropertyChangeListener {

    private final MandelbrotModel model;
    private Point dragStart;
    private Point dragEnd;
    private BufferedImage image;

    public MandelbrotPanel(MandelbrotModel model) {
        this.model = model;
        setPreferredSize(new Dimension(MandelbrotModel.DEFAULT_WIDTH, MandelbrotModel.DEFAULT_HEIGHT));
        setBackground(Color.BLACK);
        image = model.getImage();
        model.addPropertyChangeListener(this);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                dragEnd = e.getPoint();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStart != null && dragEnd != null) {
                    model.zoomToArea(dragStart.x, dragStart.y, dragEnd.x, dragEnd.y, getWidth(), getHeight());
                }
                dragStart = null;
                dragEnd = null;
                repaint();
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                dragEnd = e.getPoint();
                repaint();
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                model.setRenderSize(getWidth(), getHeight());
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (image != null) {
            g2d.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        }
        if (dragStart != null && dragEnd != null) {
            g2d.setColor(new Color(255, 255, 255, 128));
            int x = Math.min(dragStart.x, dragEnd.x);
            int y = Math.min(dragStart.y, dragEnd.y);
            int width = Math.abs(dragEnd.x - dragStart.x);
            int height = Math.abs(dragEnd.y - dragStart.y);
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawRect(x, y, width, height);
            g2d.setColor(new Color(255, 255, 255, 64));
            g2d.fillRect(x, y, width, height);
        }
        if (model.isShowZoomFactor()) {
            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.setFont(g2d.getFont().deriveFont(Font.BOLD, 14f));
            String zoomText = String.format("Zoom: x%.2f", model.getZoomFactor());
            g2d.drawString(zoomText, 10, 20);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("image".equals(evt.getPropertyName())) {
            image = (BufferedImage) evt.getNewValue();
            repaint();
        } else if ("overlay".equals(evt.getPropertyName())) {
            repaint();
        }
    }
}
