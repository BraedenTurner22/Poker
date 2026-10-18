package com.collegeshowdown.poker_project.controllers;

import com.collegeshowdown.poker_project.dto.*;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.models.College.College;
import com.collegeshowdown.poker_project.repositories.PlayerRepository;
import com.collegeshowdown.poker_project.service.GameStateBuilder;
import com.collegeshowdown.poker_project.services.LobbyManagerService;
import com.collegeshowdown.poker_project.services.LobbyManagerService.LobbyAssignment;
import com.collegeshowdown.poker_project.services.LeaderboardService;
import com.collegeshowdown.poker_project.util.JwtUtil;
import com.collegeshowdown.poker_project.util.GoogleTokenVerifier;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PlayerRESTController {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LobbyManagerService lobbyManagerService;
    private final LeaderboardService leaderboardService;
    private final GameStateBuilder gameStateBuilder;
    private final GoogleTokenVerifier googleTokenVerifier;

    @Value("${app.ws-url}")
    private String wsUrl;

    public PlayerRESTController(PlayerRepository playerRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            LobbyManagerService lobbyManagerService,
            LeaderboardService leaderboardService,
            GameStateBuilder gameStateBuilder,
            GoogleTokenVerifier googleTokenVerifier) {
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.lobbyManagerService = lobbyManagerService;
        this.leaderboardService = leaderboardService;
        this.gameStateBuilder = gameStateBuilder;
        this.googleTokenVerifier = googleTokenVerifier;
    }



    /**
     * POST /api/auth/google
     *
     * Verifies a Google Identity Services ID token, confirms the account's
     * email is a verified, supported .edu address, and logs the user in —
     * creating an account on first sign-in. This is the only way to create
     * a new account; it replaces password-based registration because it
     * actually proves the user owns the .edu inbox.
     */
    @PostMapping("/auth/google")
    public ResponseEntity<?> googleAuth(@Valid @RequestBody GoogleAuthRequest req) {
        GoogleTokenVerifier.GoogleIdentity identity;
        try {
            identity = googleTokenVerifier.verify(req.idToken());
        } catch (GoogleTokenVerifier.GoogleTokenException e) {
            return error(HttpStatus.UNAUTHORIZED, e.getMessage());
        }

        String email = identity.email();
        String emailDomain = extractDomain(email);

        if (!email.endsWith(".edu")) {
            return error(HttpStatus.BAD_REQUEST,
                    "Only .edu Google accounts are allowed.");
        }

        // Students on a department/college subdomain (e.g. cs.umass.edu,
        // andrew.cmu.edu) belong to the parent school — stored under its
        // canonical domain so they share its lobbies and leaderboards.
        String domain = College.DomainCollegeMap.resolveDomain(emailDomain);
        if (domain == null) {
            return error(HttpStatus.BAD_REQUEST,
                    "Your school (" + emailDomain + ") is not yet supported. Check back soon!");
        }
        String collegeName = College.DomainCollegeMap.getCollegeName(domain);

        PlayerRecord record = playerRepository.findByEmail(email).orElse(null);
        if (record == null) {
            record = new PlayerRecord(
                    identity.name(),
                    email,
                    passwordEncoder.encode(java.util.UUID.randomUUID().toString()),
                    domain,
                    collegeName,
                    "GOOGLE");
            try {
                record = playerRepository.save(record);
            } catch (DataIntegrityViolationException e) {
                // A concurrent first sign-in for the same email (double click,
                // two tabs) created the account first — use that one.
                record = playerRepository.findByEmail(email).orElse(null);
                if (record == null) throw e;
            }
        } else if (!identity.name().equals(record.getName())) {
            // Keep the display name fresh with whatever Google reports now.
            // A targeted UPDATE — saving the loaded entity would write its
            // bank-balance snapshot back over any concurrent cash-out.
            playerRepository.updateName(record.getId(), identity.name());
            record.setName(identity.name());
        }

        String token = jwtUtil.generateToken(record.getId(), record.getName());
        return ResponseEntity.ok(toAuthResponse(token, record));
    }



    /**
     * POST /api/auth/logout
     *
     * JWT is stateless — the client discards the token.
     * Return 200 so the client can treat this uniformly.
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }



    /**
     * POST /api/game/play
     *
     * Places the authenticated player in a lobby matching the requested
     * stakes and lobby type. Returns the lobby ID and WebSocket URL.
     */
    @PostMapping("/game/play")
    public ResponseEntity<?> play(@Valid @RequestBody PlayRequest req,
            Authentication auth) {

        int userId = Integer.parseInt((String) auth.getPrincipal());
        PlayerRecord record = playerRepository.findById(userId)
                .orElse(null);

        if (record == null) {
            return error(HttpStatus.UNAUTHORIZED, "User not found.");
        }

        // Minimum buy-in / bank checks live in joinLobby, after its
        // "already seated?" check — a seated player whose whole bank is on
        // the table must still be able to get back to their seat.
        LobbyAssignment assignment;
        try {
            assignment = lobbyManagerService.joinLobby(
                    record, req.stakes(), req.schoolLobby(), req.buyInAmount());
        } catch (LobbyManagerService.SeatingException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        Lobby lobby = assignment.lobby();
        // The game engine mutates this lobby on other threads (turn timers,
        // next-hand scheduling); read it under its lock like every other reader.
        synchronized (lobby) {
            GameStateDto initialState = gameStateBuilder.build(lobby);

            // Include the player's hole cards if the hand started immediately
            // on join. Re-resolve the seat rather than trusting the index —
            // never hand out another player's hole cards.
            int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
            var seated = seat == -1 ? null : lobby.getPlayersAtTable()[seat];
            var initialPrivate = (seated != null && !seated.getCards().isEmpty())
                    ? gameStateBuilder.buildPrivate(seated)
                    : null;

            return ResponseEntity.ok(new PlayResponse(
                    lobby.getId(),
                    wsUrl,
                    assignment.seat(),
                    assignment.buyIn(),
                    initialState,
                    initialPrivate));
        }
    }



    /**
     * GET /api/game/rejoin/{lobbyId}
     *
     * Recovers an already-seated player's session — used when the game tab
     * reloads and loses its in-memory state. 404s if they're not (or no
     * longer) seated, e.g. the disconnect grace period already expired.
     */
    @GetMapping("/game/rejoin/{lobbyId}")
    public ResponseEntity<?> rejoin(@PathVariable String lobbyId, Authentication auth) {
        int userId = Integer.parseInt((String) auth.getPrincipal());

        Lobby lobby = lobbyManagerService.getLobbyById(lobbyId);
        if (lobby == null) return error(HttpStatus.NOT_FOUND, "Lobby not found.");

        // Seat lookup and every read of it happen under the lobby's lock, so
        // the seat can't change hands between finding it and reading its
        // hole cards.
        synchronized (lobby) {
            int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
            if (seat == -1) return error(HttpStatus.NOT_FOUND, "You are not seated in this lobby.");

            // They're back — if they'd been queued for removal at the end of
            // the hand (disconnected while ALL_IN), keep their seat.
            lobbyManagerService.cancelPendingDeparture(lobby, seat);

            var seated = lobby.getPlayersAtTable()[seat];
            GameStateDto state = gameStateBuilder.build(lobby);
            var privateState = !seated.getCards().isEmpty()
                    ? gameStateBuilder.buildPrivate(seated)
                    : null;

            return ResponseEntity.ok(new PlayResponse(
                    lobby.getId(),
                    wsUrl,
                    seat,
                    // What they bought in for, not the current stack — the
                    // client shows this as the buy-in, same as a fresh join.
                    lobbyManagerService.getSessionBuyIn(userId),
                    state,
                    privateState));
        }
    }



    /**
     * GET /api/users/me
     *
     * Returns the authenticated player's profile.
     */
    @GetMapping("/users/me")
    public ResponseEntity<?> me(Authentication auth) {
        int userId = Integer.parseInt((String) auth.getPrincipal());
        return playerRepository.findById(userId)
                .<ResponseEntity<?>>map(record ->
                    ResponseEntity.ok(Map.of(
                        "id", record.getId(),
                        "name", record.getName(),
                        "email", record.getEmail(),
                        "collegeName", record.getCollegeName(),
                        "collegeDomain", record.getCollegeDomain(),
                        "chips", record.getTotalChips(),
                        "totalChipDifference", record.getTotalChipDifference()
                    )))
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, "User not found."));
    }



    /**
     * POST /api/game/rebuy
     *
     * Tops up the player's active chip stack from their bank balance, up to
     * the table maximum. Rejected while the player's stack is live in a hand.
     */
    @PostMapping("/game/rebuy")
    public ResponseEntity<?> rebuy(@Valid @RequestBody RebuyRequest req,
            Authentication auth) {

        int userId = Integer.parseInt((String) auth.getPrincipal());
        PlayerRecord record = playerRepository.findById(userId).orElse(null);
        if (record == null) return error(HttpStatus.UNAUTHORIZED, "User not found.");

        Lobby lobby = lobbyManagerService.getLobbyById(req.lobbyId());
        if (lobby == null) return error(HttpStatus.NOT_FOUND, "Lobby not found.");

        // Seat lookup, rule checks and the bank withdrawal all happen
        // atomically inside rebuy(). `record` is only a snapshot and is never
        // written back — saving it would overwrite newer bank changes.
        int added;
        try {
            added = lobbyManagerService.rebuy(lobby, userId, req.amount());
        } catch (LobbyManagerService.SeatingException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        int bankBalance = playerRepository.findTotalChipsById(userId).orElse(0);
        return ResponseEntity.ok(Map.of("added", added, "bankBalance", bankBalance));
    }



    private static LeaderboardService.Range parseRange(String range) {
        return "month".equalsIgnoreCase(range)
                ? LeaderboardService.Range.MONTHLY
                : LeaderboardService.Range.ALL_TIME;
    }

    /**
     * GET /api/leaderboard/schools?range=all|month
     *
     * Returns the top-10 schools by chip profit for the given range, plus the
     * requesting user's school entry if they are outside the top 10.
     */
    @GetMapping("/leaderboard/schools")
    public ResponseEntity<?> schoolLeaderboard(Authentication auth,
            @RequestParam(name = "range", defaultValue = "all") String range) {
        String domain = null;
        if (auth != null) {
            int userId = Integer.parseInt((String) auth.getPrincipal());
            domain = playerRepository.findById(userId)
                    .map(PlayerRecord::getCollegeDomain).orElse(null);
        }
        return ResponseEntity.ok(leaderboardService.schoolLeaderboard(domain, parseRange(range)));
    }



    /**
     * GET /api/leaderboard/players?range=all|month
     *
     * Returns the top-10 players by chip profit for the given range, plus the
     * requesting user's entry if they are outside the top 10.
     */
    @GetMapping("/leaderboard/players")
    public ResponseEntity<?> playerLeaderboard(Authentication auth,
            @RequestParam(name = "range", defaultValue = "all") String range) {
        Integer userId = null;
        if (auth != null) userId = Integer.parseInt((String) auth.getPrincipal());
        return ResponseEntity.ok(leaderboardService.playerLeaderboard(userId, parseRange(range)));
    }



    /**
     * GET /api/leaderboard/players/school?range=all|month
     *
     * Returns the top-10 players within the requesting user's school by chip
     * profit for the given range. Requires authentication — returns an empty
     * board for anonymous requests.
     */
    @GetMapping("/leaderboard/players/school")
    public ResponseEntity<?> schoolPlayerLeaderboard(Authentication auth,
            @RequestParam(name = "range", defaultValue = "all") String range) {
        String domain = null;
        Integer userId = null;
        if (auth != null) {
            userId = Integer.parseInt((String) auth.getPrincipal());
            domain = playerRepository.findById(userId)
                    .map(PlayerRecord::getCollegeDomain).orElse(null);
        }
        return ResponseEntity.ok(leaderboardService.schoolPlayerLeaderboard(domain, userId, parseRange(range)));
    }



    /**
     * Turns bean-validation failures (e.g. password too short) into a 400
     * with a readable message instead of an opaque error.
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request.");
        return error(HttpStatus.BAD_REQUEST, message);
    }



    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private static String extractDomain(String email) {
        int at = email.indexOf('@');
        return at >= 0 ? email.substring(at + 1) : "";
    }

    private AuthResponse toAuthResponse(String token, PlayerRecord record) {
        return new AuthResponse(
                token,
                record.getId(),
                record.getName(),
                record.getEmail(),
                record.getCollegeName(),
                record.getTotalChips());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
