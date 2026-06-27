package com.bravoscribe.journalservice.controller;

import com.bravoscribe.journalservice.dto.CreateTagRequest;
import com.bravoscribe.journalservice.dto.TagResponse;
import com.bravoscribe.journalservice.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/journal/tags")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tags")
@SecurityRequirement(name = "bearerAuth")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "List tags (alphabetical)")
    @GetMapping
    public List<TagResponse> listTags(@AuthenticationPrincipal Jwt jwt) {
        return tagService.listTags(userId(jwt));
    }

    @Operation(summary = "Create tag")
    @PostMapping
    public ResponseEntity<TagResponse> createTag(
            @Valid @RequestBody CreateTagRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(201).body(tagService.createTag(userId(jwt), req));
    }

    @Operation(summary = "Delete tag")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        tagService.deleteTag(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
