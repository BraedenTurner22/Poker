package com.collegeshowdown.poker_project.dto;

public record AuthResponse(
    String token,
    int id,
    String name,
    String email,
    String collegeName,
    int chips
) {}
