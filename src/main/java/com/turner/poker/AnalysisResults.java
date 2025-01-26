package com.turner.poker;

import java.util.List;

public record AnalysisResults(String id, HandRank handRank, List<Card> bestCards) {
}
