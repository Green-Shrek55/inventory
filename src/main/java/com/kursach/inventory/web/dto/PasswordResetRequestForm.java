package com.kursach.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;

public class PasswordResetRequestForm {
    @NotBlank(message = "Введите логин или email")
    private String usernameOrEmail;

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }
}
