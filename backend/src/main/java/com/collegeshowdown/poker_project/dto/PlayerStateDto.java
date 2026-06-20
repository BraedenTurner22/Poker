package com.collegeshowdown.poker_project.dto;

import java.util.List;

public record PlayerStateDto(
    int seat,
    int userId,
    String name,
    String collegeName,
    String collegeDomain,
    int chips,
    String state,           // ACTIVE, FOLDED, ALL_IN
    int betThisRound,       // chips committed this street
    List<CardDto> holeCards // non-empty only at showdown/run-out
) {}
