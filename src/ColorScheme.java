import java.awt.Color;

/**
 * Enumeration of color schemes for mapping iteration counts to colours.
 * <p>
 * Higher iteration counts mean slower escape; the values are mapped to gradients so students can experiment with different
 * visual styles.
 */
public enum ColorScheme {
    /** Simple grayscale: higher iterations appear brighter; points inside the set are black. */
    GRAYSCALE {
        @Override
        public Color map(int iteration, int maxIterations) {
            if (iteration >= maxIterations) {
                return Color.BLACK;
            }
            int value = (int) (255.0 * iteration / maxIterations);
            return new Color(value, value, value);
        }
    },
    /** Blue gradient to emphasize edges. */
    BLUE_GRADIENT {
        @Override
        public Color map(int iteration, int maxIterations) {
            if (iteration >= maxIterations) {
                return Color.BLACK;
            }
            float ratio = iteration / (float) maxIterations;
            return new Color(0, ratio, 1.0f).brighter();
        }
    },
    /** Fire gradient using red-yellow-white to highlight escape speed. */
    FIRE {
        @Override
        public Color map(int iteration, int maxIterations) {
            if (iteration >= maxIterations) {
                return Color.BLACK;
            }
            float ratio = iteration / (float) maxIterations;
            return new Color(Math.min(1.0f, ratio * 2), ratio, (float) Math.pow(ratio, 0.5));
        }
    };

    /**
     * Convert the iteration count for a point into a colour.
     *
     * @param iteration iteration count for the point
     * @param maxIterations iteration limit (used for normalization)
     * @return mapped colour
     */
    public abstract Color map(int iteration, int maxIterations);
}
