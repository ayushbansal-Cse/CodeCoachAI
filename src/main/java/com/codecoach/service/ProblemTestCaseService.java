package com.codecoach.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Stores coding problems and their test cases.
 *
 * CodeController uses:
 *
 *     getTestCases(problemName)
 *
 * Each problem has one or more test cases.
 */
@Service
public class ProblemTestCaseService {

    // =========================================================
    // TEST CASE
    // =========================================================

    public static class TestCase {

        private final String input;
        private final String expectedOutput;

        public TestCase(
                String input,
                String expectedOutput
        ) {
            this.input = input;
            this.expectedOutput = expectedOutput;
        }

        public String getInput() {
            return input;
        }

        public String getExpectedOutput() {
            return expectedOutput;
        }
    }


    // =========================================================
    // PROBLEM DATABASE
    // =========================================================

    private final Map<String, List<TestCase>> testCases =
            new LinkedHashMap<>();


    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public ProblemTestCaseService() {

        // -----------------------------------------------------
        // 1. TWO SUM
        // -----------------------------------------------------

        add(
                "Two Sum",
                "[2,7,11,15]\n9",
                "[0,1]"
        );

        add(
                "Two Sum",
                "[3,2,4]\n6",
                "[1,2]"
        );

        add(
                "Two Sum",
                "[3,3]\n6",
                "[0,1]"
        );


        // -----------------------------------------------------
        // 2. LONGEST SUBSTRING
        // -----------------------------------------------------

        add(
                "Longest Substring",
                "abcabcbb",
                "3"
        );

        add(
                "Longest Substring",
                "bbbbb",
                "1"
        );

        add(
                "Longest Substring",
                "pwwkew",
                "3"
        );


        // -----------------------------------------------------
        // 3. MERGE K LISTS
        // -----------------------------------------------------

        add(
                "Merge K Lists",
                "[[1,4,5],[1,3,4],[2,6]]",
                "[1,1,2,3,4,4,5,6]"
        );

        add(
                "Merge K Lists",
                "[]",
                "[]"
        );

        add(
                "Merge K Lists",
                "[[1],[2],[3]]",
                "[1,2,3]"
        );


        // -----------------------------------------------------
        // 4. REVERSE STRING
        // -----------------------------------------------------

        add(
                "Reverse String",
                "hello",
                "olleh"
        );

        add(
                "Reverse String",
                "CodeCoach",
                "hcaedoC"
        );

        add(
                "Reverse String",
                "a",
                "a"
        );


        // -----------------------------------------------------
        // 5. VALID PALINDROME
        // -----------------------------------------------------

        add(
                "Valid Palindrome",
                "A man, a plan, a canal: Panama",
                "true"
        );

        add(
                "Valid Palindrome",
                "race a car",
                "false"
        );

        add(
                "Valid Palindrome",
                " ",
                "true"
        );


        // -----------------------------------------------------
        // 6. VALID ANAGRAM
        // -----------------------------------------------------

        add(
                "Valid Anagram",
                "anagram\nnagaram",
                "true"
        );

        add(
                "Valid Anagram",
                "rat\ncar",
                "false"
        );

        add(
                "Valid Anagram",
                "listen\nsilent",
                "true"
        );


        // -----------------------------------------------------
        // 7. CONTAINS DUPLICATE
        // -----------------------------------------------------

        add(
                "Contains Duplicate",
                "[1,2,3,1]",
                "true"
        );

        add(
                "Contains Duplicate",
                "[1,2,3,4]",
                "false"
        );

        add(
                "Contains Duplicate",
                "[1,1]",
                "true"
        );


        // -----------------------------------------------------
        // 8. MAXIMUM SUBARRAY
        // -----------------------------------------------------

        add(
                "Maximum Subarray",
                "[-2,1,-3,4,-1,2,1,-5,4]",
                "6"
        );

        add(
                "Maximum Subarray",
                "[1]",
                "1"
        );

        add(
                "Maximum Subarray",
                "[5,4,-1,7,8]",
                "23"
        );


        // -----------------------------------------------------
        // 9. BEST TIME TO BUY AND SELL STOCK
        // -----------------------------------------------------

        add(
                "Best Time to Buy and Sell Stock",
                "[7,1,5,3,6,4]",
                "5"
        );

        add(
                "Best Time to Buy and Sell Stock",
                "[7,6,4,3,1]",
                "0"
        );

        add(
                "Best Time to Buy and Sell Stock",
                "[1,2]",
                "1"
        );


        // -----------------------------------------------------
        // 10. MOVE ZEROES
        // -----------------------------------------------------

        add(
                "Move Zeroes",
                "[0,1,0,3,12]",
                "[1,3,12,0,0]"
        );

        add(
                "Move Zeroes",
                "[0]",
                "[0]"
        );

        add(
                "Move Zeroes",
                "[1,2,3]",
                "[1,2,3]"
        );


        // -----------------------------------------------------
        // 11. BINARY SEARCH
        // -----------------------------------------------------

        add(
                "Binary Search",
                "[-1,0,3,5,9,12]\n9",
                "4"
        );

        add(
                "Binary Search",
                "[-1,0,3,5,9,12]\n2",
                "-1"
        );

        add(
                "Binary Search",
                "[5]\n5",
                "0"
        );


        // -----------------------------------------------------
        // 12. FIRST BAD VERSION
        // -----------------------------------------------------

        add(
                "First Bad Version",
                "5\n4",
                "4"
        );

        add(
                "First Bad Version",
                "1\n1",
                "1"
        );

        add(
                "First Bad Version",
                "10\n7",
                "7"
        );


        // -----------------------------------------------------
        // 13. SEARCH INSERT POSITION
        // -----------------------------------------------------

        add(
                "Search Insert Position",
                "[1,3,5,6]\n5",
                "2"
        );

        add(
                "Search Insert Position",
                "[1,3,5,6]\n2",
                "1"
        );

        add(
                "Search Insert Position",
                "[1,3,5,6]\n7",
                "4"
        );


        // -----------------------------------------------------
        // 14. SQUARES OF SORTED ARRAY
        // -----------------------------------------------------

        add(
                "Squares of Sorted Array",
                "[-4,-1,0,3,10]",
                "[0,1,9,16,100]"
        );

        add(
                "Squares of Sorted Array",
                "[-7,-3,2,3,11]",
                "[4,9,9,49,121]"
        );

        add(
                "Squares of Sorted Array",
                "[-1]",
                "[1]"
        );


        // -----------------------------------------------------
        // 15. MAJORITY ELEMENT
        // -----------------------------------------------------

        add(
                "Majority Element",
                "[3,2,3]",
                "3"
        );

        add(
                "Majority Element",
                "[2,2,1,1,1,2,2]",
                "2"
        );

        add(
                "Majority Element",
                "[1]",
                "1"
        );


        // -----------------------------------------------------
        // 16. SINGLE NUMBER
        // -----------------------------------------------------

        add(
                "Single Number",
                "[2,2,1]",
                "1"
        );

        add(
                "Single Number",
                "[4,1,2,1,2]",
                "4"
        );

        add(
                "Single Number",
                "[1]",
                "1"
        );


        // -----------------------------------------------------
        // 17. PLUS ONE
        // -----------------------------------------------------

        add(
                "Plus One",
                "[1,2,3]",
                "[1,2,4]"
        );

        add(
                "Plus One",
                "[4,3,2,1]",
                "[4,3,2,2]"
        );

        add(
                "Plus One",
                "[9]",
                "[1,0]"
        );


        // -----------------------------------------------------
        // 18. FIZZ BUZZ
        // -----------------------------------------------------

        add(
                "Fizz Buzz",
                "3",
                "[1,2,Fizz]"
        );

        add(
                "Fizz Buzz",
                "5",
                "[1,2,Fizz,4,Buzz]"
        );

        add(
                "Fizz Buzz",
                "15",
                "[1,2,Fizz,4,Buzz,Fizz,7,8,Fizz,Buzz,11,Fizz,13,14,FizzBuzz]"
        );


        // -----------------------------------------------------
        // 19. CLIMBING STAIRS
        // -----------------------------------------------------

        add(
                "Climbing Stairs",
                "2",
                "2"
        );

        add(
                "Climbing Stairs",
                "3",
                "3"
        );

        add(
                "Climbing Stairs",
                "5",
                "8"
        );


        // -----------------------------------------------------
        // 20. PASCAL TRIANGLE
        // -----------------------------------------------------

        add(
                "Pascal Triangle",
                "5",
                "[[1],[1,1],[1,2,1],[1,3,3,1],[1,4,6,4,1]]"
        );

        add(
                "Pascal Triangle",
                "1",
                "[[1]]"
        );


        // -----------------------------------------------------
        // 21. VALID PARENTHESES
        // -----------------------------------------------------

        add(
                "Valid Parentheses",
                "()",
                "true"
        );

        add(
                "Valid Parentheses",
                "()[]{}",
                "true"
        );

        add(
                "Valid Parentheses",
                "(]",
                "false"
        );


        // -----------------------------------------------------
        // 22. MERGE TWO SORTED LISTS
        // -----------------------------------------------------

        add(
                "Merge Two Sorted Lists",
                "[1,2,4]\n[1,3,4]",
                "[1,1,2,3,4,4]"
        );

        add(
                "Merge Two Sorted Lists",
                "[]\n[]",
                "[]"
        );

        add(
                "Merge Two Sorted Lists",
                "[]\n[0]",
                "[0]"
        );


        // -----------------------------------------------------
        // 23. REMOVE DUPLICATES
        // -----------------------------------------------------

        add(
                "Remove Duplicates",
                "[1,1,2]",
                "2"
        );

        add(
                "Remove Duplicates",
                "[0,0,1,1,1,2,2,3,3,4]",
                "5"
        );


        // -----------------------------------------------------
        // 24. REMOVE ELEMENT
        // -----------------------------------------------------

        add(
                "Remove Element",
                "[3,2,2,3]\n3",
                "2"
        );

        add(
                "Remove Element",
                "[0,1,2,2,3,0,4,2]\n2",
                "5"
        );


        // -----------------------------------------------------
        // 25. ROTATE ARRAY
        // -----------------------------------------------------

        add(
                "Rotate Array",
                "[1,2,3,4,5,6,7]\n3",
                "[5,6,7,1,2,3,4]"
        );

        add(
                "Rotate Array",
                "[-1,-100,3,99]\n2",
                "[3,99,-1,-100]"
        );


        // -----------------------------------------------------
        // 26. PRODUCT EXCEPT SELF
        // -----------------------------------------------------

        add(
                "Product Except Self",
                "[1,2,3,4]",
                "[24,12,8,6]"
        );

        add(
                "Product Except Self",
                "[-1,1,0,-3,3]",
                "[0,0,9,0,0]"
        );


        // -----------------------------------------------------
        // 27. CONTAINER WITH MOST WATER
        // -----------------------------------------------------

        add(
                "Container With Most Water",
                "[1,8,6,2,5,4,8,3,7]",
                "49"
        );

        add(
                "Container With Most Water",
                "[1,1]",
                "1"
        );


        // -----------------------------------------------------
        // 28. THREE SUM
        // -----------------------------------------------------

        add(
                "Three Sum",
                "[-1,0,1,2,-1,-4]",
                "[[-1,-1,2],[-1,0,1]]"
        );

        add(
                "Three Sum",
                "[0,1,1]",
                "[]"
        );

        add(
                "Three Sum",
                "[0,0,0]",
                "[[0,0,0]]"
        );


        // -----------------------------------------------------
        // 29. GROUP ANAGRAMS
        // -----------------------------------------------------

        add(
                "Group Anagrams",
                "[eat,tea,tan,ate,nat,bat]",
                "[[eat,tea,ate],[tan,nat],[bat]]"
        );

        add(
                "Group Anagrams",
                "[a]",
                "[[a]]"
        );


        // -----------------------------------------------------
        // 30. TOP K FREQUENT ELEMENTS
        // -----------------------------------------------------

        add(
                "Top K Frequent Elements",
                "[1,1,1,2,2,3]\n2",
                "[1,2]"
        );

        add(
                "Top K Frequent Elements",
                "[1]\n1",
                "[1]"
        );


        // -----------------------------------------------------
        // 31. HAPPY NUMBER
        // -----------------------------------------------------

        add(
                "Happy Number",
                "19",
                "true"
        );

        add(
                "Happy Number",
                "2",
                "false"
        );

        add(
                "Happy Number",
                "1",
                "true"
        );


        // -----------------------------------------------------
        // 32. COUNTING BITS
        // -----------------------------------------------------

        add(
                "Counting Bits",
                "2",
                "[0,1,1]"
        );

        add(
                "Counting Bits",
                "5",
                "[0,1,1,2,1,2]"
        );


        // -----------------------------------------------------
        // 33. NUMBER OF 1 BITS
        // -----------------------------------------------------

        add(
                "Number of 1 Bits",
                "00000000000000000000000000001011",
                "3"
        );

        add(
                "Number of 1 Bits",
                "00000000000000000000000010000000",
                "1"
        );


        // -----------------------------------------------------
        // 34. REVERSE BITS
        // -----------------------------------------------------

        add(
                "Reverse Bits",
                "00000010100101000001111010011100",
                "00111001011110000010100101000000"
        );


        // -----------------------------------------------------
        // 35. MISSING NUMBER
        // -----------------------------------------------------

        add(
                "Missing Number",
                "[3,0,1]",
                "2"
        );

        add(
                "Missing Number",
                "[0,1]",
                "2"
        );

        add(
                "Missing Number",
                "[9,6,4,2,3,5,7,0,1]",
                "8"
        );


        // -----------------------------------------------------
        // 36. PIVOT INDEX
        // -----------------------------------------------------

        add(
                "Pivot Index",
                "[1,7,3,6,5,6]",
                "3"
        );

        add(
                "Pivot Index",
                "[1,2,3]",
                "-1"
        );

        add(
                "Pivot Index",
                "[2,1,-1]",
                "0"
        );


        // -----------------------------------------------------
        // 37. INTERSECTION OF TWO ARRAYS
        // -----------------------------------------------------

        add(
                "Intersection of Two Arrays",
                "[1,2,2,1]\n[2,2]",
                "[2]"
        );

        add(
                "Intersection of Two Arrays",
                "[4,9,5]\n[9,4,9,8,4]",
                "[4,9]"
        );


        // -----------------------------------------------------
        // 38. INTERSECTION II
        // -----------------------------------------------------

        add(
                "Intersection II",
                "[1,2,2,1]\n[2,2]",
                "[2,2]"
        );

        add(
                "Intersection II",
                "[4,9,5]\n[9,4,9,8,4]",
                "[4,9]"
        );


        // -----------------------------------------------------
        // 39. SORT COLORS
        // -----------------------------------------------------

        add(
                "Sort Colors",
                "[2,0,2,1,1,0]",
                "[0,0,1,1,2,2]"
        );

        add(
                "Sort Colors",
                "[2,0,1]",
                "[0,1,2]"
        );


        // -----------------------------------------------------
        // 40. REVERSE LINKED LIST
        // -----------------------------------------------------

        add(
                "Reverse Linked List",
                "[1,2,3,4,5]",
                "[5,4,3,2,1]"
        );

        add(
                "Reverse Linked List",
                "[1,2]",
                "[2,1]"
        );

        add(
                "Reverse Linked List",
                "[]",
                "[]"
        );


        // -----------------------------------------------------
        // 41. MIDDLE OF LINKED LIST
        // -----------------------------------------------------

        add(
                "Middle of Linked List",
                "[1,2,3,4,5]",
                "3"
        );

        add(
                "Middle of Linked List",
                "[1,2,3,4,5,6]",
                "4"
        );


        // -----------------------------------------------------
        // 42. LINKED LIST CYCLE
        // -----------------------------------------------------

        add(
                "Linked List Cycle",
                "[3,2,0,-4]\n1",
                "true"
        );

        add(
                "Linked List Cycle",
                "[1,2]\n0",
                "true"
        );

        add(
                "Linked List Cycle",
                "[1]\n-1",
                "false"
        );


        // -----------------------------------------------------
        // 43. BINARY TREE MAX DEPTH
        // -----------------------------------------------------

        add(
                "Binary Tree Max Depth",
                "[3,9,20,null,null,15,7]",
                "3"
        );

        add(
                "Binary Tree Max Depth",
                "[1,null,2]",
                "2"
        );

        add(
                "Binary Tree Max Depth",
                "[]",
                "0"
        );


        // -----------------------------------------------------
        // 44. SAME TREE
        // -----------------------------------------------------

        add(
                "Same Tree",
                "[1,2,3]\n[1,2,3]",
                "true"
        );

        add(
                "Same Tree",
                "[1,2]\n[1,null,2]",
                "false"
        );


        // -----------------------------------------------------
        // 45. INVERT BINARY TREE
        // -----------------------------------------------------

        add(
                "Invert Binary Tree",
                "[4,2,7,1,3,6,9]",
                "[4,7,2,9,6,3,1]"
        );

        add(
                "Invert Binary Tree",
                "[2,1,3]",
                "[2,3,1]"
        );


        // -----------------------------------------------------
        // 46. FLOOD FILL
        // -----------------------------------------------------

        add(
                "Flood Fill",
                "[[1,1,1],[1,1,0],[1,0,1]]\n1\n1\n2",
                "[[2,2,2],[2,2,0],[2,0,1]]"
        );

        add(
                "Flood Fill",
                "[[0,0,0],[0,0,0]]\n0\n0\n0",
                "[[0,0,0],[0,0,0]]"
        );


        // -----------------------------------------------------
        // 47. NUMBER OF ISLANDS
        // -----------------------------------------------------

        add(
                "Number of Islands",
                "[[1,1,1,1,0],[1,1,0,1,0],[1,1,0,0,0],[0,0,0,0,0]]",
                "1"
        );

        add(
                "Number of Islands",
                "[[1,1,0,0,0],[1,1,0,0,0],[0,0,1,0,0],[0,0,0,1,1]]",
                "3"
        );


        // -----------------------------------------------------
        // 48. CLONE GRAPH
        // -----------------------------------------------------

        add(
                "Clone Graph",
                "[[2,4],[1,3],[2,4],[1,3]]",
                "[[2,4],[1,3],[2,4],[1,3]]"
        );

        add(
                "Clone Graph",
                "[]",
                "[]"
        );


        // -----------------------------------------------------
        // 49. COURSE SCHEDULE
        // -----------------------------------------------------

        add(
                "Course Schedule",
                "2\n[[1,0]]",
                "true"
        );

        add(
                "Course Schedule",
                "2\n[[1,0],[0,1]]",
                "false"
        );

        add(
                "Course Schedule",
                "3\n[[1,0],[2,1]]",
                "true"
        );


        // -----------------------------------------------------
        // 50. COIN CHANGE
        // -----------------------------------------------------

        add(
                "Coin Change",
                "[1,2,5]\n11",
                "3"
        );

        add(
                "Coin Change",
                "[2]\n3",
                "-1"
        );

        add(
                "Coin Change",
                "[1]\n0",
                "0"
        );
    }


    // =========================================================
    // ADD TEST CASE
    // =========================================================

    private void add(
            String problem,
            String input,
            String expectedOutput
    ) {

        testCases
                .computeIfAbsent(
                        problem,
                        key -> new ArrayList<>()
                )
                .add(
                        new TestCase(
                                input,
                                expectedOutput
                        )
                );
    }


    // =========================================================
    // GET TEST CASES
    // =========================================================

    public List<TestCase> getTestCases(
            String problem
    ) {

        if (problem == null ||
                problem.isBlank()) {

            return Collections.emptyList();
        }


        List<TestCase> result =
                testCases.get(problem);


        if (result == null) {

            return Collections.emptyList();
        }


        return Collections.unmodifiableList(
                result
        );
    }


    // =========================================================
    // GET ALL PROBLEM NAMES
    // =========================================================

    public List<String> getAllProblems() {

        return new ArrayList<>(
                testCases.keySet()
        );
    }


    // =========================================================
    // CHECK PROBLEM EXISTS
    // =========================================================

    public boolean problemExists(
            String problem
    ) {

        return problem != null &&
                testCases.containsKey(problem);
    }


    // =========================================================
    // GET NUMBER OF PROBLEMS
    // =========================================================

    public int getProblemCount() {

        return testCases.size();
    }
}