package com.collegeshowdown.poker_project.dto;

import java.util.List;

public record PotDto(int amount, List<Integer> eligibleSeats) {}
