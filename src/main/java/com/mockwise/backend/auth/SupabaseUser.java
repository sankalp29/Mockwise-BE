package com.mockwise.backend.auth;

public class SupabaseUser {
    private final String id;
    private final String email;
    private final boolean emailVerified;

    public SupabaseUser(String id, String email, boolean emailVerified) {
        this.id = id;
        this.email = email;
        this.emailVerified = emailVerified;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }

    @Override
    public String toString() {
        return String.format("SupabaseUser{id='%s', email='%s', emailVerified=%s}",
                id, email, emailVerified);
    }
}
