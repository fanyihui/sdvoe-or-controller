package com.or.sdvoe.workspace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 工作空间内一条拼屏配置（可推送到目的地）。 */
public final class ActiveMosaic {

    public enum Status {
        DRAFT,
        PUSHED
    }

    private final String mosaicId;
    private final String caseId;
    private final String layoutId;
    private final String layoutName;
    private final int rows;
    private final int cols;
    private final String name;
    private final List<Cell> cells;
    private final String destinationId;
    private final String destinationName;
    private final String outputStreamId;
    private final String routeId;
    private final Status status;
    private final String operator;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ActiveMosaic(
            String mosaicId,
            String caseId,
            String layoutId,
            String layoutName,
            int rows,
            int cols,
            String name,
            List<Cell> cells,
            String destinationId,
            String destinationName,
            String outputStreamId,
            String routeId,
            Status status,
            String operator,
            Instant createdAt,
            Instant updatedAt) {
        this.mosaicId = Objects.requireNonNull(mosaicId);
        this.caseId = Objects.requireNonNull(caseId);
        this.layoutId = Objects.requireNonNull(layoutId);
        this.layoutName = layoutName;
        this.rows = rows;
        this.cols = cols;
        this.name = name == null || name.isBlank() ? layoutName : name;
        this.cells = List.copyOf(cells);
        this.destinationId = destinationId;
        this.destinationName = destinationName;
        this.outputStreamId = outputStreamId;
        this.routeId = routeId;
        this.status = status == null ? Status.DRAFT : status;
        this.operator = operator;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public String getMosaicId() {
        return mosaicId;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getLayoutId() {
        return layoutId;
    }

    public String getLayoutName() {
        return layoutName;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public String getName() {
        return name;
    }

    public List<Cell> getCells() {
        return cells;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public String getOutputStreamId() {
        return outputStreamId;
    }

    public String getRouteId() {
        return routeId;
    }

    public Status getStatus() {
        return status;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isPushed() {
        return status == Status.PUSHED && destinationId != null;
    }

    public ActiveMosaic withCells(List<Cell> newCells) {
        return new ActiveMosaic(
                mosaicId,
                caseId,
                layoutId,
                layoutName,
                rows,
                cols,
                name,
                newCells,
                destinationId,
                destinationName,
                outputStreamId,
                routeId,
                status,
                operator,
                createdAt,
                Instant.now());
    }

    public ActiveMosaic withPush(
            String destId,
            String destName,
            String streamId,
            String newRouteId,
            String op) {
        return new ActiveMosaic(
                mosaicId,
                caseId,
                layoutId,
                layoutName,
                rows,
                cols,
                name,
                cells,
                destId,
                destName,
                streamId,
                newRouteId,
                Status.PUSHED,
                op,
                createdAt,
                Instant.now());
    }

    public ActiveMosaic clearedPush() {
        return new ActiveMosaic(
                mosaicId,
                caseId,
                layoutId,
                layoutName,
                rows,
                cols,
                name,
                cells,
                null,
                null,
                null,
                null,
                Status.DRAFT,
                operator,
                createdAt,
                Instant.now());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mosaicId", mosaicId);
        m.put("caseId", caseId);
        m.put("layoutId", layoutId);
        m.put("layoutName", layoutName);
        m.put("rows", rows);
        m.put("cols", cols);
        m.put("name", name);
        List<Map<String, Object>> cellMaps = new ArrayList<>();
        for (Cell c : cells) {
            cellMaps.add(c.toMap());
        }
        m.put("cells", cellMaps);
        m.put("destinationId", destinationId);
        m.put("destinationName", destinationName);
        m.put("outputStreamId", outputStreamId);
        m.put("routeId", routeId);
        m.put("status", status.name());
        m.put("pushed", isPushed());
        m.put("operator", operator);
        m.put("createdAt", createdAt.toString());
        m.put("updatedAt", updatedAt.toString());
        return m;
    }

    public record Cell(
            int index,
            int row,
            int col,
            int rowSpan,
            int colSpan,
            String sourceId,
            String sourceName,
            String deviceId,
            String streamId) {

        public Cell {
            if (rowSpan < 1) {
                rowSpan = 1;
            }
            if (colSpan < 1) {
                colSpan = 1;
            }
        }

        public boolean hasSource() {
            return sourceId != null && !sourceId.isBlank();
        }

        public Cell withSource(String sid, String sname, String did, String stream) {
            return new Cell(index, row, col, rowSpan, colSpan, sid, sname, did, stream);
        }

        public Cell withoutSource() {
            return new Cell(index, row, col, rowSpan, colSpan, null, null, null, null);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", index);
            m.put("row", row);
            m.put("col", col);
            m.put("rowSpan", rowSpan);
            m.put("colSpan", colSpan);
            m.put("sourceId", sourceId);
            m.put("sourceName", sourceName);
            m.put("deviceId", deviceId);
            m.put("streamId", streamId);
            m.put("assigned", hasSource());
            return m;
        }
    }
}
