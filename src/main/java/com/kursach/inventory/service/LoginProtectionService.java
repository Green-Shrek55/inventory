package com.kursach.inventory.service;

import com.kursach.inventory.domain.AppUser;
import com.kursach.inventory.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class LoginProtectionService {
    private final UserRepository userRepository;
    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginProtectionService(UserRepository userRepository,
                                  @Value("${app.security.login-protection.max-attempts:5}") int maxAttempts,
                                  @Value("${app.security.login-protection.lock-minutes:15}") long lockMinutes) {
        this.userRepository = userRepository;
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    public boolean isLocked(AppUser user) {
        return user.getLoginLockedUntil() != null && Instant.now().isBefore(user.getLoginLockedUntil());
    }

    public String buildLockedMessage(AppUser user) {
        return "Слишком много неудачных попыток.";
    }

    public int getRemainingAttempts(AppUser user) {
        return Math.max(0, maxAttempts - user.getFailedLoginAttempts());
    }

    public long getLockUntilEpochMillis(AppUser user) {
        return user.getLoginLockedUntil() == null ? 0 : user.getLoginLockedUntil().toEpochMilli();
    }

    @Transactional
    public void registerFailedAttempt(AppUser user) {
        if (isLocked(user)) {
            return;
        }

        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxAttempts) {
            user.setFailedLoginAttempts(0);
            user.setLoginLockedUntil(Instant.now().plus(lockDuration));
        }
        userRepository.save(user);
    }

    @Transactional
    public void registerSuccessfulPasswordCheck(AppUser user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLoginLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLoginLockedUntil(null);
            userRepository.save(user);
        }
    }
}
