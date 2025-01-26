package com.turner.poker;

import java.util.SortedSet;

public record AnalysisResults(String id, HandRank handRank, SortedSet<Card> bestCards) {
}
