package com.or.sdvoe.web;

import com.or.sdvoe.workspace.MosaicConflictException;
import com.or.sdvoe.workspace.RecordingConflictException;
import com.or.sdvoe.workspace.RouteConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RouteConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(RouteConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("conflict", true);
        body.put("error", ex.getMessage());
        body.put("existing", ex.getExisting().toMap());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MosaicConflictException.class)
    public ResponseEntity<Map<String, Object>> mosaicConflict(MosaicConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("conflict", true);
        body.put("error", ex.getMessage());
        body.put("existingKind", ex.getExistingKind());
        body.put("destinationId", ex.getDestinationId());
        body.put("existing", ex.getExisting());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(RecordingConflictException.class)
    public ResponseEntity<Map<String, Object>> recordingConflict(RecordingConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("conflict", true);
        body.put("error", ex.getMessage());
        body.put("existing", ex.getExisting().toMap());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> unprocessable(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
