package com.capstoneproject.codereviewsystem.services.review;

import com.capstoneproject.codereviewsystem.entity.AiModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final RestTemplate restTemplate;

    public String review(AiModel model, String apiKey, String prompt) {
        String url          = buildUrl(model);
        HttpEntity<Map<String, Object>> request = buildRequest(model, apiKey, prompt);

        log.debug("Calling AI endpoint: model={} provider={} url={}",
                model.getName(), model.getProvider(), url);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);

            return extractContent(response.getBody(), model);

        } catch (HttpClientErrorException e) {
            throw new AiCallException(
                    "AI provider rejected the request [" + e.getStatusCode() + "]: "
                    + summarise(e.getResponseBodyAsString()), e);

        } catch (HttpServerErrorException e) {
            throw new AiCallException(
                    "AI provider returned a server error [" + e.getStatusCode() + "]: "
                    + summarise(e.getResponseBodyAsString()), e);

        } catch (Exception e) {
            throw new AiCallException(
                    "Unexpected error calling AI provider (" + model.getProvider() + "): "
                    + e.getMessage(), e);
        }
    }

    private String buildUrl(AiModel model) {
        if (model.getApiBaseUrl() == null || model.getApiBaseUrl().isBlank()) {
            throw new AiCallException(
                "apiBaseUrl is not configured for model \"" + model.getName() + "\" ("
                + model.getProvider() + ") — set it in the Admin panel before using this model");
        }
        String base = model.getApiBaseUrl().strip().replaceAll("/+$", "");
        return base + "/v1/chat/completions";
    }

    private HttpEntity<Map<String, Object>> buildRequest(
            AiModel model, String apiKey, String userPrompt) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = buildBody(model, userPrompt);
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> buildBody(AiModel model, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();

        if (model.getSystemPrompt() != null && !model.getSystemPrompt().isBlank()) {
            messages.add(Map.of(
                    "role",    "system",
                    "content", model.getSystemPrompt()
            ));
        }

        messages.add(Map.of(
                "role",    "user",
                "content", userPrompt
        ));

        return Map.of(
                "model",       model.getName(),
                "messages",    messages,
                "temperature", model.effectiveTemperature(),
                "max_tokens",  model.effectiveMaxTokens()
        );
    }

    private String extractContent(Map<?, ?> body, AiModel model) {
        if (body == null) {
            throw new AiCallException(
                    "AI provider (" + model.getProvider() + ") returned an empty response body");
        }

        Object choicesRaw = body.get("choices");
        if (!(choicesRaw instanceof List<?> choices) || choices.isEmpty()) {
            throw new AiCallException(
                    "AI response from " + model.getProvider()
                    + " contained no choices — full body: " + summariseBody(body));
        }

        Object firstRaw = choices.get(0);
        if (!(firstRaw instanceof Map<?, ?> first)) {
            throw new AiCallException("choices[0] is not a JSON object");
        }

        Object messageRaw = first.get("message");
        if (!(messageRaw instanceof Map<?, ?> message)) {
            throw new AiCallException("choices[0].message is missing or not a JSON object");
        }

        Object content = message.get("content");
        if (content == null) {
            throw new AiCallException("choices[0].message.content is null");
        }

        String text = content.toString();
        log.debug("AI response received: model={} provider={} responseLength={}",
                model.getName(), model.getProvider(), text.length());
        return text;
    }


    private String summarise(String body) {
        if (body == null) return "(empty)";
        return body.length() > 300 ? body.substring(0, 300) + "..." : body;
    }

    private String summariseBody(Map<?, ?> body) {
        String s = body.toString();
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }


    public static class AiCallException extends RuntimeException {
        public AiCallException(String message) { super(message); }
        public AiCallException(String message, Throwable cause) { super(message, cause); }
    }
}