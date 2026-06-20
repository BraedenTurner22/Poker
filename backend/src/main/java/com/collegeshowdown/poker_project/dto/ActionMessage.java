package com.collegeshowdown.poker_project.dto;

public record ActionMessage(
    String action,  // "FOLD" | "CHECK" | "CALL" | "RAISE" | "ALL_IN"
    int amount      // raise size (ignored for non-raise actions)
) {}
