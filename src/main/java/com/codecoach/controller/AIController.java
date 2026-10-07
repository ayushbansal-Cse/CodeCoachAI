package com.codecoach.controller;

import com.codecoach.service.AIService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/review")
    public ResponseEntity<?> reviewCode(@RequestBody AIRequest request) {

        try {

            String answer = aiService.getAIResponse(
                    request.getProblem(),
                    request.getCode(),
                    request.getAction()
            );

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "response", answer
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "success", false,
                                    "error",
                                    "AI service error: " + e.getMessage()
                            )
                    );
        }
    }

    public static class AIRequest {

        private String code;
        private String problem;
        private String action;

        public AIRequest() {
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getProblem() {
            return problem;
        }

        public void setProblem(String problem) {
            this.problem = problem;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }
    }
}