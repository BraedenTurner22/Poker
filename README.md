# College Showdown — Texas Hold'Em

A real-time, multiplayer Texas Hold'Em poker platform for college students. Players register with a `.edu` email, get $1,000 in chips, and compete at three stakes levels. School leaderboards rank universities by monthly profit.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Angular 21 (standalone components), Phaser 3.90, @stomp/stompjs |
| Backend | Java 23, Spring Boot 3.4.3, Spring Security 6, JWT (jjwt 0.12.6) |
| Database | PostgreSQL via Supabase (session pooler), JPA/Hibernate (`ddl-auto=update`) |
| Real-time | STOMP over native WebSocket (`/ws`) |

---

## Project Structure

```
Poker/
├── backend/                          Spring Boot application
│   └── src/main/java/com/collegeshowdown/poker_project/
│       ├── Application.java          Entry point (@EnableScheduling)
│       ├── config/
│       │   ├── SecurityConfig.java   JWT filter chain, CORS
│       │   ├── WebSocketConfig.java  STOMP endpoint registration
│       │   └── GameSettings.java     Tunable game constants
│       ├── controllers/
│       │   ├── PlayerRESTController.java   REST: auth, play, rebuy, leaderboards
│       │   └── WebSocketsController.java   WS: game actions, chat, disconnect
│       ├── domain/
│       │   ├── card/                 Card, Deck, Rank, Suit
│       │   └── lobby/                Lobby, GamePhase, StakesLevel, Pot
│       ├── dto/                      JSON data-transfer objects
│       ├── models/
│       │   ├── PlayerRecord.java     JPA entity — bank chips, lifetime + weekly + monthly stats
│       │   └── College/              College name → email domain map
│       ├── repositories/             Spring Data JPA repos + custom JPQL queries
│       ├── security/                 JWT auth filter, STOMP interceptor
│       ├── service/
│       │   └── GameStateBuilder.java Converts live Lobby → broadcast DTOs
│       └── services/
│           ├── DealerService.java        Hand start, hole card deal
│           ├── LobbyGameService.java     Turn engine, action processing, auto-fold timer
│           ├── LobbyManagerService.java  Seat assignment, buy-in, rebuy, cash-out
│           ├── PotManagerService.java    Side-pot algorithm
│           ├── WinnerService.java        Hand evaluation, chip award
│           └── LeaderboardService.java   Monthly school stats + scheduled reset
└── frontend/                         Angular application
    └── src/
        ├── app/
        │   ├── app.component.ts      Root component (RouterOutlet)
        │   ├── app.config.ts         provideRouter + provideHttpClient
        │   └── app.routes.ts         Route definitions
        ├── assets/
        │   ├── logo.png
        │   ├── Poker_Table.png
        │   └── svg_playing_cards/    Card face PNGs + back SVG
        ├── components/
        │   ├── main-menu/            Homepage: logo, Play, Leaderboards
        │   ├── table-select/         Auth forms + stakes tiles + buy-in selector
        │   ├── leaderboard/          Schools (monthly) and all-time players tabs
        │   └── game/
        │       ├── poker-game/       Angular shell: action panel, chat, rebuy modal
        │       └── scenes/
        │           └── PokerGameScene.ts  Phaser scene: table, seats, cards, bet labels
        ├── services/
        │   ├── auth.service.ts       HTTP: register, login, joinLobby, rebuy, leaderboards
        │   └── websocket.service.ts  STOMP connection, rxjs subjects
        └── types/
            └── game.types.ts         Shared TypeScript interfaces
```

---

## Running Locally

### Prerequisites
- Java 23+, Maven 3.9+
- Node 20+, npm
- A Supabase project (free tier works fine)

### 1 — Configure the backend

Create `backend/.env`:
```env
DB_USERNAME=postgres.<your-project-ref>
DB_PASSWORD=<your-db-password>
JWT_SECRET=<any-256-bit-hex-string>
```

The datasource URL is already set in `application.properties` to the Supabase session pooler. Hibernate's `ddl-auto=update` creates/updates tables automatically on first boot.

### 2 — Start the backend

```bash
cd backend
./mvnw spring-boot:run
# Starts on http://localhost:8081
```

### 3 — Start the frontend

```bash
cd frontend
npm install
npm run dev-nolog      # plain ng serve
# Opens on http://localhost:4200
```

Frontend tests run under Vitest in headless Chromium (Phaser needs a real
canvas). Download the browser once, then run them:

```bash
npx playwright install chromium-headless-shell
npx ng test --watch=false
```

---

## User Flow

```
/ (Homepage)
├── Play ──► /play  (TableSelectComponent)
│   ├── Not logged in → register or log in with .edu email
│   └── Logged in:
│       ├── Pick a stakes tile  (LOW 1/2 · MEDIUM 2/5 · HIGH 5/10)
│       ├── Drag slider to choose buy-in amount (capped at bank balance)
│       └── Sit Down ──► /game  (PokerGameComponent)
│           ├── Real-time Phaser table (8 seats, rotating so you're always at bottom)
│           ├── Action panel: Fold · Check · Call · Raise (slider) · All In
│           ├── Pre-select Fold / Check-Fold while waiting for your turn
│           ├── Bet labels in front of each seat, animate into pot on new streets
│           ├── School logo circle on every occupied seat
│           ├── Rebuy popup when your chips reach 0 at hand end
│           └── Leave Table ──► /play
└── Leaderboards ──► /leaderboard
    ├── Schools tab  — top 10 schools by monthly chip profit (resets 1st of every month)
    └── Players tab  — top 10 all-time players by total profit
        Both tabs show your own rank below the top 10 if you're outside it.
```

---

## REST API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | — | Register with .edu email; returns JWT + profile |
| POST | `/api/auth/login` | — | Login; returns JWT + profile |
| POST | `/api/auth/logout` | — | No-op (stateless JWT; client drops token) |
| GET | `/api/users/me` | JWT | Current player profile + bank balance |
| POST | `/api/game/play` | JWT | Join or rejoin a lobby; returns `PlayResponse` |
| POST | `/api/game/rebuy` | JWT | Top up active stack from bank while seated |
| GET | `/api/leaderboard/schools` | optional JWT | Monthly school leaderboard |
| GET | `/api/leaderboard/players` | optional JWT | All-time player leaderboard |

### PlayRequest body
```json
{ "stakes": "LOW|MEDIUM|HIGH", "schoolLobby": false, "buyInAmount": 100 }
```

### PlayResponse body
```json
{ "lobbyId": "uuid", "wsUrl": "ws://localhost:8081/ws", "seatNumber": 3, "buyInAmount": 100 }
```

---

## WebSocket (STOMP)

Connect to `ws://localhost:8081/ws` with a `token` header containing the JWT.

| Direction | Topic / Destination | Payload |
|-----------|---------------------|---------|
| Subscribe | `/topic/lobby/{lobbyId}` | `GameStateDto` — full table state broadcast |
| Subscribe | `/user/queue/private` | `PrivateStateDto` — your hole cards only |
| Subscribe | `/topic/chat/{lobbyId}` | `ChatBroadcast` |
| Publish | `/app/action` | `ActionMessage { lobbyId, action, amount }` |
| Publish | `/app/chat` | `ChatMessage { lobbyId, message }` |
| Publish | `/app/leave` | `{ lobbyId }` |

---

## Stakes & Rules

| Level | Blinds | Min Buy-in | Max Buy-in |
|-------|--------|-----------|-----------|
| LOW | 1 / 2 | $40 | $200 |
| MEDIUM | 2 / 5 | $100 | $500 |
| HIGH | 5 / 10 | $200 | $1,000 |

- New players start with **$1,000** in their bank (`PlayerRecord.totalChips`)
- Buy-in moves chips bank → table; leaving returns the remainder to the bank
- Side pots are computed correctly — an all-in player can only win the portion of the pot they matched from each opponent
- Turn timer: 30 seconds; auto-fold on timeout
- Monthly leaderboard resets on the **1st of each month at midnight (server time)**

---

## Key Design Decisions

**Why session pooler for Supabase?**  
The transaction pooler doesn't support prepared statements, which Hibernate requires. The session pooler (port 5432) does.

**Why Angular Router state for PlayResponse?**  
`/game` is purely a game view — it shouldn't know how to join a lobby. Passing `PlayResponse` via `history.state` keeps concerns separate. Refreshing on `/game` redirects to `/play` (graceful degradation, no zombie connections).

**Why Phaser for the table?**  
Real-time card dealing, chip animations, and per-seat countdown arcs are much cleaner in a canvas game loop than in DOM/CSS. Angular manages the overlay UI (buttons, chat, modals); Phaser manages the game visual.

**Why colored circles for school logos in Phaser?**  
Clearbit logos are loaded dynamically at runtime (`https://logo.clearbit.com/{domain}?size=40`) and displayed when ready. The colored initial circle shows immediately as a placeholder. In the leaderboard HTML view, `<img>` tags load Clearbit logos directly.
