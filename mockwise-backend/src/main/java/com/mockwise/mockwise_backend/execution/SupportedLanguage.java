package com.mockwise.mockwise_backend.execution;

public enum SupportedLanguage {
    JAVA("java"),
    PYTHON("python"),
    CPP("cpp");

    private final String label;

    SupportedLanguage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static SupportedLanguage fromString(String language) {
        for (SupportedLanguage lang : values()) {
            if (lang.label.equalsIgnoreCase(language) || lang.name().equalsIgnoreCase(language)) {
                return lang;
            }
        }
        throw new IllegalArgumentException("Unsupported language: " + language);
    }
}