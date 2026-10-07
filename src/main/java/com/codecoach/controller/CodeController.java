package com.codecoach.controller;

import com.codecoach.service.CodeExecutionService;
import com.codecoach.service.ProblemTestCaseService;
import com.codecoach.service.ProblemTestCaseService.TestCase;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/code")
@CrossOrigin(origins = "*")
public class CodeController {

    private final CodeExecutionService executionService;
    private final ProblemTestCaseService testCaseService;

    public CodeController(
            CodeExecutionService executionService,
            ProblemTestCaseService testCaseService
    ) {
        this.executionService = executionService;
        this.testCaseService = testCaseService;
    }

    // =========================================================
    // RUN CODE
    // =========================================================

    @PostMapping("/run")
    public ResponseEntity<?> runCode(
            @RequestBody CodeRequest request
    ) {

        try {

            if (request == null) {
                return ResponseEntity
                        .badRequest()
                        .body(Map.of(
                                "success", false,
                                "error", "Request cannot be empty."
                        ));
            }

            if (request.getCode() == null ||
                    request.getCode().isBlank()) {

                return ResponseEntity
                        .badRequest()
                        .body(Map.of(
                                "success", false,
                                "error", "Code cannot be empty."
                        ));
            }

            String language =
                    normalizeLanguage(
                            request.getLanguage()
                    );

            String problem =
                    safeString(request.getProblem());

            String input = "";

            // =====================================================
            // GET FIRST TEST CASE FOR RUN
            // =====================================================

            if (!problem.isBlank()) {

                List<TestCase> testCases =
                        testCaseService.getTestCases(problem);

                if (testCases != null &&
                        !testCases.isEmpty()) {

                    TestCase firstTestCase =
                            testCases.get(0);

                    if (firstTestCase != null) {

                        input =
                                safeString(
                                        firstTestCase.getInput()
                                );
                    }
                }
            }

            // =====================================================
            // EXECUTE
            // =====================================================

            Map<String, Object> result =
                    executionService.execute(
                            request.getCode(),
                            language,
                            input,
                            "",
                            problem
                    );

            return ResponseEntity.ok(
                    buildExecutionResponse(
                            result,
                            language,
                            problem,
                            input
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "success", false,
                            "error", getErrorMessage(e)
                    ));

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(Map.of(
                            "success", false,
                            "error",
                            "Code execution error: " +
                                    getErrorMessage(e)
                    ));
        }
    }

    // =========================================================
    // SUBMIT CODE
    // =========================================================

    @PostMapping("/submit")
    public ResponseEntity<?> submitCode(
            @RequestBody CodeRequest request
    ) {

        try {

            // =====================================================
            // BASIC VALIDATION
            // =====================================================

            if (request == null) {

                return ResponseEntity
                        .badRequest()
                        .body(Map.of(
                                "success", false,
                                "error",
                                "Request cannot be empty."
                        ));
            }

            if (request.getCode() == null ||
                    request.getCode().isBlank()) {

                return ResponseEntity
                        .badRequest()
                        .body(Map.of(
                                "success", false,
                                "error",
                                "Please write your solution first."
                        ));
            }

            if (request.getProblem() == null ||
                    request.getProblem().isBlank()) {

                return ResponseEntity
                        .badRequest()
                        .body(Map.of(
                                "success", false,
                                "error",
                                "Problem information is missing."
                        ));
            }

            String language =
                    normalizeLanguage(
                            request.getLanguage()
                    );

            String problem =
                    request.getProblem().trim();

            // =====================================================
            // GET TEST CASES
            // =====================================================

            List<TestCase> testCases =
                    testCaseService.getTestCases(problem);

            if (testCases == null ||
                    testCases.isEmpty()) {

                return ResponseEntity
                        .badRequest()
                        .body(Map.of(
                                "success", false,
                                "error",
                                "No test cases found for this problem."
                        ));
            }

            int passedTests = 0;

            List<Map<String, Object>> testResults =
                    new ArrayList<>();

            // =====================================================
            // RUN ALL TEST CASES
            // =====================================================

            for (int i = 0;
                 i < testCases.size();
                 i++) {

                TestCase testCase =
                        testCases.get(i);

                Map<String, Object> testResult =
                        new LinkedHashMap<>();

                String input = "";

                String expectedOutput = "";

                if (testCase != null) {

                    input =
                            safeString(
                                    testCase.getInput()
                            );

                    expectedOutput =
                            safeString(
                                    testCase.getExpectedOutput()
                            );
                }

                testResult.put(
                        "testCase",
                        i + 1
                );

                testResult.put(
                        "input",
                        input
                );

                testResult.put(
                        "expectedOutput",
                        expectedOutput
                );

                try {

                    // =================================================
                    // EXECUTE TEST CASE
                    // =================================================

                    Map<String, Object> result =
                            executionService.execute(
                                    request.getCode(),
                                    language,
                                    input,
                                    expectedOutput,
                                    problem
                            );

                    String status =
                            getStatusDescription(result);

                    String actualOutput =
                            getOutput(result);

                    boolean passed =
                            "Accepted".equalsIgnoreCase(
                                    status
                            );

                    if (passed) {
                        passedTests++;
                    }

                    testResult.put(
                            "actualOutput",
                            actualOutput
                    );

                    testResult.put(
                            "status",
                            status
                    );

                    testResult.put(
                            "passed",
                            passed
                    );

                    String error =
                            getExecutionError(result);

                    if (!error.isBlank()) {

                        testResult.put(
                                "error",
                                error
                        );
                    }

                } catch (Exception e) {

                    testResult.put(
                            "actualOutput",
                            ""
                    );

                    testResult.put(
                            "status",
                            "ERROR"
                    );

                    testResult.put(
                            "passed",
                            false
                    );

                    testResult.put(
                            "error",
                            getErrorMessage(e)
                    );
                }

                testResults.add(
                        testResult
                );
            }

            // =====================================================
            // SCORE
            // =====================================================

            int totalTests =
                    testCases.size();

            int score =
                    totalTests == 0
                            ? 0
                            : Math.round(
                            (passedTests * 100f)
                                    / totalTests
                    );

            // =====================================================
            // FINAL STATUS
            // =====================================================

            String finalStatus;

            if (passedTests == totalTests) {

                finalStatus =
                        "ACCEPTED";

            } else if (passedTests > 0) {

                finalStatus =
                        "PARTIALLY ACCEPTED";

            } else {

                finalStatus =
                        "WRONG ANSWER";
            }

            // =====================================================
            // MESSAGE
            // =====================================================

            String message;

            if ("ACCEPTED".equals(
                    finalStatus
            )) {

                message =
                        "🎉 All test cases passed!";

            } else if ("PARTIALLY ACCEPTED".equals(
                    finalStatus
            )) {

                message =
                        "Some test cases passed, but some failed.";

            } else {

                message =
                        "Your solution failed the test cases.";
            }

            // =====================================================
            // FINAL RESPONSE
            // =====================================================

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "success",
                    true
            );

            response.put(
                    "status",
                    finalStatus
            );

            response.put(
                    "message",
                    message
            );

            response.put(
                    "language",
                    language
            );

            response.put(
                    "problem",
                    problem
            );

            response.put(
                    "passedTests",
                    passedTests
            );

            response.put(
                    "totalTests",
                    totalTests
            );

            response.put(
                    "score",
                    score
            );

            response.put(
                    "tests",
                    testResults
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "success", false,
                            "error", getErrorMessage(e)
                    ));

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(Map.of(
                            "success", false,
                            "error",
                            "Submission error: " +
                                    getErrorMessage(e)
                    ));
        }
    }

    // =========================================================
    // BUILD RUN RESPONSE
    // =========================================================

    private Map<String, Object> buildExecutionResponse(
            Map<String, Object> result,
            String language,
            String problem,
            String input
    ) {

        String statusText =
                getStatusDescription(result);

        String stdout =
                safeResultString(
                        result,
                        "stdout"
                );

        String stderr =
                safeResultString(
                        result,
                        "stderr"
                );

        String compileOutput =
                safeResultString(
                        result,
                        "compile_output"
                );

        String message =
                safeResultString(
                        result,
                        "message"
                );

        String output;

        if (!compileOutput.isBlank()) {

            output = compileOutput;

        } else if (!stderr.isBlank()) {

            output = stderr;

        } else if (!stdout.isBlank()) {

            output = stdout;

        } else if (!message.isBlank()) {

            output = message;

        } else {

            output =
                    "Program executed successfully with no output.";
        }

        boolean success =
                isSuccessfulExecutionStatus(
                        statusText
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "success",
                success
        );

        response.put(
                "language",
                language
        );

        response.put(
                "problem",
                problem
        );

        response.put(
                "input",
                input
        );

        response.put(
                "status",
                statusText
        );

        response.put(
                "output",
                output
        );

        response.put(
                "stdout",
                stdout
        );

        response.put(
                "stderr",
                stderr
        );

        response.put(
                "compile_output",
                compileOutput
        );

        response.put(
                "time",
                result == null
                        ? null
                        : result.get("time")
        );

        response.put(
                "memory",
                result == null
                        ? null
                        : result.get("memory")
        );

        return response;
    }

    // =========================================================
    // STATUS
    // =========================================================

    private String getStatusDescription(
            Map<String, Object> result
    ) {

        if (result == null) {
            return "UNKNOWN";
        }

        Object statusObject =
                result.get("status");

        if (!(statusObject instanceof Map)) {
            return "UNKNOWN";
        }

        Map<?, ?> status =
                (Map<?, ?>) statusObject;

        Object description =
                status.get("description");

        if (description == null) {
            return "UNKNOWN";
        }

        return description.toString();
    }

    // =========================================================
    // CHECK EXECUTION STATUS
    // =========================================================

    private boolean isSuccessfulExecutionStatus(
            String status
    ) {

        if (status == null) {
            return false;
        }

        return status.equalsIgnoreCase(
                "Accepted"
        ) ||
                status.equalsIgnoreCase(
                        "Accepted"
                ) ||
                status.equalsIgnoreCase(
                        "Finished"
                );
    }

    // =========================================================
    // OUTPUT
    // =========================================================

    private String getOutput(
            Map<String, Object> result
    ) {

        if (result == null) {
            return "";
        }

        String stdout =
                safeResultString(
                        result,
                        "stdout"
                );

        if (!stdout.isBlank()) {
            return stdout.trim();
        }

        String compileOutput =
                safeResultString(
                        result,
                        "compile_output"
                );

        if (!compileOutput.isBlank()) {
            return compileOutput.trim();
        }

        String stderr =
                safeResultString(
                        result,
                        "stderr"
                );

        if (!stderr.isBlank()) {
            return stderr.trim();
        }

        String message =
                safeResultString(
                        result,
                        "message"
                );

        return message.trim();
    }

    // =========================================================
    // EXECUTION ERROR
    // =========================================================

    private String getExecutionError(
            Map<String, Object> result
    ) {

        if (result == null) {
            return "";
        }

        String compile =
                safeResultString(
                        result,
                        "compile_output"
                );

        if (!compile.isBlank()) {
            return compile.trim();
        }

        String stderr =
                safeResultString(
                        result,
                        "stderr"
                );

        if (!stderr.isBlank()) {
            return stderr.trim();
        }

        String message =
                safeResultString(
                        result,
                        "message"
                );

        return message.trim();
    }

    // =========================================================
    // LANGUAGE NORMALIZATION
    // =========================================================

    private String normalizeLanguage(
            String language
    ) {

        if (language == null ||
                language.isBlank()) {

            return "javascript";
        }

        String value =
                language
                        .trim()
                        .toLowerCase();

        return switch (value) {

            case "js",
                 "javascript" ->
                    "javascript";

            case "py",
                 "python",
                 "python3" ->
                    "python";

            case "java" ->
                    "java";

            case "cpp",
                 "c++",
                 "cxx" ->
                    "cpp";

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported language: " +
                                    language
                    );
        };
    }

    // =========================================================
    // SAFE STRING
    // =========================================================

    private String safeString(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }

    // =========================================================
    // SAFE RESULT STRING
    // =========================================================

    private String safeResultString(
            Map<String, Object> result,
            String key
    ) {

        if (result == null ||
                result.get(key) == null) {

            return "";
        }

        return result.get(key)
                .toString();
    }

    // =========================================================
    // ERROR MESSAGE
    // =========================================================

    private String getErrorMessage(
            Exception e
    ) {

        if (e == null) {
            return "Unknown error.";
        }

        if (e.getMessage() != null &&
                !e.getMessage().isBlank()) {

            return e.getMessage();
        }

        return e.getClass()
                .getSimpleName();
    }

    // =========================================================
    // REQUEST DTO
    // =========================================================

    public static class CodeRequest {

        private String code;

        private String language;

        private String problem;

        public CodeRequest() {
        }

        public String getCode() {
            return code;
        }

        public void setCode(
                String code
        ) {
            this.code = code;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(
                String language
        ) {
            this.language = language;
        }

        public String getProblem() {
            return problem;
        }

        public void setProblem(
                String problem
        ) {
            this.problem = problem;
        }
    }

    // =========================================================
// GET ALL PROBLEMS
// =========================================================

    @GetMapping("/problems")
    public ResponseEntity<?> getAllProblems() {

        try {

            List<String> problems =
                    testCaseService.getAllProblems();

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "count", problems.size(),
                            "problems", problems
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
                                    "Failed to load problems: " +
                                            getErrorMessage(e)
                            )
                    );
        }
    }
}