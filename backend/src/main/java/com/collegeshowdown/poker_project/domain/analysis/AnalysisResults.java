package com.collegeshowdown.poker_project.models;

import java.util.List;

import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.player.HandRank;

public record AnalysisResults(int id, HandRank handRank, List<Card> bestCards) {
}
