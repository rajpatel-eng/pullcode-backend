package com.capstoneproject.codereviewsystem.kafka.consumer;

import com.capstoneproject.codereviewsystem.kafka.KafkaTopics;
import com.capstoneproject.codereviewsystem.kafka.events.ReviewSubmittedEvent;
import com.capstoneproject.codereviewsystem.services.storage.StorageProvider;
import com.capstoneproject.codereviewsystem.sse.ReviewProgressEvent;
import com.capstoneproject.codereviewsystem.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityScanConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SseEmitterRegistry sseRegistry;
    private final StorageProvider storageProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.storage.local.base-path:uploads}")
    private String basePath;

    private static final long   MAX_SINGLE_FILE_BYTES  = 50L  * 1024 * 1024;
    private static final long   MAX_TOTAL_BYTES        = 500L * 1024 * 1024;
    private static final int    MAX_FILE_COUNT         = 10_000;
    private static final int    MAX_DEPTH              = 15;

    @KafkaListener(
            topics = KafkaTopics.REVIEW_SUBMITTED,
            groupId = "security-scan-group",
            containerFactory = "submittedKafkaListenerContainerFactory"
    )
    public void consume(ReviewSubmittedEvent event) {
        log.info("SecurityScan: eventId={} path={}", event.getEventId(), event.getStoragePath());
        String key = "security:" + event.getEventId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }
        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.SECURITY_SCANNING)
                .message("🔍 Scanning for security threats...")
                .source(event.getSource().name())
                .build());

        ThreatReport threat = scan(event.getStoragePath());

        if (threat.hasThreats()) {
            // Delete files
            try { storageProvider.deleteDirectory(event.getStoragePath()); }
            catch (IOException e) {
                log.error("Failed to delete threat files {}: {}", event.getStoragePath(), e.getMessage());
            }

            sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                    .stage(ReviewProgressEvent.Stage.SECURITY_THREAT)
                    .message("⚠️ Security threat detected — submission rejected. Reason: " + threat.summary())
                    .source(event.getSource().name())
                    .metadata(Map.of("threats", threat.threats()))
                    .build());

            log.warn("Threat detected eventId={}: {}", event.getEventId(), threat.summary());
            return;
        }

        int fileCount = countFiles(event.getStoragePath());

        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.SECURITY_PASSED)
                .message("✅ Security scan passed — " + fileCount + " files stored")
                .source(event.getSource().name())
                .metadata(Map.of("fileCount", fileCount))
                .build());

        kafkaTemplate.send(KafkaTopics.REVIEW_CLEAN,
                String.valueOf(event.getUserId()), event);
        redisTemplate.opsForValue().set(key,"processed",Duration.ofDays(7));
        log.info("SecurityScan passed eventId={} files={}", event.getEventId(), fileCount);
    }

    private ThreatReport scan(String relativePath) {
        List<String> threats = new ArrayList<>();
        Path root = Paths.get(basePath, relativePath.replace("/", File.separator));

        if (!Files.exists(root)) {
            threats.add("PATH_NOT_FOUND");
            return new ThreatReport(threats);
        }

        long[] totalSize = {0};
        int[]  count     = {0};

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String rel = root.relativize(dir).toString();
                    if (rel.contains("..")) {
                        threats.add("PATH_TRAVERSAL:" + rel);
                        return FileVisitResult.TERMINATE;
                    }
                    int depth = root.relativize(dir).getNameCount();
                    if (depth > MAX_DEPTH) {
                        threats.add("EXCESSIVE_DEPTH:" + depth);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isSymbolicLink()) {
                        threats.add("SYMLINK:" + root.relativize(file));
                        return FileVisitResult.TERMINATE;
                    }

                    count[0]++;
                    if (count[0] > MAX_FILE_COUNT) {
                        threats.add("EXCESSIVE_FILE_COUNT:" + count[0]);
                        return FileVisitResult.TERMINATE;
                    }

                    long size = attrs.size();
                    if (size > MAX_SINGLE_FILE_BYTES) {
                        threats.add("OVERSIZED_FILE:" + root.relativize(file));
                        return FileVisitResult.TERMINATE;
                    }

                    totalSize[0] += size;
                    if (totalSize[0] > MAX_TOTAL_BYTES) {
                        threats.add("ZIP_BOMB_TOTAL_SIZE");
                        return FileVisitResult.TERMINATE;
                    }

                    String name = file.getFileName().toString().toLowerCase();
                    if (name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".war")) {
                        threats.add("NESTED_ARCHIVE:" + root.relativize(file));
                        return FileVisitResult.TERMINATE;
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            threats.add("SCAN_ERROR:" + e.getMessage());
        }

        return new ThreatReport(threats);
    }

    private int countFiles(String relativePath) {
        Path root = Paths.get(basePath, relativePath.replace("/", File.separator));
        try (var s = Files.walk(root)) {
            return (int) s.filter(Files::isRegularFile).count();
        } catch (IOException e) { return 0; }
    }

    private record ThreatReport(List<String> threats) {
        boolean hasThreats() { return !threats.isEmpty(); }
        String summary()     { return String.join(", ", threats); }
    }
}
