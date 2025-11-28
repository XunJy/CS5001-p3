import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * Entry point and controller wiring for the Mandelbrot explorer GUI.
 * <p>
 * 作为整个应用的启动类：
 * <ul>
 *   <li>创建模型与画布，并将它们放入窗口；</li>
 *   <li>搭建侧边栏控件，把按钮和输入框事件转交给模型；</li>
 *   <li>提供保存/加载/导出等文件对话框操作。</li>
 * </ul>
 */
public class MandelbrotExplorer extends JFrame {

    private final MandelbrotModel model;
    private final MandelbrotPanel panel;

    /**
     * 初始化窗口、模型与控件布局。
     */
    public MandelbrotExplorer() {
        super("Mandelbrot Explorer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        model = new MandelbrotModel();
        panel = new MandelbrotPanel(model);
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(buildControlPanel(), BorderLayout.EAST);
        pack();
        setMinimumSize(new Dimension(1100, 850));
        setLocationRelativeTo(null);
    }

    /**
     * 构建右侧控制面板，提供迭代次数、配色、缩放显示、平移与历史操作按钮。
     */
    private JPanel buildControlPanel() {
        JPanel container = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        JLabel iterationLabel = new JLabel("Max iterations:");
        gbc.anchor = GridBagConstraints.WEST;
        container.add(iterationLabel, gbc);

        gbc.gridy++;
        // 迭代次数通过 JSpinner 设置，避免用户输入非数字
        JSpinner iterationSpinner = new JSpinner(new SpinnerNumberModel(MandelbrotCalculator.INITIAL_MAX_ITERATIONS, 10, 2000, 10));
        iterationSpinner.addChangeListener(e -> model.setMaxIterations((Integer) iterationSpinner.getValue()));
        container.add(iterationSpinner, gbc);

        gbc.gridy++;
        container.add(new JLabel("Colour scheme:"), gbc);

        gbc.gridy++;
        JComboBox<ColorScheme> schemeBox = new JComboBox<>(ColorScheme.values());
        schemeBox.addActionListener(e -> model.setColorScheme((ColorScheme) schemeBox.getSelectedItem()));
        container.add(schemeBox, gbc);

        gbc.gridy++;
        JCheckBox zoomFactorBox = new JCheckBox("Show zoom factor");
        zoomFactorBox.addActionListener(e -> model.setShowZoomFactor(zoomFactorBox.isSelected()));
        container.add(zoomFactorBox, gbc);

        gbc.gridy++;
        container.add(new JLabel("Pan fraction (e.g. 0.2):"), gbc);

        gbc.gridy++;
        JTextField panField = new JTextField("0.2", 5);
        container.add(panField, gbc);

        gbc.gridy++;
        container.add(buildPanButtons(panField), gbc);

        gbc.gridy++;
        JPanel historyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton undoButton = new JButton("Undo");
        undoButton.addActionListener(e -> model.undo());
        JButton redoButton = new JButton("Redo");
        redoButton.addActionListener(e -> model.redo());
        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> model.reset());
        historyPanel.add(undoButton);
        historyPanel.add(redoButton);
        historyPanel.add(resetButton);
        container.add(historyPanel, gbc);

        gbc.gridy++;
        JPanel saveLoadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveButton = new JButton("Save params...");
        JButton loadButton = new JButton("Load params...");
        saveButton.addActionListener(e -> saveParameters());
        loadButton.addActionListener(e -> loadParameters());
        saveLoadPanel.add(saveButton);
        saveLoadPanel.add(loadButton);
        container.add(saveLoadPanel, gbc);

        gbc.gridy++;
        JButton exportButton = new JButton("Export image...");
        exportButton.addActionListener(e -> exportImage());
        container.add(exportButton, gbc);

        return container;
    }

    /**
     * 构建平移按钮区，将文本框中的比例转为横纵偏移。
     */
    private JPanel buildPanButtons(JTextField panField) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 1;
        gbc.gridy = 0;
        JButton up = new JButton("Up");
        up.addActionListener(e -> panFromField(panField, 0, -1));
        panel.add(up, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        JButton left = new JButton("Left");
        left.addActionListener(e -> panFromField(panField, -1, 0));
        panel.add(left, gbc);

        gbc.gridx = 2;
        JButton right = new JButton("Right");
        right.addActionListener(e -> panFromField(panField, 1, 0));
        panel.add(right, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        JButton down = new JButton("Down");
        down.addActionListener(e -> panFromField(panField, 0, 1));
        panel.add(down, gbc);

        return panel;
    }

    /**
     * 从文本框读取平移比例并调用模型平移。
     */
    private void panFromField(JTextField panField, int directionX, int directionY) {
        try {
            double fraction = Double.parseDouble(panField.getText());
            model.panByFraction(directionX * fraction, directionY * fraction);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid pan fraction", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 通过文件对话框保存当前参数到 Properties 文件。
     */
    private void saveParameters() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                model.saveParameters(chooser.getSelectedFile());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to save parameters: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 通过文件对话框加载参数并刷新视图。
     */
    private void loadParameters() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                model.loadParameters(chooser.getSelectedFile());
            } catch (IOException | NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Failed to load parameters: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 导出当前渲染结果为 PNG 文件。
     */
    private void exportImage() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File target = chooser.getSelectedFile();
                if (!target.getName().toLowerCase().endsWith(".png")) {
                    target = new File(target.getAbsolutePath() + ".png");
                }
                model.exportImage(target);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to export image: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 程序入口，确保在 EDT 创建并显示窗口。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MandelbrotExplorer explorer = new MandelbrotExplorer();
            explorer.setVisible(true);
        });
    }
}
