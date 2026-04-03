package com.kursach.inventory.service;

import com.kursach.inventory.domain.AppUser;
import com.kursach.inventory.domain.Department;
import com.kursach.inventory.domain.Role;
import com.kursach.inventory.repo.DepartmentRepository;
import com.kursach.inventory.repo.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private static final long PASSWORD_RESET_TOKEN_MINUTES = 30;

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActionLogService actionLogService;

    public UserService(UserRepository userRepository,
                       DepartmentRepository departmentRepository,
                       PasswordEncoder passwordEncoder,
                       ActionLogService actionLogService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.actionLogService = actionLogService;
    }

    public List<AppUser> listAll() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "username"));
    }

    public AppUser getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional
    public AppUser create(String username, String rawPassword, String email, Role role, Long departmentId, boolean enabled, String actor) {
        userRepository.findByUsername(username.trim()).ifPresent(u -> {
            throw new IllegalArgumentException("Username is already used");
        });
        userRepository.findByEmailIgnoreCase(email.trim()).ifPresent(u -> {
            throw new IllegalArgumentException("Email is already used");
        });

        AppUser user = new AppUser();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setEmail(email.trim());
        user.setRole(role);
        user.setDepartment(resolveDepartment(departmentId));
        user.setEnabled(enabled);

        AppUser saved = userRepository.save(user);
        actionLogService.log(actor, "Created user " + user.getUsername() + " with role " + role);
        return saved;
    }

    @Transactional
    public AppUser updateProfile(Long id, String email, Role role, Long departmentId, boolean enabled, String actor) {
        AppUser user = getById(id);
        userRepository.findByEmailIgnoreCase(email.trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Email is already used");
                });

        user.setEmail(email.trim());
        user.setRole(role);
        user.setDepartment(resolveDepartment(departmentId));
        user.setEnabled(enabled);
        AppUser saved = userRepository.save(user);
        actionLogService.log(actor, "Updated user profile " + user.getUsername());
        return saved;
    }

    @Transactional
    public void updatePassword(Long id, String newPassword, String actor) {
        validatePassword(newPassword);
        AppUser user = getById(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        clearPasswordResetToken(user);
        userRepository.save(user);
        actionLogService.log(actor, "Reset password for user " + user.getUsername());
    }

    @Transactional
    public String issuePasswordResetToken(AppUser user) {
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(PASSWORD_RESET_TOKEN_MINUTES * 60));
        userRepository.save(user);
        return token;
    }

    public AppUser getByPasswordResetToken(String token) {
        AppUser user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Ссылка для смены пароля недействительна"));
        if (!StringUtils.hasText(user.getPasswordResetToken())
                || user.getPasswordResetTokenExpiresAt() == null
                || Instant.now().isAfter(user.getPasswordResetTokenExpiresAt())) {
            throw new IllegalArgumentException("Срок действия ссылки для смены пароля истек");
        }
        return user;
    }

    @Transactional
    public void updatePasswordByResetToken(String token, String newPassword) {
        validatePassword(newPassword);
        AppUser user = getByPasswordResetToken(token);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
        clearPasswordResetToken(user);
        userRepository.save(user);
        actionLogService.log(user.getUsername(), "User changed password using reset link");
    }

    @Transactional
    public void delete(Long id, String actor) {
        AppUser user = getById(id);
        userRepository.delete(user);
        actionLogService.log(actor, "Deleted user " + user.getUsername());
    }

    private Department resolveDepartment(Long id) {
        if (id == null) return null;
        return departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
    }

    private void validatePassword(String newPassword) {
        if (newPassword == null || newPassword.length() < 4) {
            throw new IllegalArgumentException("Пароль должен содержать минимум 4 символа");
        }
    }

    private void clearPasswordResetToken(AppUser user) {
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        user.setPasswordResetCodeHash(null);
        user.setPasswordResetCodeExpiresAt(null);
    }
}
