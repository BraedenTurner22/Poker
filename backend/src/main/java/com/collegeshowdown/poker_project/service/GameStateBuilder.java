package com.collegeshowdown.poker_project.service;

import com.collegeshowdown.poker_project.domain.lobby.GamePhase;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerState;
import com.collegeshowdown.poker_project.dto.*;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class GameStateBuilder {

    /**
     * Builds the public game state sent to every player in the lobby.
     * Hole cards are NOT included — those are sent privately.
     */
    public GameStateDto build(Lobby lobby) {
        List<CardDto> board = lobby.getBoard().stream()
                .map(CardDto::from)
                .toList();

        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        // An uncalled-bet pot is the bettor's own chips on their way back —
        // displayChipsFor shows them in that player's stack, so listing them
        // as a pot too made the table show that money twice.
        List<PotDto> pots = lobby.getAllActivePots().stream()
                .filter(pot -> returningTo(pot, lobby) == null)
                .map(pot -> toPotDto(pot, table))
                .toList();

        List<PlayerStateDto> players = buildPlayerStates(lobby);

        List<Integer> winnerSeats = buildWinnerSeats(lobby);

        return new GameStateDto(
                lobby.getId(),
                lobby.getGamePhase().name(),
                board,
                pots,
                lobby.getCurrentPlayerIndex(),
                lobby.getCurrentBetAmount(),
                lobby.getLastRaiseSize(),
                lobby.getBigBlind(),
                lobby.getSmallBlindSeat(),
                lobby.getBigBlindSeat(),
                lobby.getTurnDeadlineEpochMs(),
                players,
                winnerSeats,
                new ArrayList<>(lobby.getRaiseClosedSeats()));
    }

    /**
     * Builds the private state sent only to the given player — their hole cards.
     * Called after dealHoleCards so the player can see their hand.
     */
    public PrivateStateDto buildPrivate(ConnectedPlayer player) {
        List<CardDto> holeCards = player.getCards().stream()
                .limit(2)
                .map(CardDto::from)
                .toList();
        return new PrivateStateDto(holeCards);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private List<PlayerStateDto> buildPlayerStates(Lobby lobby) {
        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        boolean reveal = shouldRevealHoleCards(lobby);
        List<PlayerStateDto> result = new ArrayList<>();

        for (int seat = 0; seat < table.length; seat++) {
            ConnectedPlayer p = table[seat];
            if (p == null) continue;

            List<CardDto> exposed = (reveal && p.getPlayerState() != PlayerState.FOLDED)
                    ? p.getCards().stream().limit(2).map(CardDto::from).toList()
                    : List.of();

            result.add(new PlayerStateDto(
                    seat,
                    p.playerRecord.getId(),
                    p.playerRecord.getName(),
                    p.playerRecord.getCollegeName(),
                    p.playerRecord.getCollegeDomain(),
                    displayChipsFor(p, lobby),
                    p.getPlayerState().name(),
                    lobby.getRoundContribution(p),
                    exposed));
        }

        return result;
    }

    /**
     * True when hole cards should be included in the public game state.
     * Covers two scenarios:
     *  1. Normal showdown — river betting finished with 2+ live players.
     *  2. All-in run-out — board is being revealed with no live bettors.
     * A fold-win (only one non-folded player) never reveals cards.
     */
    private boolean shouldRevealHoleCards(Lobby lobby) {
        GamePhase phase = lobby.getGamePhase();
        if (phase == GamePhase.WAITING || phase == GamePhase.PRE_FLOP) return false;

        // Fold-win: single non-folded player — nothing to reveal.
        // Only count players actually dealt into this hand: a busted (0-chip)
        // player keeps their ALL_IN state from the hand they lost and is never
        // reset to ACTIVE or dealt cards, so counting them would make a plain
        // fold-win look like a 2-player showdown and expose the winner's cards.
        int nonFolded = 0;
        for (ConnectedPlayer p : lobby.getPlayersAtTable()) {
            if (p != null && p.getPlayerState() != PlayerState.FOLDED
                    && !p.getCards().isEmpty()) nonFolded++;
        }
        if (nonFolded < 2) return false;

        // Always reveal at showdown / hand complete.
        if (phase == GamePhase.SHOWDOWN || phase == GamePhase.HAND_COMPLETE) return true;

        // During board run-out: reveal once the staged run-out has actually
        // begun (currentPlayerIndex == -1, set by beginStagedRunOut once the
        // betting round is fully closed) — not merely whenever fewer than 2
        // players are ACTIVE, which goes true the instant someone shoves,
        // even while the remaining player still has a pending decision.
        return lobby.getCurrentPlayerIndex() == -1;
    }

    /**
     * Returns the chip count to display for a player: their stack plus any
     * uncalled bet the engine has split off into its own pot (it goes straight
     * back to them, so it is shown as chips behind and left out of the pot
     * list in {@link #build}).
     *
     * Only an uncalled-bet pot counts — not "my contribution minus everyone
     * else's": that is also true mid-round right after a shove, before the
     * others have had the chance to call, and at HAND_COMPLETE the pots have
     * already been paid into getActiveChips() (an all-in winner's stack was
     * shown without their winnings).
     */
    private int displayChipsFor(ConnectedPlayer p, Lobby lobby) {
        int returning = 0;
        for (Pot pot : lobby.getAllActivePots()) {
            if (returningTo(pot, lobby) == p) returning += pot.getAmountInPot();
        }
        return p.getActiveChips() + returning;
    }

    /**
     * The player an uncalled-bet pot is definitely going back to, or null.
     * Requires that no other player dealt into the hand is still ACTIVE: the
     * blinds step can flag the big blind's excess over a short all-in small
     * blind as "uncalled" while the rest of the table has yet to act on it.
     * Once a betting round has closed, an uncalled pot implies every other
     * live player is all-in or folded, so this only filters that case.
     */
    private ConnectedPlayer returningTo(Pot pot, Lobby lobby) {
        if (!pot.isUncalledBet() || pot.getPlayersInPot().size() != 1) return null;
        ConnectedPlayer owner = pot.getPlayersInPot().get(0);
        for (ConnectedPlayer other : lobby.getPlayersAtTable()) {
            if (other != null && other != owner && !other.getCards().isEmpty()
                    && other.getPlayerState() == PlayerState.ACTIVE) {
                return null;
            }
        }
        return owner;
    }

    private PotDto toPotDto(Pot pot, ConnectedPlayer[] table) {
        List<Integer> seats = new ArrayList<>();
        if (pot.getEligiblePlayers() != null) {
            for (ConnectedPlayer p : pot.getEligiblePlayers()) {
                for (int seat = 0; seat < table.length; seat++) {
                    if (table[seat] == p) { seats.add(seat); break; }
                }
            }
        }
        return new PotDto(pot.getAmountInPot(), seats);
    }

    private List<Integer> buildWinnerSeats(Lobby lobby) {
        List<ConnectedPlayer> winners = lobby.getWinners();
        if (winners == null || winners.isEmpty()) return List.of();

        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        List<Integer> seats = new ArrayList<>();
        for (int i = 0; i < table.length; i++) {
            if (table[i] != null && winners.contains(table[i])) {
                seats.add(i);
            }
        }
        return seats;
    }
}
