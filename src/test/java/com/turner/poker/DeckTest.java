package com.turner.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeckTest {

    @BeforeEach
    void setUp() {
        Deck.reset();
        Players.reset();
    }

    // @Test
    // void testGetTopCard() {
    // Deck.reset();
    // Card card = Deck.getTopCard();
    // Suit suit = card.getSuit();
    // assertTrue(
    // suit == Suit.CLUBS || suit == Suit.DIAMONDS || suit == Suit.HEARTS
    // || suit == Suit.SPADES,
    // "Suit should be CLUBS, DIAMONDS, HEARTS, or SPADES ");
    // Rank rank = card.getRank();
    // assertTrue(
    // rank == Rank.TWO || rank == Rank.THREE || rank == Rank.FOUR || rank == Rank.FIVE
    // || rank == Rank.SIX || rank == Rank.SEVEN || rank == Rank.EIGHT
    // || rank == Rank.NINE || rank == Rank.TEN || rank == Rank.JACK
    // || rank == Rank.QUEEN || rank == Rank.KING || rank == Rank.ACE,
    // "Rank should be TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING,
    // ACE");
    // }

    @Test
    void testGetDeckSize() {
        assertEquals(52, Deck.getDeckSize(), "Size of deck should be 52");
        Deck.getTopCard();
        assertEquals(51, Deck.getDeckSize(), "Size of deck should be 51");
    }

    @Test
    void testShuffle() {
        // develop pre-shuffle test
        Deck.shuffle();
        // develop pre-shuffle test
    }

    @Test
    void testGetTopCard() {
        Card card = Deck.getTopCard();
        assertEquals(Suit.CLUBS, card.getSuit(), "Card suit should be CLUBS");
        assertEquals(Rank.TWO, card.getRank(), "Card rank should be TWO");
    }

    @Test
    void testDealCardsToPlayersNoPlayers() {
        Deck.dealCardsToPlayers(2);
        // There are no players so the deck size should be 52
        assertEquals(52, Deck.getDeckSize(), "Size of deck should be 52");
    }

    @Test
    void testDealCardsToPlayersTwoPlayers() {
        // There are two players so the deck size should be 48
        for (int i = 0; i < 2; i++) {
            Players.addPlayer(new Player(Integer.toString(i), i, 100));
        }
        Deck.dealCardsToPlayers(2);
        assertEquals(48, Deck.getDeckSize(), "Size of deck should be 48");
    }
}
