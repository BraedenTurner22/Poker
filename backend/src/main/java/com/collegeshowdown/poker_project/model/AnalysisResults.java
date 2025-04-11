package com.collegeshowdown.poker_project.model;

import java.util.List;

import com.collegeshowdown.poker_project.runtime.Card;

public record AnalysisResults(int id, HandRank handRank, List<Card> bestCards) {
}
