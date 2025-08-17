package com.mockwise.mockwise_backend.interview;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionDataLoader implements CommandLineRunner {

    private final QuestionRepository questionRepository;

    @Override
    public void run(String... args) {
        try {
            long count = questionRepository.count();
            log.info("Current questions in database: {}", count);
            
            if (count == 0) {
                log.info("Loading sample interview questions...");
                loadSampleQuestions();
                log.info("Sample questions loaded successfully. Total questions: {}", questionRepository.count());
            } else {
                log.info("Questions already exist in database, skipping data loading.");
            }
        } catch (Exception e) {
            log.error("Error in QuestionDataLoader: ", e);
        }
    }

    private void loadSampleQuestions() {
        List<Question> questions = List.of(
            createQuestion(
                "Two Sum",
                "Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target. Each input has exactly one solution, and you may not use the same element twice.",
                "Input: nums = [2,7,11,15], target = 9\nOutput: [0,1]\nExplanation: Because nums[0] + nums[1] == 9, we return [0, 1].",
                "• 2 ≤ nums.length ≤ 10^4\n• -10^9 ≤ nums[i] ≤ 10^9\n• -10^9 ≤ target ≤ 10^9\n• Only one valid answer exists.",
                Question.Difficulty.EASY,
                "function twoSum(nums, target) {\n    // Write your solution here\n    return [];\n}"
            ),
            
            createQuestion(
                "Valid Parentheses",
                "Given a string s containing just the characters '(', ')', '{', '}', '[' and ']', determine if the input string is valid. An input string is valid if: Open brackets must be closed by the same type of brackets, Open brackets must be closed in the correct order, Every close bracket has a corresponding open bracket of the same type.",
                "Input: s = \"()\"\nOutput: true\n\nInput: s = \"()[]{}\"\nOutput: true\n\nInput: s = \"(]\"\nOutput: false",
                "• 1 ≤ s.length ≤ 10^4\n• s consists of parentheses only '()[]{}'.",
                Question.Difficulty.EASY,
                "function isValid(s) {\n    // Write your solution here\n    return false;\n}"
            ),
            
            createQuestion(
                "Merge Intervals",
                "Given an array of intervals where intervals[i] = [starti, endi], merge all overlapping intervals, and return an array of the non-overlapping intervals that cover all the intervals in the input.",
                "Input: intervals = [[1,3],[2,6],[8,10],[15,18]]\nOutput: [[1,6],[8,10],[15,18]]\nExplanation: Since intervals [1,3] and [2,6] overlap, merge them into [1,6].",
                "• 1 ≤ intervals.length ≤ 10^4\n• intervals[i].length == 2\n• 0 ≤ starti ≤ endi ≤ 10^4",
                Question.Difficulty.MEDIUM,
                "function merge(intervals) {\n    // Write your solution here\n    return [];\n}"
            ),
            
            createQuestion(
                "Longest Substring Without Repeating Characters",
                "Given a string s, find the length of the longest substring without repeating characters.",
                "Input: s = \"abcabcbb\"\nOutput: 3\nExplanation: The answer is \"abc\", with the length of 3.\n\nInput: s = \"bbbbb\"\nOutput: 1\nExplanation: The answer is \"b\", with the length of 1.",
                "• 0 ≤ s.length ≤ 5 * 10^4\n• s consists of English letters, digits, symbols and spaces.",
                Question.Difficulty.MEDIUM,
                "function lengthOfLongestSubstring(s) {\n    // Write your solution here\n    return 0;\n}"
            ),
            
            createQuestion(
                "Trapping Rain Water",
                "Given n non-negative integers representing an elevation map where the width of each bar is 1, compute how much water it can trap after raining.",
                "Input: height = [0,1,0,2,1,0,1,3,2,1,2,1]\nOutput: 6\nExplanation: The elevation map is represented by array [0,1,0,2,1,0,1,3,2,1,2,1]. In this case, 6 units of rain water are being trapped.",
                "• n == height.length\n• 1 ≤ n ≤ 2 * 10^4\n• 0 ≤ height[i] ≤ 3 * 10^4",
                Question.Difficulty.HARD,
                "function trap(height) {\n    // Write your solution here\n    return 0;\n}"
            ),
            
            createQuestion(
                "Median of Two Sorted Arrays",
                "Given two sorted arrays nums1 and nums2 of size m and n respectively, return the median of the two sorted arrays. The overall run time complexity should be O(log (m+n)).",
                "Input: nums1 = [1,3], nums2 = [2]\nOutput: 2.00000\nExplanation: merged array = [1,2,3] and median is 2.\n\nInput: nums1 = [1,2], nums2 = [3,4]\nOutput: 2.50000\nExplanation: merged array = [1,2,3,4] and median is (2 + 3) / 2 = 2.5.",
                "• nums1.length == m\n• nums2.length == n\n• 0 ≤ m ≤ 1000\n• 0 ≤ n ≤ 1000\n• 1 ≤ m + n ≤ 2000\n• -10^6 ≤ nums1[i], nums2[i] ≤ 10^6",
                Question.Difficulty.HARD,
                "function findMedianSortedArrays(nums1, nums2) {\n    // Write your solution here\n    return 0.0;\n}"
            )
        );

        questionRepository.saveAll(questions);
        log.info("Loaded {} sample questions", questions.size());
    }

    private Question createQuestion(String title, String description, String example, 
                                  String constraints, Question.Difficulty difficulty, String template) {
        Question question = new Question();
        question.setTitle(title);
        question.setDescription(description);
        question.setExample(example);
        question.setConstraints(constraints);
        question.setDifficulty(difficulty);
        question.setDefaultTemplate(template);
        return question;
    }
}
