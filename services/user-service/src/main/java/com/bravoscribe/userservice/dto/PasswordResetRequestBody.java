package com.bravoscribe.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequestBody(
        @Email @NotBlank String email
) {}
