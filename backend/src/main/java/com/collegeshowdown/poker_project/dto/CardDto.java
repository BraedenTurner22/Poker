package com.collegeshowdown.poker_project.dto;

import com.collegeshowdown.poker_project.domain.card.Card;

public record CardDto(String rank, String suit) {
    public static CardDto from(Card card) {
        return new CardDto(card.getRank().name(), card.getSuit().name());
    }
}
