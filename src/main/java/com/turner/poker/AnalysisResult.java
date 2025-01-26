package com.turner.poker;

import java.util.List;

public record AnalysisResult(String id, HandRank handRank, List<Card> bestCards) {
}
