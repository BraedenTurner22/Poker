package com.collegeshowdown.poker_project.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
    @NotBlank(message = "must not be blank") String idToken
) {}
