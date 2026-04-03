package com.kursach.inventory.config;

import com.kursach.inventory.domain.AppUser;
import com.kursach.inventory.repo.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> {
            AppUser u = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            if (!u.isEnabled()) throw new DisabledException("User disabled");

            return new org.springframework.security.core.userdetails.User(
                    u.getUsername(),
                    u.getPasswordHash(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()))
            );
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/h2-console/**", "/login", "/login/cancel", "/login/verify", "/login/resend", "/password/forgot", "/password/confirm", "/password/reset", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/post-login").authenticated()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/it/**").hasAnyRole("ADMIN", "IT")
                .requestMatchers("/economist/**").hasAnyRole("ADMIN", "ECONOMIST")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/perform-login")
                .permitAll()
            )
            .logout(l -> l
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .requestCache(cache -> cache.disable());

        http.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));
        http.headers(h -> h.frameOptions(f -> f.sameOrigin()));

        return http.build();
    }
}
