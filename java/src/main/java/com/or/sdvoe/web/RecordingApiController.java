package com.or.sdvoe.web;

import com.or.sdvoe.workspace.ActiveRecording;
import com.or.sdvoe.workspace.RecordingService;
import com.or.sdvoe.workspace.SurgeryWorkspaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/or/cases/{caseId}/recordings")
public class RecordingApiController {

    private final RecordingService recordingService;
    private final SurgeryWorkspaceService workspaceService;

    public RecordingApiController(
            RecordingService recordingService, SurgeryWorkspaceService workspaceService) {
        this.recordingService = recordingService;
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public Map<String, Object> list(@PathVariable String caseId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseId", caseId);
        body.put(
                "recordings",
                recordingService.listRecordings(caseId).stream().map(ActiveRecording::toMap).toList());
        ActiveRecording active = recordingService.getActive(caseId);
        body.put("activeRecording", active == null ? null : active.toMap());
        return body;
    }

    @GetMapping("/active")
    public Map<String, Object> active(@PathVariable String caseId) {
        Map<String, Object> body = new LinkedHashMap<>();
        ActiveRecording active = recordingService.getActive(caseId);
        body.put("activeRecording", active == null ? null : active.toMap());
        return body;
    }

    @PostMapping
    public Map<String, Object> start(
            @PathVariable String caseId, @RequestBody StartRequest request) {
        if (request.sourceId() == null || request.sourceId().isBlank()) {
            throw new IllegalArgumentException("sourceId required");
        }
        ActiveRecording recording = recordingService.start(caseId, request.sourceId(), request.operator());
        return ok(caseId, recording);
    }

    @PostMapping("/{sessionId}/stop")
    public Map<String, Object> stop(@PathVariable String caseId, @PathVariable String sessionId) {
        ActiveRecording recording = recordingService.stop(caseId, sessionId);
        return ok(caseId, recording);
    }

    private Map<String, Object> ok(String caseId, ActiveRecording recording) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("recording", recording.toMap());
        body.put("workspace", workspaceService.getWorkspace(caseId).toMap());
        return body;
    }

    public record StartRequest(String sourceId, String operator) {
    }
}
