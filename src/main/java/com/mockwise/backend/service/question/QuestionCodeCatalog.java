package com.mockwise.backend.service.question;

import com.mockwise.backend.repository.question.ProgrammingLanguage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Starter code and a reference solution for every supported language.
 * Both versions are written so a syntax check can compile them.
 */
public final class QuestionCodeCatalog {

    public record Sample(String stub, String optimal) {
    }

    public record SeedQuestion(
            String title,
            String description,
            String example,
            String constraints,
            Map<ProgrammingLanguage, Sample> samples
    ) {
    }

    private QuestionCodeCatalog() {
    }

    public static List<SeedQuestion> questions() {
        return List.of(
                question("Two Sum", twoSumMarkdown(), twoSumSamples()),
                question("Valid Parentheses", parensMarkdown(), parensSamples()),
                question("Longest Substring Without Repeating Characters", windowMarkdown(), windowSamples()),
                question("Merge Intervals", intervalsMarkdown(), intervalSamples()),
                question("Trapping Rain Water", rainMarkdown(), rainSamples())
        );
    }

    private static SeedQuestion question(String title, String[] markdown, Map<ProgrammingLanguage, Sample> samples) {
        return new SeedQuestion(title, markdown[0], markdown[1], markdown[2], samples);
    }

    private static Map<ProgrammingLanguage, Sample> pack(String javaSig, String javaStub, String javaOpt,
                                            String pySig, String pyStub, String pyOpt,
                                            String cppSig, String cppStub, String cppOpt,
                                            String jsStub, String jsOpt,
                                            String tsStub, String tsOpt,
                                            String goStub, String goOpt,
                                            String rustStub, String rustOpt,
                                            String rubyStub, String rubyOpt,
                                            String scalaStub, String scalaOpt,
                                            String csStub, String csOpt) {
        Map<ProgrammingLanguage, Sample> samples = new LinkedHashMap<>();
        samples.put(ProgrammingLanguage.JAVA, new Sample(javaWrap(javaSig, javaStub), javaWrap(javaSig, javaOpt)));
        samples.put(ProgrammingLanguage.PYTHON, new Sample(pyWrap(pySig, pyStub), pyWrap(pySig, pyOpt)));
        samples.put(ProgrammingLanguage.CPP, new Sample(cppWrap(cppSig, cppStub), cppWrap(cppSig, cppOpt)));
        samples.put(ProgrammingLanguage.JAVASCRIPT, new Sample(jsStub, jsOpt));
        samples.put(ProgrammingLanguage.TYPESCRIPT, new Sample(tsStub, tsOpt));
        samples.put(ProgrammingLanguage.GO, new Sample(goWrap(goStub), goWrap(goOpt)));
        samples.put(ProgrammingLanguage.RUST, new Sample(rustStub, rustOpt));
        samples.put(ProgrammingLanguage.RUBY, new Sample(rubyStub, rubyOpt));
        samples.put(ProgrammingLanguage.SCALA, new Sample(scalaStub, scalaOpt));
        samples.put(ProgrammingLanguage.CSHARP, new Sample(csWrap(csStub), csWrap(csOpt)));
        return samples;
    }

    private static String javaWrap(String signature, String body) {
        return "class Solution {\n    " + signature + " {\n" + body + "\n    }\n}\n";
    }

    private static String pyWrap(String signature, String body) {
        return "class Solution:\n    " + signature + "\n" + body + "\n";
    }

    private static String cppWrap(String signature, String body) {
        return "#include <bits/stdc++.h>\nusing namespace std;\nclass Solution {\npublic:\n    "
                + signature + " {\n" + body + "\n    }\n};\n";
    }

    private static String goWrap(String body) {
        return "package main\n\n" + body + "\n";
    }

    private static String csWrap(String method) {
        return "public class Solution {\n" + method + "\n    public static void Main() {}\n}\n";
    }

    private static Map<ProgrammingLanguage, Sample> twoSumSamples() {
        return pack(
                "public int[] twoSum(int[] nums, int target)",
                "        return new int[0];",
                """
                        java.util.Map<Integer, Integer> seen = new java.util.HashMap<>();
                        for (int i = 0; i < nums.length; i++) {
                            int need = target - nums[i];
                            if (seen.containsKey(need)) {
                                return new int[] { seen.get(need), i };
                            }
                            seen.put(nums[i], i);
                        }
                        return new int[0];""",
                "def two_sum(self, nums, target):",
                "        return []",
                """
                        seen = {}
                        for i, value in enumerate(nums):
                            need = target - value
                            if need in seen:
                                return [seen[need], i]
                            seen[value] = i
                        return []""",
                "vector<int> twoSum(vector<int>& nums, int target)",
                "        return {};",
                """
                        unordered_map<int, int> seen;
                        for (int i = 0; i < (int) nums.size(); i++) {
                            int need = target - nums[i];
                            if (seen.count(need)) return {seen[need], i};
                            seen[nums[i]] = i;
                        }
                        return {};""",
                "function twoSum(nums, target) {\n  return [];\n}\n",
                """
                        function twoSum(nums, target) {
                          const seen = new Map();
                          for (let i = 0; i < nums.length; i++) {
                            const need = target - nums[i];
                            if (seen.has(need)) return [seen.get(need), i];
                            seen.set(nums[i], i);
                          }
                          return [];
                        }
                        """,
                "function twoSum(nums: number[], target: number): number[] {\n  return [];\n}\n",
                """
                        function twoSum(nums: number[], target: number): number[] {
                          const seen = new Map<number, number>();
                          for (let i = 0; i < nums.length; i++) {
                            const need = target - nums[i];
                            if (seen.has(need)) return [seen.get(need) as number, i];
                            seen.set(nums[i], i);
                          }
                          return [];
                        }
                        """,
                "func twoSum(nums []int, target int) []int {\n    return []int{}\n}\n",
                """
                        func twoSum(nums []int, target int) []int {
                            seen := map[int]int{}
                            for i, value := range nums {
                                if j, ok := seen[target-value]; ok {
                                    return []int{j, i}
                                }
                                seen[value] = i
                            }
                            return []int{}
                        }
                        """,
                "fn two_sum(_nums: &[i32], _target: i32) -> Vec<i32> {\n    Vec::new()\n}\n",
                """
                        use std::collections::HashMap;
                        fn two_sum(nums: &[i32], target: i32) -> Vec<i32> {
                            let mut seen = HashMap::new();
                            for (i, value) in nums.iter().enumerate() {
                                let need = target - value;
                                if let Some(&j) = seen.get(&need) {
                                    return vec![j as i32, i as i32];
                                }
                                seen.insert(value, i);
                            }
                            Vec::new()
                        }
                        """,
                "def two_sum(nums, target)\n  []\nend\n",
                """
                        def two_sum(nums, target)
                          seen = {}
                          nums.each_with_index do |value, i|
                            need = target - value
                            return [seen[need], i] if seen.key?(need)
                            seen[value] = i
                          end
                          []
                        end
                        """,
                "object Solution {\n  def twoSum(nums: Array[Int], target: Int): Array[Int] = Array()\n}\n",
                """
                        object Solution {
                          def twoSum(nums: Array[Int], target: Int): Array[Int] = {
                            val seen = scala.collection.mutable.Map[Int, Int]()
                            var i = 0
                            while (i < nums.length) {
                              val need = target - nums(i)
                              if (seen.contains(need)) return Array(seen(need), i)
                              seen(nums(i)) = i
                              i += 1
                            }
                            Array()
                          }
                        }
                        """,
                "    public int[] TwoSum(int[] nums, int target) { return new int[0]; }\n",
                """
                            public int[] TwoSum(int[] nums, int target) {
                                var seen = new System.Collections.Generic.Dictionary<int, int>();
                                for (int i = 0; i < nums.Length; i++) {
                                    int need = target - nums[i];
                                    if (seen.ContainsKey(need)) return new int[] { seen[need], i };
                                    seen[nums[i]] = i;
                                }
                                return new int[0];
                            }
                        """
        );
    }

    private static Map<ProgrammingLanguage, Sample> parensSamples() {
        return pack(
                "public boolean isValid(String s)",
                "        return false;",
                """
                        java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
                        for (int i = 0; i < s.length(); i++) {
                            char c = s.charAt(i);
                            if (c == '(' || c == '[' || c == '{') stack.push(c);
                            else if (stack.isEmpty()) return false;
                            else {
                                char open = stack.pop();
                                if ((c == ')' && open != '(') || (c == ']' && open != '[') || (c == '}' && open != '{')) return false;
                            }
                        }
                        return stack.isEmpty();""",
                "def is_valid(self, s):",
                "        return False",
                """
                        pairs = {')': '(', ']': '[', '}': '{'}
                        stack = []
                        for ch in s:
                            if ch in '([{':
                                stack.append(ch)
                            elif not stack or stack.pop() != pairs.get(ch):
                                return False
                        return not stack""",
                "bool isValid(string s)",
                "        return false;",
                """
                        stack<char> open;
                        for (char c : s) {
                            if (c == '(' || c == '[' || c == '{') open.push(c);
                            else if (open.empty()) return false;
                            else {
                                char o = open.top(); open.pop();
                                if ((c == ')' && o != '(') || (c == ']' && o != '[') || (c == '}' && o != '{')) return false;
                            }
                        }
                        return open.empty();""",
                "function isValid(s) {\n  return false;\n}\n",
                """
                        function isValid(s) {
                          const pairs = { ')': '(', ']': '[', '}': '{' };
                          const stack = [];
                          for (const ch of s) {
                            if ('([{'.includes(ch)) stack.push(ch);
                            else if (stack.pop() !== pairs[ch]) return false;
                          }
                          return stack.length === 0;
                        }
                        """,
                "function isValid(s: string): boolean {\n  return false;\n}\n",
                """
                        function isValid(s: string): boolean {
                          const pairs: Record<string, string> = { ')': '(', ']': '[', '}': '{' };
                          const stack: string[] = [];
                          for (const ch of s) {
                            if ('([{'.includes(ch)) stack.push(ch);
                            else if (stack.pop() !== pairs[ch]) return false;
                          }
                          return stack.length === 0;
                        }
                        """,
                "func isValid(s string) bool {\n    return false\n}\n",
                """
                        func isValid(s string) bool {
                            pairs := map[byte]byte{')': '(', ']': '[', '}': '{'}
                            stack := []byte{}
                            for i := 0; i < len(s); i++ {
                                c := s[i]
                                if c == '(' || c == '[' || c == '{' {
                                    stack = append(stack, c)
                                } else if len(stack) == 0 || stack[len(stack)-1] != pairs[c] {
                                    return false
                                } else {
                                    stack = stack[:len(stack)-1]
                                }
                            }
                            return len(stack) == 0
                        }
                        """,
                "fn is_valid(_s: &str) -> bool {\n    false\n}\n",
                """
                        fn is_valid(s: &str) -> bool {
                            let mut stack = Vec::new();
                            for c in s.chars() {
                                match c {
                                    '(' | '[' | '{' => stack.push(c),
                                    ')' => if stack.pop() != Some('(') { return false; },
                                    ']' => if stack.pop() != Some('[') { return false; },
                                    '}' => if stack.pop() != Some('{') { return false; },
                                    _ => {}
                                }
                            }
                            stack.is_empty()
                        }
                        """,
                "def is_valid(s)\n  false\nend\n",
                """
                        def is_valid(s)
                          pairs = { ')' => '(', ']' => '[', '}' => '{' }
                          stack = []
                          s.each_char do |ch|
                            if '([{'.include?(ch)
                              stack << ch
                            elsif stack.pop != pairs[ch]
                              return false
                            end
                          end
                          stack.empty?
                        end
                        """,
                "object Solution {\n  def isValid(s: String): Boolean = false\n}\n",
                """
                        object Solution {
                          def isValid(s: String): Boolean = {
                            val pairs = Map(')' -> '(', ']' -> '[', '}' -> '{')
                            val stack = scala.collection.mutable.Stack[Char]()
                            s.foreach { ch =>
                              if ("([{".contains(ch)) stack.push(ch)
                              else if (stack.isEmpty || stack.pop() != pairs(ch)) return false
                            }
                            stack.isEmpty
                          }
                        }
                        """,
                "    public bool IsValid(string s) { return false; }\n",
                """
                            public bool IsValid(string s) {
                                var pairs = new System.Collections.Generic.Dictionary<char, char> { [')'] = '(', [']'] = '[', ['}'] = '{' };
                                var stack = new System.Collections.Generic.Stack<char>();
                                foreach (char c in s) {
                                    if (c == '(' || c == '[' || c == '{') stack.Push(c);
                                    else if (stack.Count == 0 || stack.Pop() != pairs[c]) return false;
                                }
                                return stack.Count == 0;
                            }
                        """
        );
    }

    private static Map<ProgrammingLanguage, Sample> windowSamples() {
        return pack(
                "public int lengthOfLongestSubstring(String s)",
                "        return 0;",
                """
                        java.util.Map<Character, Integer> last = new java.util.HashMap<>();
                        int best = 0;
                        int start = 0;
                        for (int i = 0; i < s.length(); i++) {
                            char c = s.charAt(i);
                            if (last.containsKey(c) && last.get(c) >= start) start = last.get(c) + 1;
                            last.put(c, i);
                            best = Math.max(best, i - start + 1);
                        }
                        return best;""",
                "def length_of_longest_substring(self, s):",
                "        return 0",
                """
                        last = {}
                        best = start = 0
                        for i, ch in enumerate(s):
                            if ch in last and last[ch] >= start:
                                start = last[ch] + 1
                            last[ch] = i
                            best = max(best, i - start + 1)
                        return best""",
                "int lengthOfLongestSubstring(string s)",
                "        return 0;",
                """
                        unordered_map<char, int> last;
                        int best = 0, start = 0;
                        for (int i = 0; i < (int) s.size(); i++) {
                            if (last.count(s[i]) && last[s[i]] >= start) start = last[s[i]] + 1;
                            last[s[i]] = i;
                            best = max(best, i - start + 1);
                        }
                        return best;""",
                "function lengthOfLongestSubstring(s) {\n  return 0;\n}\n",
                """
                        function lengthOfLongestSubstring(s) {
                          const last = new Map();
                          let best = 0, start = 0;
                          for (let i = 0; i < s.length; i++) {
                            if (last.has(s[i]) && last.get(s[i]) >= start) start = last.get(s[i]) + 1;
                            last.set(s[i], i);
                            best = Math.max(best, i - start + 1);
                          }
                          return best;
                        }
                        """,
                "function lengthOfLongestSubstring(s: string): number {\n  return 0;\n}\n",
                """
                        function lengthOfLongestSubstring(s: string): number {
                          const last = new Map<string, number>();
                          let best = 0, start = 0;
                          for (let i = 0; i < s.length; i++) {
                            const prev = last.get(s[i]);
                            if (prev !== undefined && prev >= start) start = prev + 1;
                            last.set(s[i], i);
                            best = Math.max(best, i - start + 1);
                          }
                          return best;
                        }
                        """,
                "func lengthOfLongestSubstring(s string) int {\n    return 0\n}\n",
                """
                        func lengthOfLongestSubstring(s string) int {
                            last := map[byte]int{}
                            best, start := 0, 0
                            for i := 0; i < len(s); i++ {
                                if prev, ok := last[s[i]]; ok && prev >= start {
                                    start = prev + 1
                                }
                                last[s[i]] = i
                                if i-start+1 > best {
                                    best = i - start + 1
                                }
                            }
                            return best
                        }
                        """,
                "fn length_of_longest_substring(_s: &str) -> i32 {\n    0\n}\n",
                """
                        use std::collections::HashMap;
                        fn length_of_longest_substring(s: &str) -> i32 {
                            let mut last = HashMap::new();
                            let (mut best, mut start) = (0, 0);
                            for (i, c) in s.chars().enumerate() {
                                if let Some(&prev) = last.get(&c) {
                                    if prev >= start { start = prev + 1; }
                                }
                                last.insert(c, i);
                                best = best.max(i - start + 1);
                            }
                            best as i32
                        }
                        """,
                "def length_of_longest_substring(s)\n  0\nend\n",
                """
                        def length_of_longest_substring(s)
                          last = {}
                          best = start = 0
                          s.chars.each_with_index do |ch, i|
                            start = last[ch] + 1 if last.key?(ch) && last[ch] >= start
                            last[ch] = i
                            best = [best, i - start + 1].max
                          end
                          best
                        end
                        """,
                "object Solution {\n  def lengthOfLongestSubstring(s: String): Int = 0\n}\n",
                """
                        object Solution {
                          def lengthOfLongestSubstring(s: String): Int = {
                            val last = scala.collection.mutable.Map[Char, Int]()
                            var best = 0
                            var start = 0
                            s.indices.foreach { i =>
                              val ch = s.charAt(i)
                              if (last.contains(ch) && last(ch) >= start) start = last(ch) + 1
                              last(ch) = i
                              best = math.max(best, i - start + 1)
                            }
                            best
                          }
                        }
                        """,
                "    public int LengthOfLongestSubstring(string s) { return 0; }\n",
                """
                            public int LengthOfLongestSubstring(string s) {
                                var last = new System.Collections.Generic.Dictionary<char, int>();
                                int best = 0, start = 0;
                                for (int i = 0; i < s.Length; i++) {
                                    if (last.ContainsKey(s[i]) && last[s[i]] >= start) start = last[s[i]] + 1;
                                    last[s[i]] = i;
                                    best = System.Math.Max(best, i - start + 1);
                                }
                                return best;
                            }
                        """
        );
    }

    private static Map<ProgrammingLanguage, Sample> intervalSamples() {
        return pack(
                "public int[][] merge(int[][] intervals)",
                "        return new int[0][0];",
                """
                        if (intervals.length == 0) return new int[0][0];
                        java.util.Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));
                        java.util.List<int[]> merged = new java.util.ArrayList<>();
                        int[] current = intervals[0];
                        for (int i = 1; i < intervals.length; i++) {
                            if (intervals[i][0] <= current[1]) current[1] = Math.max(current[1], intervals[i][1]);
                            else { merged.add(current); current = intervals[i]; }
                        }
                        merged.add(current);
                        return merged.toArray(new int[0][]);""",
                "def merge(self, intervals):",
                "        return []",
                """
                        if not intervals:
                            return []
                        intervals = sorted(intervals)
                        merged = [list(intervals[0])]
                        for start, end in intervals[1:]:
                            if start <= merged[-1][1]:
                                merged[-1][1] = max(merged[-1][1], end)
                            else:
                                merged.append([start, end])
                        return merged""",
                "vector<vector<int>> merge(vector<vector<int>>& intervals)",
                "        return {};",
                """
                        if (intervals.empty()) return {};
                        sort(intervals.begin(), intervals.end());
                        vector<vector<int>> merged;
                        merged.push_back(intervals[0]);
                        for (size_t i = 1; i < intervals.size(); i++) {
                            if (intervals[i][0] <= merged.back()[1]) merged.back()[1] = max(merged.back()[1], intervals[i][1]);
                            else merged.push_back(intervals[i]);
                        }
                        return merged;""",
                "function merge(intervals) {\n  return [];\n}\n",
                """
                        function merge(intervals) {
                          if (!intervals.length) return [];
                          intervals.sort((a, b) => a[0] - b[0]);
                          const merged = [intervals[0].slice()];
                          for (let i = 1; i < intervals.length; i++) {
                            const last = merged[merged.length - 1];
                            if (intervals[i][0] <= last[1]) last[1] = Math.max(last[1], intervals[i][1]);
                            else merged.push(intervals[i].slice());
                          }
                          return merged;
                        }
                        """,
                "function merge(intervals: number[][]): number[][] {\n  return [];\n}\n",
                """
                        function merge(intervals: number[][]): number[][] {
                          if (!intervals.length) return [];
                          intervals.sort((a, b) => a[0] - b[0]);
                          const merged: number[][] = [intervals[0].slice()];
                          for (let i = 1; i < intervals.length; i++) {
                            const last = merged[merged.length - 1];
                            if (intervals[i][0] <= last[1]) last[1] = Math.max(last[1], intervals[i][1]);
                            else merged.push(intervals[i].slice());
                          }
                          return merged;
                        }
                        """,
                "func merge(intervals [][]int) [][]int {\n    return [][]int{}\n}\n",
                """
                        import "sort"
                        func merge(intervals [][]int) [][]int {
                            if len(intervals) == 0 {
                                return [][]int{}
                            }
                            sort.Slice(intervals, func(i, j int) bool { return intervals[i][0] < intervals[j][0] })
                            merged := [][]int{intervals[0]}
                            for _, cur := range intervals[1:] {
                                last := merged[len(merged)-1]
                                if cur[0] <= last[1] {
                                    if cur[1] > last[1] {
                                        last[1] = cur[1]
                                    }
                                } else {
                                    merged = append(merged, cur)
                                }
                            }
                            return merged
                        }
                        """,
                "fn merge(_intervals: &[[i32; 2]]) -> Vec<[i32; 2]> {\n    Vec::new()\n}\n",
                """
                        fn merge(intervals: &[[i32; 2]]) -> Vec<[i32; 2]> {
                            if intervals.is_empty() { return Vec::new(); }
                            let mut items = intervals.to_vec();
                            items.sort_by_key(|item| item[0]);
                            let mut merged = vec![items[0]];
                            for cur in items.into_iter().skip(1) {
                                let last = merged.last_mut().unwrap();
                                if cur[0] <= last[1] { last[1] = last[1].max(cur[1]); }
                                else { merged.push(cur); }
                            }
                            merged
                        }
                        """,
                "def merge(intervals)\n  []\nend\n",
                """
                        def merge(intervals)
                          return [] if intervals.empty?
                          sorted = intervals.sort_by { |start, _| start }
                          merged = [sorted[0].dup]
                          sorted[1..].each do |start, finish|
                            if start <= merged[-1][1]
                              merged[-1][1] = [merged[-1][1], finish].max
                            else
                              merged << [start, finish]
                            end
                          end
                          merged
                        end
                        """,
                "object Solution {\n  def merge(intervals: Array[Array[Int]]): Array[Array[Int]] = Array()\n}\n",
                """
                        object Solution {
                          def merge(intervals: Array[Array[Int]]): Array[Array[Int]] = {
                            if (intervals.isEmpty) return Array()
                            val sorted = intervals.sortBy(_(0))
                            val merged = scala.collection.mutable.ArrayBuffer(sorted.head)
                            sorted.tail.foreach { cur =>
                              val last = merged.last
                              if (cur(0) <= last(1)) last(1) = math.max(last(1), cur(1))
                              else merged += cur
                            }
                            merged.toArray
                          }
                        }
                        """,
                "    public int[][] Merge(int[][] intervals) { return new int[0][]; }\n",
                """
                            public int[][] Merge(int[][] intervals) {
                                if (intervals.Length == 0) return new int[0][];
                                System.Array.Sort(intervals, (a, b) => a[0].CompareTo(b[0]));
                                var merged = new System.Collections.Generic.List<int[]> { intervals[0] };
                                for (int i = 1; i < intervals.Length; i++) {
                                    var last = merged[merged.Count - 1];
                                    if (intervals[i][0] <= last[1]) last[1] = System.Math.Max(last[1], intervals[i][1]);
                                    else merged.Add(intervals[i]);
                                }
                                return merged.ToArray();
                            }
                        """
        );
    }

    private static Map<ProgrammingLanguage, Sample> rainSamples() {
        return pack(
                "public int trap(int[] height)",
                "        return 0;",
                """
                        int left = 0;
                        int right = height.length - 1;
                        int leftMax = 0;
                        int rightMax = 0;
                        int water = 0;
                        while (left < right) {
                            if (height[left] < height[right]) {
                                leftMax = Math.max(leftMax, height[left]);
                                water += leftMax - height[left];
                                left++;
                            } else {
                                rightMax = Math.max(rightMax, height[right]);
                                water += rightMax - height[right];
                                right--;
                            }
                        }
                        return water;""",
                "def trap(self, height):",
                "        return 0",
                """
                        left, right = 0, len(height) - 1
                        left_max = right_max = water = 0
                        while left < right:
                            if height[left] < height[right]:
                                left_max = max(left_max, height[left])
                                water += left_max - height[left]
                                left += 1
                            else:
                                right_max = max(right_max, height[right])
                                water += right_max - height[right]
                                right -= 1
                        return water""",
                "int trap(vector<int>& height)",
                "        return 0;",
                """
                        int left = 0, right = (int) height.size() - 1;
                        int leftMax = 0, rightMax = 0, water = 0;
                        while (left < right) {
                            if (height[left] < height[right]) {
                                leftMax = max(leftMax, height[left]);
                                water += leftMax - height[left];
                                left++;
                            } else {
                                rightMax = max(rightMax, height[right]);
                                water += rightMax - height[right];
                                right--;
                            }
                        }
                        return water;""",
                "function trap(height) {\n  return 0;\n}\n",
                """
                        function trap(height) {
                          let left = 0, right = height.length - 1;
                          let leftMax = 0, rightMax = 0, water = 0;
                          while (left < right) {
                            if (height[left] < height[right]) {
                              leftMax = Math.max(leftMax, height[left]);
                              water += leftMax - height[left];
                              left++;
                            } else {
                              rightMax = Math.max(rightMax, height[right]);
                              water += rightMax - height[right];
                              right--;
                            }
                          }
                          return water;
                        }
                        """,
                "function trap(height: number[]): number {\n  return 0;\n}\n",
                """
                        function trap(height: number[]): number {
                          let left = 0, right = height.length - 1;
                          let leftMax = 0, rightMax = 0, water = 0;
                          while (left < right) {
                            if (height[left] < height[right]) {
                              leftMax = Math.max(leftMax, height[left]);
                              water += leftMax - height[left];
                              left++;
                            } else {
                              rightMax = Math.max(rightMax, height[right]);
                              water += rightMax - height[right];
                              right--;
                            }
                          }
                          return water;
                        }
                        """,
                "func trap(height []int) int {\n    return 0\n}\n",
                """
                        func trap(height []int) int {
                            left, right := 0, len(height)-1
                            leftMax, rightMax, water := 0, 0, 0
                            for left < right {
                                if height[left] < height[right] {
                                    if height[left] > leftMax { leftMax = height[left] }
                                    water += leftMax - height[left]
                                    left++
                                } else {
                                    if height[right] > rightMax { rightMax = height[right] }
                                    water += rightMax - height[right]
                                    right--
                                }
                            }
                            return water
                        }
                        """,
                "fn trap(_height: &[i32]) -> i32 {\n    0\n}\n",
                """
                        fn trap(height: &[i32]) -> i32 {
                            if height.is_empty() { return 0; }
                            let (mut left, mut right) = (0, height.len() - 1);
                            let (mut left_max, mut right_max, mut water) = (0, 0, 0);
                            while left < right {
                                if height[left] < height[right] {
                                    left_max = left_max.max(height[left]);
                                    water += left_max - height[left];
                                    left += 1;
                                } else {
                                    right_max = right_max.max(height[right]);
                                    water += right_max - height[right];
                                    right -= 1;
                                }
                            }
                            water
                        }
                        """,
                "def trap(height)\n  0\nend\n",
                """
                        def trap(height)
                          left = 0
                          right = height.length - 1
                          left_max = right_max = water = 0
                          while left < right
                            if height[left] < height[right]
                              left_max = [left_max, height[left]].max
                              water += left_max - height[left]
                              left += 1
                            else
                              right_max = [right_max, height[right]].max
                              water += right_max - height[right]
                              right -= 1
                            end
                          end
                          water
                        end
                        """,
                "object Solution {\n  def trap(height: Array[Int]): Int = 0\n}\n",
                """
                        object Solution {
                          def trap(height: Array[Int]): Int = {
                            var left = 0
                            var right = height.length - 1
                            var leftMax = 0
                            var rightMax = 0
                            var water = 0
                            while (left < right) {
                              if (height(left) < height(right)) {
                                leftMax = math.max(leftMax, height(left))
                                water += leftMax - height(left)
                                left += 1
                              } else {
                                rightMax = math.max(rightMax, height(right))
                                water += rightMax - height(right)
                                right -= 1
                              }
                            }
                            water
                          }
                        }
                        """,
                "    public int Trap(int[] height) { return 0; }\n",
                """
                            public int Trap(int[] height) {
                                int left = 0, right = height.Length - 1;
                                int leftMax = 0, rightMax = 0, water = 0;
                                while (left < right) {
                                    if (height[left] < height[right]) {
                                        leftMax = System.Math.Max(leftMax, height[left]);
                                        water += leftMax - height[left];
                                        left++;
                                    } else {
                                        rightMax = System.Math.Max(rightMax, height[right]);
                                        water += rightMax - height[right];
                                        right--;
                                    }
                                }
                                return water;
                            }
                        """
        );
    }

    private static String[] twoSumMarkdown() {
        return new String[] {
                """
                ## Two Sum

                Given an array of integers `nums` and an integer `target`, return the **indices** of the two numbers that add up to `target`.

                You may assume each input has **exactly one** solution, and you may not use the same element twice.
                """,
                """
                **Example**

                ```text
                Input: nums = [2,7,11,15], target = 9
                Output: [0,1]
                ```
                """,
                """
                - `2 <= nums.length <= 10^4`
                - Exactly one valid answer exists
                """
        };
    }

    private static String[] parensMarkdown() {
        return new String[] {
                """
                ## Valid Parentheses

                Given a string `s` containing just the characters `(`, `)`, `{`, `}`, `[` and `]`, determine if the input string is valid.

                A string is valid when brackets close in the correct order.
                """,
                """
                **Example**

                ```text
                Input: s = "()[]{}"
                Output: true
                ```
                """,
                "- `1 <= s.length <= 10^4`\n- `s` consists of parentheses only"
        };
    }

    private static String[] windowMarkdown() {
        return new String[] {
                """
                ## Longest Substring Without Repeating Characters

                Given a string `s`, find the length of the **longest substring** without repeating characters.
                """,
                """
                **Example**

                ```text
                Input: s = "abcabcbb"
                Output: 3
                Explanation: "abc" is the longest substring without a repeat.
                ```
                """,
                "- `0 <= s.length <= 5 * 10^4`"
        };
    }

    private static String[] intervalsMarkdown() {
        return new String[] {
                """
                ## Merge Intervals

                Given an array of `intervals` where `intervals[i] = [start, end]`, merge all overlapping intervals and return the result.
                """,
                """
                **Example**

                ```text
                Input: intervals = [[1,3],[2,6],[8,10],[15,18]]
                Output: [[1,6],[8,10],[15,18]]
                ```
                """,
                "- `1 <= intervals.length <= 10^4`"
        };
    }

    private static String[] rainMarkdown() {
        return new String[] {
                """
                ## Trapping Rain Water

                Given `n` non-negative integers representing an elevation map where the width of each bar is `1`, compute how much water it can trap after raining.
                """,
                """
                **Example**

                ```text
                Input: height = [0,1,0,2,1,0,1,3,2,1,2,1]
                Output: 6
                ```
                """,
                "- `n == height.length`\n- `1 <= n <= 2 * 10^4`"
        };
    }
}
