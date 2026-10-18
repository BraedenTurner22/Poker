package com.collegeshowdown.poker_project;

import com.collegeshowdown.poker_project.controllers.WebSocketsController;
import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.card.Rank;
import com.collegeshowdown.poker_project.domain.card.Suit;
import com.collegeshowdown.poker_project.domain.lobby.GamePhase;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.LobbyType;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.domain.lobby.StakesLevel;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerState;
import com.collegeshowdown.poker_project.dto.ActionMessage;
import com.collegeshowdown.poker_project.dto.GameStateDto;
import com.collegeshowdown.poker_project.dto.PlayerStateDto;
import com.collegeshowdown.poker_project.dto.PotDto;
import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.security.StompAuthInterceptor;
import com.collegeshowdown.poker_project.service.GameStateBuilder;
import com.collegeshowdown.poker_project.services.ChatModerationService;
import com.collegeshowdown.poker_project.services.LobbyGameService;
import com.collegeshowdown.poker_project.services.LobbyManagerService;
import com.collegeshowdown.poker_project.util.JwtUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * WebSocket-layer tests: STOMP CONNECT/SUBSCRIBE/SEND authorization, chat
 * moderation evasion, public-state hole-card exposure, and the disconnect
 * grace-period bookkeeping. No Spring context, no DB.
 */
class WebSocketLayerTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123456789";

    private static PlayerRecord record(int id) {
        PlayerRecord record = new PlayerRecord("p" + id, "p" + id + "@umass.edu",
                "x", "umass.edu", "UMass", "LOCAL");
        try {
            Field f = PlayerRecord.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(record, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return record;
    }

    private static Principal principal(int userId) {
        return new StompAuthInterceptor.StompPrincipal(String.valueOf(userId), "p" + userId);
    }

    // ─── StompAuthInterceptor ────────────────────────────────────────────────────

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(jwtUtil);

    private Message<byte[]> frame(StompCommand cmd, String destination, Principal user, String authHeader) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(cmd);
        if (destination != null) acc.setDestination(destination);
        if (user != null) acc.setUser(user);
        if (authHeader != null) acc.addNativeHeader("Authorization", authHeader);
        acc.setSessionId("s");
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    @Test
    void connectWithoutValidTokenIsRejected() {
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), null));
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, "Bearer garbage"), null));
    }

    @Test
    void connectWithValidTokenSetsPrincipal() {
        String token = jwtUtil.generateToken(42, "Alice");
        Message<byte[]> msg = frame(StompCommand.CONNECT, null, null, "Bearer " + token);
        interceptor.preSend(msg, null);
        Principal user = StompHeaderAccessor.wrap(msg).getUser();
        assertNotNull(user);
        assertEquals("42", user.getName());
    }

    @Test
    void cannotSubscribeToAnotherPlayersHoleCards() {
        Principal me = principal(7);
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/game/L1/private/8", me, null), null));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/game/L1/kick/8", me, null), null));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/game/L1/chat-blocked/8", me, null), null));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/game/L1/error/8", me, null), null));
        // Own topics and public table topics are fine
        assertDoesNotThrow(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/game/L1/private/7", me, null), null));
        assertDoesNotThrow(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/game/L1/state", me, null), null));
        assertDoesNotThrow(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/game/L1/chat", me, null), null));
    }

    @Test
    void wildcardAndUnauthenticatedSubscriptionsAreRejected() {
        Principal me = principal(7);
        for (String dest : List.of("/topic/**", "/topic/game/*/private/*", "/topic/game/L1/private/{id}",
                "/topic/game/L1/private/?")) {
            assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                    frame(StompCommand.SUBSCRIBE, dest, me, null), null), dest);
        }
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/game/L1/state", null, null), null));
    }

    @Test
    void clientCannotSendStraightToBrokerTopics() {
        Principal me = principal(7);
        // SimpleBroker would otherwise relay these to every subscriber verbatim
        // (forged chat / state / kick / hole cards).
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SEND, "/topic/game/L1/chat", me, null), null));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SEND, "/user/8/queue/x", me, null), null));
        assertDoesNotThrow(() -> interceptor.preSend(
                frame(StompCommand.SEND, "/app/game/L1/chat", me, null), null));
    }

    // ─── ChatModerationService ───────────────────────────────────────────────────

    private static String firstBlockedTerm() throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource("moderation/blocked-terms.txt").getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.strip().toLowerCase();
                if (!t.isEmpty() && !t.startsWith("#")) return t;
            }
        }
        throw new IllegalStateException("empty blocklist");
    }

    @Test
    void moderationCatchesUnicodeAndSpacingEvasions() throws Exception {
        ChatModerationService mod = new ChatModerationService();
        String term = firstBlockedTerm();
        assertTrue(mod.containsBlockedTerm("you " + term));

        // zero-width space between every letter
        assertTrue(mod.containsBlockedTerm("you " + String.join("​", term.split(""))));
        // fullwidth letters
        StringBuilder fw = new StringBuilder();
        for (char c : term.toCharArray()) fw.append((char) (c - 'a' + 'ａ'));
        assertTrue(mod.containsBlockedTerm("you " + fw));
        // combining accent on the first letter
        assertTrue(mod.containsBlockedTerm("you " + term.charAt(0) + "́" + term.substring(1)));
        // spaced / dotted out
        assertTrue(mod.containsBlockedTerm("you " + String.join(" ", term.split(""))));
        assertTrue(mod.containsBlockedTerm("you " + String.join(".", term.split(""))));

        // ordinary chat still passes
        assertFalse(mod.containsBlockedTerm("nice hand, A K Q on the flop, gg"));
        assertFalse(mod.containsBlockedTerm("café crème"));
    }

    // ─── GameStateBuilder ────────────────────────────────────────────────────────

    @Test
    void foldWinDoesNotRevealWinnersCardsWhenABustedPlayerIsSeated() {
        Lobby lobby = new Lobby(LobbyType.GLOBAL, StakesLevel.LOW);
        ConnectedPlayer folder = new ConnectedPlayer(record(1));
        folder.buyIn(100);
        folder.setPlayerState(PlayerState.FOLDED);
        ConnectedPlayer winner = new ConnectedPlayer(record(2));
        winner.buyIn(100);
        winner.addCards(List.of(new Card(Rank.ACE, Suit.SPADES), new Card(Rank.KING, Suit.SPADES)));
        // Busted last hand: 0 chips, state stays ALL_IN, never dealt this hand
        ConnectedPlayer busted = new ConnectedPlayer(record(3));
        busted.setPlayerState(PlayerState.ALL_IN);
        lobby.getPlayersAtTable()[0] = folder;
        lobby.getPlayersAtTable()[1] = winner;
        lobby.getPlayersAtTable()[2] = busted;
        lobby.setGamePhase(GamePhase.HAND_COMPLETE);

        GameStateDto state = new GameStateBuilder().build(lobby);
        for (PlayerStateDto p : state.players()) {
            assertTrue(p.holeCards().isEmpty(), "seat " + p.seat() + " cards leaked on a fold-win");
        }
    }

    @Test
    void realShowdownStillRevealsLivePlayersCards() {
        Lobby lobby = new Lobby(LobbyType.GLOBAL, StakesLevel.LOW);
        ConnectedPlayer a = new ConnectedPlayer(record(1));
        a.addCards(List.of(new Card(Rank.ACE, Suit.SPADES), new Card(Rank.KING, Suit.SPADES)));
        ConnectedPlayer b = new ConnectedPlayer(record(2));
        b.addCards(List.of(new Card(Rank.TWO, Suit.CLUBS), new Card(Rank.THREE, Suit.CLUBS)));
        lobby.getPlayersAtTable()[0] = a;
        lobby.getPlayersAtTable()[1] = b;
        lobby.setGamePhase(GamePhase.HAND_COMPLETE);

        GameStateDto state = new GameStateBuilder().build(lobby);
        assertEquals(2, state.players().get(0).holeCards().size());
        assertEquals(2, state.players().get(1).holeCards().size());
    }

    @Test
    void emptyBearerTokenIsInvalidNotAnException() {
        // jjwt throws IllegalArgumentException for "", which used to escape
        // JwtAuthFilter as a 500 for any request sent with "Bearer ".
        assertFalse(jwtUtil.isValid(""));
        assertEquals(-1, jwtUtil.extractUserId(""));
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, "Bearer "), null));
    }

    @Test
    void moderationDoesNotFlagOrdinaryWordsThatStartWithATerm() throws Exception {
        ChatModerationService mod = new ChatModerationService();
        for (String ok : List.of("that river was spicy", "pumpkin spice", "s p i c y",
                "I'm from Pakistan", "my coonhound", "fire retardant")) {
            assertFalse(mod.containsBlockedTerm(ok), ok);
        }
        String term = firstBlockedTerm();
        assertTrue(mod.containsBlockedTerm("you " + term + "s"));
        assertTrue(mod.containsBlockedTerm("that's retarded"));
    }

    private static Pot pot(int amount, boolean uncalled, ConnectedPlayer... eligible) {
        Pot pot = new Pot(amount, List.of(eligible));
        pot.uncalledBet = uncalled;
        return pot;
    }

    private static int potTotal(GameStateDto state) {
        return state.pots().stream().mapToInt(PotDto::amount).sum();
    }

    @Test
    void uncalledAllInExcessIsShownOnceNotInStackAndPot() {
        // A all-in 500, B all-in for 200: B's 200 + A's 200 contested, A's 300 comes back.
        Lobby lobby = new Lobby(LobbyType.GLOBAL, StakesLevel.LOW);
        ConnectedPlayer a = new ConnectedPlayer(record(1));
        ConnectedPlayer b = new ConnectedPlayer(record(2));
        a.setPlayerState(PlayerState.ALL_IN);
        b.setPlayerState(PlayerState.ALL_IN);
        lobby.getPlayersAtTable()[0] = a;
        lobby.getPlayersAtTable()[1] = b;
        lobby.addHandContribution(a, 500);
        lobby.addHandContribution(b, 200);
        lobby.setAllActivePots(new ArrayList<>(List.of(pot(400, false, a, b), pot(300, true, a))));
        lobby.setGamePhase(GamePhase.FLOP);

        GameStateDto state = new GameStateBuilder().build(lobby);
        assertEquals(400, potTotal(state));
        assertEquals(300, state.players().get(0).chips());
        assertEquals(0, state.players().get(1).chips());
    }

    @Test
    void shoveNotYetCalledIsNotShownAsChipsBehind() {
        // A just shoved 500 into the working pot; B (bet 2) still has to act.
        Lobby lobby = new Lobby(LobbyType.GLOBAL, StakesLevel.LOW);
        ConnectedPlayer a = new ConnectedPlayer(record(1));
        ConnectedPlayer b = new ConnectedPlayer(record(2));
        b.buyIn(998);
        a.setPlayerState(PlayerState.ALL_IN);
        lobby.getPlayersAtTable()[0] = a;
        lobby.getPlayersAtTable()[1] = b;
        lobby.addHandContribution(a, 500);
        lobby.addHandContribution(b, 2);
        lobby.setAllActivePots(new ArrayList<>(List.of(pot(502, false, a, b))));
        lobby.setGamePhase(GamePhase.PRE_FLOP);

        GameStateDto state = new GameStateBuilder().build(lobby);
        assertEquals(0, state.players().get(0).chips());
        assertEquals(502, potTotal(state));
    }

    @Test
    void blindExcessFlaggedUncalledStaysInPotWhileOthersCanStillAct() {
        // SB all-in for 1, BB posted 2: the blinds step flags BB's extra 1 as
        // "uncalled", but UTG hasn't acted yet — it's still a live bet.
        Lobby lobby = new Lobby(LobbyType.GLOBAL, StakesLevel.LOW);
        ConnectedPlayer sb = new ConnectedPlayer(record(1));
        ConnectedPlayer bb = new ConnectedPlayer(record(2));
        ConnectedPlayer utg = new ConnectedPlayer(record(3));
        bb.buyIn(98);
        utg.buyIn(100);
        sb.setPlayerState(PlayerState.ALL_IN);
        for (ConnectedPlayer p : List.of(sb, bb, utg)) {
            p.addCards(List.of(new Card(Rank.TWO, Suit.CLUBS), new Card(Rank.THREE, Suit.CLUBS)));
        }
        lobby.getPlayersAtTable()[0] = sb;
        lobby.getPlayersAtTable()[1] = bb;
        lobby.getPlayersAtTable()[2] = utg;
        lobby.setAllActivePots(new ArrayList<>(List.of(pot(2, false, sb, bb), pot(1, true, bb))));
        lobby.setGamePhase(GamePhase.PRE_FLOP);

        GameStateDto state = new GameStateBuilder().build(lobby);
        assertEquals(3, potTotal(state));
        assertEquals(98, state.players().get(1).chips());
    }

    @Test
    void allInWinnerShowsWinningsAtHandComplete() {
        Lobby lobby = new Lobby(LobbyType.GLOBAL, StakesLevel.LOW);
        ConnectedPlayer a = new ConnectedPlayer(record(1));
        ConnectedPlayer b = new ConnectedPlayer(record(2));
        a.setPlayerState(PlayerState.ALL_IN);
        b.setPlayerState(PlayerState.ALL_IN);
        lobby.getPlayersAtTable()[0] = a;
        lobby.getPlayersAtTable()[1] = b;
        lobby.addHandContribution(a, 500);
        lobby.addHandContribution(b, 200);
        a.addChips(700); // won the 400 main pot + 300 uncalled back; pots paid out (emptied)
        lobby.setAllActivePots(new ArrayList<>(List.of(pot(0, false, a, b), pot(0, true, a))));
        lobby.setGamePhase(GamePhase.HAND_COMPLETE);

        GameStateDto state = new GameStateBuilder().build(lobby);
        assertEquals(700, state.players().get(0).chips());
    }

    // ─── WebSocketsController disconnect grace period ────────────────────────────

    private Lobby lobby;
    private LobbyManagerService manager;
    private LobbyGameService game;
    private WebSocketsController controller;
    private final List<Integer> vacatedSeats = new ArrayList<>();

    @BeforeEach
    void setUpController() throws Exception {
        lobby = new Lobby(LobbyType.GLOBAL, StakesLevel.LOW);
        ConnectedPlayer p = new ConnectedPlayer(record(5));
        p.buyIn(100);
        lobby.getPlayersAtTable()[3] = p;

        manager = mock(LobbyManagerService.class);
        game = mock(LobbyGameService.class);
        when(manager.getLobbyById(lobby.getId())).thenReturn(lobby);
        when(manager.getSeatForPlayer(eq(lobby), anyInt())).thenAnswer(inv -> {
            int uid = inv.getArgument(1);
            ConnectedPlayer[] t = lobby.getPlayersAtTable();
            for (int i = 0; i < t.length; i++) {
                if (t[i] != null && t[i].playerRecord.getId() == uid) return i;
            }
            return -1;
        });
        doAnswer(inv -> {
            int seat = inv.getArgument(1);
            vacatedSeats.add(seat);
            lobby.getPlayersAtTable()[seat] = null;
            return null;
        }).when(manager).leaveTable(eq(lobby), anyInt());
        when(game.handlePlayerDeparture(any(), anyInt())).thenReturn(true);

        controller = new WebSocketsController(manager, game, new GameStateBuilder(),
                new SimpMessagingTemplate((message, timeout) -> true), new ChatModerationService());
        Method m = WebSocketsController.class.getDeclaredMethod("setDisconnectGraceMillis", long.class);
        m.setAccessible(true);
        m.invoke(controller, 150L);
    }

    private void join(String sessionId) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SEND);
        acc.setSessionId(sessionId);
        controller.joinGame(lobby.getId(), acc, principal(5));
    }

    private void disconnect(String sessionId) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        acc.setSessionId(sessionId);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
        controller.handleDisconnect(new SessionDisconnectEvent(this, msg, sessionId, CloseStatus.NORMAL));
    }

    @Test
    void closingOneOfTwoTabsDoesNotEvictTheSeat() throws Exception {
        join("tabA");
        join("tabB");
        disconnect("tabB");
        Thread.sleep(400);
        assertTrue(vacatedSeats.isEmpty(), "seat evicted while another tab was still connected");

        disconnect("tabA");
        Thread.sleep(400);
        assertEquals(List.of(3), vacatedSeats);
    }

    @Test
    void reconnectWithinGracePeriodKeepsSeat() throws Exception {
        join("s1");
        disconnect("s1");
        join("s2");
        Thread.sleep(400);
        assertTrue(vacatedSeats.isEmpty());
    }

    @Test
    void secondDisconnectGetsItsOwnFullGracePeriod() throws Exception {
        join("s1");
        disconnect("s1");     // eviction task #1 due at +150ms
        Thread.sleep(50);
        join("s2");
        disconnect("s2");     // eviction task #2 due at ~+200ms
        Thread.sleep(120);    // task #1 has fired by now
        assertTrue(vacatedSeats.isEmpty(), "stale task from the first disconnect evicted the seat");
        Thread.sleep(300);
        assertEquals(List.of(3), vacatedSeats);
    }

    @Test
    void reconnectCancelsDeferredAllInDeparture() {
        lobby.getPendingDepartureSeats().add(3);
        join("s1");
        assertFalse(lobby.getPendingDepartureSeats().contains(3));
    }

    @Test
    void nullActionIsIgnoredNotThrown() {
        assertDoesNotThrow(() -> controller.playerAction(lobby.getId(),
                new ActionMessage(null, 0), principal(5)));
        verify(game, never()).processPlayerAction(any(), anyInt(), any(), anyInt());
    }
}
