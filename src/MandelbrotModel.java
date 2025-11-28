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
 * 这是 MVC 中的 Model：
 * <ul>
 *   <li>维护复平面边界、最大迭代次数、配色方案等状态；</li>
 *   <li>用 {@link MandelbrotCalculator} 计算迭代矩阵，并用 {@link ColorScheme} 着色生成 {@link BufferedImage}；</li>
 *   <li>通过 {@link PropertyChangeSupport} 通知视图刷新；</li>
 *   <li>使用栈结构记录历史，支持撤销/重做。</li>
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
     * 初始化模型并立即触发首次渲染。
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
     * 估算当前缩放倍率，用于叠加显示（与初始范围比较）。
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

    /** 切换配色方案。 */
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
     * 设置迭代上限，过小值被忽略以避免计算异常。
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
     * 当画布尺寸变化时更新渲染分辨率。
     */
    public void setRenderSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        this.width = width;
        this.height = height;
        render();
    }

    /** 将所有参数恢复到初始默认值。 */
    public void reset() {
        saveState();
        minReal = MandelbrotCalculator.INITIAL_MIN_REAL;
        maxReal = MandelbrotCalculator.INITIAL_MAX_REAL;
        minImaginary = MandelbrotCalculator.INITIAL_MIN_IMAGINARY;
        maxImaginary = MandelbrotCalculator.INITIAL_MAX_IMAGINARY;
        maxIterations = MandelbrotCalculator.INITIAL_MAX_ITERATIONS;
        radiusSquared = MandelbrotCalculator.DEFAULT_RADIUS_SQUARED;
        showZoomFactor = false;
        colorScheme = ColorScheme.GRAYSCALE;
        redoStack.clear();
        render();
    }

    /**
     * 将屏幕坐标选框转换为新的复平面边界，并触发重新渲染。
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
     * 按当前视窗宽高的一定比例进行平移。
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
     * 按鼠标拖拽的像素位移进行平移，使得拖拽起点在释放时移动到拖拽终点。
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

    /** 撤销最近一次参数修改。 */
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

    /** 重做最近一次被撤销的操作。 */
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
     * 将当前参数写入 Properties 文件，便于下次加载。
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
     * 从 Properties 文件读取参数，若颜色枚举名称不匹配则回退到默认灰度。
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

    /** 将当前渲染结果写出为 PNG。 */
    public void exportImage(File file) throws IOException {
        if (image != null) {
            ImageIO.write(image, "png", file);
        }
    }

    /**
     * 异步渲染当前参数对应的分形图像。
     * 使用 {@link SwingWorker} 避免阻塞 EDT，完成后触发 "image" 属性事件以刷新视图。
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

    /** 捕获当前参数的不可变快照，供历史栈使用。 */
    private Parameters snapshot() {
        return new Parameters(minReal, maxReal, minImaginary, maxImaginary, maxIterations, radiusSquared, showZoomFactor,
                colorScheme);
    }

    /** 将某个参数快照应用回当前模型。 */
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
     * 在修改前保存当前状态到撤销栈，限制栈深度防止无限增长。
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
