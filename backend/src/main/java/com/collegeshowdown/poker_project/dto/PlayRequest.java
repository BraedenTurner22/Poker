package com.collegeshowdown.poker_project.dto;

import com.collegeshowdown.poker_project.domain.lobby.StakesLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlayRequest(
    @NotNull StakesLevel stakes,
    boolean schoolLobby,
    @Min(0) int buyInAmount   // 0 = use table default; positive = explicit buy-in
) {}
