package com.or.sdvoe.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P0 Stub：模拟软件录制 Worker。
 * 在输出目录写入占位文件，不真正拉流；P1 再接 FFmpeg/GStreamer。
 */
public class SoftwareRecorderAdapter implements RecorderControlPort {

    private static final Logger log = LoggerFactory.getLogger(SoftwareRecorderAdapter.class);

    private final String targetId;
    private final Path outputDir;
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public SoftwareRecorderAdapter(String targetId, Path outputDir) {
        this.targetId = targetId == null || targetId.isBlank() ? "software-recorder-stub" : targetId;
        this.outputDir = outputDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.outputDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create recording output dir: " + this.outputDir, e);
        }
    }

    @Override
    public String targetId() {
        return targetId;
    }

    @Override
    public StartResult start(StartRequest request) {
        if (request == null || request.sessionId() == null) {
            return StartResult.failure("sessionId required");
        }
        if (jobs.containsKey(request.sessionId())) {
            return StartResult.failure("session already recording: " + request.sessionId());
        }
        Path file = resolveOutputPath(request);
        try {
            Files.createDirectories(file.getParent());
            String placeholder = """
                    OR Desk software recording stub
                    sessionId=%s
                    orId=%s
                    caseId=%s
                    sourceId=%s
                    streamId=%s
                    startedAt=%s
                    """.formatted(
                    request.sessionId(),
                    request.orId(),
                    request.caseId(),
                    request.sourceId(),
                    request.streamId(),
                    Instant.now());
            Files.writeString(file, placeholder, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return StartResult.failure("failed to create artifact: " + e.getMessage());
        }
        String uri = file.toUri().toString();
        jobs.put(request.sessionId(), new Job(request.sessionId(), uri, Instant.now()));
        log.info("Stub recorder started session={} file={}", request.sessionId(), file);
        return StartResult.success(uri);
    }

    @Override
    public StopResult stop(String sessionId) {
        Job job = jobs.remove(sessionId);
        if (job == null) {
            return StopResult.failure("session not active in worker: " + sessionId);
        }
        long bytes = 0L;
        try {
            Path path = Path.of(java.net.URI.create(job.artifactUri()));
            if (Files.isRegularFile(path)) {
                String footer = "\nstoppedAt=" + Instant.now() + "\n";
                Files.writeString(
                        path,
                        Files.readString(path, StandardCharsets.UTF_8) + footer,
                        StandardCharsets.UTF_8);
                bytes = Files.size(path);
            }
        } catch (Exception e) {
            log.warn("Failed to finalize stub artifact {}: {}", job.artifactUri(), e.getMessage());
        }
        log.info("Stub recorder stopped session={} bytes={}", sessionId, bytes);
        return StopResult.success(job.artifactUri(), bytes);
    }

    @Override
    public boolean isActive(String sessionId) {
        return jobs.containsKey(sessionId);
    }

    private Path resolveOutputPath(StartRequest request) {
        if (request.outputUriHint() != null && !request.outputUriHint().isBlank()) {
            String hint = request.outputUriHint();
            if (hint.startsWith("file:")) {
                return Path.of(java.net.URI.create(hint));
            }
            return Path.of(hint);
        }
        return outputDir
                .resolve(safe(request.orId()))
                .resolve(safe(request.caseId()))
                .resolve(safe(request.sessionId()) + ".mkv.stub");
    }

    private static String safe(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record Job(String sessionId, String artifactUri, Instant startedAt) {
    }

    /** 测试/运维可见的活跃任务快照。 */
    public Map<String, String> activeJobs() {
        Map<String, String> snap = new ConcurrentHashMap<>();
        jobs.forEach((id, job) -> snap.put(id, job.artifactUri()));
        return Map.copyOf(snap);
    }
}
