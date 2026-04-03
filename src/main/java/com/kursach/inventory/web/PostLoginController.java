package com.kursach.inventory.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PostLoginController {

    @GetMapping("/post-login")
    public String postLogin(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        if (isAdmin) {
            return "redirect:/admin";
        }
        if (hasRole(authentication, "ROLE_IT")) {
            return "redirect:/it";
        }
        if (hasRole(authentication, "ROLE_ECONOMIST")) {
            return "redirect:/economist";
        }
        return "redirect:/";
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
