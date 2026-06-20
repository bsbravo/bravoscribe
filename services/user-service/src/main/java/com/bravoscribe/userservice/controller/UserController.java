package com.bravoscribe.userservice.controller;

import com.bravoscribe.userservice.dto.*;
import com.bravoscribe.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Profile", description = "View and update the authenticated user's profile and password")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get my profile",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Profile returned",
                            content = @Content(schema = @Schema(implementation = UserResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getMe(extractUserId(jwt)));
    }

    @Operation(summary = "Update my profile",
            description = "Only `name` is accepted. Role and active status cannot be changed via this endpoint.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Profile updated",
                            content = @Content(schema = @Schema(implementation = UserResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation failed",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PutMapping
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(userService.updateProfile(extractUserId(jwt), req));
    }

    @Operation(summary = "Change password",
            description = "Requires the current password. Revokes all existing refresh tokens on success, forcing re-login on all devices.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Password changed successfully", content = @Content),
                    @ApiResponse(responseCode = "400", description = "Current password is incorrect or new password fails validation",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(extractUserId(jwt), req);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update notification preferences",
            description = "Phase 2 — sets daily reminder time (HH:mm UTC) and weekly summary opt-in.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Preferences updated",
                            content = @Content(schema = @Schema(implementation = PreferencesResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation failed",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PutMapping("/preferences")
    public ResponseEntity<PreferencesResponse> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdatePreferencesRequest req) {
        return ResponseEntity.ok(userService.updatePreferences(extractUserId(jwt), req));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
