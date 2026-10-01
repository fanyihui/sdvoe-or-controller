package com.or.sdvoe.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 拼屏布局预设：定义网格行列与每个格子的位置/跨度。
 * 支持 1×1、2×2、3×3、4×4 及常见手术室布局。
 */
public final class MosaicLayout {

    private final String id;
    private final String name;
    private final int rows;
    private final int cols;
    private final List<CellTemplate> cells;

    public MosaicLayout(String id, String name, int rows, int cols, List<CellTemplate> cells) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.rows = rows;
        this.cols = cols;
        this.cells = List.copyOf(cells);
        if (rows < 1 || cols < 1) {
            throw new IllegalArgumentException("rows/cols must be >= 1");
        }
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("layout must have at least one cell");
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public List<CellTemplate> getCells() {
        return cells;
    }

    public int cellCount() {
        return cells.size();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("rows", rows);
        m.put("cols", cols);
        m.put("cellCount", cells.size());
        List<Map<String, Object>> cellMaps = new ArrayList<>();
        for (CellTemplate c : cells) {
            cellMaps.add(c.toMap());
        }
        m.put("cells", cellMaps);
        return m;
    }

    /** 格子模板（未绑定视频源）。 */
    public record CellTemplate(int index, int row, int col, int rowSpan, int colSpan) {
        public CellTemplate {
            if (index < 0 || row < 0 || col < 0 || rowSpan < 1 || colSpan < 1) {
                throw new IllegalArgumentException("invalid cell template");
            }
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", index);
            m.put("row", row);
            m.put("col", col);
            m.put("rowSpan", rowSpan);
            m.put("colSpan", colSpan);
            return m;
        }
    }

    /** 内置布局预设。 */
    public static List<MosaicLayout> presets() {
        return List.of(
                grid("1x1", "1×1 单画面", 1, 1),
                grid("2x1", "2×1 左右分屏", 1, 2),
                grid("1x2", "1×2 上下分屏", 2, 1),
                grid("2x2", "2×2 四宫格", 2, 2),
                layout1Plus3(),
                grid("3x3", "3×3 九宫格", 3, 3),
                grid("4x4", "4×4 十六宫格", 4, 4));
    }

    public static MosaicLayout byId(String layoutId) {
        return presets().stream()
                .filter(l -> l.getId().equals(layoutId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown mosaic layout: " + layoutId));
    }

    private static MosaicLayout grid(String id, String name, int rows, int cols) {
        List<CellTemplate> cells = new ArrayList<>();
        int index = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells.add(new CellTemplate(index++, r, c, 1, 1));
            }
        }
        return new MosaicLayout(id, name, rows, cols, cells);
    }

    /** 左侧大画面 + 右侧三路小画面。 */
    private static MosaicLayout layout1Plus3() {
        List<CellTemplate> cells = List.of(
                new CellTemplate(0, 0, 0, 3, 2),
                new CellTemplate(1, 0, 2, 1, 1),
                new CellTemplate(2, 1, 2, 1, 1),
                new CellTemplate(3, 2, 2, 1, 1));
        return new MosaicLayout("1+3", "1+3 主辅拼屏", 3, 3, cells);
    }
}
