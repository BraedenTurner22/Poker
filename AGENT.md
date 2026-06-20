# AGENT.md — College Showdown

Guidelines and architecture reference for Claude Code (and other AI coding
agents) working in this repository. Keep this file up to date — when you
make a structural change, update the relevant section here in the same
session, not as a follow-up.

See also:
- `LESSONS_LEARNED.md` — gotchas and mistakes from past sessions, read
  before touching Phaser rendering, Google sign-in testing, or the
  concurrency model.
- `SCALABILITY.md` — the planned future migration to a horizontally
  scalable, multi-instance backend. **Not implemented** — today's
  backend is intentionally single-instance (see "Known Limitations").

---

## ⚠️ Operational note: this repo's git history is stale

As of this writing, the last real **commit** on this branch predates a
very large amount of working-tree change (Google OAuth, the entire
poker-engine concurrency/correctness audit, leaderboard rework, chat
moderation, disconnect resilience, env config, this doc). All of it is
sitting **uncommitted**. If you're starting a new session and `git log`
looks stale relative to what's actually in the files, that's why — this
isn't a sign the working tree is wrong, the git history is just behind.
Don't assume an old commit or stale-looking diff reflects current
behavior; read the actual files. Flag to the user that committing this
work in logical chunks is overdue.

---

## Project Overview

**College Showdown** is a real-time Texas Hold'Em poker platform for
college students. Players sign in with Google (`.edu` domain enforced
server-side), receive a persistent bank of chips, and compete at three
stakes levels at tables of up to 8. A leaderboard ranks both schools and
individual players, all-time or by the past month.

Monorepo layout:
- `backend/` — Java 23 / Spring Boot 3.4.3 REST + WebSocket (STOMP) server
- `frontend/` — Angular 17 standalone-component SPA with a Phaser 3 game canvas

---

## Architecture at a Glance

```
Browser (Angular + Phaser)
    │
    ├─ HTTP REST ──────────► PlayerRESTController  (auth, play/rejoin, rebuy, leaderboard, users/me)
    └─ STOMP/WebSocket ────► WebSocketsController  (join, actions, chat, leave, disconnect handling)
                                      │
                              LobbyManagerService   (seat assignment, buy-in, cash-out, lobby registry)
                              LobbyGameService       (turn engine, betting rounds, timers, hand lifecycle)
                              PotManagerService      (side-pot calculation)
                              WinnerService          (showdown, hand comparison, payout)
                              ChatModerationService  (slur blocklist)
                              LeaderboardService     (stats, monthly reset)
                                      │
                              Supabase PostgreSQL    (players table — persistent bank chips, identity)
```

**Angular routing:**
- `/` → `MainMenuComponent` — logo homepage; shows feature pitch + Sign Up when logged out, Play/Leaderboards when logged in
- `/play` → `TableSelectComponent` — Google sign-in (if logged out) + stakes/buy-in selection
- `/game` → `PokerGameComponent` — Phaser canvas + action panel (receives `PlayResponse` via `history.state`, **or** recovers it via `GET /api/game/rejoin/{lobbyId}` on a hard reload — see "Reload/disconnect resilience" below)
- `/leaderboard` → `LeaderboardComponent` — three boards side by side (Schools / Players / Your School), All Time / Past Month toggle

---

## Authentication — Google OAuth only, no passwords

This was a full email/password → Google OAuth migration. There is **no
password-based registration or login anymore** — `POST /api/auth/register`
and `POST /api/auth/login` do not exist. The only way to create or access
an account is `POST /api/auth/google`.

Flow:
1. Frontend loads Google Identity Services (`index.html` script tag),
   renders the real "Continue with Google" button in
   `table-select.component.ts` (`google.accounts.id.initialize` +
   `renderButton`, with a short retry-poll since the GIS script loads
   async).
2. Google returns a signed ID token directly to the frontend.
3. Frontend POSTs `{ idToken }` to `POST /api/auth/google`.
4. Backend (`GoogleTokenVerifier.java`) verifies the token via Google's
   `tokeninfo` endpoint — checks `aud` matches `google.oauth.client-id`
   and `email_verified == "true"`. No client secret needed, no new
   dependency (plain `RestTemplate`).
5. `PlayerRESTController.googleAuth()` re-runs the same `.edu` +
   `College.DomainCollegeMap` checks `register()` used to do, finds the
   account by email or creates one (random unusable password hash,
   `authProvider="GOOGLE"`), refreshes the stored display name to match
   Google's current `name` claim on every login, and issues the existing
   JWT.

**Config needed:** `GOOGLE_CLIENT_ID` in `backend/.env`, and the same
value as the `GOOGLE_CLIENT_ID` constant in
`frontend/src/services/auth.service.ts`. It's a public Client ID, not a
secret. The Google Cloud OAuth client's "Authorized JavaScript origins"
must list whatever origin the frontend is served from (`http://localhost:4200`
and `:4300` for local dev — see "Environment config" below).

**Gotcha:** Google's origin-allowlist changes can take much longer to
propagate than other Cloud Console changes (we saw ~40+ minutes once).
Also: **headless/automated browser testing of the Google sign-in button
gives false "origin not allowed" failures even with correct config** —
always verify in a real browser before chasing config. See
`LESSONS_LEARNED.md`.

---

## Identity: no chosen usernames, real names only

Players are identified everywhere (leaderboards, table seats, chat,
profile) by their real name from Google's `name` claim — refreshed on
every login, never user-editable.

- `PlayerRecord.name` — **the DB column is still physically named
  `username`** (`@Column(name = "username")`) because `ddl-auto=update`
  can't rename columns, but the Java field, getter, and every API-facing
  field name are `name`. Don't be confused by the mismatch — it's
  intentional and documented at the field declaration.
- The `username` column's old `unique` constraint had to be explicitly
  dropped at startup (`SchemaFixupRunner.java`, a `CommandLineRunner`)
  since `ddl-auto=update` never drops constraints either, and real names
  collide (two "John Smith"s are expected). It's idempotent — safe to
  leave running indefinitely, no-ops once the constraint is gone.
- `PlayerRepository.findByUsername`/`existsByUsername`,
  `RegisterRequest`/`LoginRequest` DTOs, and the
  `PATCH /users/me/username` endpoint are all **deleted**, not just
  unused — there's no user-editable identity anymore by design.

---

## Reload/disconnect resilience

Two related but distinct problems were fixed here — know which one
you're dealing with before changing this code.

**1. Page reload on `/game` used to lose your seat entirely.**
`PokerGameComponent` only knew its lobby/seat via Angular Router
`history.state`, which doesn't survive a hard reload. Fixed via:
- `AuthService.setActiveLobby()/getActiveLobby()/clearActiveLobby()` —
  persists the current lobby ID to `localStorage` on join, cleared on
  explicit leave or kick.
- `GET /api/game/rejoin/{lobbyId}` — recovers an already-seated player's
  full `PlayResponse` shape (same DTO as a fresh join) so the frontend
  can feed it through the exact same init path. 404s if the seat's
  genuinely gone (see #2).
- `PokerGameComponent.ngOnInit()`: if no `history.state.playResponse`,
  falls back to `getActiveLobby()` + `rejoinLobby()` before giving up
  and redirecting to `/play`.

**2. A WebSocket disconnect used to instantly fold + cash out the
player, including a brief reload's own disconnect/reconnect cycle.**
Fixed via a 20-second grace period in `WebSocketsController`:
`handleDisconnect()` schedules eviction instead of running it
immediately, and cancels itself if a newer session for the same
lobby+user registers (i.e. they reconnected) before the grace period
expires. This also incidentally makes the app resilient to brief real
network blips, since the STOMP client already auto-reconnects.

**3. An `ALL_IN` player who disconnects must not be folded out** — they
have no more decisions to make but are still entitled to the pot. See
"Poker engine" below.

---

## Poker engine — concurrency model and correctness

This went through a full audit (race conditions, side-pot math, raise
validation) — see `LESSONS_LEARNED.md` for what was found and why some
fixes turned out simpler than first attempted.

### Locking: one lock per `Lobby`, not per service

`LobbyGameService` and `LobbyManagerService` used to each synchronize on
their own singleton (`this`) — two different, uncoordinated locks
guarding the same shared `Lobby`/`ConnectedPlayer` objects. **Now every
method in both services that touches a lobby's game state synchronizes
on that specific `Lobby` instance** (`synchronized(lobby) { ... }`).
Java intrinsic locks are reentrant, so nested calls on the same thread
(e.g. `processPlayerAction` → `processPlayerActionCore`,
`handlePlayerDeparture` → `processPlayerActionCore`) work exactly as
before — no new lock object needed, `Lobby` is already unique per table.

`LobbyManagerService` additionally has a `registryLock` — guards
**only** the `lobbies`/`lobbyById` maps (which lobbies exist, pool
membership). Deliberately separate from any per-lobby lock and **never
held at the same time as one** — `joinLobby`/`leaveTable` acquire them
sequentially, never nested, so the two can never deadlock against each
other.

**Rule: any new method added to either service that reads or mutates
`Lobby`/`ConnectedPlayer` state must start with `synchronized(lobby) { ... }`.**
This includes scheduled-task lambdas (turn timer, run-out steps,
next-hand scheduling) — wrap the whole lambda body, not just the call
into an already-synchronized method, so check-then-act sequences inside
the lambda are atomic too.

### `startHand` is idempotent

`startHand(lobby)` only proceeds if the phase is `WAITING` or
`HAND_COMPLETE` — never mid-hand. This guards against two overlapping
triggers (e.g. a rebuy and a new join landing close together) both
reaching `startHand` and re-dealing on top of an in-progress hand.
`concludeHand`/`concludeFoldWin` have the same kind of guard (return
immediately if already `HAND_COMPLETE`).

### Disconnecting while `ALL_IN` defers seat removal, doesn't fold

`handlePlayerDeparture` checks `PlayerState.ALL_IN` before the
already-`FOLDED` check: an all-in player gets added to
`Lobby.pendingDepartureSeats` and the method returns `false` (caller
must NOT vacate the seat yet) instead of folding them out. Folding them
would strip their equity from every pot they're contesting, and
`leaveTable` would then cash them out for `getActiveChips()` — `0` for
an all-in player, since their stake is in the pot, not their stack.
`concludeHand`/`concludeFoldWin` process `pendingDepartureSeats` after
distributing winnings, so the seat is correctly vacated (with whatever
they won) once the hand is actually over.

### Server-side validation that didn't used to exist

- `RAISE` actions are validated against `lobby.getLastRaiseSize()` in
  `processPlayerActionCore` — previously the server trusted whatever
  `raiseAmount` the client sent, including `0`, which could stall a
  betting round indefinitely (any raise, even a no-op one, reset
  `playersActedThisRound`). A short raise is still allowed through if
  the player can't afford a full minimum raise — it naturally becomes
  an all-in via the existing chip-truncation logic, matching real rules.
- `req.buyInAmount()` is validated against `req.stakes().minimumBuyIn`
  in `PlayerRESTController.play()` — previously only the player's *bank
  total* was checked, not the amount they were actually buying in for.

### Side-pot math (verified correct, not changed)

`PotManagerService.recalculatePots()` — sorts unique contribution
levels, slices the pot per level, eligible-to-win at each slice =
non-folded contributors at that level. Handles dead money from folded
contributors and uncalled-bet excess (returned via a single-player pot
the over-bettor trivially "wins" back) correctly without special-casing
either. Don't touch this without running `./mvnw test` before and after
— `GameEngineTest.java` has dedicated multi-way-all-in coverage.

---

## Environment config (frontend + backend)

Both frontend and backend used to hardcode `localhost:8081` — would
break immediately on deploy. Now:

- **Frontend**: `frontend/src/environments/environment.ts` (dev) /
  `environment.prod.ts` (prod placeholder URLs — fill in once you pick
  a host). Swapped via `angular.json`'s production `fileReplacements`.
  `auth.service.ts`'s `API` const and `websocket.service.ts`'s
  `brokerURL` both read from `environment.apiUrl`/`environment.wsUrl`.
- **Backend**: `app.cors.allowed-origins` (`application.properties`,
  overridable via `CORS_ALLOWED_ORIGINS` env var) — defaults to
  `localhost:4200` (Angular CLI's actual default dev port) **and**
  `:4300` (used throughout recent local testing) so `ng serve` works
  either way without extra setup. Used by both the HTTP CORS config
  (`SecurityConfig.java`) and the WebSocket endpoint's allowed origins
  (`WebSocketConfig.java`) — kept consistent on purpose, though the
  WebSocket one isn't a real auth boundary (every action still requires
  a valid JWT regardless of origin).
- `app.ws-url` (`APP_WS_URL` env var) — the WS URL handed back in
  `PlayResponse`. Currently dead data the frontend doesn't read (it uses
  its own `environment.wsUrl` instead), fixed anyway for correctness in
  case that ever changes.

**When deploying:** fill in `environment.prod.ts`'s two URLs, set
`CORS_ALLOWED_ORIGINS`/`APP_WS_URL` to your real domains, and add the
production frontend origin to the Google Cloud OAuth client's
authorized origins.

---

## Phaser rendering — read before touching canvas/texture code

Two non-obvious gotchas, both costly to rediscover. Full detail in
`LESSONS_LEARNED.md`.

1. **DPR/Retina sharpness**: the `resolution` config property is a
   no-op in this Phaser version. Use `Scale.NONE` + physical pixel
   dimensions (`window.innerWidth * dpr`), then pin the canvas's CSS
   size back to CSS pixels. See `poker-game.component.ts`'s
   `startPhaser()` — don't reintroduce `resolution` or `zoom`.
2. **Small-texture blur (school logos)**: WebGL can only mipmap
   power-of-two textures. Logos are 128×128 PNGs (`assets/college_logos/`)
   with `mipmapFilter: 'LINEAR_MIPMAP_LINEAR'` set in the Phaser game
   config. **Never call `texture.setFilter(...)` after load** — it
   resets to flat (non-mipmapped) filtering and silently undoes the fix.
   New logos must be added at 128×128 with transparency flattened to
   white (Pillow `Image.alpha_composite` over a white background), not
   just dropped in at arbitrary size.

---

## Common Tasks

### Add a new game action (e.g., STRADDLE)
1. Add the action name to `PlayerAction` enum + `ActionMessage` DTO
2. Handle it in `LobbyGameService.applyAction()` (inside the existing
   `synchronized(lobby)` block in `processPlayerActionCore`)
3. Add server-side validation if it moves chips — don't trust the client
4. Add button in `poker-game.component.html` + handler in `.ts`
5. Write a unit test in `GameEngineTest.java`, run `./mvnw test`

### Add a new player stat column
1. Add `@Column` to `PlayerRecord` with a `columnDefinition` DEFAULT —
   required for `NOT NULL` columns or Postgres rejects the migration on
   existing rows (`ddl-auto=update` can't backfill)
2. Add getter + update call in `updateChips()` if it's chips-related
3. Expose it in `/api/users/me` if the frontend needs it
4. Restart the backend — Hibernate adds the column automatically (but
   never renames/drops — see the `SchemaFixupRunner` pattern if you
   ever need to retire a constraint)

### Add a new stakes level
1. Add an entry to `StakesLevel` enum (`backend/domain/lobby/StakesLevel.java`)
2. Add a tile in the `STAKES` array in `table-select.component.ts`
3. Min/max buy-in validation already reads from `StakesLevel` —
   `PlayerRESTController.play()` doesn't need a code change

### Add a new supported college
Edit `College.DomainCollegeMap` (the static nested class inside
`backend/models/College/College.java`) — key is the `.edu` domain
suffix, value is the display name. If you want a logo too: resize to
128×128, flatten any transparency to white, drop the PNG in
`frontend/src/assets/college_logos/`, add one line to `DOMAIN_LOGO` in
`frontend/src/types/college-logos.ts`.

---

## Sensitive Files — Do Not Commit
- `backend/.env` — DB credentials, JWT secret, Google Client ID
  (confirmed gitignored, never committed)
- Any file matching `*.env`, `*.properties` containing credentials

---

## Testing

```bash
# Backend unit tests — pure in-memory, no Spring context, no DB, ~0.4s total
cd backend && ./mvnw test

# Backend compiles (faster than full test run, doesn't catch test-source rot —
# see LESSONS_LEARNED.md for why that matters)
cd backend && ./mvnw compile

# Frontend build (production config — also the strictest CSS/bundle budget check)
cd frontend && npx ng build --base-href='./'
```

Key test files (41 tests total, all passing as of this writing):
- `GameEngineTest.java` (20) — full hand simulation: blinds, raises,
  side pots, departures (including the `ALL_IN`-disconnect case), illegal
  action rejection, chip conservation across consecutive hands
- `HandEvaluatorTest.java` (20) — hand ranking correctness, every
  category, kickers, split pots
- `PokerProjectApplicationTests.java` (1) — Spring context loads
- `ForcedCardsTestDriver.java` — deterministic card sequences, used by
  the above, not a standalone test class itself

---

## Phaser Scene Layout Reference

Seats are positioned as fractions of the canvas. Slot 0 is always the
local player (bottom-center), rotated by `mySeat` offset.

```
           [3]    [4]    [5]
       [2]                   [6]
           [1]         [7]
                 [0]
              (you)
```

Bet labels sit 40% of the way from each seat toward the table center
(0.5, 0.472). When a new street starts, animated copies tween from each
bet label to the pot text position.

---

## Known Limitations (Prototype)

- **Single backend instance only.** Lobby/game state lives entirely in
  JVM memory (`ConcurrentHashMap`s + the per-lobby locks above), and
  Spring's WebSocket broker is the in-memory `SimpleBroker` — neither
  works across multiple instances. A restart drops every active table.
  Deliberately not addressed yet — see `SCALABILITY.md` for the planned
  approach when this actually needs solving, and don't start that work
  without re-reading that doc first, it's a large, deliberate design.
- Only `.edu` domains in `College.DomainCollegeMap` are accepted at
  sign-in — add domains manually (see "Common Tasks" above)
- No spectator mode, no tournament bracket support
- Bank chips are persistent but not tied to real money
- Mobile responsiveness was explicitly deprioritized — this app is
  designed for desktop. Don't spend effort on phone-width layouts
  unless specifically asked; do keep desktop pages scrollable (every
  top-level routed component's `:host` needs its own
  `height: 100vh; overflow-y: auto` — see `LESSONS_LEARNED.md` for why
  `min-height` + relying on page-level scroll doesn't work here)
