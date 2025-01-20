package com.turner.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeckTest {

    private Deck deck;

    @BeforeEach
    void setUp() {
        deck = new Deck();
    }

    @Test
    void testGetTopCard() {
        Card card = deck.getTopCard();
        Suit suit = card.getSuit();
        assertTrue(
                suit == Suit.CLUBS || suit == Suit.DIAMONDS || suit == Suit.HEARTS
                        || suit == Suit.SPADES,
                "Suit should be CLUBS, DIAMONDS, HEARTS, or SPADES ");
        Rank rank = card.getRank();
        assertTrue(
                rank == Rank.TWO || rank == Rank.THREE || rank == Rank.FOUR || rank == Rank.FIVE
                        || rank == Rank.SIX || rank == Rank.SEVEN || rank == Rank.EIGHT
                        || rank == Rank.NINE || rank == Rank.TEN || rank == Rank.JACK
                        || rank == Rank.QUEEN || rank == Rank.KING || rank == Rank.ACE,
                "Rank should be TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING, ACE");
    }

    @Test
    void testGetDeckSize() {
        int size = deck.getDeckSize();
        assertEquals(52, size, "Size of deck should be 52");
        deck.getTopCard();
        size = deck.getDeckSize();
        assertEquals(51, size, "Size of deck should be 51");
    }

    // @Test
    // void testGetDeckCopy() {}

    // @Test
    // void testShuffle() {
    // deck.shuffle();
    // assertEquals(52, card.getSuit(), "Suit should be CLUBS");

    // }

    // @Test
    // void getTopCard() {}
}
