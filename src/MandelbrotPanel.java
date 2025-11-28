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
import javax.swing.SwingUtilities;

/**
 * Panel that displays the Mandelbrot image and supports drag interactions.
 * <p>
 * As the view layer it is responsible for:
 * <ul>
 *   <li>Painting the current fractal from the model-provided {@link BufferedImage};</li>
 *   <li>Capturing mouse drags, drawing a translucent zoom rectangle, and converting drag coordinates to complex-plane bounds.</li>
 * </ul>
 * By implementing {@link PropertyChangeListener}, it repaints automatically when the model fires "image" or "overlay" events.
 */
public class MandelbrotPanel extends JPanel implements PropertyChangeListener {

    private final MandelbrotModel model;
    private Point dragStart;
    private Point dragEnd;
    private int dragButton = MouseEvent.NOBUTTON;
    private BufferedImage image;
    private boolean panning;

    /**
     * Wires listeners and initial drag state for the panel.
     */
    public MandelbrotPanel(MandelbrotModel model) {
        this.model = model;
        setPreferredSize(new Dimension(MandelbrotModel.DEFAULT_WIDTH, MandelbrotModel.DEFAULT_HEIGHT));
        setBackground(Color.BLACK);
        image = model.getImage();
        model.addPropertyChangeListener(this);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Right or middle button drags indicate panning; left drag stays for zooming
                dragButton = e.getButton();
                panning = SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e);
                dragStart = e.getPoint();
                dragEnd = e.getPoint();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStart != null && dragEnd != null) {
                    if (panning) {
                        // Pan: convert mouse displacement into complex-plane shift
                        int deltaX = dragEnd.x - dragStart.x;
                        int deltaY = dragEnd.y - dragStart.y;
                        model.panByPixels(deltaX, deltaY, getWidth(), getHeight());
                    } else {
                        // Convert the screen rectangle to complex-plane bounds and delegate to the model
                        model.zoomToArea(dragStart.x, dragStart.y, dragEnd.x, dragEnd.y, getWidth(), getHeight());
                    }
                }
                dragStart = null;
                dragEnd = null;
                dragButton = MouseEvent.NOBUTTON;
                panning = false;
                repaint();
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // Update the drag endpoint, repaint the translucent rectangle, and avoid triggering computation mid-drag
                dragEnd = e.getPoint();
                // Keep panning mode during right/middle-button drags to avoid drawing a zoom rectangle
                if ((e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0
                        || (e.getModifiersEx() & MouseEvent.BUTTON2_DOWN_MASK) != 0
                        || dragButton == MouseEvent.BUTTON3 || dragButton == MouseEvent.BUTTON2) {
                    panning = true;
                }
                repaint();
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // When the panel resizes, ask the model to render at the new resolution
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
            if (panning && dragStart != null && dragEnd != null) {
                int offsetX = dragEnd.x - dragStart.x;
                int offsetY = dragEnd.y - dragStart.y;
                g2d.drawImage(image, offsetX, offsetY, getWidth(), getHeight(), null);
                // Fill empty areas revealed by the translated preview to avoid artifacts
                g2d.setColor(getBackground());
                if (offsetX > 0) {
                    g2d.fillRect(0, 0, offsetX, getHeight());
                } else if (offsetX < 0) {
                    g2d.fillRect(getWidth() + offsetX, 0, -offsetX, getHeight());
                }
                if (offsetY > 0) {
                    g2d.fillRect(0, 0, getWidth(), offsetY);
                } else if (offsetY < 0) {
                    g2d.fillRect(0, getHeight() + offsetY, getWidth(), -offsetY);
                }
            } else {
                g2d.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            }
        }
        if (dragStart != null && dragEnd != null && !panning) {
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
