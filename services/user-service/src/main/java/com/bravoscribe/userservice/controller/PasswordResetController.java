package com.bravoscribe.userservice.controller;

import com.bravoscribe.userservice.dto.PasswordResetRequestBody;
import com.bravoscribe.userservice.dto.ResetPasswordRequest;
import com.bravoscribe.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/password-reset")
@Tag(name = "Password Reset", description = "Forgot-password flow — no JWT required")
public class PasswordResetController {

    private final UserService userService;

    public PasswordResetController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Request a password reset",
            description = "Always returns 204 — never reveals whether the email exists. If found, a reset link is sent via Kafka → Notification Service.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Request processed (email sent if account exists)", content = @Content),
                    @ApiResponse(responseCode = "400", description = "Validation failed",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/request")
    public ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequestBody req) {
        userService.requestPasswordReset(req.email());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Confirm password reset",
            description = "Validates the reset token, updates the password, marks the token as used, and revokes all refresh tokens. Token expires in 15 minutes and is single-use.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Password reset successfully", content = @Content),
                    @ApiResponse(responseCode = "400", description = "Token not found, already used, expired, or new password fails validation",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PutMapping("/confirm")
    public ResponseEntity<Void> confirmReset(@Valid @RequestBody ResetPasswordRequest req) {
        userService.confirmPasswordReset(req);
        return ResponseEntity.noContent().build();
    }
}
