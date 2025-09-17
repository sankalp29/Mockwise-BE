package com.mockwise.mockwise_backend.interview;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CodeGeneratorService {

    // Method to generate Java driver code
    public String generateJavaDriverCode(String userCode, Question question, List<TestCase> testCases) {
        String javaTemplate = question.getDriverCodeTemplates().stream()
                .filter(template -> "java".equalsIgnoreCase(template.getLanguage()))
                .map(DriverCodeTemplate::getTemplateContent)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Java driver code template not found for question " + question.getId()));

        if (javaTemplate == null) {
            throw new IllegalArgumentException("Java driver code template not found for question " + question.getId());
        }

        // Prepare test cases data as JSON for injection
        StringBuilder testCasesJson = new StringBuilder();
        testCasesJson.append("[");
        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            testCasesJson.append("{\"input\": \"").append(testCase.getInput().replace("\"", "\\\"")).append("\", ");
            testCasesJson.append("\"expectedOutput\": \"").append(testCase.getExpectedOutput().replace("\"", "\\\"")).append("\"}");
            if (i < testCases.size() - 1) {
                testCasesJson.append(",");
            }
        }
        testCasesJson.append("]");

        String fullCode = javaTemplate.replace("{{USER_CODE}}", userCode);
        fullCode = fullCode.replace("{{TEST_CASES_JSON}}", testCasesJson.toString());

        return fullCode;
    }

    // Method to generate Python driver code
    public String generatePythonDriverCode(String userCode, Question question, List<TestCase> testCases) {
        String pythonTemplate = question.getDriverCodeTemplates().stream()
                .filter(template -> "python".equalsIgnoreCase(template.getLanguage()))
                .map(DriverCodeTemplate::getTemplateContent)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Python driver code template not found for question " + question.getId()));

        if (pythonTemplate == null) {
            throw new IllegalArgumentException("Python driver code template not found for question " + question.getId());
        }

        // Prepare test cases data as JSON for injection
        StringBuilder testCasesJson = new StringBuilder();
        testCasesJson.append("[");
        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            testCasesJson.append("{\"input\": \"").append(testCase.getInput().replace("\"", "\\\"")).append("\", ");
            testCasesJson.append("\"expectedOutput\": \"").append(testCase.getExpectedOutput().replace("\"", "\\\"")).append("\"}");
            if (i < testCases.size() - 1) {
                testCasesJson.append(",");
            }
        }
        testCasesJson.append("]");

        String fullCode = pythonTemplate.replace("{{USER_CODE}}", userCode);
        fullCode = fullCode.replace("{{TEST_CASES_JSON}}", testCasesJson.toString());

        return fullCode;
    }

    // Method to generate C++ driver code
    public String generateCppDriverCode(String userCode, Question question, List<TestCase> testCases) {
        String cppTemplate = question.getDriverCodeTemplates().stream()
                .filter(template -> "cpp".equalsIgnoreCase(template.getLanguage()))
                .map(DriverCodeTemplate::getTemplateContent)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("C++ driver code template not found for question " + question.getId()));

        if (cppTemplate == null) {
            throw new IllegalArgumentException("C++ driver code template not found for question " + question.getId());
        }

        // Prepare test cases data as JSON for injection
        StringBuilder testCasesJson = new StringBuilder();
        testCasesJson.append("[");
        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            // For C++, we might need to escape double quotes differently or use a raw string literal if available
            testCasesJson.append("{\"input\": \"").append(testCase.getInput().replace("\"", "\\\"")).append("\", ");
            testCasesJson.append("\"expectedOutput\": \"").append(testCase.getExpectedOutput().replace("\"", "\\\"")).append("\"}");
            if (i < testCases.size() - 1) {
                testCasesJson.append(",");
            }
        }
        testCasesJson.append("]");

        String fullCode = cppTemplate.replace("{{USER_CODE}}", userCode);
        fullCode = fullCode.replace("{{TEST_CASES_JSON}}", testCasesJson.toString());

        return fullCode;
    }

    // In a more advanced scenario, you might have a method like this to get the language ID for Judge0
    public String getJudge0LanguageId(String language) {
        return switch (language.toLowerCase()) {
            case "java" -> "62"; // Example Judge0 Language ID for Java
            case "python" -> "71"; // Example Judge0 Language ID for Python 3
            case "cpp" -> "54"; // Example Judge0 Language ID for C++ (GCC 9.2.0)
            // Add more languages as needed
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }
}
