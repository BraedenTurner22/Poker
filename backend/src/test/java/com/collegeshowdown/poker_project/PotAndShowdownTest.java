package com.collegeshowdown.poker_project;

import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.card.Rank;
import com.collegeshowdown.poker_project.domain.card.Suit;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.LobbyType;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.domain.lobby.StakesLevel;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerState;
import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.services.PotManagerService;
import com.collegeshowdown.poker_project.services.WinnerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.collegeshowdown.poker_project.domain.card.Rank.*;
import static com.collegeshowdown.poker_project.domain.card.Suit.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Side-pot construction and showdown payout, driven directly through
 * PotManagerService + WinnerService with hand-built contributions and cards.
 */
class PotAndShowdownTest {

    private final PotManagerService potManager = new PotManagerService();
    private final WinnerService winnerService = new WinnerService();
    private Lobby lobby;
    private int nextId = 1;

    @BeforeEach
    void setUp() {
        lobby = new Lobby(LobbyType.GLOBAL, StakesLevel.LOW);
    }

    private static Card c(Rank r, Suit s) { return new Card(r, s); }

    /** Seats a player who has put {@code contributed} into the pot this hand. */
    private ConnectedPlayer seat(int seat, int contributed, PlayerState state, Card... hole) {
        PlayerRecord record = new PlayerRecord("p" + nextId, "p" + nextId + "@umass.edu",
                "x", "umass.edu", "UMass", "LOCAL");
        try {
            Field f = PlayerRecord.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(record, nextId++);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        ConnectedPlayer p = new ConnectedPlayer(record);
        p.setCards(new ArrayList<>(List.of(hole)));
        p.setPlayerState(state);
        lobby.getPlayersAtTable()[seat] = p;
        if (contributed > 0) lobby.addHandContribution(p, contributed);
        return p;
    }

    private void board(Card... cards) {
        lobby.getBoard().addAll(List.of(cards));
    }

    private int potTotal() {
        return lobby.getAllActivePots().stream().mapToInt(Pot::getAmountInPot).sum();
    }

    private void showdown() {
        potManager.recalculatePots(lobby);
        winnerService.determineWinners(lobby);
    }

    // A dry board that plays for everyone: split pots
    private void splitBoard() {
        board(c(ACE, HEARTS), c(KING, SPADES), c(QUEEN, DIAMONDS), c(JACK, CLUBS), c(TEN, HEARTS));
    }

    @Test
    void sidePotsAcrossMultipleAllInLevelsConserveChips() {
        ConnectedPlayer a = seat(0, 100, PlayerState.ALL_IN, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer b = seat(1, 300, PlayerState.ALL_IN, c(KING, HEARTS), c(KING, CLUBS));
        ConnectedPlayer cc = seat(2, 500, PlayerState.ACTIVE, c(TWO, HEARTS), c(SEVEN, CLUBS));
        ConnectedPlayer d = seat(3, 500, PlayerState.ACTIVE, c(THREE, HEARTS), c(EIGHT, CLUBS));
        ConnectedPlayer folded = seat(4, 200, PlayerState.FOLDED);
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        potManager.recalculatePots(lobby);
        assertEquals(1600, potTotal());
        winnerService.determineWinners(lobby);

        // A (trip aces): 100 x 5 = 500. B (trip kings): 100 x 4 (incl. the
        // folded 100) + 100 x 3 = 700. C/D contest the 200 x 2 = 400 top level.
        assertEquals(500, a.getActiveChips());
        assertEquals(700, b.getActiveChips());
        assertEquals(400, cc.getActiveChips() + d.getActiveChips());
        assertEquals(0, folded.getActiveChips());
        assertEquals(0, potTotal());
    }

    @Test
    void oddChipGoesToFirstWinnerLeftOfButton() {
        lobby.setSmallBlindSeat(5);
        // Seats 2 and 6 split; a folded player's single chip makes the pot odd.
        ConnectedPlayer seat2 = seat(2, 10, PlayerState.ACTIVE, c(TWO, HEARTS), c(THREE, CLUBS));
        ConnectedPlayer seat6 = seat(6, 10, PlayerState.ACTIVE, c(TWO, SPADES), c(THREE, DIAMONDS));
        seat(5, 1, PlayerState.FOLDED);
        splitBoard();

        showdown();

        // Clockwise from the small blind (seat 5): seat 6 comes before seat 2
        assertEquals(11, seat6.getActiveChips());
        assertEquals(10, seat2.getActiveChips());
    }

    @Test
    void oddChipOrderDoesNotDependOnPlayerIds() {
        // Same table as above, but seat ids created in the opposite order
        lobby.setSmallBlindSeat(5);
        ConnectedPlayer seat6 = seat(6, 10, PlayerState.ACTIVE, c(TWO, SPADES), c(THREE, DIAMONDS));
        ConnectedPlayer seat2 = seat(2, 10, PlayerState.ACTIVE, c(TWO, HEARTS), c(THREE, CLUBS));
        seat(5, 1, PlayerState.FOLDED);
        splitBoard();

        showdown();

        assertEquals(11, seat6.getActiveChips());
        assertEquals(10, seat2.getActiveChips());
    }

    @Test
    void evenSplitIsNotSkewedByDeadMoneySlices() {
        // Three players fold after putting in 1, 2 and 3 chips; the two
        // remaining players split 26 chips — must be 13 / 13, not 14 / 12.
        lobby.setSmallBlindSeat(0);
        seat(0, 1, PlayerState.FOLDED);
        seat(1, 2, PlayerState.FOLDED);
        seat(2, 3, PlayerState.FOLDED);
        ConnectedPlayer x = seat(3, 10, PlayerState.ACTIVE, c(TWO, HEARTS), c(THREE, CLUBS));
        ConnectedPlayer y = seat(4, 10, PlayerState.ACTIVE, c(TWO, SPADES), c(THREE, DIAMONDS));
        splitBoard();

        showdown();

        assertEquals(13, x.getActiveChips());
        assertEquals(13, y.getActiveChips());
    }

    @Test
    void uncalledExcessReturnedButBettorIsNotAWinner() {
        // Short stack all-in for 100 and wins; the big stack's uncalled 400
        // comes back to them, but they lost the hand.
        ConnectedPlayer shortStack = seat(0, 100, PlayerState.ALL_IN, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer bigStack = seat(1, 500, PlayerState.ACTIVE, c(TWO, HEARTS), c(SEVEN, CLUBS));
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        showdown();

        assertEquals(200, shortStack.getActiveChips());
        assertEquals(400, bigStack.getActiveChips());
        assertEquals(List.of(shortStack), lobby.getWinners());
    }

    @Test
    void sidePotWonFromFoldedPlayerStillCountsAsWin() {
        // C folds to B after both put 500 in; A all-in for 100 wins the main pot.
        // B collects C's dead money from the side pot, which is a real win.
        ConnectedPlayer a = seat(0, 100, PlayerState.ALL_IN, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer b = seat(1, 500, PlayerState.ACTIVE, c(TWO, HEARTS), c(SEVEN, CLUBS));
        seat(2, 500, PlayerState.FOLDED);
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        showdown();

        assertEquals(300, a.getActiveChips());
        assertEquals(800, b.getActiveChips());
        assertTrue(lobby.getWinners().contains(a));
        assertTrue(lobby.getWinners().contains(b));
    }

    @Test
    void bustedSeatWithoutHoleCardsIsNotGivenTheBoard() {
        // A seated player who busted last hand stays ALL_IN with no cards
        ConnectedPlayer busted = seat(0, 0, PlayerState.ALL_IN);
        ConnectedPlayer a = seat(1, 50, PlayerState.ACTIVE, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer b = seat(2, 50, PlayerState.ACTIVE, c(TWO, HEARTS), c(SEVEN, CLUBS));
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        showdown();

        assertTrue(busted.getCards().isEmpty(), "board cards must not become a busted seat's hole cards");
        assertEquals(100, a.getActiveChips());
        assertEquals(0, b.getActiveChips());
    }

    @Test
    void headsUpOddChipGoesToBigBlind() {
        // Heads-up the button posts the small blind; first seat left of it is the BB
        lobby.setHeadsUpHand(true);
        lobby.setSmallBlindSeat(2);
        lobby.setBigBlindSeat(6);
        ConnectedPlayer sb = seat(2, 10, PlayerState.ACTIVE, c(TWO, HEARTS), c(THREE, CLUBS));
        ConnectedPlayer bb = seat(6, 10, PlayerState.ACTIVE, c(TWO, SPADES), c(THREE, DIAMONDS));
        seat(4, 1, PlayerState.FOLDED); // a departed player's dead chip makes it odd
        splitBoard();

        showdown();

        assertEquals(11, bb.getActiveChips());
        assertEquals(10, sb.getActiveChips());
    }

    @Test
    void potWhoseContestantsAllFoldedAfterRecalcIsNotDestroyed() {
        // C bets 200, B all-in calls 100, A all-in 50: C's top 100 is a pot
        // only C contests. C then leaves during the run-out (folded, no recalc).
        ConnectedPlayer a = seat(0, 50, PlayerState.ALL_IN, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer b = seat(1, 100, PlayerState.ALL_IN, c(KING, HEARTS), c(KING, CLUBS));
        ConnectedPlayer cc = seat(2, 200, PlayerState.ACTIVE, c(TWO, HEARTS), c(SEVEN, CLUBS));
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        potManager.recalculatePots(lobby);
        cc.foldCards(lobby.getAllActivePots());
        winnerService.determineWinners(lobby);

        assertEquals(350, a.getActiveChips() + b.getActiveChips(), "all 350 chips must be paid out");
        assertEquals(0, potTotal());
    }

    @Test
    void foldedPlayersHighestContributionIsNotLost() {
        // Folded player put in more than everyone still in the hand
        ConnectedPlayer a = seat(0, 100, PlayerState.ALL_IN, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer b = seat(1, 100, PlayerState.ALL_IN, c(TWO, HEARTS), c(SEVEN, CLUBS));
        seat(2, 300, PlayerState.FOLDED);
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        showdown();

        assertEquals(500, a.getActiveChips() + b.getActiveChips());
        assertEquals(500, a.getActiveChips());
    }
}
