package com.collegeshowdown.poker_project.model;

import java.util.List;

import com.collegeshowdown.poker_project.runtime.card.Card;
import com.collegeshowdown.poker_project.runtime.player.HandRank;

public record AnalysisResults(int id, HandRank handRank, List<Card> bestCards) {
}
