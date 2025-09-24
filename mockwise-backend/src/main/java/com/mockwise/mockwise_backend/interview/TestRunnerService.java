package com.mockwise.mockwise_backend.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestRunnerService {

    private final QuestionTestCaseRepository testCaseRepository;
    private final ObjectMapper objectMapper;
    private final QuestionDriverTemplateRepository driverTemplateRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${judge0.url:https://judge0-ce.p.rapidapi.com}")
    private String judge0Url;

    @Value("${judge0.api.host:}")
    private String judge0Host;

    @Value("${judge0.api.key:}")
    private String judge0Key;

    public static record TestCaseResult(UUID testCaseId, boolean passed, String actualJson, long timeMs, long memoryKb, String stderr) {}

    public List<TestCaseResult> runAll(UUID questionId, String language, String userSource) {
        List<QuestionTestCase> cases = testCaseRepository.findByQuestionIdAndEnabledOrderById(questionId, true);
        if (cases.isEmpty()) return java.util.Collections.emptyList();

        // Prepare batch submissions and keep index mapping back to cases
        List<Map<String, Object>> submissions = new ArrayList<>(cases.size());
        for (QuestionTestCase tc : cases) {
            try {
                String driver = renderDriver(language, questionId, tc.getArgsJson());
                String fullSource = assemble(language, userSource, driver);
                Map<String, Object> s = new java.util.HashMap<>();
                s.put("language_id", mapLanguageId(language));
                s.put("source_code", Base64.getEncoder().encodeToString(fullSource.getBytes(StandardCharsets.UTF_8)));
                s.put("redirect_stderr_to_stdout", true);
                submissions.add(s);
            } catch (Exception e) {
                log.error("Preparing submission failed for testCase {}: {}", tc.getId(), e.getMessage());
                // Put a placeholder so indices align; will mark as failed later
                submissions.add(null);
            }
        }

        // Submit in batch and poll results together
        List<String> tokens = submitBatchToJudge0(submissions);
        Map<String, Map<String, Object>> byToken = pollBatchResults(tokens);

        // Build results in original order
        List<TestCaseResult> results = new ArrayList<>(cases.size());
        for (int i = 0; i < cases.size(); i++) {
            QuestionTestCase tc = cases.get(i);
            String token = (tokens != null && i < tokens.size()) ? tokens.get(i) : null;
            Map<String, Object> res = token != null ? byToken.get(token) : null;
            try {
                String stdoutB64 = res != null ? Objects.toString(res.get("stdout"), null) : null;
                String timeStr = res != null ? Objects.toString(res.get("time"), "0") : "0";
                String memoryStr = res != null ? Objects.toString(res.get("memory"), "0") : "0";
                String stderrB64 = res != null ? Objects.toString(res.get("stderr"), null) : null;
                String out = base64Decode(stdoutB64);
                String err = base64Decode(stderrB64);
                boolean passed = compare(tc.getExpectedJson(), out, tc.getComparator(), tc.getTolerance());
                results.add(new TestCaseResult(tc.getId(), passed, out, (long) (Double.parseDouble(timeStr) * 1000), Long.parseLong(memoryStr), err));
            } catch (Exception e) {
                results.add(new TestCaseResult(tc.getId(), false, null, 0, 0, e.getMessage()));
            }
        }
        log.info("*** Test case run results: *** {}", results);
        return results;
    }

    public QuestionTestCase getTestCase(UUID id) {
        return testCaseRepository.findById(id).orElse(null);
    }

    private String renderDriver(String language, UUID questionId, String argsJson) throws Exception {
        JsonNode args = objectMapper.readTree(argsJson).get("args");
        String callArgs;
        if ("java".equalsIgnoreCase(language)) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < args.size(); i++) { if (i>0) b.append(',').append(' '); b.append(toJavaLiteral(args.get(i))); }
            callArgs = b.toString();
        } else if ("python".equalsIgnoreCase(language) || "py".equalsIgnoreCase(language)) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < args.size(); i++) { if (i>0) b.append(',').append(' '); b.append(toPythonLiteral(args.get(i))); }
            callArgs = b.toString();
        } else if ("cpp".equalsIgnoreCase(language) || "c++".equalsIgnoreCase(language)) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < args.size(); i++) { if (i>0) b.append(',').append(' '); b.append(toCppLiteral(args.get(i))); }
            callArgs = b.toString();
        } else {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }

        // Use stored driver template if available (expects {{ARGS}} placeholder)
        try {
            var opt = driverTemplateRepository.findFirstByQuestion_IdAndLanguageIgnoreCase(questionId, language);
            if (opt.isPresent()) {
                String tmpl = opt.get().getTemplate();
                if (tmpl != null && !tmpl.isBlank()) {
                    return tmpl.replace("{{ARGS}}", callArgs);
                }
            }
        } catch (Exception e) {
            log.warn("Driver template lookup failed for question={}, lang={}: {}", questionId, language, e.getMessage());
        }

        throw new IllegalStateException("No driver template found for question " + questionId + " and language " + language);
    }

    private String toJavaLiteral(JsonNode n) {
        if (n.isInt() || n.isLong()) return n.asText();
        if (n.isDouble() || n.isFloat()) return n.asText();
        if (n.isTextual()) return "\"" + n.asText().replace("\"", "\\\"") + "\"";
        if (n.isArray()) {
            // simple int array for demo; extend for other shapes
            if (n.size() > 0 && n.get(0).isInt()) {
                StringBuilder b = new StringBuilder("new int[]{");
                for (int i = 0; i < n.size(); i++) { if (i>0) b.append(','); b.append(n.get(i).asInt()); }
                b.append('}');
                return b.toString();
            }
        }
        return "null";
    }

    private String toPythonLiteral(JsonNode n) {
        if (n.isInt() || n.isLong()) return n.asText();
        if (n.isDouble() || n.isFloat()) return n.asText();
        if (n.isTextual()) return "\"" + n.asText().replace("\"", "\\\"") + "\"";
        if (n.isArray()) {
            if (n.size() > 0 && n.get(0).isInt()) {
                StringBuilder b = new StringBuilder("[");
                for (int i = 0; i < n.size(); i++) { if (i>0) b.append(','); b.append(n.get(i).asInt()); }
                b.append(']');
                return b.toString();
            }
        }
        return "None";
    }

    private String toCppLiteral(JsonNode n) {
        if (n.isInt() || n.isLong()) return n.asText();
        if (n.isDouble() || n.isFloat()) return n.asText();
        if (n.isTextual()) return "\"" + n.asText().replace("\"", "\\\"") + "\"";
        if (n.isArray()) {
            if (n.size() > 0 && n.get(0).isInt()) {
                StringBuilder b = new StringBuilder("std::vector<int>{");
                for (int i = 0; i < n.size(); i++) { if (i>0) b.append(','); b.append(n.get(i).asInt()); }
                b.append('}');
                return b.toString();
            }
        }
        return "{}";
    }

    private String assemble(String language, String user, String driver) {
        if ("java".equalsIgnoreCase(language)) {
            String prelude = "import java.util.*;\nimport java.math.*;\n";
            return prelude + user + "\n" + driver;
        } else if ("python".equalsIgnoreCase(language) || "py".equalsIgnoreCase(language)) {
            // User's Python code followed by driver (which imports json and has main guard)
            return user + "\n\n" + driver;
        } else if ("cpp".equalsIgnoreCase(language) || "c++".equalsIgnoreCase(language)) {
            String prelude = "#include <bits/stdc++.h>\nusing namespace std;\n";
            return prelude + user + "\n" + driver;
        }
        throw new IllegalArgumentException("Unsupported language: " + language);
    }

    private int mapLanguageId(String language) {
        return switch (language.toLowerCase()) {
            case "java" -> 62; // Judge0 Java (OpenJDK 21)
            case "python" -> 71; // Python 3.11
            case "cpp", "c++" -> 54; // GCC 14 C++
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private String submitToJudge0(Map<String, Object> submission) {
        String url = judge0Url + "/submissions?base64_encoded=true&wait=false";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (judge0Host != null && !judge0Host.isBlank()) headers.set("X-RapidAPI-Host", judge0Host);
        if (judge0Key != null && !judge0Key.isBlank()) headers.set("X-RapidAPI-Key", judge0Key);
        Map<String, Object> res = restTemplate.postForObject(url, new HttpEntity<>(submission, headers), Map.class);
        return Objects.toString(res.get("token"), null);
    }

    private Map<String, Object> pollResult(String token) throws InterruptedException {
        String url = judge0Url + "/submissions/" + token + "?base64_encoded=true";
        for (int i = 0; i < 30; i++) {
            Map<String, Object> res = restTemplate.getForObject(url, Map.class);
            if (res == null) { Thread.sleep(300); continue; }
            Object status = ((Map<?,?>)res.get("status")).get("id");
            if (Objects.equals(status, 3) || Objects.equals(status, 4)) {
                return res;
            }
            Thread.sleep(500);
        }
        return Map.of("status", Map.of("id", -1), "stderr", Base64.getEncoder().encodeToString("Timeout".getBytes(StandardCharsets.UTF_8)));
    }

    // Submit multiple submissions in a single call; returns tokens in same order
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<String> submitBatchToJudge0(List<Map<String, Object>> submissions) {
        String url = judge0Url + "/submissions/batch?base64_encoded=true&wait=false";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (judge0Host != null && !judge0Host.isBlank()) headers.set("X-RapidAPI-Host", judge0Host);
        if (judge0Key != null && !judge0Key.isBlank()) headers.set("X-RapidAPI-Key", judge0Key);

        Map<String, Object> body = Map.of("submissions", submissions);
        Map resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
        if (resp == null) return java.util.Collections.nCopies(submissions.size(), null);
        Object arr = resp.get("submissions");
        if (arr instanceof List list) {
            List<String> tokens = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map m) tokens.add(Objects.toString(m.get("token"), null));
                else tokens.add(null);
            }
            return tokens;
        }
        return java.util.Collections.nCopies(submissions.size(), null);
    }

    // Poll multiple tokens until all complete or timeout; returns map token->result
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Map<String, Object>> pollBatchResults(List<String> tokens) {
        Map<String, Map<String, Object>> done = new java.util.HashMap<>();
        if (tokens == null || tokens.isEmpty()) return done;
        List<String> pending = new ArrayList<>();
        for (String t : tokens) if (t != null) pending.add(t);
        String joined = String.join(",", pending);

        for (int attempt = 0; attempt < 30 && !pending.isEmpty(); attempt++) {
            String url = judge0Url + "/submissions/batch?tokens=" + joined + "&base64_encoded=true";
            Map resp = restTemplate.getForObject(url, Map.class);
            if (resp != null) {
                Object arr = resp.get("submissions");
                if (arr instanceof List list) {
                    List<String> stillPending = new ArrayList<>();
                    for (Object o : list) {
                        if (!(o instanceof Map m)) continue;
                        String token = Objects.toString(m.get("token"), null);
                        Object statusObj = m.get("status");
                        Integer statusId = null;
                        if (statusObj instanceof Map st) statusId = (Integer) st.get("id");
                        if (Objects.equals(statusId, 3) || Objects.equals(statusId, 4)) {
                            done.put(token, (Map<String, Object>) m);
                        } else {
                            stillPending.add(token);
                        }
                    }
                    pending = stillPending;
                    joined = String.join(",", pending);
                }
            }
            try { Thread.sleep(500); } catch (InterruptedException ignored) { }
        }
        return done;
    }

    private String base64Decode(String b64) {
        if (b64 == null) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean compare(String expectedJson, String actualText, String comparator, Double tolerance) {
        try {
            JsonNode exp = objectMapper.readTree(expectedJson).get("value");
            // For demo: compare string forms; extend with comparator modes
            String expStr = exp.isArray() ? objectMapper.writeValueAsString(exp) : exp.asText();
            String actStr = actualText != null ? actualText.trim() : null;
            return Objects.equals(expStr, actStr);
        } catch (Exception e) {
            return false;
        }
    }
}


