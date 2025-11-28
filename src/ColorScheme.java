import java.awt.Color;

/**
 * Enumeration of color schemes for mapping iteration counts to colours.
 * <p>
 * 迭代次数越大表示逃逸越慢，这里将数值映射成渐变色，方便学生尝试不同视觉风格。
 */
public enum ColorScheme {
    /** 简单灰度，迭代越多越亮，极限值为黑色表示属于集合。 */
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
    /** 蓝色渐变，突出边缘轮廓。 */
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
    /** 火焰渐变，利用红-黄-白的层次强调逃逸速度。 */
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
     * 将某个点的迭代次数转为颜色。
     *
     * @param iteration 当前点的迭代次数
     * @param maxIterations 迭代上限（用于归一化）
     * @return 映射后的颜色
     */
    public abstract Color map(int iteration, int maxIterations);
}
