package com.mockwise.backend.repository.dashboard;

public enum Practice {
    CODING,
    SYSTEM_DESIGN;

    public String label() {
        return switch (this) {
            case CODING -> "Data Structures & Algorithms";
            case SYSTEM_DESIGN -> "System Design";
        };
    }
}
