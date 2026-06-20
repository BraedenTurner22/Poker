package com.collegeshowdown.poker_project.dto;

public record PlayResponse(
    String lobbyId,
    String wsUrl,
    int seatNumber,
    int buyInAmount,
    GameStateDto initialState,
    PrivateStateDto initialPrivateState   // null if hand not yet in progress
) {}
