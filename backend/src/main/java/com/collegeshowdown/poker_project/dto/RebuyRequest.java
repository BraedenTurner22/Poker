package com.collegeshowdown.poker_project.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RebuyRequest(
    @NotBlank String lobbyId,
    @Min(1)   int    amount
) {}
