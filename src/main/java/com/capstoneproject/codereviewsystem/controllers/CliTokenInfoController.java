package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.CliDtos.*;
import com.capstoneproject.codereviewsystem.entity.CliToken;
import com.capstoneproject.codereviewsystem.services.cli.CliPushService;
import com.capstoneproject.codereviewsystem.services.cli.CliTokenService;
import com.capstoneproject.codereviewsystem.services.cli.CliTokenService.TokenValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/cli")
@RequiredArgsConstructor
public class CliTokenInfoController {

    private final CliTokenService cliTokenService;
    private final CliPushService  cliPushService;


    @GetMapping("/token-info")
    public ResponseEntity<?> tokenInfo(
            @RequestHeader("X-CLI-Token") String rawToken) {

        TokenValidationResult v = cliTokenService.validateAndTouch(rawToken);
        CliToken token = v.token();

        return ResponseEntity.ok(Map.of(
                "projectId",    token.getZipProject().getId(),
                "projectTitle", token.getZipProject().getTitle(),
                "userId",       token.getUser().getId(),
                "tokenName",    token.getName()
        ));
    }

    @GetMapping("/log")
    public ResponseEntity<ProjectCliHistoryResponse> tokenLog(
            @RequestHeader("X-CLI-Token") String rawToken) {

        TokenValidationResult v = cliTokenService.validateAndTouch(rawToken);
        return ResponseEntity.ok(
                cliPushService.getCliHistory(v.project().getId(), v.user().getId()));
    }
}
