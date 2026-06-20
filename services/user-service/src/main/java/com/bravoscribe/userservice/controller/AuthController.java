package com.bravoscribe.userservice.controller;

import com.bravoscribe.userservice.dto.*;
import com.bravoscribe.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Authentication", description = "Registration, login, logout, and token refresh")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user",
            description = "Creates a new account and immediately issues tokens — no second login required.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Registered successfully",
                            headers = @Header(name = "Set-Cookie", description = "httpOnly refreshToken cookie"),
                            content = @Content(schema = @Schema(implementation = AccessTokenResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation failed (name/email/password constraints)",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "409", description = "Email already registered",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/register")
    public ResponseEntity<AccessTokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        AuthService.LoginResult result = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
                .body(new AccessTokenResponse(result.accessToken()));
    }

    @Operation(summary = "Login",
            description = "Returns an access token and sets the httpOnly refreshToken cookie. Always returns 401 for wrong email or wrong password — no enumeration.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Logged in successfully",
                            headers = @Header(name = "Set-Cookie", description = "httpOnly refreshToken cookie"),
                            content = @Content(schema = @Schema(implementation = AccessTokenResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials or deactivated account",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthService.LoginResult result = authService.login(req);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
                .body(new AccessTokenResponse(result.accessToken()));
    }

    @Operation(summary = "Refresh access token",
            description = "Reads the refreshToken httpOnly cookie, rotates it, and issues a new access token. The old refresh token is immediately revoked.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token rotated successfully",
                            headers = @Header(name = "Set-Cookie", description = "New httpOnly refreshToken cookie"),
                            content = @Content(schema = @Schema(implementation = AccessTokenResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Refresh token missing, expired, revoked, or reuse detected",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String rawToken) {
        AuthService.LoginResult result = authService.refresh(rawToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
                .body(new AccessTokenResponse(result.accessToken()));
    }

    @Operation(summary = "Logout",
            description = "Revokes the refreshToken cookie and clears it. Safe to call even if the cookie is missing.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Logged out successfully", content = @Content)
            })
    @DeleteMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String rawToken) {
        var clearCookie = authService.logout(rawToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }
}
