package com.codecoach.service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeExecutionService {

    private static final String JUDGE0_URL =
            "https://ce.judge0.com";

    private final RestTemplate restTemplate =
            new RestTemplate();

    // =========================================================
    // LANGUAGE
    // =========================================================

    private String normalizeLanguage(String language) {

        if (language == null || language.isBlank()) {
            return "javascript";
        }

        return switch (language.trim().toLowerCase()) {

            case "js", "javascript" -> "javascript";

            case "py", "python", "python3" -> "python";

            case "java" -> "java";

            case "cpp", "c++", "cxx" -> "cpp";

            default -> throw new IllegalArgumentException(
                    "Unsupported language: " + language
            );
        };
    }

    private int getLanguageId(String language) {

        return switch (normalizeLanguage(language)) {

            case "javascript" -> 63;

            case "python" -> 71;

            case "java" -> 62;

            case "cpp" -> 54;

            default -> throw new IllegalArgumentException(
                    "Unsupported language: " + language
            );
        };
    }

    // =========================================================
    // EXECUTE
    // =========================================================

    public Map<String, Object> execute(
            String code,
            String language,
            String input,
            String expectedOutput
    ) throws InterruptedException {

        return execute(
                code,
                language,
                input,
                expectedOutput,
                null
        );
    }

    public Map<String, Object> execute(
            String code,
            String language,
            String input,
            String expectedOutput,
            String problem
    ) throws InterruptedException {

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "Code cannot be empty."
            );
        }

        if (problem == null || problem.isBlank()) {
            throw new IllegalArgumentException(
                    "Problem name is required."
            );
        }

        String normalizedLanguage =
                normalizeLanguage(language);

        int languageId =
                getLanguageId(normalizedLanguage);

        String executableCode =
                prepareExecutableCode(
                        code,
                        normalizedLanguage,
                        input,
                        problem
                );

        Map<String, Object> body =
                new HashMap<>();

        body.put(
                "source_code",
                executableCode
        );

        body.put(
                "language_id",
                languageId
        );

        /*
         * Input is injected into generated runner.
         * Therefore Judge0 stdin is empty.
         */
        body.put(
                "stdin",
                ""
        );

        body.put(
                "cpu_time_limit",
                5
        );

        body.put(
                "wall_time_limit",
                10
        );

        body.put(
                "memory_limit",
                128000
        );

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(
                        body,
                        headers
                );

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        JUDGE0_URL +
                                "/submissions/" +
                                "?base64_encoded=false" +
                                "&wait=false",
                        request,
                        Map.class
                );

        if (!response.getStatusCode().is2xxSuccessful()) {

            throw new RuntimeException(
                    "Judge0 submission failed: " +
                            response.getStatusCode()
            );
        }

        Map resultBody =
                response.getBody();

        if (resultBody == null ||
                resultBody.get("token") == null) {

            throw new RuntimeException(
                    "Judge0 did not return submission token."
            );
        }

        String token =
                resultBody
                        .get("token")
                        .toString();

        // =====================================================
        // POLLING
        // =====================================================

        for (int attempt = 0;
             attempt < 30;
             attempt++) {

            Thread.sleep(1000);

            ResponseEntity<Map> resultResponse =
                    restTemplate.getForEntity(
                            JUDGE0_URL +
                                    "/submissions/" +
                                    token +
                                    "?base64_encoded=true",
                            Map.class
                    );

            if (!resultResponse.getStatusCode()
                    .is2xxSuccessful()) {

                continue;
            }

            Map result =
                    resultResponse.getBody();

            if (result == null) {
                continue;
            }

            // Judge0 may return non-UTF-8 compiler/runtime bytes. Poll using
            // Base64 and decode the text fields locally.
            decodeJudge0Field(result, "stdout");
            decodeJudge0Field(result, "stderr");
            decodeJudge0Field(result, "compile_output");
            decodeJudge0Field(result, "message");

            Map<String, Object> status =
                    getStatusMap(result);

            if (status == null ||
                    status.get("id") == null) {

                continue;
            }

            int statusId;

            try {

                statusId =
                        Integer.parseInt(
                                status.get("id").toString()
                        );

            } catch (NumberFormatException e) {

                continue;
            }

            // 1 = In Queue
            // 2 = Processing

            if (statusId < 3) {
                continue;
            }

            // =================================================
            // CUSTOM VALIDATION
            // =================================================

            if (statusId == 3 &&
                    expectedOutput != null &&
                    !expectedOutput.isBlank()) {

                String stdout =
                        result.get("stdout") == null
                                ? ""
                                : result
                                .get("stdout")
                                .toString()
                                .trim();

                boolean correct =
                        isCorrectOutput(
                                problem,
                                input,
                                stdout,
                                expectedOutput
                        );

                Map<String, Object> finalStatus =
                        new LinkedHashMap<>();

                if (correct) {

                    finalStatus.put(
                            "id",
                            3
                    );

                    finalStatus.put(
                            "description",
                            "Accepted"
                    );

                } else {

                    finalStatus.put(
                            "id",
                            4
                    );

                    finalStatus.put(
                            "description",
                            "Wrong Answer"
                    );
                }

                result.put(
                        "status",
                        finalStatus
                );
            }

            return result;
        }

        throw new RuntimeException(
                "Code execution timed out."
        );
    }

    // =========================================================
    // STATUS
    // =========================================================

    private void decodeJudge0Field(Map result, String field) {
        Object value = result.get(field);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode((String) value);
            result.put(field, new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ignored) {
            // Keep original value if the field is already plain text.
        }
    }

    private Map<String, Object> getStatusMap(
            Map<String, Object> result
    ) {

        Object status =
                result.get("status");

        if (!(status instanceof Map)) {
            return null;
        }

        Map<?, ?> source =
                (Map<?, ?>) status;

        Map<String, Object> copy =
                new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry :
                source.entrySet()) {

            copy.put(
                    String.valueOf(
                            entry.getKey()
                    ),
                    entry.getValue()
            );
        }

        return copy;
    }

    // =========================================================
    // OUTPUT VALIDATION
    // =========================================================

    public boolean isCorrectOutput(
            String problem,
            String input,
            String actualOutput,
            String expectedOutput
    ) {

        if (actualOutput == null) {
            return false;
        }

        String p =
                problem == null
                        ? ""
                        : problem.trim();

        // Two Sum can have multiple valid answers.
        if (p.equalsIgnoreCase("Two Sum")) {

            return isValidTwoSumOutput(
                    actualOutput,
                    input
            );
        }

        if (p.equalsIgnoreCase("Three Sum")) {

            return isSameNestedIntegerArray(
                    actualOutput,
                    expectedOutput
            );
        }

        if (p.equalsIgnoreCase("Group Anagrams")) {

            return isSameNestedStringArray(
                    actualOutput,
                    expectedOutput
            );
        }

        if (p.equalsIgnoreCase("Merge K Lists") ||
                p.equalsIgnoreCase("Merge K Sorted Lists")) {

            return isSameIntegerArray(
                    actualOutput,
                    expectedOutput
            );
        }

        return normalizeOutput(actualOutput)
                .equals(
                        normalizeOutput(expectedOutput)
                );
    }

    // =========================================================
    // TWO SUM
    // =========================================================

    private boolean isValidTwoSumOutput(
            String actual,
            String input
    ) {

        try {

            if (actual == null ||
                    actual.isBlank() ||
                    input == null ||
                    input.isBlank()) {

                return false;
            }

            String[] lines =
                    input.trim()
                            .split("\\R");

            if (lines.length < 2) {
                return false;
            }

            String arrayText =
                    lines[0]
                            .trim()
                            .replace("[", "")
                            .replace("]", "");

            String[] parts =
                    arrayText.split(",");

            int[] nums =
                    new int[parts.length];

            for (int i = 0;
                 i < parts.length;
                 i++) {

                nums[i] =
                        Integer.parseInt(
                                parts[i].trim()
                        );
            }

            int target =
                    Integer.parseInt(
                            lines[1].trim()
                    );

            String cleaned =
                    actual
                            .trim()
                            .replace("[", "")
                            .replace("]", "")
                            .replaceAll(
                                    "\\s+",
                                    ""
                            );

            String[] answer =
                    cleaned.split(",");

            if (answer.length != 2) {
                return false;
            }

            int first =
                    Integer.parseInt(
                            answer[0]
                    );

            int second =
                    Integer.parseInt(
                            answer[1]
                    );

            if (first < 0 ||
                    second < 0 ||
                    first >= nums.length ||
                    second >= nums.length ||
                    first == second) {

                return false;
            }

            return nums[first] +
                    nums[second] ==
                    target;

        } catch (Exception e) {

            return false;
        }
    }

    // =========================================================
    // INTEGER ARRAY
    // =========================================================

    private boolean isSameIntegerArray(
            String actual,
            String expected
    ) {

        return parseIntegerList(actual)
                .equals(
                        parseIntegerList(expected)
                );
    }

    private List<Integer> parseIntegerList(
            String value
    ) {

        List<Integer> result =
                new ArrayList<>();

        if (value == null ||
                value.isBlank()) {

            return result;
        }

        Matcher matcher =
                Pattern.compile(
                        "-?\\d+"
                ).matcher(value);

        while (matcher.find()) {

            try {

                result.add(
                        Integer.parseInt(
                                matcher.group()
                        )
                );

            } catch (Exception ignored) {
            }
        }

        return result;
    }

    // =========================================================
    // NESTED INTEGER ARRAY
    // =========================================================

    private boolean isSameNestedIntegerArray(
            String actual,
            String expected
    ) {

        List<List<Integer>> a =
                parseNestedIntegerArray(actual);

        List<List<Integer>> b =
                parseNestedIntegerArray(expected);

        normalizeNestedIntegerArray(a);
        normalizeNestedIntegerArray(b);

        return a.equals(b);
    }

    private List<List<Integer>>
    parseNestedIntegerArray(
            String value
    ) {

        List<List<Integer>> result =
                new ArrayList<>();

        if (value == null ||
                value.isBlank()) {

            return result;
        }

        Matcher matcher =
                Pattern.compile(
                        "\\[([^\\[\\]]*)\\]"
                ).matcher(value);

        while (matcher.find()) {

            String content =
                    matcher.group(1).trim();

            List<Integer> row =
                    new ArrayList<>();

            if (!content.isBlank()) {

                for (String part :
                        content.split(",")) {

                    try {

                        row.add(
                                Integer.parseInt(
                                        part.trim()
                                )
                        );

                    } catch (Exception ignored) {
                    }
                }
            }

            result.add(row);
        }

        return result;
    }

    private void normalizeNestedIntegerArray(
            List<List<Integer>> value
    ) {

        for (List<Integer> row : value) {
            Collections.sort(row);
        }

        value.sort(
                (a, b) -> {

                    int size =
                            Math.min(
                                    a.size(),
                                    b.size()
                            );

                    for (int i = 0;
                         i < size;
                         i++) {

                        int compare =
                                Integer.compare(
                                        a.get(i),
                                        b.get(i)
                                );

                        if (compare != 0) {
                            return compare;
                        }
                    }

                    return Integer.compare(
                            a.size(),
                            b.size()
                    );
                }
        );
    }

    // =========================================================
    // NESTED STRING ARRAY
    // =========================================================

    private boolean isSameNestedStringArray(
            String actual,
            String expected
    ) {

        List<List<String>> a =
                parseNestedStringArray(actual);

        List<List<String>> b =
                parseNestedStringArray(expected);

        normalizeNestedStringArray(a);
        normalizeNestedStringArray(b);

        return a.equals(b);
    }

    private List<List<String>>
    parseNestedStringArray(
            String value
    ) {

        List<List<String>> result =
                new ArrayList<>();

        if (value == null ||
                value.isBlank()) {

            return result;
        }

        Matcher matcher =
                Pattern.compile(
                        "\\[([^\\[\\]]*)\\]"
                ).matcher(value);

        while (matcher.find()) {

            String content =
                    matcher.group(1).trim();

            List<String> row =
                    new ArrayList<>();

            if (!content.isBlank()) {

                for (String part :
                        content.split(",")) {

                    String clean =
                            part.trim()
                                    .replace(
                                            "\"",
                                            ""
                                    )
                                    .replace(
                                            "'",
                                            ""
                                    );

                    if (!clean.isBlank()) {
                        row.add(clean);
                    }
                }
            }

            result.add(row);
        }

        return result;
    }

    private void normalizeNestedStringArray(
            List<List<String>> value
    ) {

        for (List<String> row : value) {
            Collections.sort(row);
        }

        value.sort(
                (a, b) ->
                        String.join(",", a)
                                .compareTo(
                                        String.join(",", b)
                                )
        );
    }

    // =========================================================
    // PREPARE EXECUTABLE CODE
    // =========================================================

    private String prepareExecutableCode(
            String code,
            String language,
            String input,
            String problem
    ) {

        if (problem == null ||
                problem.isBlank()) {

            throw new IllegalArgumentException(
                    "Problem name is required."
            );
        }

        return switch (language) {

            case "javascript" ->
                    prepareJavaScript(
                            code,
                            input,
                            problem
                    );

            case "python" ->
                    preparePython(
                            code,
                            input,
                            problem
                    );

            case "java" ->
                    prepareJava(
                            code,
                            input,
                            problem
                    );

            case "cpp" ->
                    prepareCpp(
                            code,
                            input,
                            problem
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported language: " +
                                    language
                    );
        };
    }

    // =========================================================
    // JAVASCRIPT
    // =========================================================

    private String prepareJavaScript(
            String code,
            String input,
            String problem
    ) {

        if (containsJavaScriptMain(code)) {
            return code;
        }

        String[] lines =
                splitInput(input);

        String first =
                getLine(lines, 0);

        String second =
                getLine(lines, 1);

        String p =
                problem.trim().toLowerCase();

        StringBuilder b =
                new StringBuilder();

        b.append(code);
        b.append("\n\n");

        switch (p) {

            case "two sum" -> {

                b.append(
                        "const __nums = "
                ).append(first).append(";\n");

                b.append(
                        "const __target = "
                ).append(second).append(";\n");

                b.append(
                        "const __result = " +
                                "twoSum(__nums, __target);\n"
                );

                b.append(
                        "console.log(JSON.stringify(__result));\n"
                );
            }

            case "longest substring" -> {

                b.append(
                        "console.log(lengthOfLongestSubstring(" +
                                JSONString(first) +
                                "));\n"
                );
            }

            case "reverse string" -> {

                b.append(
                        "const __result = " +
                                "reverseString(" +
                                JSONString(first) +
                                ");\n"
                );

                b.append(
                        "console.log(typeof __result === 'string' " +
                                "? __result : JSON.stringify(__result));\n"
                );
            }

            case "valid palindrome" -> {

                b.append(
                        "console.log(isPalindrome(" +
                                JSONString(first) +
                                "));\n"
                );
            }

            case "valid anagram" -> {

                b.append(
                        "console.log(isAnagram(" +
                                JSONString(first) +
                                ", " +
                                JSONString(second) +
                                "));\n"
                );
            }

            case "contains duplicate" -> {

                b.append(
                        "console.log(containsDuplicate(" +
                                first +
                                "));\n"
                );
            }

            case "maximum subarray" -> {

                b.append(
                        "console.log(maxSubArray(" +
                                first +
                                "));\n"
                );
            }

            case "best time to buy and sell stock" -> {

                b.append(
                        "console.log(maxProfit(" +
                                first +
                                "));\n"
                );
            }

            case "move zeroes" -> {

                b.append(
                        "const __nums = " +
                                first +
                                ";\n"
                );

                b.append(
                        "const __result = " +
                                "moveZeroes(__nums);\n"
                );

                b.append(
                        "console.log(JSON.stringify(" +
                                "__result === undefined " +
                                "? __nums : __result));\n"
                );
            }

            case "binary search" -> {

                b.append(
                        "console.log(search(" +
                                first +
                                ", " +
                                second +
                                "));\n"
                );
            }

            case "search insert position" -> {

                b.append(
                        "console.log(searchInsert(" +
                                first +
                                ", " +
                                second +
                                "));\n"
                );
            }

            case "majority element" -> {

                b.append(
                        "console.log(majorityElement(" +
                                first +
                                "));\n"
                );
            }

            case "single number" -> {

                b.append(
                        "console.log(singleNumber(" +
                                first +
                                "));\n"
                );
            }

            case "plus one" -> {

                b.append(
                        "console.log(JSON.stringify(" +
                                "plusOne(" +
                                first +
                                ")));\n"
                );
            }

            case "climbing stairs" -> {

                b.append(
                        "console.log(climbStairs(" +
                                first +
                                "));\n"
                );
            }

            case "valid parentheses" -> {

                b.append(
                        "console.log(isValid(" +
                                JSONString(first) +
                                "));\n"
                );
            }

            case "happy number" -> {

                b.append(
                        "console.log(isHappy(" +
                                first +
                                "));\n"
                );
            }

            case "counting bits" -> {

                b.append(
                        "console.log(JSON.stringify(" +
                                "countBits(" +
                                first +
                                ")));\n"
                );
            }

            case "missing number" -> {

                b.append(
                        "console.log(missingNumber(" +
                                first +
                                "));\n"
                );
            }

            case "pivot index" -> {

                b.append(
                        "console.log(pivotIndex(" +
                                first +
                                "));\n"
                );
            }

            case "sort colors" -> {

                b.append(
                        "const __nums = " +
                                first +
                                ";\n"
                );

                b.append(
                        "const __result = " +
                                "sortColors(__nums);\n"
                );

                b.append(
                        "console.log(JSON.stringify(" +
                                "__result === undefined " +
                                "? __nums : __result));\n"
                );
            }

            case "product except self" -> {

                b.append(
                        "console.log(JSON.stringify(" +
                                "productExceptSelf(" +
                                first +
                                ")));\n"
                );
            }

            case "container with most water" -> {

                b.append(
                        "console.log(maxArea(" +
                                first +
                                "));\n"
                );
            }

            case "top k frequent elements" -> {

                b.append(
                        "console.log(JSON.stringify(" +
                                "topKFrequent(" +
                                first +
                                ", " +
                                second +
                                ")));\n"
                );
            }

            case "coin change" -> {

                b.append(
                        "console.log(coinChange(" +
                                first +
                                ", " +
                                second +
                                "));\n"
                );
            }

            case "three sum" -> {

                b.append(
                        "console.log(JSON.stringify(" +
                                "threeSum(" +
                                first +
                                ")));\n"
                );
            }

            case "group anagrams" -> {

                b.append(
                        "console.log(JSON.stringify(" +
                                "groupAnagrams(" +
                                first +
                                ")));\n"
                );
            }

            default -> {
                return prepareJavaScriptGeneric(b, input, problem);
            }
        }

        return b.toString();
    }

    private String prepareJavaScriptGeneric(
            StringBuilder existing,
            String input,
            String problem
    ) {
        String[] lines = splitInput(input);
        String a=getLine(lines,0), b=getLine(lines,1), c=getLine(lines,2), d=getLine(lines,3);
        String p=problem==null?"":problem.trim().toLowerCase();
        String call;
        switch(p){
            case "merge k lists","merge k sorted lists" -> call="console.log(JSON.stringify(mergeKLists("+a+")));";
            case "fizz buzz" -> call="console.log(JSON.stringify(fizzBuzz("+a+")));";
            case "pascal triangle" -> call="console.log(JSON.stringify(generate("+a+")));";
            case "merge two sorted lists" -> call="console.log(JSON.stringify(mergeTwoLists("+a+","+b+")));";
            case "remove duplicates" -> call="console.log(removeDuplicates("+a+"));";
            case "remove element" -> call="console.log(removeElement("+a+","+b+"));";
            case "rotate array" -> call="const __r=rotate("+a+","+b+"); console.log(JSON.stringify(__r===undefined?"+a+":__r));";
            case "squares of sorted array" -> call="console.log(JSON.stringify(sortedSquares("+a+")));";
            case "first bad version" -> call="console.log(firstBadVersion("+a+","+b+"));";
            case "number of 1 bits" -> call="console.log(hammingWeight("+JSONString(a)+"));";
            case "reverse bits" -> call="console.log(reverseBits("+JSONString(a)+"));";
            case "intersection of two arrays" -> call="console.log(JSON.stringify(intersection("+a+","+b+")));";
            case "intersection ii" -> call="console.log(JSON.stringify(intersect("+a+","+b+")));";
            case "flood fill" -> call="console.log(JSON.stringify(floodFill("+a+","+b+","+c+","+d+")));";
            case "number of islands" -> call="console.log(numIslands("+a+"));";
            case "clone graph" -> call="console.log(JSON.stringify(cloneGraph("+a+")));";
            case "course schedule" -> call="console.log(canFinish("+a+","+b+"));";
            case "reverse linked list" -> call="console.log(JSON.stringify(reverseList("+a+")));";
            case "middle of linked list" -> call="console.log(middleNode("+a+"));";
            case "linked list cycle" -> call="console.log(hasCycle("+a+","+b+"));";
            case "binary tree max depth" -> call="console.log(maxDepth("+a+"));";
            case "same tree" -> call="console.log(isSameTree("+a+","+b+"));";
            case "invert binary tree" -> call="console.log(JSON.stringify(invertTree("+a+")));";
            default -> throw new IllegalArgumentException("JavaScript runner not implemented for problem: "+problem);
        }
        existing.append("\n").append(call).append("\n");
        return existing.toString();
    }

    private String preparePython(
            String code,
            String input,
            String problem
    ) {

        if (containsPythonMain(code)) {
            return code;
        }

        String[] lines =
                splitInput(input);

        String first =
                getLine(lines, 0);

        String second =
                getLine(lines, 1);

        String p =
                problem.trim().toLowerCase();

        StringBuilder b =
                new StringBuilder();

        b.append(code);
        b.append("\n\n");

        switch (p) {

            case "two sum" -> {

                b.append(
                        "__nums = "
                ).append(first).append("\n");

                b.append(
                        "__target = "
                ).append(second).append("\n");

                b.append(
                        "print(twoSum(__nums, __target))\n"
                );
            }

            case "longest substring" -> {

                b.append(
                        "print(lengthOfLongestSubstring(" +
                                pythonString(first) +
                                "))\n"
                );
            }

            case "reverse string" -> {

                b.append(
                        "__result = reverseString(" +
                                pythonString(first) +
                                ")\n"
                );

                b.append(
                        "print(__result)\n"
                );
            }

            case "valid palindrome" -> {

                b.append(
                        "print(isPalindrome(" +
                                pythonString(first) +
                                "))\n"
                );
            }

            case "valid anagram" -> {

                b.append(
                        "print(isAnagram(" +
                                pythonString(first) +
                                ", " +
                                pythonString(second) +
                                "))\n"
                );
            }

            case "contains duplicate" -> {

                b.append(
                        "print(containsDuplicate(" +
                                first +
                                "))\n"
                );
            }

            case "maximum subarray" -> {

                b.append(
                        "print(maxSubArray(" +
                                first +
                                "))\n"
                );
            }

            case "best time to buy and sell stock" -> {

                b.append(
                        "print(maxProfit(" +
                                first +
                                "))\n"
                );
            }

            case "move zeroes" -> {

                b.append(
                        "__nums = "
                ).append(first).append("\n");

                b.append(
                        "__result = moveZeroes(__nums)\n"
                );

                b.append(
                        "print(__nums if __result is None " +
                                "else __result)\n"
                );
            }

            case "binary search" -> {

                b.append(
                        "print(search(" +
                                first +
                                ", " +
                                second +
                                "))\n"
                );
            }

            case "search insert position" -> {

                b.append(
                        "print(searchInsert(" +
                                first +
                                ", " +
                                second +
                                "))\n"
                );
            }

            case "majority element" -> {

                b.append(
                        "print(majorityElement(" +
                                first +
                                "))\n"
                );
            }

            case "single number" -> {

                b.append(
                        "print(singleNumber(" +
                                first +
                                "))\n"
                );
            }

            case "plus one" -> {

                b.append(
                        "print(plusOne(" +
                                first +
                                "))\n"
                );
            }

            case "climbing stairs" -> {

                b.append(
                        "print(climbStairs(" +
                                first +
                                "))\n"
                );
            }

            case "valid parentheses" -> {

                b.append(
                        "print(isValid(" +
                                pythonString(first) +
                                "))\n"
                );
            }

            case "happy number" -> {

                b.append(
                        "print(isHappy(" +
                                first +
                                "))\n"
                );
            }

            case "counting bits" -> {

                b.append(
                        "print(countBits(" +
                                first +
                                "))\n"
                );
            }

            case "missing number" -> {

                b.append(
                        "print(missingNumber(" +
                                first +
                                "))\n"
                );
            }

            case "pivot index" -> {

                b.append(
                        "print(pivotIndex(" +
                                first +
                                "))\n"
                );
            }

            case "product except self" -> {

                b.append(
                        "print(productExceptSelf(" +
                                first +
                                "))\n"
                );
            }

            case "container with most water" -> {

                b.append(
                        "print(maxArea(" +
                                first +
                                "))\n"
                );
            }

            case "top k frequent elements" -> {

                b.append(
                        "print(topKFrequent(" +
                                first +
                                ", " +
                                second +
                                "))\n"
                );
            }

            case "coin change" -> {

                b.append(
                        "print(coinChange(" +
                                first +
                                ", " +
                                second +
                                "))\n"
                );
            }

            case "three sum" -> {

                b.append(
                        "print(threeSum(" +
                                first +
                                "))\n"
                );
            }

            case "group anagrams" -> {

                b.append(
                        "print(groupAnagrams(" +
                                first +
                                "))\n"
                );
            }

            case "sort colors" -> {

                b.append(
                        "__nums = "
                ).append(first).append("\n");

                b.append(
                        "__result = sortColors(__nums)\n"
                );

                b.append(
                        "print(__nums if __result is None " +
                                "else __result)\n"
                );
            }

            default -> {
                return preparePythonGeneric(b, input, problem);
            }
        }

        return b.toString();
    }

    private String preparePythonGeneric(
            StringBuilder existing,
            String input,
            String problem
    ) {
        String[] lines=splitInput(input);
        String a=getLine(lines,0), b=getLine(lines,1), c=getLine(lines,2), d=getLine(lines,3);
        String p=problem==null?"":problem.trim().toLowerCase();
        String call;
        switch(p){
            case "merge k lists","merge k sorted lists" -> call="print(mergeKLists("+a+"))";
            case "fizz buzz" -> call="print(fizzBuzz("+a+"))";
            case "pascal triangle" -> call="print(generate("+a+"))";
            case "merge two sorted lists" -> call="print(mergeTwoLists("+a+","+b+"))";
            case "remove duplicates" -> call="print(removeDuplicates("+a+"))";
            case "remove element" -> call="print(removeElement("+a+","+b+"))";
            case "rotate array" -> call="__r=rotate("+a+","+b+")\nprint("+a+" if __r is None else __r)";
            case "squares of sorted array" -> call="print(sortedSquares("+a+"))";
            case "first bad version" -> call="print(firstBadVersion("+a+","+b+"))";
            case "number of 1 bits" -> call="print(hammingWeight('"+escapePythonString(a)+"'))";
            case "reverse bits" -> call="print(reverseBits('"+escapePythonString(a)+"'))";
            case "intersection of two arrays" -> call="print(intersection("+a+","+b+"))";
            case "intersection ii" -> call="print(intersect("+a+","+b+"))";
            case "flood fill" -> call="print(floodFill("+a+","+b+","+c+","+d+"))";
            case "number of islands" -> call="print(numIslands("+a+"))";
            case "clone graph" -> call="print(cloneGraph("+a+"))";
            case "course schedule" -> call="print(canFinish("+a+","+b+"))";
            case "reverse linked list" -> call="print(reverseList("+a+"))";
            case "middle of linked list" -> call="print(middleNode("+a+"))";
            case "linked list cycle" -> call="print(hasCycle("+a+","+b+"))";
            case "binary tree max depth" -> call="print(maxDepth("+a+"))";
            case "same tree" -> call="print(isSameTree("+a+","+b+"))";
            case "invert binary tree" -> call="print(invertTree("+a+"))";
            default -> throw new IllegalArgumentException("Python runner not implemented for problem: "+problem);
        }
        existing.append("\n").append(call).append("\n");
        return existing.toString();
    }

    private String prepareJava(
            String code,
            String input,
            String problem
    ) {

        String javaCode =
                convertJavaClassName(
                        code.trim()
                );

        // User has supplied complete program.
        if (javaCode.contains(
                "public static void main"
        )) {

            return javaCode;
        }

        if (isJavaTreeProblem(problem)) {

            return prepareJavaTree(
                    javaCode,
                    input,
                    problem
            );
        }

        return prepareJavaGeneric(
                javaCode,
                input,
                problem
        );
    }

    // =========================================================
    // JAVA GENERIC
    // =========================================================

    private String prepareJavaGeneric(
            String code,
            String input,
            String problem
    ) {

        String javaCode =
                convertJavaClassName(
                        code.trim()
                );

        String[] lines =
                splitInput(input);

        String first =
                getLine(lines, 0);

        String second =
                getLine(lines, 1);

        StringBuilder runner =
                new StringBuilder();

        runner.append(
                "\n    public static void main(String[] args) {\n" +
                        "        Main __solution = new Main();\n"
        );

        String p =
                problem.trim().toLowerCase();

        switch (p) {

            case "two sum" -> {

                runner.append(
                        "        int[] __nums = "
                ).append(javaIntArray(first)).append(";\n");

                runner.append(
                        "        int __target = "
                ).append(second).append(";\n");

                runner.append(
                        "        System.out.println(" +
                                "java.util.Arrays.toString(" +
                                "twoSum(__nums, __target)));\n"
                );
            }

            case "longest substring" -> {

                runner.append(
                        "        String __input = \""
                ).append(
                        escapeJavaString(first)
                ).append(
                        "\";\n"
                );

                runner.append(
                        "        System.out.println(" +
                                "lengthOfLongestSubstring(__input));\n"
                );
            }

            case "reverse string" -> {

                runner.append(
                        "        char[] __input = \""
                ).append(
                        escapeJavaString(first)
                ).append(
                        "\".toCharArray();\n"
                );

                runner.append(
                        "        reverseString(__input);\n"
                );

                runner.append(
                        "        System.out.println(" +
                                "new String(__input));\n"
                );
            }

            case "valid palindrome" -> {

                runner.append(
                        "        System.out.println(" +
                                "isPalindrome(\""
                ).append(
                        escapeJavaString(first)
                ).append(
                        "\"));\n"
                );
            }

            case "valid anagram" -> {

                runner.append(
                        "        System.out.println(" +
                                "isAnagram(\""
                ).append(
                        escapeJavaString(first)
                ).append(
                        "\", \""
                ).append(
                        escapeJavaString(second)
                ).append(
                        "\"));\n"
                );
            }

            case "contains duplicate" -> {

                runner.append(
                        "        System.out.println(" +
                                "containsDuplicate("
                ).append(
                        javaIntArray(first)
                ).append(
                        "));\n"
                );
            }

            case "maximum subarray" -> {

                runner.append(
                        "        System.out.println(" +
                                "maxSubArray("
                ).append(
                        javaIntArray(first)
                ).append(
                        "));\n"
                );
            }

            case "best time to buy and sell stock" -> {

                runner.append(
                        "        System.out.println(" +
                                "maxProfit("
                ).append(
                        javaIntArray(first)
                ).append(
                        "));\n"
                );
            }

            case "move zeroes" -> {

                runner.append(
                        "        int[] __nums = "
                ).append(
                        javaIntArray(first)
                ).append(
                        ";\n"
                );

                runner.append(
                        "        moveZeroes(__nums);\n"
                );

                runner.append(
                        "        System.out.println(" +
                                "java.util.Arrays.toString(__nums));\n"
                );
            }

            case "binary search" -> {

                runner.append(
                        "        System.out.println(search("
                ).append(
                        javaIntArray(first)
                ).append(
                        ", "
                ).append(
                        second
                ).append(
                        "));\n"
                );
            }

            case "search insert position" -> {

                runner.append(
                        "        System.out.println(searchInsert("
                ).append(
                        javaIntArray(first)
                ).append(
                        ", "
                ).append(
                        second
                ).append(
                        "));\n"
                );
            }

            case "majority element" -> {

                runner.append(
                        "        System.out.println(majorityElement("
                ).append(
                        javaIntArray(first)
                ).append(
                        "));\n"
                );
            }

            case "single number" -> {

                runner.append(
                        "        System.out.println(singleNumber("
                ).append(
                        javaIntArray(first)
                ).append(
                        "));\n"
                );
            }

            case "plus one" -> {

                runner.append(
                        "        System.out.println(" +
                                "java.util.Arrays.toString(" +
                                "plusOne("
                ).append(
                        javaIntArray(first)
                ).append(
                        ")));\n"
                );
            }

            case "climbing stairs" -> {

                runner.append(
                        "        System.out.println(climbStairs("
                ).append(
                        first
                ).append(
                        "));\n"
                );
            }

            case "valid parentheses" -> {

                runner.append(
                        "        System.out.println(isValid(\""
                ).append(
                        escapeJavaString(first)
                ).append(
                        "\"));\n"
                );
            }

            case "happy number" -> {

                runner.append(
                        "        System.out.println(isHappy("
                ).append(
                        first
                ).append(
                        "));\n"
                );
            }

            case "counting bits" -> {

                runner.append(
                        "        System.out.println(" +
                                "java.util.Arrays.toString(" +
                                "countBits("
                ).append(
                        first
                ).append(
                        ")));\n"
                );
            }

            case "missing number" -> {

                runner.append(
                        "        System.out.println(missingNumber("
                ).append(
                        first
                ).append(
                        "));\n"
                );
            }

            case "pivot index" -> {

                runner.append(
                        "        System.out.println(pivotIndex("
                ).append(
                        first
                ).append(
                        "));\n"
                );
            }

            case "product except self" -> {

                runner.append(
                        "        System.out.println(" +
                                "java.util.Arrays.toString(" +
                                "productExceptSelf("
                ).append(
                        javaIntArray(first)
                ).append(
                        ")));\n"
                );
            }

            case "container with most water" -> {

                runner.append(
                        "        System.out.println(maxArea("
                ).append(
                        javaIntArray(first)
                ).append(
                        "));\n"
                );
            }

            case "top k frequent elements" -> {

                runner.append(
                        "        System.out.println(" +
                                "java.util.Arrays.toString(" +
                                "topKFrequent("
                ).append(
                        javaIntArray(first)
                ).append(
                        ", "
                ).append(
                        second
                ).append(
                        ")));\n"
                );
            }

            case "coin change" -> {

                runner.append(
                        "        System.out.println(coinChange("
                ).append(
                        javaIntArray(first)
                ).append(
                        ", "
                ).append(
                        second
                ).append(
                        "));\n"
                );
            }

            default -> {
                return prepareJavaGenericFallback(javaCode, input, problem);
            }
        }

        runner.append(
                "    }\n"
        );

        return insertBeforeLastBrace(
                javaCode,
                javaSolutionCalls(runner.toString())
        );
    }

    // =========================================================
    // JAVA TREE
    // =========================================================

    private String prepareJavaTree(
            String code,
            String input,
            String problem
    ) {

        String javaCode =
                convertJavaClassName(
                        code.trim()
                );

        /*
         * TreeNode is inserted INSIDE Main class.
         * This fixes the problem in your old Part 2.
         */

        if (!javaCode.contains(
                "class TreeNode"
        )) {

            String treeNode =
                    """
                    
                        static class TreeNode {
                            int val;
                            TreeNode left;
                            TreeNode right;

                            TreeNode(int val) {
                                this.val = val;
                            }

                            TreeNode(
                                    int val,
                                    TreeNode left,
                                    TreeNode right
                            ) {
                                this.val = val;
                                this.left = left;
                                this.right = right;
                            }
                        }
                    """;

            javaCode =
                    insertBeforeLastBrace(
                            javaCode,
                            treeNode
                    );
        }

        String[] lines =
                splitInput(input);

        String first =
                getLine(lines, 0);

        String second =
                getLine(lines, 1);

        StringBuilder runner =
                new StringBuilder();

        runner.append(
                "\n    public static void main(String[] args) {\n" +
                        "        Main __solution = new Main();\n"
        );

        if (problem.equalsIgnoreCase(
                "Same Tree"
        )) {

            runner.append(
                    "        TreeNode p = buildTree(\""
            ).append(
                    escapeJavaString(first)
            ).append(
                    "\");\n"
            );

            runner.append(
                    "        TreeNode q = buildTree(\""
            ).append(
                    escapeJavaString(second)
            ).append(
                    "\");\n"
            );

            runner.append(
                    "        System.out.println(" +
                            "isSameTree(p, q));\n"
            );

        } else if (
                problem.equalsIgnoreCase(
                        "Binary Tree Max Depth"
                )
        ) {

            runner.append(
                    "        TreeNode root = buildTree(\""
            ).append(
                    escapeJavaString(first)
            ).append(
                    "\");\n"
            );

            runner.append(
                    "        System.out.println(" +
                            "maxDepth(root));\n"
            );

        } else if (
                problem.equalsIgnoreCase(
                        "Invert Binary Tree"
                )
        ) {

            runner.append(
                    "        TreeNode root = buildTree(\""
            ).append(
                    escapeJavaString(first)
            ).append(
                    "\");\n"
            );

            runner.append(
                    "        TreeNode result = " +
                            "invertTree(root);\n"
            );

            runner.append(
                    "        System.out.println(" +
                            "treeToString(result));\n"
            );

        } else {

            throw new IllegalArgumentException(
                    "Java tree runner not implemented for: " +
                            problem
            );
        }

        runner.append(
                "    }\n"
        );

        String helpers =
                """
                
                    static TreeNode buildTree(
                            String data
                    ) {

                        if (data == null) {
                            return null;
                        }

                        String s =
                                data.trim();

                        if (s.isEmpty() ||
                                s.equals("[]")) {
                            return null;
                        }

                        if (s.startsWith("[") &&
                                s.endsWith("]")) {

                            s =
                                    s.substring(
                                            1,
                                            s.length() - 1
                                    );
                        }

                        if (s.trim().isEmpty()) {
                            return null;
                        }

                        String[] parts =
                                s.split(",");

                        if (parts.length == 0 ||
                                parts[0]
                                    .trim()
                                    .equalsIgnoreCase("null")) {

                            return null;
                        }

                        TreeNode root =
                                new TreeNode(
                                        Integer.parseInt(
                                                parts[0].trim()
                                        )
                                );

                        Queue<TreeNode> queue =
                                new LinkedList<>();

                        queue.offer(root);

                        int i = 1;

                        while (!queue.isEmpty() &&
                                i < parts.length) {

                            TreeNode current =
                                    queue.poll();

                            if (i < parts.length &&
                                    !parts[i]
                                        .trim()
                                        .equalsIgnoreCase("null") &&
                                    !parts[i]
                                        .trim()
                                        .isEmpty()) {

                                current.left =
                                        new TreeNode(
                                                Integer.parseInt(
                                                        parts[i].trim()
                                                )
                                        );

                                queue.offer(
                                        current.left
                                );
                            }

                            i++;

                            if (i < parts.length &&
                                    !parts[i]
                                        .trim()
                                        .equalsIgnoreCase("null") &&
                                    !parts[i]
                                        .trim()
                                        .isEmpty()) {

                                current.right =
                                        new TreeNode(
                                                Integer.parseInt(
                                                        parts[i].trim()
                                                )
                                        );

                                queue.offer(
                                        current.right
                                );
                            }

                            i++;
                        }

                        return root;
                    }

                    static String treeToString(
                            TreeNode root
                    ) {

                        if (root == null) {
                            return "[]";
                        }

                        List<String> result =
                                new ArrayList<>();

                        Queue<TreeNode> queue =
                                new LinkedList<>();

                        queue.offer(root);

                        while (!queue.isEmpty()) {

                            TreeNode node =
                                    queue.poll();

                            if (node == null) {

                                result.add("null");

                            } else {

                                result.add(
                                        String.valueOf(
                                                node.val
                                        )
                                );

                                queue.offer(
                                        node.left
                                );

                                queue.offer(
                                        node.right
                                );
                            }
                        }

                        while (!result.isEmpty() &&
                                result.get(
                                        result.size() - 1
                                ).equals("null")) {

                            result.remove(
                                    result.size() - 1
                            );
                        }

                        return "[" +
                                String.join(
                                        ",",
                                        result
                                ) +
                                "]";
                    }
                """;

        javaCode =
                insertBeforeLastBrace(
                        javaCode,
                        helpers
                );

        return insertBeforeLastBrace(
                javaCode,
                javaSolutionCalls(runner.toString())
        );
    }

    private boolean isJavaTreeProblem(
            String problem
    ) {

        if (problem == null) {
            return false;
        }

        return problem.equalsIgnoreCase(
                "Same Tree"
        )
                ||
                problem.equalsIgnoreCase(
                        "Binary Tree Max Depth"
                )
                ||
                problem.equalsIgnoreCase(
                        "Invert Binary Tree"
                );
    }

    /**
     * Converts ProblemTestCaseService array literals to valid Java source.
     *
     * [2,7,11,15]       -> new int[]{2,7,11,15}
     * [[1,2],[3,4]]     -> new int[][]{{1,2},{3,4}}
     * [1]               -> new int[]{1}
     *
     * Non-array values are returned unchanged.
     */

    private String javaSolutionCalls(String runner) {
        String[] names = {
                "twoSum", "lengthOfLongestSubstring", "reverseString", "isPalindrome",
                "isAnagram", "containsDuplicate", "maxSubArray", "maxProfit", "moveZeroes",
                "search", "firstBadVersion", "searchInsert", "sortedSquares", "majorityElement",
                "singleNumber", "plusOne", "fizzBuzz", "climbStairs", "generate", "isValid",
                "mergeTwoLists", "removeDuplicates", "removeElement", "rotate", "productExceptSelf",
                "maxArea", "threeSum", "groupAnagrams", "topKFrequent", "isHappy", "countBits",
                "hammingWeight", "reverseBits", "missingNumber", "pivotIndex", "intersection",
                "intersect", "sortColors", "reverseList", "middleNode", "hasCycle", "maxDepth",
                "isSameTree", "invertTree", "floodFill", "numIslands", "cloneGraph", "canFinish",
                "coinChange", "mergeKLists"
        };
        String result = runner;
        for (String name : names) {
            result = result.replaceAll(
                    "(?<![A-Za-z0-9_$.])" + Pattern.quote(name) + "\\s*\\(",
                    "__solution." + name + "("
            );
        }
        return result;
    }

    private String javaIntArray(String value) {

        if (value == null) {
            return "new int[]{}";
        }

        String v = value.trim();

        if (v.startsWith("new int[]") ||
                v.startsWith("new int[][]")) {
            return v;
        }

        if (v.startsWith("[[") && v.endsWith("]]")) {
            return "new int[][]" +
                    v.replace('[', '{')
                            .replace(']', '}');
        }

        if (v.startsWith("[") && v.endsWith("]")) {
            return "new int[]{" +
                    v.substring(1, v.length() - 1).trim() +
                    "}";
        }

        return v;
    }

    private String prepareJavaGenericFallback(
            String code,
            String input,
            String problem
    ) {
        if (code.contains("public static void main")) return code;

        String[] lines=splitInput(input);
        String a=getLine(lines,0), b=getLine(lines,1), c=getLine(lines,2), d=getLine(lines,3);
        String p=problem==null?"":problem.trim().toLowerCase();
        StringBuilder r=new StringBuilder("\n    public static void main(String[] args) {\n        Main __solution = new Main();\n");

        switch(p){
            case "merge k lists","merge k sorted lists" -> {
                r.append("        int[][] v=").append(javaInt2D(a)).append(";\n");
                r.append("        System.out.println(java.util.Arrays.toString(mergeKLists(v)));\n");
            }
            case "fizz buzz" -> r.append("        System.out.println(fizzBuzz(").append(a).append("));\n");
            case "pascal triangle" -> r.append("        System.out.println(generate(").append(a).append("));\n");
            case "merge two sorted lists" -> r.append("        System.out.println(java.util.Arrays.toString(mergeTwoLists(").append(javaIntArray(a)).append(",").append(javaIntArray(b)).append(")));\n");
            case "remove duplicates" -> r.append("        System.out.println(removeDuplicates(").append(javaIntArray(a)).append("));\n");
            case "remove element" -> r.append("        System.out.println(removeElement(").append(javaIntArray(a)).append(",").append(b).append("));\n");
            case "rotate array" -> r.append("        int[] v=").append(javaIntArray(a)).append("; rotate(v,").append(b).append("); System.out.println(java.util.Arrays.toString(v));\n");
            case "squares of sorted array" -> r.append("        System.out.println(java.util.Arrays.toString(sortedSquares(").append(javaIntArray(a)).append(")));\n");
            case "first bad version" -> r.append("        System.out.println(firstBadVersion(").append(a).append(",").append(b).append("));\n");
            case "number of 1 bits" -> r.append("        System.out.println(hammingWeight(\"").append(escapeJavaString(a)).append("\"));\n");
            case "reverse bits" -> r.append("        System.out.println(reverseBits(\"").append(escapeJavaString(a)).append("\"));\n");
            case "intersection of two arrays" -> r.append("        System.out.println(java.util.Arrays.toString(intersection(").append(javaIntArray(a)).append(",").append(javaIntArray(b)).append(")));\n");
            case "intersection ii" -> r.append("        System.out.println(java.util.Arrays.toString(intersect(").append(javaIntArray(a)).append(",").append(javaIntArray(b)).append(")));\n");
            case "sort colors" -> r.append("        int[] v=").append(javaIntArray(a)).append("; sortColors(v); System.out.println(java.util.Arrays.toString(v));\n");
            case "flood fill" -> r.append("        int[][] v=").append(javaInt2D(a)).append("; System.out.println(java.util.Arrays.deepToString(floodFill(v,").append(b).append(",").append(c).append(",").append(d).append(")));\n");
            case "number of islands" -> r.append("        System.out.println(numIslands(").append(javaCharGrid(a)).append("));\n");
            case "clone graph" -> r.append("        System.out.println(java.util.Arrays.deepToString(cloneGraph(").append(javaInt2D(a)).append(")));\n");
            case "course schedule" -> r.append("        System.out.println(canFinish(").append(a).append(",").append(javaInt2D(b)).append("));\n");
            case "reverse linked list" -> r.append("        System.out.println(java.util.Arrays.toString(reverseList(").append(javaIntArray(a)).append(")));\n");
            case "middle of linked list" -> r.append("        System.out.println(middleNode(").append(javaIntArray(a)).append("));\n");
            case "linked list cycle" -> r.append("        System.out.println(hasCycle(").append(javaIntArray(a)).append(",").append(b).append("));\n");
            case "coin change" -> r.append("        System.out.println(coinChange(").append(javaIntArray(a)).append(",").append(b).append("));\n");
            default -> throw new IllegalArgumentException("Java runner not implemented for problem: "+problem);
        }
        r.append("    }\n");
        return insertBeforeLastBrace(code,javaSolutionCalls(r.toString()));
    }

    private String javaInt2D(String v){
        if(v==null||v.isBlank()||v.trim().equals("[]")) return "new int[][]{}";
        String s=v.trim();
        if(s.startsWith("[[")&&s.endsWith("]]"))
            return "new int[][]"+s.replace('[','{').replace(']','}');
        return "new int[][]{}";
    }

    private String javaCharGrid(String v){
        if(v==null||v.isBlank()||v.trim().equals("[]")) return "new char[][]{}";
        String s=v.trim();
        if(!s.startsWith("[[")||!s.endsWith("]]")) return "new char[][]{}";
        String body=s.substring(2,s.length()-2);
        String[] rows=body.split("\\],\\[");
        StringBuilder out=new StringBuilder("new char[][]{");
        for(String row:rows){
            row=row.replace("[","").replace("]","");
            out.append("{");
            for(String ch:row.split(",")){
                String q=ch.trim().replace("\"","").replace("'","");
                if(!q.isEmpty()) out.append("'").append(q.charAt(0)).append("',");
            }
            if(out.charAt(out.length()-1)==',')out.setLength(out.length()-1);
            out.append("},");
        }
        if(out.charAt(out.length()-1)==',')out.setLength(out.length()-1);
        return out.append("}").toString();
    }

    private String prepareCpp(
            String code,
            String input,
            String problem
    ) {

        if (code == null ||
                code.isBlank()) {

            throw new IllegalArgumentException(
                    "C++ code cannot be empty."
            );
        }

        /*
         * If user supplied complete C++ program,
         * don't generate another main().
         */
        if (containsCppMain(code)) {
            return code;
        }

        String[] lines =
                splitInput(input);

        String first =
                getLine(lines, 0);

        String second =
                getLine(lines, 1);

        // Convert JSON/Python-style arrays from test input into valid C++ vectors.
        // Scalar values (e.g. target, n, k) are returned unchanged.
        String firstCpp = cppIntVector(first);
        String secondCpp = cppIntVector(second);

        StringBuilder b =
                new StringBuilder();

        b.append(
                "#include <bits/stdc++.h>\n"
        );

        b.append(
                "using namespace std;\n\n"
        );

        b.append(code);
        b.append("\n\n");

        /*
         * LeetCode-style C++ submissions normally use:
         *
         * class Solution {
         * public:
         *     ...
         * };
         *
         * Therefore create Solution object when needed.
         */
        boolean hasSolutionClass =
                code.contains(
                        "class Solution"
                );

        if (hasSolutionClass) {

            b.append(
                    "int main() {\n"
            );

            b.append(
                    "    Solution sol;\n"
            );

        } else {

            b.append(
                    "int main() {\n"
            );
        }

        String p =
                problem.trim().toLowerCase();

        switch (p) {

            case "two sum" -> {

                b.append(
                        "    vector<int> nums = "
                ).append(
                        firstCpp
                ).append(
                        ";\n"
                );

                b.append(
                        "    int target = "
                ).append(
                        secondCpp
                ).append(
                        ";\n"
                );

                b.append(
                        "    auto result = "
                );

                b.append(
                        hasSolutionClass
                                ? "sol.twoSum(nums, target)"
                                : "twoSum(nums, target)"
                );

                b.append(
                        ";\n"
                );

                b.append(
                        "    cout << \"[\";\n"
                );

                b.append(
                        "    for (int i = 0; i < (int)result.size(); i++) {\n"
                );

                b.append(
                        "        if (i) cout << \",\";\n"
                );

                b.append(
                        "        cout << result[i];\n"
                );

                b.append(
                        "    }\n"
                );

                b.append(
                        "    cout << \"]\" << endl;\n"
                );
            }

            case "longest substring" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.lengthOfLongestSubstring("
                                : "lengthOfLongestSubstring("
                ).append(
                        cppString(first)
                ).append(
                        ") << endl;\n"
                );
            }

            case "reverse string" -> {

                b.append(
                        "    string __s = "
                ).append(
                        cppString(first)
                ).append(
                        ";\n"
                );

                b.append(
                        "    "
                ).append(
                        hasSolutionClass
                                ? "sol.reverseString(__s)"
                                : "reverseString(__s)"
                ).append(
                        ";\n"
                );

                b.append(
                        "    cout << __s << endl;\n"
                );
            }

            case "valid palindrome" -> {

                b.append(
                        "    cout << boolalpha << "
                ).append(
                        hasSolutionClass
                                ? "sol.isPalindrome("
                                : "isPalindrome("
                ).append(
                        cppString(first)
                ).append(
                        ") << endl;\n"
                );
            }

            case "valid anagram" -> {

                b.append(
                        "    cout << boolalpha << "
                ).append(
                        hasSolutionClass
                                ? "sol.isAnagram("
                                : "isAnagram("
                ).append(
                        cppString(first)
                ).append(
                        ", "
                ).append(
                        cppString(second)
                ).append(
                        ") << endl;\n"
                );
            }

            case "contains duplicate" -> {

                b.append(
                        "    cout << boolalpha << "
                ).append(
                        hasSolutionClass
                                ? "sol.containsDuplicate("
                                : "containsDuplicate("
                ).append(
                        firstCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "maximum subarray" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.maxSubArray("
                                : "maxSubArray("
                ).append(
                        firstCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "best time to buy and sell stock" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.maxProfit("
                                : "maxProfit("
                ).append(
                        firstCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "binary search" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.search("
                                : "search("
                ).append(
                        firstCpp
                ).append(
                        ", "
                ).append(
                        secondCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "search insert position" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.searchInsert("
                                : "searchInsert("
                ).append(
                        firstCpp
                ).append(
                        ", "
                ).append(
                        secondCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "majority element" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.majorityElement("
                                : "majorityElement("
                ).append(
                        firstCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "single number" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.singleNumber("
                                : "singleNumber("
                ).append(
                        firstCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "climbing stairs" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.climbStairs("
                                : "climbStairs("
                ).append(
                        firstCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "valid parentheses" -> {

                b.append(
                        "    cout << boolalpha << "
                ).append(
                        hasSolutionClass
                                ? "sol.isValid("
                                : "isValid("
                ).append(
                        cppString(first)
                ).append(
                        ") << endl;\n"
                );
            }

            case "happy number" -> {

                b.append(
                        "    cout << boolalpha << "
                ).append(
                        hasSolutionClass
                                ? "sol.isHappy("
                                : "isHappy("
                ).append(
                        firstCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "missing number" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.missingNumber("
                                : "missingNumber("
                ).append(
                        firstCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "pivot index" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.pivotIndex("
                                : "pivotIndex("
                ).append(
                        firstCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "container with most water" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.maxArea("
                                : "maxArea("
                ).append(
                        firstCpp
                ).append(
                        ") << endl;\n"
                );
            }

            case "coin change" -> {

                b.append(
                        "    cout << "
                ).append(
                        hasSolutionClass
                                ? "sol.coinChange("
                                : "coinChange("
                ).append(
                        firstCpp
                ).append(
                        ", "
                ).append(
                        secondCpp
                ).append(
                        ") << endl;\n"
                );
            }

            default -> {
                return prepareCppGenericFallback(code, input, problem);
            }
        }

        b.append(
                "    return 0;\n"
        );

        b.append(
                "}\n"
        );

        return b.toString();
    }

    // =========================================================
    // GENERAL HELPERS
    // =========================================================

    private String[] splitInput(
            String input
    ) {

        if (input == null) {
            return new String[0];
        }

        return input.split(
                "\\R",
                -1
        );
    }

    private String getLine(
            String[] lines,
            int index
    ) {

        if (lines == null ||
                index < 0 ||
                index >= lines.length) {

            return "";
        }

        return lines[index].trim();
    }

    private String JSONString(
            String value
    ) {

        return "\"" +
                escapeJavaScriptString(value) +
                "\"";
    }

    private String pythonString(
            String value
    ) {

        return "'" +
                escapePythonString(value) +
                "'";
    }

    private String cppString(
            String value
    ) {

        return "\"" +
                escapeCppString(value) +
                "\"";
    }

    private String escapeJavaScriptString(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String escapePythonString(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String escapeJavaString(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String escapeCppString(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String normalizeOutput(
            String output
    ) {

        if (output == null) {
            return "";
        }

        return output
                .trim()
                .replaceAll(
                        "\\s+",
                        ""
                );
    }

    // =========================================================
    // MAIN DETECTION
    // =========================================================

    private boolean containsJavaScriptMain(
            String code
    ) {

        if (code == null) {
            return false;
        }

        return code.contains(
                "process.stdin"
        )
                ||
                code.contains(
                        "require('fs')"
                )
                ||
                code.contains(
                        "require(\"fs\")"
                );
    }

    private boolean containsPythonMain(
            String code
    ) {

        if (code == null) {
            return false;
        }

        return code.contains(
                "if __name__ == \"__main__\""
        )
                ||
                code.contains(
                        "if __name__ == '__main__'"
                );
    }

    private String prepareCppGenericFallback(
            String code,
            String input,
            String problem
    ) {
        if (containsCppMain(code)) return code;
        String[] lines=splitInput(input);
        String a=getLine(lines,0), b=getLine(lines,1), c=getLine(lines,2), d=getLine(lines,3);
        String p=problem==null?"":problem.trim().toLowerCase();
        String aa=cppIntVector(a), bb=cppIntVector(b), aaa=cppInt2D(a), bbb=cppInt2D(b);
        StringBuilder x=new StringBuilder("#include <bits/stdc++.h>\nusing namespace std;\n\n");
        x.append(code).append("\n\nint main(){\n");
        switch(p){
            case "merge k lists","merge k sorted lists" -> x.append("auto r=mergeKLists(").append(aaa).append("); for(int z:r) cout<<z<<\" \";");
            case "fizz buzz" -> x.append("auto r=fizzBuzz(").append(a).append("); for(auto &z:r)cout<<z<<\" \";");
            case "pascal triangle" -> x.append("auto r=generate(").append(a).append("); for(auto &v:r){for(int z:v)cout<<z<<\" \";}");
            case "merge two sorted lists" -> x.append("auto r=mergeTwoLists(").append(aa).append(",").append(bb).append("); for(int z:r)cout<<z<<\" \";");
            case "remove duplicates" -> x.append("auto v=").append(aa).append("; cout<<removeDuplicates(v);");
            case "remove element" -> x.append("auto v=").append(aa).append("; cout<<removeElement(v,").append(b).append(");");
            case "rotate array" -> x.append("auto v=").append(aa).append("; rotate(v,").append(b).append("); for(int z:v)cout<<z<<\" \";");
            case "squares of sorted array" -> x.append("auto r=sortedSquares(").append(aa).append("); for(int z:r)cout<<z<<\" \";");
            case "first bad version" -> x.append("cout<<firstBadVersion(").append(a).append(",").append(b).append(");");
            case "number of 1 bits" -> x.append("cout<<hammingWeight(\"").append(escapeCppString(a)).append("\");");
            case "reverse bits" -> x.append("cout<<reverseBits(\"").append(escapeCppString(a)).append("\");");
            case "intersection of two arrays" -> x.append("auto r=intersection(").append(aa).append(",").append(bb).append("); for(int z:r)cout<<z<<\" \";");
            case "intersection ii" -> x.append("auto r=intersect(").append(aa).append(",").append(bb).append("); for(int z:r)cout<<z<<\" \";");
            case "flood fill" -> x.append("auto v=").append(aaa).append("; auto r=floodFill(v,").append(b).append(",").append(c).append(",").append(d).append("); for(auto &row:r)for(int z:row)cout<<z<<\" \";");
            case "number of islands" -> x.append("cout<<numIslands(").append(aaa).append(");");
            case "course schedule" -> x.append("cout<<boolalpha<<canFinish(").append(a).append(",").append(bbb).append(");");
            case "reverse linked list" -> x.append("auto r=reverseList(").append(aa).append("); for(int z:r)cout<<z<<\" \";");
            case "middle of linked list" -> x.append("cout<<middleNode(").append(aa).append(");");
            case "linked list cycle" -> x.append("cout<<boolalpha<<hasCycle(").append(aa).append(",").append(b).append(");");
            case "binary tree max depth" -> x.append("cout<<maxDepth(").append(aa).append(");");
            case "same tree" -> x.append("cout<<boolalpha<<isSameTree(").append(aa).append(",").append(bb).append(");");
            case "invert binary tree" -> x.append("auto r=invertTree(").append(aa).append("); cout<<r;");
            default -> throw new IllegalArgumentException("C++ runner not implemented for problem: "+problem);
        }
        x.append("\nreturn 0;\n}\n");
        return x.toString();
    }

    private String cppIntVector(String v){
        if(v==null||v.isBlank()) return "vector<int>{}";
        String s=v.trim();
        if(s.startsWith("[")&&s.endsWith("]")&&!s.startsWith("[[")){
            return "vector<int>{" + s.substring(1,s.length()-1) + "}";
        }
        return s;
    }

    private String cppInt2D(String v){
        if(v==null||v.isBlank()||v.trim().equals("[]")) return "vector<vector<int>>{}";
        String s=v.trim();
        if(s.startsWith("[[")&&s.endsWith("]]")){
            String body=s.substring(2,s.length()-2);
            String[] rows=body.split("\\],\\[");
            StringBuilder z=new StringBuilder("vector<vector<int>>{");
            for(String row:rows) z.append("{").append(row.replace("[","").replace("]","")).append("},");
            if(z.charAt(z.length()-1)==',')z.setLength(z.length()-1);
            return z.append("}").toString();
        }
        return s;
    }

    private boolean containsCppMain(
            String code
    ) {

        if (code == null) {
            return false;
        }

        return Pattern
                .compile(
                        "\\bmain\\s*\\("
                )
                .matcher(code)
                .find();
    }

    // =========================================================
    // JAVA CLASS FIX
    // =========================================================

    private String convertJavaClassName(
            String code
    ) {

        if (code == null ||
                code.isBlank()) {

            throw new IllegalArgumentException(
                    "Java code cannot be empty."
            );
        }

        String result =
                code;

        /*
         * Judge0 Java uses Main.java.
         *
         * Solution -> Main
         * MyClass  -> Main
         */

        result =
                result.replaceFirst(
                        "(?m)\\bpublic\\s+class\\s+" +
                                "[A-Za-z_$][A-Za-z0-9_$]*",
                        "public class Main"
                );

        /*
         * If class is not public.
         */
        if (!result.contains(
                "public class Main"
        )) {

            result =
                    result.replaceFirst(
                            "(?m)^\\s*class\\s+" +
                                    "[A-Za-z_$][A-Za-z0-9_$]*",
                            "class Main"
                    );
        }

        if (!result.matches(
                "(?s).*\\bclass\\s+" +
                        "[A-Za-z_$][A-Za-z0-9_$]*.*"
        )) {

            throw new IllegalArgumentException(
                    "Invalid Java code: class declaration not found."
            );
        }

        return result;
    }

    // =========================================================
    // INSERT BEFORE LAST }
    // =========================================================

    private String insertBeforeLastBrace(
            String code,
            String content
    ) {

        int lastBrace =
                code.lastIndexOf('}');

        if (lastBrace == -1) {

            throw new IllegalArgumentException(
                    "Invalid Java code: closing brace not found."
            );
        }

        return code.substring(
                0,
                lastBrace
        )
                +
                "\n"
                +
                content
                +
                "\n"
                +
                code.substring(
                        lastBrace
                );
    }
}
