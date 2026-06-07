package com.capstoneproject.codereviewsystem.services.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewResultParser {

    private final ObjectMapper objectMapper;

    private static final Pattern FENCE = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private static final Pattern JSON_ARRAY = Pattern.compile(
            "\\[\\s*\\{[\\s\\S]*?\\}\\s*\\]");


    public Map<String, List<FileReviewService.ParsedError>> parse(
            String aiResponse, List<String> stagedFiles) {

        Map<String, List<FileReviewService.ParsedError>> result = new LinkedHashMap<>();

        for (String f : stagedFiles) result.put(f, new ArrayList<>());

        if (aiResponse == null || aiResponse.isBlank()) {
            log.warn("AI returned empty response — treating all files as clean");
            return result;
        }

        String json = extractJson(aiResponse);
        if (json == null) {
            log.warn("Could not extract JSON from AI response: {}",
                    aiResponse.length() > 200 ? aiResponse.substring(0, 200) + "..." : aiResponse);
            return result;
        }

        try {
            List<Map<String, Object>> issues = objectMapper.readValue(
                    json, new TypeReference<>() {});

            for (Map<String, Object> issue : issues) {
                FileReviewService.ParsedError err = toError(issue);
                if (err == null) continue;
                result.computeIfAbsent(err.filePath(), k -> new ArrayList<>()).add(err);
            }

            int total = result.values().stream().mapToInt(List::size).sum();
            log.info("Parsed AI response: {} issues across {} files", total, result.size());

        } catch (Exception e) {
            log.error("Failed to parse AI JSON: {} | raw snippet: {}", e.getMessage(),
                    json.length() > 300 ? json.substring(0, 300) : json);
        }

        return result;
    }


    private String extractJson(String raw) {
        Matcher fenceMatcher = FENCE.matcher(raw);
        if (fenceMatcher.find()) {
            String inner = fenceMatcher.group(1).trim();
            if (inner.startsWith("[")) return inner;
        }

        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) return trimmed;

        Matcher arrayMatcher = JSON_ARRAY.matcher(raw);
        if (arrayMatcher.find()) return arrayMatcher.group();

        return null;
    }


    private FileReviewService.ParsedError toError(Map<String, Object> m) {
        String filePath = stringOrNull(m, "filePath");
        if (filePath == null || filePath.isBlank()) {
            log.debug("Skipping issue with null/blank filePath");
            return null;
        }

        int line    = intOrDefault(m, "line",   0);
        int column  = intOrDefault(m, "column", 0);
        String severity  = defaultSeverity(stringOrNull(m, "severity"));
        String message   = stringOrDefault(m, "message", "No message provided");
        String ruleId    = stringOrNull(m, "ruleId");
        String suggestion = stringOrNull(m, "suggestion");

        return new FileReviewService.ParsedError(
                filePath, line, column, severity, message, ruleId, suggestion);
    }

    private String stringOrNull(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private String stringOrDefault(Map<String, Object> m, String key, String def) {
        String v = stringOrNull(m, key);
        return (v != null && !v.isBlank()) ? v : def;
    }

    private int intOrDefault(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.toString().trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private String defaultSeverity(String raw) {
        if (raw == null) return "MEDIUM";
        return switch (raw.toUpperCase().trim()) {
            case "CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO" -> raw.toUpperCase().trim();
            default -> "MEDIUM";
        };
    }
}
