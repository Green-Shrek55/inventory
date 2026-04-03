package com.kursach.inventory.repo;

import com.kursach.inventory.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByPasswordResetToken(String passwordResetToken);
}