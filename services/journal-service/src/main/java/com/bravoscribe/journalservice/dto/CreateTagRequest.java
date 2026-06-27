package com.bravoscribe.journalservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(
        @NotBlank @Size(max = 50) String name
) {}
