package com.collegeshowdown.poker_project.model;

import java.util.List;

public record AnalysisResults(String id, HandRank handRank, List<Card> bestCards) {
}
