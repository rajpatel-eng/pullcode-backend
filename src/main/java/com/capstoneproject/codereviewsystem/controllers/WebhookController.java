package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.services.webhook.WebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;

    @PostMapping("/github")
    public ResponseEntity<String> githubWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestHeader(value = "X-Hub-Signature-256", defaultValue = "") String signature,
            @RequestBody String payload) {

        log.info("GitHub webhook received | event: {}", event);

        if (!event.equals("push")) {
            return ResponseEntity.ok("Event ignored: " + event);
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            webhookService.processGithubPush(root, signature, payload);
            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("GitHub webhook failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Failed: " + e.getMessage());
        }
    }

    @PostMapping("/gitlab")
    public ResponseEntity<String> gitlabWebhook(
            @RequestHeader(value = "X-Gitlab-Event", defaultValue = "") String event,
            // FIX: extract GitLab secret token from header
            @RequestHeader(value = "X-Gitlab-Token", defaultValue = "") String token,
            @RequestBody String payload) {

        log.info("GitLab webhook received | event: {}", event);

        if (!event.equals("Push Hook")) {
            return ResponseEntity.ok("Event ignored: " + event);
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            webhookService.processGitlabPush(root, token);
            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("GitLab webhook failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Failed: " + e.getMessage());
        }
    }

    @PostMapping("/bitbucket")
    public ResponseEntity<String> bitbucketWebhook(
            @RequestHeader(value = "X-Event-Key", defaultValue = "") String event,
            @RequestBody String payload) {

        log.info("Bitbucket webhook received | event: {}", event);

        if (!event.equals("repo:push")) {
            return ResponseEntity.ok("Event ignored: " + event);
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            webhookService.processBitbucketPush(root);
            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("Bitbucket webhook failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Failed: " + e.getMessage());
        }
    }
}