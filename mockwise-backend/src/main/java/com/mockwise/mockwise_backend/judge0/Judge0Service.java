package com.mockwise.mockwise_backend.judge0;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.Getter;
import lombok.Setter;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Map;

@Service
public class Judge0Service {

    @Value("${judge0.api.url}")
    private String judge0ApiUrl;

    @Value("${judge0.api.key}")
    private String judge0ApiKey;

    private final RestTemplate restTemplate;

    public Judge0Service(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Judge0SubmissionResponse submitCode(String sourceCode, String languageId, String stdin, String expectedOutput) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("X-RapidAPI-Key", judge0ApiKey);
        headers.set("X-RapidAPI-Host", "judge0-ce.p.rapidapi.com");

        // Prepare the request body
        Map<String, Object> requestBody = Map.of(
                "source_code", sourceCode,
                "language_id", languageId,
                "stdin", stdin,
                "expected_output", expectedOutput,
                "cpu_time_limit", 2, // Example limit
                "memory_limit", 128000 // Example limit (128 MB)
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String submissionUrl = judge0ApiUrl + "/submissions?base64_encoded=false&wait=true";

        ResponseEntity<Judge0SubmissionResponse> response = restTemplate.exchange(
                submissionUrl, HttpMethod.POST, entity, Judge0SubmissionResponse.class
        );

        return response.getBody();
    }

    // You might need a separate class for the Judge0 response. For now, let's assume a simple structure.
    @Getter
    @Setter
    public static class Judge0SubmissionResponse {
        private String token;
        private String status;
        private String stdout;
        private String stderr;
        private String compile_output;
        private String message;
        private String time;
        private String memory;
        private String exit_code;
        private String exit_signal;
        private String judge_0_response_reason;
    }

    // You'll also need a configuration for RestTemplate or use WebClient if preferred
}
