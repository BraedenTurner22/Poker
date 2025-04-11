package com.collegeshowdown.poker_project.model;

import java.util.List;

public record AnalysisResults(int id, HandRank handRank, List<Card> bestCards) {
}
