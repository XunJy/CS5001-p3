# CS5001-p3 Mandelbrot Explorer

A Swing-based Mandelbrot set explorer with drag-to-zoom, mouse-drag panning, undo/redo, colour scheme switching, parameter save/load, and PNG export.

## Build and run first
1. Compile from the project root:
   ```bash
   javac src/*.java
   ```
2. Launch the GUI:
   ```bash
   java -cp src MandelbrotExplorer
   ```

## Program structure and design
### Overall architecture
The program follows a lightweight MVC-style separation:
- **Model (`MandelbrotModel`)** stores the complex-plane bounds, iteration limit, colour scheme, and other settings. It performs background rendering and maintains undo/redo history plus save/load/export logic.
- **View (`MandelbrotPanel`)** displays the rendered `BufferedImage`, draws zoom rectangles, shows the zoom factor overlay, and translates mouse gestures into screen coordinates.
- **Controller (`MandelbrotExplorer`)** builds the Swing window and side controls, wiring user actions to model methods.
- **Pure computation (`MandelbrotCalculator`)** exposes constants for initial bounds/iteration limits and computes iteration counts without any GUI dependencies.
- **Colour mapping (`ColorScheme`)** enumerates gradients that map iteration counts to RGB values.

### Object-oriented design
- **Encapsulation:** Rendering parameters and history are private to the model and accessed through methods, preventing UI code from coupling to internal state.
- **Responsibility-driven classes:** Each class owns one concern (calculation, rendering state, drawing, or UI wiring), enabling simpler testing and changes.
- **Reuse and extension:** The calculator and colour-mapping enum are independent of Swing, making it easy to plug in new palettes or reuse the math elsewhere.

### Model–view separation (MVC rationale)
- The model emits `PropertyChangeEvent`s for image/overlay updates. The panel listens and repaints without knowing how the data was produced, keeping computation off the EDT.
- User input is confined to the view/controller; the model only receives interpreted parameters (bounds, iteration counts, palette choices). This separation prevents rendering logic from depending on Swing components.
- Undo/redo stacks and persistence live in the model so that GUI code remains thin; alternative UIs could reuse the same model unchanged.

### Class responsibilities and interactions
- `MandelbrotCalculator`: calculates iteration counts for a given resolution and complex-plane window using configurable max iterations and escape radius.
- `MandelbrotModel`: owns current bounds, max iterations, radius, overlay flag, palette, image buffer, and render size. It schedules background rendering, maps iteration grids to colours, manages undo/redo stacks, and handles save/load/export. It notifies listeners when images or overlay state change.
- `MandelbrotPanel`: listens to model events, paints the buffered image, draws zoom rectangles, shows zoom factor text, and interprets mouse drags. Left-drag selects a zoom box; right/middle drag translates into a pan request. While panning, it shows a live translated preview of the current image.
- `MandelbrotExplorer`: assembles the frame, panel, and side controls (iteration Apply button, palette selector, zoom-factor toggle, pan buttons and text field, undo/redo/reset, save/load, export). It delegates actions to the model and displays dialogs for file operations.
- `ColorScheme`: converts iteration counts to colours via selectable gradients (grayscale, blue gradient, fire-inspired).

### Why this design?
- **Clarity under change:** Keeping rendering math and history in the model means UI adjustments (layout, new buttons, alternate input methods) do not risk breaking computation.
- **Smooth interaction:** Background rendering via `SwingWorker` keeps the EDT responsive; the panel’s live preview during panning gives immediate feedback without recomputing.
- **Recoverability:** Centralised undo/redo around parameter snapshots ensures every zoom/pan/parameter tweak can be reverted, supporting exploratory use.
- **Extensibility:** Decoupled colour-mapping and calculator components make it straightforward to add new palettes or reuse the core calculations in other contexts.

## Basic requirements (implementation summary)
- **Zoom (drag select):** The panel captures a left-button drag rectangle, converts its screen bounds to complex-plane bounds using pixels-per-unit ratios, and asks the model to render the new window.
- **Pan (mouse drag):** Right or middle button drags are locked into pan mode. The panel computes pixel deltas, shows a live shifted preview, and the model converts deltas into complex-plane offsets before re-rendering.
- **Max iterations adjustment:** A spinner plus Apply button collects the desired iteration cap. When Apply is clicked, the model saves history, updates the value, clears redo, and re-renders for sharper detail when zoomed.
- **Undo/redo:** The model stores parameter snapshots on a bounded stack before each change. Undo swaps in the previous snapshot and pushes the current one to redo; redo performs the reverse and re-renders.
- **Show zoom factor:** When enabled, the panel overlays the current zoom level computed by comparing the initial real-axis span to the active span. The overlay state survives resets if it was previously on.

## Enhancements implemented
- **Multiple colour maps:** The `ColorScheme` enum maps iteration counts to grayscale, blue-to-white, red-to-white, or fire gradients. Palette toggle buttons on the control panel trigger a re-render so colours update consistently.
- **Parameter save/load:** Model methods serialize bounds, iterations, radius, overlay flag, and palette to a properties file and restore them later, making exploration resumable.
- **PNG export:** The current rendered `BufferedImage` can be written to disk via `ImageIO`, preserving the on-screen view.
- **Extended undo/redo/reset:** History covers zoom, pan, palette, iteration limit, overlay toggle, and load operations; reset restores all defaults while respecting an active overlay toggle.
- **Responsive panning preview:** During right/middle drags the panel translates the existing image instead of triggering recalculation, giving a smooth preview before the model renders the new view.

## Usage tips
- Left-drag to zoom into a rectangle; right or middle drag to pan.
- Use the spinner + Apply button to increase iterations after zooming for sharper edges.
- Toggle “Show zoom factor” to keep a visible indicator of magnification.
- Save parameters to revisit interesting areas later, and export a PNG of any rendered view.

## Common issues
- If classes are not found, ensure you compiled from the project root and ran with `-cp src`.
- For save/export errors, choose a directory where you have write permissions.
