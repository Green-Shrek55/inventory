package com.kursach.inventory.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

@Entity
@Table(name = "users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String username;

    @NotBlank
    @Column(nullable = false)
    private String passwordHash;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 255)
    private String twoFactorCodeHash;

    private Instant twoFactorCodeExpiresAt;

    @Column(length = 255)
    private String passwordResetToken;

    private Instant passwordResetTokenExpiresAt;

    @Column(length = 255)
    private String passwordResetCodeHash;

    private Instant passwordResetCodeExpiresAt;

    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private Instant loginLockedUntil;

    public AppUser() {}

    public AppUser(String username, String passwordHash, String email, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public Department getDepartment() { return department; }
    public boolean isEnabled() { return enabled; }
    public String getTwoFactorCodeHash() { return twoFactorCodeHash; }
    public Instant getTwoFactorCodeExpiresAt() { return twoFactorCodeExpiresAt; }
    public String getPasswordResetToken() { return passwordResetToken; }
    public Instant getPasswordResetTokenExpiresAt() { return passwordResetTokenExpiresAt; }
    public String getPasswordResetCodeHash() { return passwordResetCodeHash; }
    public Instant getPasswordResetCodeExpiresAt() { return passwordResetCodeExpiresAt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLoginLockedUntil() { return loginLockedUntil; }

    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setEmail(String email) { this.email = email; }
    public void setRole(Role role) { this.role = role; }
    public void setDepartment(Department department) { this.department = department; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setTwoFactorCodeHash(String twoFactorCodeHash) { this.twoFactorCodeHash = twoFactorCodeHash; }
    public void setTwoFactorCodeExpiresAt(Instant twoFactorCodeExpiresAt) { this.twoFactorCodeExpiresAt = twoFactorCodeExpiresAt; }
    public void setPasswordResetToken(String passwordResetToken) { this.passwordResetToken = passwordResetToken; }
    public void setPasswordResetTokenExpiresAt(Instant passwordResetTokenExpiresAt) { this.passwordResetTokenExpiresAt = passwordResetTokenExpiresAt; }
    public void setPasswordResetCodeHash(String passwordResetCodeHash) { this.passwordResetCodeHash = passwordResetCodeHash; }
    public void setPasswordResetCodeExpiresAt(Instant passwordResetCodeExpiresAt) { this.passwordResetCodeExpiresAt = passwordResetCodeExpiresAt; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public void setLoginLockedUntil(Instant loginLockedUntil) { this.loginLockedUntil = loginLockedUntil; }
}
