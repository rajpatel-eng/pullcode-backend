package com.capstoneproject.codereviewsystem.services.review;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AiReviewPromptBuilder {

    private static final int PER_FILE_CHAR_LIMIT = 15_000;
    private static final int TOTAL_CHAR_LIMIT    = 120_000;

    private static final String INSTRUCTION = """
            You are an expert code reviewer. Analyze the [CHANGED] files below for bugs, \
            security vulnerabilities, performance issues, code quality problems, and best practice violations.

            For each issue found, produce a JSON object with these exact fields:
              filePath  (string)  — relative path of the file
              line      (int)     — line number where the issue occurs (1-indexed; 0 if unknown)
              column    (int)     — column number (0 if unknown)
              severity  (string)  — one of: CRITICAL, HIGH, MEDIUM, LOW, INFO
              message   (string)  — clear description of the issue
              ruleId    (string)  — short rule identifier e.g. "NULL_DEREF", "SQL_INJECTION" (nullable)
              suggestion (string) — how to fix it (nullable)

            Return ONLY a JSON array of issue objects — no markdown, no explanation, no preamble.
            If a file has no issues, do not include it in the array.
            Example: [{"filePath":"src/Foo.java","line":42,"column":0,"severity":"HIGH","message":"...","ruleId":"...","suggestion":"..."}]
            """;


    public String build(Map<String, String> fileContents, List<String> changedFiles) {
        Set<String> changedSet = new HashSet<>(changedFiles);

        Map<String, String> changed = new LinkedHashMap<>();
        Map<String, String> context = new LinkedHashMap<>();

        for (var entry : fileContents.entrySet()) {
            String path = entry.getKey();
            String content = truncate(entry.getValue(), PER_FILE_CHAR_LIMIT, path);
            if (changedSet.contains(path)) changed.put(path, content);
            else                           context.put(path, content);
        }

        StringBuilder sb = new StringBuilder(INSTRUCTION.length() + TOTAL_CHAR_LIMIT);
        sb.append(INSTRUCTION).append("\n\n");

        int usedChars = sb.length();

        for (var entry : changed.entrySet()) {
            String block = formatFile("[CHANGED]", entry.getKey(), entry.getValue());
            sb.append(block);
            usedChars += block.length();
        }

        for (var entry : context.entrySet()) {
            String block = formatFile("[CONTEXT]", entry.getKey(), entry.getValue());
            if (usedChars + block.length() > TOTAL_CHAR_LIMIT) {
                log.debug("Total prompt cap reached — dropping context file: {}", entry.getKey());
                continue;
            }
            sb.append(block);
            usedChars += block.length();
        }

        log.debug("Prompt built: totalChars={} changedFiles={} contextFiles={}",
                usedChars, changed.size(), context.size());
        return sb.toString();
    }

    private String formatFile(String label, String path, String content) {
        return label + " " + path + "\n```\n" + content + "\n```\n\n";
    }

    private String truncate(String content, int maxChars, String path) {
        if (content == null) return "";
        if (content.length() <= maxChars) return content;
        log.debug("File truncated at {} chars: {}", maxChars, path);
        return content.substring(0, maxChars)
                + "\n... [truncated at " + maxChars + " chars]";
    }
}
