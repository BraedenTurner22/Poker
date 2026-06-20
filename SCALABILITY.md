# SCALABILITY.md — Horizontal Scaling Plan (Future Work)

**Status: not implemented.** The backend is intentionally single-instance
today. This document is the plan for *when* you need more than one
backend instance — read it fully before starting that work, since the
migration touches the same locking model that was just carefully
audited and fixed (see `AGENT.md` → "Poker engine" and
`LESSONS_LEARNED.md`).

## When to actually do this

Not yet. A single modern server instance comfortably handles many
concurrent 8-player tables — this is a lightweight, turn-based game, not
a heavy compute workload. Revisit this when you have **actual evidence**
of approaching one instance's real limits (CPU/memory pressure, request
queueing, measured WebSocket connection counts), not preemptively.
Also worth knowing: most free hosting tiers (Render/Fly.io free tier)
only give you one instance anyway — this work isn't even usable until
you're on a paid tier regardless of code readiness.

## The problem, concretely

Today, a single JVM instance owns:
1. **All lobby/game state in memory** — `LobbyManagerService`'s
   `lobbies`/`lobbyById` `ConcurrentHashMap`s, and every `Lobby` /
   `ConnectedPlayer` object reachable from them. Nothing about this
   state exists outside that one process's heap.
2. **The WebSocket pub/sub broker** — Spring's `enableSimpleBroker("/topic")`
   (`WebSocketConfig.java`) is in-memory and instance-local. A message
   published via `messagingTemplate.convertAndSend(...)` on instance A
   only reaches WebSocket clients currently connected *to instance A*.
3. **Every scheduled timer** (turn timeout, run-out steps, next-hand
   delay, disconnect grace period) — all `ScheduledExecutorService`
   instances living in that one JVM.

Run two instances behind a load balancer today, with zero other
changes, and two players seated at the same table can land on different
instances — they'd never see each other's actions, chat, or even know
the other is connected. This is the actual problem; "add more servers"
alone doesn't solve it.

## Target architecture

Two changes, both required together:

1. **External STOMP relay broker** (RabbitMQ, via Spring's
   `enableStompBrokerRelay` instead of `enableSimpleBroker`) so a
   message published on any instance reaches every subscribed client
   regardless of which instance they're connected to. This is the
   *easy* half — it's a Spring config change plus standing up a broker,
   no application-logic changes needed.

2. **Distributed lobby state + locking**, replacing the in-memory
   `ConcurrentHashMap`s and `synchronized(lobby)` locks with Redis:
   - **State**: `Lobby`/`ConnectedPlayer` (and everything reachable from
     them — `Pot`, hole cards, contribution maps) move from JVM heap
     into Redis, so any instance can load/serve any lobby.
   - **Locking**: `synchronized(lobby)` (a JVM-local intrinsic lock,
     meaningless across processes) becomes a **Redis distributed lock**
     — [Redisson](https://github.com/redisson/redisson)'s `RLock` is
     the standard Java client for this and has the closest semantics to
     what's there today (`lock.lock()` / `try-finally { lock.unlock() }`
     wrapping the same critical sections that are currently
     `synchronized(lobby) { ... }`).
   - **Registry**: the `registryLock`-guarded `lobbies`/`lobbyById` maps
     become Redis hashes/sets, guarded by their own Redis lock (same
     "never hold both the registry lock and a per-lobby lock at once"
     rule as today — see `AGENT.md`).

### Why not just sticky sessions?

A simpler-looking option is sticky sessions (route a client to the same
instance for the life of their session) without touching the state
model at all. **This doesn't actually work for this app**: sticky
sessions key off the *connecting client*, not the *lobby* — two
different players joining the same table have no reason to be routed to
the same instance by a generic load balancer. Making that work requires
either a lobby-aware routing layer (real infra, and you'd still need a
shared registry to know which instance owns which lobby — most of the
complexity of the Redis approach without the benefit of letting any
instance serve any request) or accepting that a table is pinned to
whichever instance created it forever, which reintroduces a
single-point-of-failure per table. The full Redis approach was chosen
specifically because, combined with a relay broker, *any* instance can
serve *any* client — no custom routing layer needed, which is simpler
infra overall despite more application-code change.

## Keep game *logic* decoupled from *storage*

This is the most important design constraint for the migration, and the
reason it's worth planning rather than improvising: **`PotManagerService`,
`WinnerService`, `AnalysisEngine`, and the betting rules in
`LobbyGameService` should keep operating on plain in-memory `Lobby`
objects**, not get Redis calls sprinkled directly into them. Structure
it as a repository/unit-of-work pattern instead:

```
acquire distributed lock for lobbyId
  → load Lobby from Redis (deserialize)
  → run today's exact in-memory logic against it, unchanged
  → save Lobby back to Redis (serialize)
release lock
```

Why this matters: `GameEngineTest.java` and `HandEvaluatorTest.java`
(41 tests, ~0.4s total) currently test the real engine with zero
infrastructure — no Spring context, no DB, no broker. That's extremely
valuable and should survive this migration. If Redis calls get woven
directly into `LobbyGameService`'s methods, those tests either need a
real/mocked Redis (slower, more brittle, infra-dependent) or stop
testing the real code path. Keeping the load → mutate → save shape
means the *mutate* step — all the actual poker logic — stays exactly as
testable as it is today; only a new, thin
load/lock/save wrapper layer needs infra-dependent testing.

## Serialization

`Lobby`/`ConnectedPlayer`/`Pot`/`Card`/etc. have never needed to leave
the JVM, so there's been no reason to think about serialization. Moving
to Redis requires picking a strategy:
- Simplest: Jackson JSON serialization of the domain objects directly.
  Watch for circular references (`Pot.playersInPot` holds
  `ConnectedPlayer`s, which don't currently hold a back-reference to
  their `Pot`, so this should be fine, but re-check after any future
  change) and non-serializable fields.
- `ConnectedPlayer.equals()/hashCode()` are already ID-based (not
  reference-based) as of the engine audit — this matters a lot here,
  since deserializing will produce *new* object instances on every
  load, and anything relying on reference equality would silently break
  the moment state round-trips through Redis. Confirm nothing new
  introduces reference-equality assumptions before this migration.

## Timer/scheduler ownership

Turn timers, run-out steps, and the disconnect grace period are
currently scheduled via `ScheduledExecutorService` inside the same JVM
that's handling the lobby. In a multi-instance world, whichever instance
processes a player's action needs to be the one whose timer eventually
fires for that same lobby — two instances racing to fire the same
table's turn timer would double-process it.

Simplest correct approach: a lobby's timers are owned by whichever
instance is currently *holding its Redis lock* at the moment a timer is
scheduled, and that instance re-acquires the lock when its own timer
fires (same as today's flow, just now lock-acquisition is distributed
instead of JVM-local). This avoids needing a separate distributed
scheduler — the existing `ScheduledExecutorService` pattern can stay,
it's just that "the lock" now spans processes.

## Suggested migration stages (incremental, not big-bang)

1. Add Redis + Redisson dependencies. Stand up a Redis instance (Upstash
   or Redis Cloud's free/cheap tiers are fine to start, or self-hosted).
2. Introduce `RLock`-based locking *without* yet moving state — i.e.
   prove the distributed-lock mechanics work correctly under the
   existing single-instance deployment first, as a lower-risk
   intermediate step.
3. Move the lobby registry (`lobbies`/`lobbyById`) to Redis.
4. Move `Lobby`/`ConnectedPlayer` state to Redis behind the
   load/lock/mutate/save wrapper described above, keeping
   `LobbyGameService`/`PotManagerService`/`WinnerService`'s actual logic
   untouched.
5. Swap `enableSimpleBroker` for `enableStompBrokerRelay` against
   RabbitMQ.
6. Only then actually run 2+ instances behind a load balancer, and load
   test before trusting it with real users.

At each stage, run `./mvnw test` — the 41 existing tests should keep
passing throughout if the logic/storage separation above is respected.

## Infra/cost implications

This is a genuinely larger and more expensive setup than today's
"one Spring Boot process + Supabase Postgres":
- A managed Redis instance (or self-hosted + ops burden)
- A managed RabbitMQ instance (or self-hosted + ops burden)
- A load balancer in front of 2+ backend instances
- All of the above cost real money on top of whatever you're already
  paying for compute — factor this in before committing to the timeline
  for this work, not just the engineering effort.
