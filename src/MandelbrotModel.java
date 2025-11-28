import java.awt.Color;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.SwingWorker;

/**
 * Model responsible for managing Mandelbrot parameters, rendering, and history.
 * <p>
 * As the MVC model it:
 * <ul>
 *   <li>Maintains complex-plane bounds, max iterations, colour scheme, and overlay settings;</li>
 *   <li>Uses {@link MandelbrotCalculator} to compute iteration grids and {@link ColorScheme} to colourize them into a {@link BufferedImage};</li>
 *   <li>Notifies listeners through {@link PropertyChangeSupport};</li>
 *   <li>Records parameter history on stacks for undo/redo.</li>
 * </ul>
 */
public class MandelbrotModel {

    public static final int DEFAULT_WIDTH = 800;
    public static final int DEFAULT_HEIGHT = 800;

    private final MandelbrotCalculator calculator = new MandelbrotCalculator();
    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private final Deque<Parameters> undoStack = new ArrayDeque<>();
    private final Deque<Parameters> redoStack = new ArrayDeque<>();

    private double minReal = MandelbrotCalculator.INITIAL_MIN_REAL;
    private double maxReal = MandelbrotCalculator.INITIAL_MAX_REAL;
    private double minImaginary = MandelbrotCalculator.INITIAL_MIN_IMAGINARY;
    private double maxImaginary = MandelbrotCalculator.INITIAL_MAX_IMAGINARY;
    private int maxIterations = MandelbrotCalculator.INITIAL_MAX_ITERATIONS;
    private double radiusSquared = MandelbrotCalculator.DEFAULT_RADIUS_SQUARED;
    private boolean showZoomFactor = false;
    private ColorScheme colorScheme = ColorScheme.GRAYSCALE;

    private BufferedImage image;
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;

    private SwingWorker<BufferedImage, Void> currentWorker;

    /**
     * Initialize the model and trigger the first render immediately.
     */
    public MandelbrotModel() {
        render();
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

    public BufferedImage getImage() {
        return image;
    }

    /**
     * Estimate the current zoom factor for overlay display (compared to the initial bounds).
     */
    public double getZoomFactor() {
        double startingRange = MandelbrotCalculator.INITIAL_MAX_REAL - MandelbrotCalculator.INITIAL_MIN_REAL;
        double currentRange = maxReal - minReal;
        if (currentRange == 0) {
            return 1.0;
        }
        return startingRange / currentRange;
    }

    public boolean isShowZoomFactor() {
        return showZoomFactor;
    }

    public void setShowZoomFactor(boolean showZoomFactor) {
        if (this.showZoomFactor != showZoomFactor) {
            saveState();
            this.showZoomFactor = showZoomFactor;
            redoStack.clear();
            changeSupport.firePropertyChange("overlay", !showZoomFactor, showZoomFactor);
        }
    }

    public ColorScheme getColorScheme() {
        return colorScheme;
    }

    /** Switch the colour scheme. */
    public void setColorScheme(ColorScheme colorScheme) {
        Objects.requireNonNull(colorScheme);
        if (this.colorScheme != colorScheme) {
            saveState();
            this.colorScheme = colorScheme;
            redoStack.clear();
            render();
        }
    }

    /**
     * Set the iteration cap; very small values are ignored to prevent unstable output.
     */
    public void setMaxIterations(int maxIterations) {
        if (maxIterations < 10) {
            return;
        }
        if (this.maxIterations != maxIterations) {
            saveState();
            this.maxIterations = maxIterations;
            redoStack.clear();
            render();
        }
    }

    /**
     * Update render resolution when the canvas size changes.
     */
    public void setRenderSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        this.width = width;
        this.height = height;
        render();
    }

    /** Restore all parameters to their initial defaults. */
    public void reset() {
        saveState();
        boolean previousOverlay = showZoomFactor;
        minReal = MandelbrotCalculator.INITIAL_MIN_REAL;
        maxReal = MandelbrotCalculator.INITIAL_MAX_REAL;
        minImaginary = MandelbrotCalculator.INITIAL_MIN_IMAGINARY;
        maxImaginary = MandelbrotCalculator.INITIAL_MAX_IMAGINARY;
        maxIterations = MandelbrotCalculator.INITIAL_MAX_ITERATIONS;
        radiusSquared = MandelbrotCalculator.DEFAULT_RADIUS_SQUARED;
        colorScheme = ColorScheme.GRAYSCALE;
        redoStack.clear();
        render();
        // Preserve the zoom-factor overlay after reset if it was enabled before
        if (previousOverlay) {
            changeSupport.firePropertyChange("overlay", false, true);
        }
    }

    /**
     * Convert a screen-space selection rectangle into new complex-plane bounds and render.
     */
    public void zoomToArea(int startX, int startY, int endX, int endY, int panelWidth, int panelHeight) {
        if (panelWidth <= 0 || panelHeight <= 0) {
            return;
        }
        int minX = Math.min(startX, endX);
        int maxX = Math.max(startX, endX);
        int minY = Math.min(startY, endY);
        int maxY = Math.max(startY, endY);
        if (maxX - minX < 2 || maxY - minY < 2) {
            return;
        }

        saveState();
        redoStack.clear();

        double realPerPixel = (maxReal - minReal) / panelWidth;
        double imagPerPixel = (maxImaginary - minImaginary) / panelHeight;

        double newMinReal = minReal + minX * realPerPixel;
        double newMaxReal = minReal + maxX * realPerPixel;
        double newMinImag = minImaginary + minY * imagPerPixel;
        double newMaxImag = minImaginary + maxY * imagPerPixel;

        minReal = newMinReal;
        maxReal = newMaxReal;
        minImaginary = newMinImag;
        maxImaginary = newMaxImag;
        render();
    }

    /**
     * Pan by a fraction of the current viewport width/height.
     */
    public void panByFraction(double fractionX, double fractionY) {
        saveState();
        redoStack.clear();
        double realShift = (maxReal - minReal) * fractionX;
        double imagShift = (maxImaginary - minImaginary) * fractionY;
        minReal += realShift;
        maxReal += realShift;
        minImaginary += imagShift;
        maxImaginary += imagShift;
        render();
    }

    /**
     * Pan based on mouse drag pixel deltas so the drag start moves to the drag end when released.
     */
    public void panByPixels(int deltaX, int deltaY, int panelWidth, int panelHeight) {
        if (panelWidth <= 0 || panelHeight <= 0 || (deltaX == 0 && deltaY == 0)) {
            return;
        }
        saveState();
        redoStack.clear();
        double realPerPixel = (maxReal - minReal) / panelWidth;
        double imagPerPixel = (maxImaginary - minImaginary) / panelHeight;
        double realShift = -deltaX * realPerPixel;
        double imagShift = -deltaY * imagPerPixel;
        minReal += realShift;
        maxReal += realShift;
        minImaginary += imagShift;
        maxImaginary += imagShift;
        render();
    }

    /** Undo the most recent parameter change. */
    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        Parameters current = snapshot();
        Parameters previous = undoStack.pop();
        redoStack.push(current);
        apply(previous);
        render();
    }

    /** Redo the most recently undone operation. */
    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        Parameters current = snapshot();
        Parameters next = redoStack.pop();
        undoStack.push(current);
        apply(next);
        render();
    }

    /**
     * Write current parameters to a Properties file for later loading.
     */
    public void saveParameters(File file) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("minReal", Double.toString(minReal));
        properties.setProperty("maxReal", Double.toString(maxReal));
        properties.setProperty("minImaginary", Double.toString(minImaginary));
        properties.setProperty("maxImaginary", Double.toString(maxImaginary));
        properties.setProperty("maxIterations", Integer.toString(maxIterations));
        properties.setProperty("radiusSquared", Double.toString(radiusSquared));
        properties.setProperty("showZoomFactor", Boolean.toString(showZoomFactor));
        properties.setProperty("colorScheme", colorScheme.name());
        try (FileOutputStream out = new FileOutputStream(file)) {
            properties.store(out, "Mandelbrot parameters");
        }
    }

    /**
     * Load parameters from a Properties file; if the colour scheme name is unknown, fall back to grayscale.
     */
    public void loadParameters(File file) throws IOException {
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            properties.load(in);
        }
        saveState();
        minReal = Double.parseDouble(properties.getProperty("minReal", Double.toString(minReal)));
        maxReal = Double.parseDouble(properties.getProperty("maxReal", Double.toString(maxReal)));
        minImaginary = Double.parseDouble(properties.getProperty("minImaginary", Double.toString(minImaginary)));
        maxImaginary = Double.parseDouble(properties.getProperty("maxImaginary", Double.toString(maxImaginary)));
        maxIterations = Integer.parseInt(properties.getProperty("maxIterations", Integer.toString(maxIterations)));
        radiusSquared = Double.parseDouble(properties.getProperty("radiusSquared", Double.toString(radiusSquared)));
        showZoomFactor = Boolean.parseBoolean(properties.getProperty("showZoomFactor", Boolean.toString(showZoomFactor)));
        String schemeName = properties.getProperty("colorScheme", colorScheme.name());
        try {
            colorScheme = ColorScheme.valueOf(schemeName);
        } catch (IllegalArgumentException ex) {
            colorScheme = ColorScheme.GRAYSCALE;
        }
        redoStack.clear();
        render();
    }

    /** Export the current render as a PNG file. */
    public void exportImage(File file) throws IOException {
        if (image != null) {
            ImageIO.write(image, "png", file);
        }
    }

    /**
     * Render the fractal for current parameters asynchronously.
     * Uses {@link SwingWorker} to avoid blocking the EDT and fires an "image" property event on completion.
     */
    private void render() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }
        final int renderWidth = width;
        final int renderHeight = height;
        currentWorker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                int[][] iterations = calculator.calcMandelbrotSet(renderWidth, renderHeight, minReal, maxReal, minImaginary,
                        maxImaginary, maxIterations, radiusSquared);
                BufferedImage img = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < renderHeight; y++) {
                    for (int x = 0; x < renderWidth; x++) {
                        if (isCancelled()) {
                            return null;
                        }
                        int iteration = iterations[y][x];
                        Color color = colorScheme.map(iteration, maxIterations);
                        img.setRGB(x, y, color.getRGB());
                    }
                }
                return img;
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    BufferedImage newImage = get();
                    if (newImage != null) {
                        BufferedImage oldImage = image;
                        image = newImage;
                        changeSupport.firePropertyChange("image", oldImage, image);
                    }
                } catch (Exception ignored) {
                }
            }
        };
        currentWorker.execute();
    }

    /** Capture an immutable snapshot of current parameters for the history stacks. */
    private Parameters snapshot() {
        return new Parameters(minReal, maxReal, minImaginary, maxImaginary, maxIterations, radiusSquared, showZoomFactor,
                colorScheme);
    }

    /** Apply a parameter snapshot back onto the current model. */
    private void apply(Parameters parameters) {
        minReal = parameters.minReal();
        maxReal = parameters.maxReal();
        minImaginary = parameters.minImaginary();
        maxImaginary = parameters.maxImaginary();
        maxIterations = parameters.maxIterations();
        radiusSquared = parameters.radiusSquared();
        showZoomFactor = parameters.showZoomFactor();
        colorScheme = parameters.colorScheme();
    }

    /**
     * Save current state to the undo stack before a change, trimming depth to avoid unbounded growth.
     */
    private void saveState() {
        undoStack.push(snapshot());
        while (undoStack.size() > 50) {
            undoStack.removeLast();
        }
    }

    private record Parameters(double minReal, double maxReal, double minImaginary, double maxImaginary, int maxIterations,
            double radiusSquared, boolean showZoomFactor, ColorScheme colorScheme) {
    }
}
