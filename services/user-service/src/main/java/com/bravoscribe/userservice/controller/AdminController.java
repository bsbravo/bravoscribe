package com.bravoscribe.userservice.controller;

import com.bravoscribe.userservice.dto.UserResponse;
import com.bravoscribe.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin", description = "User management — ADMIN role required")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "List all users",
            description = "Returns a paginated list sorted by createdAt descending. Page size is capped at 100.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page of users returned",
                            content = @Content(schema = @Schema(implementation = Page.class))),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "403", description = "Authenticated but not ADMIN",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping
    public ResponseEntity<Page<UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "31") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(userService.getUserPage(pageable));
    }

    @Operation(summary = "Deactivate a user",
            description = "Sets user.active = false and revokes all existing refresh tokens. The user is locked out immediately on next token refresh.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "User deactivated", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "403", description = "Authenticated but not ADMIN",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "404", description = "User not found",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }
}
