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
        String domain = extractDomain(email);

        if (!email.endsWith(".edu")) {
            return error(HttpStatus.BAD_REQUEST,
                    "Only .edu Google accounts are allowed.");
        }

        String collegeName = College.DomainCollegeMap.getCollegeName(domain);
        if (collegeName.equals("Unknown College")) {
            return error(HttpStatus.BAD_REQUEST,
                    "Your school (" + domain + ") is not yet supported. Check back soon!");
        }

        PlayerRecord record = playerRepository.findByEmail(email).orElse(null);
        if (record == null) {
            record = new PlayerRecord(
                    identity.name(),
                    email,
                    passwordEncoder.encode(java.util.UUID.randomUUID().toString()),
                    domain,
                    collegeName,
                    "GOOGLE");
            playerRepository.save(record);
        } else if (!identity.name().equals(record.getName())) {
            // Keep the display name fresh with whatever Google reports now.
            record.setName(identity.name());
            playerRepository.save(record);
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

        if (record.getTotalChips() < req.stakes().minimumBuyIn) {
            return error(HttpStatus.BAD_REQUEST,
                    "You need at least " + req.stakes().minimumBuyIn
                    + " chips to join a " + req.stakes().name() + " table. "
                    + "You have " + record.getTotalChips() + ".");
        }

        if (req.buyInAmount() > 0 && req.buyInAmount() < req.stakes().minimumBuyIn) {
            return error(HttpStatus.BAD_REQUEST,
                    "Buy-in must be at least " + req.stakes().minimumBuyIn
                    + " for a " + req.stakes().name() + " table.");
        }

        LobbyAssignment assignment = lobbyManagerService.joinLobby(
                record, req.stakes(), req.schoolLobby(), req.buyInAmount());

        GameStateDto initialState = gameStateBuilder.build(assignment.lobby());

        // Include the player's hole cards if the hand started immediately on join
        var seated = assignment.lobby().getPlayersAtTable()[assignment.seat()];
        var initialPrivate = (seated != null && !seated.getCards().isEmpty())
                ? gameStateBuilder.buildPrivate(seated)
                : null;

        return ResponseEntity.ok(new PlayResponse(
                assignment.lobby().getId(),
                wsUrl,
                assignment.seat(),
                assignment.buyIn(),
                initialState,
                initialPrivate));
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

        int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
        if (seat == -1) return error(HttpStatus.NOT_FOUND, "You are not seated in this lobby.");

        var seated = lobby.getPlayersAtTable()[seat];
        GameStateDto state = gameStateBuilder.build(lobby);
        var privateState = !seated.getCards().isEmpty()
                ? gameStateBuilder.buildPrivate(seated)
                : null;

        return ResponseEntity.ok(new PlayResponse(
                lobby.getId(),
                wsUrl,
                seat,
                seated.getActiveChips(),
                state,
                privateState));
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
     * Tops up the player's active chip stack from their bank balance.
     * Only valid while the player is still seated with 0 active chips.
     */
    @PostMapping("/game/rebuy")
    public ResponseEntity<?> rebuy(@Valid @RequestBody RebuyRequest req,
            Authentication auth) {

        int userId = Integer.parseInt((String) auth.getPrincipal());
        PlayerRecord record = playerRepository.findById(userId).orElse(null);
        if (record == null) return error(HttpStatus.UNAUTHORIZED, "User not found.");

        Lobby lobby = lobbyManagerService.getLobbyById(req.lobbyId());
        if (lobby == null) return error(HttpStatus.NOT_FOUND, "Lobby not found.");

        int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
        if (seat == -1) return error(HttpStatus.BAD_REQUEST, "You are not seated in this lobby.");

        if (record.getTotalChips() < req.amount()) {
            return error(HttpStatus.BAD_REQUEST,
                    "Insufficient bank balance. You have " + record.getTotalChips() + " chips.");
        }

        int added = lobbyManagerService.rebuy(lobby, seat, record, req.amount());
        return ResponseEntity.ok(Map.of("added", added, "bankBalance", record.getTotalChips()));
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
