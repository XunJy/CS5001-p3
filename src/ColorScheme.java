import java.awt.Color;

/**
 * Enumeration of color schemes for mapping iteration counts to colours.
 */
public enum ColorScheme {
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

    public abstract Color map(int iteration, int maxIterations);
}
