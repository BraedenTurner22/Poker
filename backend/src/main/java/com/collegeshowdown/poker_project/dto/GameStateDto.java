package com.collegeshowdown.poker_project.dto;

import java.util.List;

public record GameStateDto(
    String lobbyId,
    String phase,
    List<CardDto> board,
    List<PotDto> pots,
    int currentPlayerSeat,    // -1 when no active turn
    int currentBetAmount,
    int lastRaiseSize,        // size of the last raise increment; drives minimum re-raise on frontend
    int bigBlind,
    int smallBlindSeat,
    int bigBlindSeat,
    long turnDeadlineEpochMs, // epoch millis when timer expires; 0 if no active timer
    List<PlayerStateDto> players,
    List<Integer> winnerSeats // populated at HAND_COMPLETE
) {}
