package com.codecoach.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AIService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=";

    public String getAIResponse(String problem, String code, String action) {

        String prompt = buildPrompt(problem, code, action);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                GEMINI_URL + geminiApiKey,
                HttpMethod.POST,
                request,
                Map.class
        );

        Map body = response.getBody();

        if (body == null) {
            throw new RuntimeException("Empty response from Gemini");
        }

        List candidates = (List) body.get("candidates");

        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Gemini returned no response");
        }

        Map candidate = (Map) candidates.get(0);
        Map content = (Map) candidate.get("content");

        if (content == null) {
            throw new RuntimeException("Gemini response content is empty");
        }

        List parts = (List) content.get("parts");

        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("Gemini response parts are empty");
        }

        Map firstPart = (Map) parts.get(0);

        if (firstPart.get("text") == null) {
            throw new RuntimeException("Gemini response text is empty");
        }

        return firstPart.get("text").toString();
    }

    private String buildPrompt(
            String problem,
            String code,
            String action
    ) {

        String instruction;

        switch (action.toLowerCase()) {

            case "hint":
                instruction =
                        "Give the student a helpful hint. " +
                                "Do not give the complete solution.";
                break;

            case "explain":
                instruction =
                        "Explain the problem clearly in beginner-friendly language.";
                break;

            case "review":
                instruction =
                        "Review the student's code and point out mistakes " +
                                "and possible improvements.";
                break;

            case "debug":
                instruction =
                        "Help debug the student's code. Identify bugs " +
                                "and explain how to fix them.";
                break;

            default:
                instruction =
                        "Help the student solve the coding problem.";
        }

        return """
                You are CodeCoach AI, a programming tutor.

                %s

                PROBLEM:
                %s

                STUDENT CODE:
                %s

                Give a clear and concise answer.
                Teach the student instead of blindly giving the answer.
                """.formatted(
                instruction,
                problem,
                code.isBlank() ? "(No code written yet)" : code
        );
    }
}