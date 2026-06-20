# LESSONS_LEARNED.md — College Showdown

Gotchas, mistakes, and corrections accumulated across development
sessions. Read the relevant section before touching the area it covers
— each of these cost real time to figure out once; no reason to pay
that cost twice. Referenced from `AGENT.md` — keep both in sync when
something new earns a place here.

---

## Phaser / rendering

### `resolution` config does nothing — use `Scale.NONE` + physical pixels for Retina sharpness

Phaser 3.88.2's deprecated `resolution` root config property is a no-op
in `RESIZE` mode — `ScaleManager` always sets `canvas.width =
baseSize.width` (CSS pixels, no DPR multiplier). The canvas renders at
CSS resolution and the browser upscales it on Retina displays, causing
blur. A canvas is only DPR-aware if `canvas.width > canvas.clientWidth`.

**Fix:** `Scale.NONE` with physical pixel dimensions
(`window.innerWidth * dpr`), then immediately pin the canvas's CSS size
back to CSS pixels. Add a `window.resize` listener that calls
`scale.resize(physW, physH)` and re-pins CSS on resize; clean it up in
`ngOnDestroy`. See `poker-game.component.ts` `startPhaser()` for the
reference implementation. Don't reach for `resolution` or `zoom` —
neither does what you'd expect here.

### Small in-game textures (school logos) looked blurry — WebGL mipmap requirement

Logos rendered crisp in the leaderboard (plain HTML `<img>`, browsers
downsample those fine) but blurry as small ~30px badges inside the
Phaser canvas. Root cause: WebGL can only generate mipmaps for
power-of-two texture dimensions. The source PNGs were 150×150 (not
POT) — minifying them ~5-10x with no mipmap falls back to a single
blurry bilinear sample.

**Fix applied:** resized all logos to 128×128 (POT) and set
`mipmapFilter: 'LINEAR_MIPMAP_LINEAR'` in the Phaser game's `render`
config.

**The fix didn't fully work the first time** — there was a leftover
`texture.setFilter(Phaser.Textures.FilterMode.LINEAR)` call right after
the logo finished loading (`tryLoadLogo()` in `PokerGameScene.ts`).
`Texture.setFilter()` sets the GL min/mag filter to flat `LINEAR` with
**no mipmap variant**, silently overriding the mipmap filter the
texture manager had just auto-applied based on the game config. Lesson:
when debugging "I set X in config but it's not taking effect," check
for a later imperative call that resets the same GPU/texture state —
config and imperative calls fight silently, no error, no warning.

New logos: 128×128, transparency flattened to white (not left
transparent — composite over a white background first, e.g. Pillow's
`Image.alpha_composite`), placed in `assets/college_logos/`.

---

## Auth / Google Identity Services

### Headless/automated browser testing of Google sign-in gives false negatives

Tested the "Continue with Google" button via headless Playwright;
console showed `[GSI_LOGGER]: The given origin is not allowed for the
given client ID` for ~40+ minutes straight, even after the user
triple-verified the Google Cloud Console config was exactly correct
(right client ID, exact origin match, confirmed persisted after a hard
refresh of the console page, confirmed only one client existed). The
moment the user tested in their own real Chrome browser, it worked
immediately, on the first try.

**Why:** Google appears to treat automated/headless browser sessions
differently for bot-detection purposes, producing an origin-mismatch-
looking error unrelated to the actual Authorized JavaScript Origins
config.

**How to apply:** never trust a headless-browser check as the source of
truth for "is Google sign-in broken." If config has been verified
correct (double-check exact origin string, no trailing slash, right
client, actually saved), ask the user to test in their own real browser
*before* spending more time on Cloud Console config or waiting out a
hypothetical propagation delay. We burned a long async wait-and-poll
loop on this before pivoting to "just ask the user to click it
themselves" — should have been the second thing tried, not the last.

### Google Cloud "Authorized JavaScript origins" can propagate slowly

Unlike most Cloud Console changes, this specific one was observed
taking 40+ minutes to take effect once — though in that case the real
problem turned out to be the headless-testing false-negative above, not
actual propagation delay. Don't assume it's propagation without first
ruling out the headless-testing issue.

---

## Backend / data model

### `ddl-auto=update` is additive-only — never drops or renames anything

Hibernate's `ddl-auto=update` will add new columns, but it will
**never** drop a column, drop a constraint, or rename a column. This
project has no Flyway/Liquibase — every schema change goes through this
mechanism, so its limits matter:

- Adding a `NOT NULL` column requires a `columnDefinition` with a
  `DEFAULT` clause, or Postgres rejects the migration (existing rows
  would get `NULL`).
- Removing a constraint that's no longer wanted (e.g. dropping
  `UNIQUE` on what's now a real-name column, not a chosen username)
  requires a manual fixup — see `SchemaFixupRunner.java` for the
  pattern: a `CommandLineRunner` that queries
  `information_schema.table_constraints`/`key_column_usage` for the
  constraint and drops it if found, idempotent so it's safe to leave
  running on every boot.
- Renaming a column isn't worth fighting — `PlayerRecord.name` maps to
  a DB column still physically called `username`
  (`@Column(name = "username")`) rather than attempting a rename. Document the mismatch at the field declaration so it doesn't read as a bug.

### `mvn compile` doesn't catch test-source rot

A `PlayerRecord` constructor was deleted as part of the username→name
rename. `./mvnw compile` (which only compiles `src/main`) reported
success the entire time — the test sources in `src/test`, which still
called the deleted 5-arg constructor, were never compiled and the
breakage went undetected for the rest of that session. Only running
`./mvnw test` (or `test-compile`) surfaces this. **Run the full test
suite, not just a compile check, after any change to a constructor,
method signature, or field that test helper code might reference** —
especially after a rename/refactor pass.

---

## Concurrency / correctness

### Two services each locking on their own singleton ≠ safe for shared mutable state

`LobbyGameService` and `LobbyManagerService` each used
`synchronized(this)` for their own methods — two *different* lock
objects, both mutating the same `Lobby`/`ConnectedPlayer` instances.
Safe within each service's own methods, but nothing prevented a thread
inside `LobbyGameService.processPlayerActionCore` from running
concurrently with a thread inside `LobbyManagerService.leaveTable` on
the *same* lobby. Fixed by synchronizing on the shared `Lobby` instance
itself in both services instead of on either service's own singleton —
see the "Poker engine — concurrency model" section of `AGENT.md`.

General rule: if two classes both mutate the same shared object, the
lock has to be **on that object** (or a lock explicitly associated with
it), not on either caller's own `this`. Locking on `this` only ever
protects against concurrent calls *into that one object* — it says
nothing about a different object touching the same shared state through
a different door.

### Before keeping an elaborate fix, trace how the value is actually consumed

While fixing the all-in betting logic, a "partial reopen" was built for
incomplete all-in raises — selectively un-marking only the seats that
owed more chips, instead of resetting every active player's
acted-status. It looked like a real improvement over a blanket reset.

Before finalizing, the only consumer of that acted-status
(`isBettingRoundComplete`, which only ever checks `ACTIVE` players) was
traced, and an invariant fell out: an active player's round
contribution can never exceed `currentBetAmount` (the betting rules
guarantee it — you only ever call up to the current bet, never beyond,
unless you're the one raising it). That invariant means "selectively
un-mark players below the new bet" and "reset everyone" produce
*identical* results for every active player, every time. The elaborate
version was dead-weight complexity with zero behavioral difference from
the simple one — reverted to the simpler version with a comment
explaining why.

**How to apply:** before keeping a more complex fix over a simpler one,
explicitly trace every place that reads the value/state being changed.
If an invariant elsewhere in the code makes two approaches produce the
same observable result in every reachable case, prefer the simpler one
— and say so in a comment, since the next person will otherwise assume
the complexity is load-bearing.

### Disconnect/cleanup paths need the same rigor as the happy path

The original "WebSocket disconnect → instantly fold + remove seat"
logic didn't distinguish an `ACTIVE` player (correctly handled — they
lose nothing beyond what they already wagered) from an `ALL_IN` player
(incorrectly folded out, stripping their pot equity, then cashed out
for `$0` since their stake was in the pot, not their stack). Real-time
multiplayer disconnect/cleanup code is exactly as load-bearing as the
main action-processing path and deserves the same scrutiny — it's easy
to write the happy path carefully and then bolt on cleanup as an
afterthought.

---

## Frontend layout

### A parent's `overflow: hidden` can silently swallow a child's scrollable content

`AppComponent`'s `:host` has `overflow: hidden; height: 100vh` (to
avoid a second native page-level scrollbar). Two different child-page
patterns existed for making a *routed* page scrollable:

- **Works:** `height: 100vh; overflow-y: auto` directly on the child's
  own `:host` — this creates the child's own internal scroll region,
  sized exactly to fit inside the parent, independent of the parent's
  `overflow: hidden`.
- **Silently broken:** `min-height: 100vh` on the child's `:host`,
  relying on the *page itself* growing taller and the *browser*
  scrolling it natively. The parent's `overflow: hidden` blocks that
  outer scroll entirely — content past the first screen becomes
  completely unreachable, with no scrollbar and no error of any kind.

This wasn't caught by "does the page look cut off" — it had to be
caught by literally dispatching a real mouse-wheel event (Playwright's
`mouse.wheel()`, not a programmatic `el.scrollTop = ...` assignment,
which bypasses the exact mechanism a real user relies on) and checking
whether `scrollTop` actually moved. **When auditing "is this scrollable," test with a real wheel event, not direct DOM scrollTop assignment** — the latter can succeed even when the former (what a user actually does) would not.

Every top-level routed component should use the `height:100vh +
overflow-y:auto` pattern on its own `:host`, not rely on page-level
scroll.

---

## Process / tooling

### A security-review skill's diff can look stale/wrong if the project's last commit predates a long working session

Invoked a `/security-review`-style skill; the diff it produced referenced
classes and methods (`School`, `queuedPlayersForSchools`,
`fillLobby()`) that don't exist anywhere in the current codebase — first
instinct was that the skill had picked up a stale or fabricated diff.
Checking `git log`/`git status` directly showed the diff was **real** —
the repository's last actual commit was many months old, and an entire
long session's worth of work was sitting uncommitted on top of it. The
skill was correctly diffing committed HEAD against the working tree;
the apparent "staleness" was really just the enormous, never-committed
gap, not a tooling bug.

**How to apply:** before assuming a diff/tool-result is stale, wrong, or
injected, check `git log` and `git status` directly. A diff that looks
unrecognizable can simply mean the git history is far behind the actual
working tree — which is itself worth surfacing to the user (uncommitted
work of that size is a real risk), not a reason to distrust the tool.

### Don't introduce a new convention (port, env var, etc.) without checking the project's existing one first

The backend's CORS allowlist was configured to default to
`http://localhost:4300` — a port that had been used ad hoc throughout a
testing session for convenience, without checking that the project's
actual configured Angular CLI default (`angular.json`'s `serve.options.port`)
was `4200`. Had the user run the documented `npm run dev` workflow
as-is, CORS would have rejected it. Fixed by defaulting to both ports.
**When hardcoding a "sensible default" for config, grep for whether the
project already has one rather than assuming the value used during
ad hoc testing is the project's real convention.**
