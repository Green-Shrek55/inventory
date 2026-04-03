package com.kursach.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordResetCodeForm {
    @NotBlank(message = "Введите код из письма")
    private String code;

    @NotBlank(message = "Введите новый пароль")
    @Size(min = 4, message = "Минимум 4 символа")
    private String password;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
