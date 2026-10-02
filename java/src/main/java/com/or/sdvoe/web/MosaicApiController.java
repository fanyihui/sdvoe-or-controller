package com.or.sdvoe.web;

import com.or.sdvoe.domain.MosaicLayout;
import com.or.sdvoe.workspace.ActiveMosaic;
import com.or.sdvoe.workspace.MosaicService;
import com.or.sdvoe.workspace.SurgeryWorkspaceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/or")
public class MosaicApiController {

    private final MosaicService mosaicService;
    private final SurgeryWorkspaceService workspaceService;

    public MosaicApiController(MosaicService mosaicService, SurgeryWorkspaceService workspaceService) {
        this.mosaicService = mosaicService;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/mosaic/layouts")
    public Map<String, Object> layouts() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(
                "layouts",
                mosaicService.listLayouts().stream().map(MosaicLayout::toMap).toList());
        return body;
    }

    @GetMapping("/cases/{caseId}/mosaics")
    public Map<String, Object> list(@PathVariable String caseId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseId", caseId);
        body.put(
                "mosaics",
                mosaicService.listMosaics(caseId).stream().map(ActiveMosaic::toMap).toList());
        return body;
    }

    @PostMapping("/cases/{caseId}/mosaics")
    public Map<String, Object> create(
            @PathVariable String caseId, @RequestBody MosaicCreateRequest request) {
        if (request.layoutId() == null || request.layoutId().isBlank()) {
            throw new IllegalArgumentException("layoutId required");
        }
        ActiveMosaic mosaic = mosaicService.createMosaic(
                caseId, request.layoutId(), request.name(), request.cells());
        return ok(caseId, mosaic);
    }

    @PutMapping("/cases/{caseId}/mosaics/{mosaicId}/cells")
    public Map<String, Object> updateCells(
            @PathVariable String caseId,
            @PathVariable String mosaicId,
            @RequestBody MosaicCellsRequest request) {
        if (request.cells() == null) {
            throw new IllegalArgumentException("cells required");
        }
        ActiveMosaic mosaic = mosaicService.updateCells(caseId, mosaicId, request.cells());
        return ok(caseId, mosaic);
    }

    @PostMapping("/cases/{caseId}/mosaics/{mosaicId}/push")
    public Map<String, Object> push(
            @PathVariable String caseId,
            @PathVariable String mosaicId,
            @RequestBody MosaicPushRequest request) {
        if (request.destinationId() == null || request.destinationId().isBlank()) {
            throw new IllegalArgumentException("destinationId required");
        }
        ActiveMosaic mosaic = mosaicService.push(
                caseId,
                mosaicId,
                request.destinationId(),
                request.operator(),
                Boolean.TRUE.equals(request.confirmed()));
        return ok(caseId, mosaic);
    }

    @DeleteMapping("/cases/{caseId}/mosaics/{mosaicId}/push")
    public Map<String, Object> stopPush(
            @PathVariable String caseId, @PathVariable String mosaicId) {
        mosaicService.stopPush(caseId, mosaicId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("workspace", workspaceService.getWorkspace(caseId).toMap());
        return body;
    }

    @DeleteMapping("/cases/{caseId}/mosaics/{mosaicId}")
    public Map<String, Object> delete(
            @PathVariable String caseId, @PathVariable String mosaicId) {
        mosaicService.deleteMosaic(caseId, mosaicId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("workspace", workspaceService.getWorkspace(caseId).toMap());
        return body;
    }

    private Map<String, Object> ok(String caseId, ActiveMosaic mosaic) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("mosaic", mosaic.toMap());
        body.put("workspace", workspaceService.getWorkspace(caseId).toMap());
        return body;
    }

    public record MosaicCreateRequest(
            String layoutId, String name, List<Map<String, Object>> cells) {
    }

    public record MosaicCellsRequest(List<Map<String, Object>> cells) {
    }

    public record MosaicPushRequest(String destinationId, String operator, Boolean confirmed) {
    }
}
