package com.turner.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;

class CardTest {

    private Card card;

    @BeforeEach
    void setUp() {
        card = new Card(Rank.ACE, Suit.CLUBS);
    }

    // @Test
    void testSuit() {
        assertEquals(Suit.CLUBS, card.getSuit(), "Suit should be CLUBS");
    }

    // @Test
    void testRank() {
        assertEquals(Rank.ACE, card.getRank(), "Rank should be ACE");
    }
}
