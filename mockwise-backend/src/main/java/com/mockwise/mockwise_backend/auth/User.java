package com.mockwise.mockwise_backend.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email_lower", columnNames = {"email_lower"}),
        @UniqueConstraint(name = "uk_users_google_sub", columnNames = {"google_sub"})
})
public class User {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @Column(name = "email_lower", nullable = false)
    private String emailLower;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash; // null for OAuth-only users

    @Column(name = "google_sub")
    private String googleSub; // nullable

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    protected User() {}

    public User(String name, String email, String passwordHash, String googleSub) {
        this.name = name;
        this.email = email;
        this.emailLower = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.googleSub = googleSub;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getEmailLower() { return emailLower; }
    public String getPasswordHash() { return passwordHash; }
    public String getGoogleSub() { return googleSub; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isEmailVerified() { return emailVerified; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) {
        this.email = email;
        this.emailLower = email == null ? null : email.toLowerCase();
    }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
}


