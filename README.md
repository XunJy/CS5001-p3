# CS5001-p3 Mandelbrot Explorer

本项目提供一个基于 Swing 的曼德博集合探索器，支持拖拽缩放、平移、配色切换、撤销/重做以及参数保存/加载和图像导出等功能。

## 设计思路
- **分层结构（近似 MVC）**：
  - **Model（`MandelbrotModel`）** 持有当前视角参数（复平面边界、最大迭代次数、配色方案等），负责调用计算器异步生成图像，并管理撤销/重做历史、保存/加载和导出逻辑。
  - **View（`MandelbrotPanel`）** 负责绘制 `BufferedImage`，监听模型的属性变更事件，处理鼠标拖拽以选择缩放框，并将屏幕坐标映射为模型的边界。
  - **Controller/Delegate（`MandelbrotExplorer`）** 构建 Swing 控件（按钮、文本框、下拉框等），把用户操作转化为对模型的调用。
  - **核心算法（`MandelbrotCalculator`）** 提供纯计算方法，返回迭代次数矩阵，方便在模型中用不同配色方案上色。
  - **颜色映射（`ColorScheme`）** 定义枚举以映射迭代值到不同渐变色，便于一键切换。
- **事件驱动**：模型通过 `PropertyChangeSupport` 通知视图刷新；视图在鼠标拖拽时即时绘制半透明选框避免重复计算；界面使用 `SwingUtilities.invokeLater` 保证在 EDT 创建窗口。
- **状态管理**：使用 `Deque` 记录参数快照以实现撤销/重做，并在修改参数前保存旧状态，保持操作可恢复。
- **文件交互**：使用 `Properties` 保存/加载参数，用 `ImageIO` 导出 PNG；`JFileChooser` 提供简单的文件选择界面。

## 各文件作用
- `src/MandelbrotCalculator.java`：纯计算类，给定分辨率与复平面边界返回迭代次数矩阵，内含基础参数常量。
- `src/MandelbrotModel.java`：模型层，封装渲染参数、异步渲染任务、历史记录、保存/加载与导出。
- `src/MandelbrotPanel.java`：绘制层，展示 `BufferedImage`，处理鼠标拖拽缩放与组件缩放事件。
- `src/MandelbrotExplorer.java`：启动与控制层，搭建 Swing 窗口和侧边栏控件，调用模型完成缩放、平移、撤销/重做、重置、保存/加载和导出。
- `src/ColorScheme.java`：颜色枚举，提供灰度、蓝色渐变、火焰风格三种映射。

## 涉及的 Swing/GUI 知识点
- **布局管理器**：`BorderLayout` 将画布和侧栏分区；`GridBagLayout` 灵活排布参数控件；`FlowLayout` 整理按钮组。
- **事件处理**：使用 `ActionListener` 响应按钮/下拉框，`ChangeListener` 处理 `JSpinner`，`MouseAdapter` 处理拖拽选框，`ComponentAdapter` 监听面板尺寸变化以重算图像。
- **EDT 线程模型**：通过 `SwingUtilities.invokeLater` 在事件调度线程创建界面；耗时渲染用 `SwingWorker` 后台执行，完成后再回到 EDT 更新图像。
- **绘图技巧**：在 `paintComponent` 内用 `Graphics2D` 绘制 `BufferedImage` 和半透明矩形作为缩放框；使用抗锯齿渲染提示提高视觉质量。

## 运行环境
- 需要安装 JDK 8 或更高版本。

## 编译与运行
1. 在项目根目录编译：
   ```bash
   javac src/*.java
   ```
2. 运行图形界面：
   ```bash
   java -cp src MandelbrotExplorer
   ```

首次启动会显示默认视角。使用鼠标拖拽在画布上选取区域可放大，侧边栏可调整迭代次数、颜色映射，执行平移、撤销/重做、重置视角，以及保存/加载参数或导出 PNG 图像。

## 常见问题
- 若启动时报找不到类或方法，确认已在项目根目录执行编译命令，并使用 `-cp src` 作为类路径。
- 导出或保存文件时如遇权限问题，请选择当前用户有写权限的目录。
