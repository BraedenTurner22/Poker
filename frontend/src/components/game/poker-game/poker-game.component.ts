import {
    Component, OnInit, OnDestroy, ElementRef, ViewChild, ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import Phaser from 'phaser';
import { Subscription, finalize } from 'rxjs';
import { PokerGameScene } from '../scenes/PokerGameScene';
import { EventBus } from '../EventBus';
import { AuthService } from '../../../services/auth.service';
import { WebSocketService } from '../../../services/websocket.service';
import {
    GameStateDto, PrivateStateDto, ChatBroadcast, PlayResponse,
} from '../../../types/game.types';

@Component({
    selector: 'app-poker-game',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './poker-game.component.html',
    styleUrls: ['./poker-game.component.css'],
})
export class PokerGameComponent implements OnInit, OnDestroy {
    @ViewChild('gameContainer', { static: true }) gameContainer!: ElementRef;
    @ViewChild('chatScroll') chatScroll?: ElementRef<HTMLElement>;

    // ─── Phaser ──────────────────────────────────────────────────────────────────
    private phaserGame!: Phaser.Game;
    private phaserWindowResizeListener?: () => void;

    // ─── State ───────────────────────────────────────────────────────────────────
    gameState: GameStateDto | null = null;
    myUserId = -1;
    mySeat   = -1;
    lobbyId  = '';
    tableBuyIn = 0;
    connected = false;
    connectingMsg = 'Joining the table…';
    private connectingTimeoutWarning?: ReturnType<typeof setTimeout>;

    /** Short, badge-friendly form of the lobby ID — full UUID is shown on hover. */
    get shortLobbyId(): string {
        return this.lobbyId.length > 8 ? this.lobbyId.slice(0, 8) : this.lobbyId;
    }

    // ─── Action panel ────────────────────────────────────────────────────────────
    raiseAmount = 0;
    raiseMin    = 0;
    raiseMax    = 0;
    /** Set once an action is sent; cleared when the next state arrives. Blocks double-submits. */
    actionInFlight = false;
    private wasMyTurn = false;
    /** Phase of the last state seen on our turn — a new street is a new turn. */
    private myTurnPhase = '';

    // ─── Pre-selected action ─────────────────────────────────────────────────────
    pendingAction: 'FOLD' | 'CHECK_FOLD' | null = null;

    // ─── Chat ────────────────────────────────────────────────────────────────────
    chatMessages: ChatBroadcast[] = [];
    chatInput = '';
    chatCollapsed = false;
    chatError = '';
    private chatErrorTimer?: ReturnType<typeof setTimeout>;

    // ─── Rebuy popup ─────────────────────────────────────────────────────────────
    showRebuyPopup  = false;
    rebuyAmount     = 0;
    bankBalance     = 0;
    rebuyCountdown  = 30;
    private rebuyTimer?: ReturnType<typeof setInterval>;
    /** True while the profile fetch that opens the popup is in flight. */
    private rebuyOpening = false;
    /** Shown inside the popup — the status bar sits underneath its overlay. */
    rebuyError = '';

    /** The server caps a stack at 100 big blinds — don't offer more than it will add. */
    get rebuyMax(): number {
        const bb = this.gameState?.bigBlind ?? 0;
        if (bb <= 0) return this.bankBalance;
        const room = Math.max(0, 100 * bb - (this.myPlayer?.chips ?? 0));
        return Math.min(this.bankBalance, room);
    }

    // ─── Inactivity kick ─────────────────────────────────────────────────────────
    kickMessage = '';
    kickTitle   = 'Removed for Inactivity';
    private kickTimer?: ReturnType<typeof setTimeout>;
    private sceneReadyHandler?: () => void;

    statusMsg = '';

    // ─── Buffered WS state (used to re-seed Phaser if scene wasn't ready yet) ───
    private latestGameState:    GameStateDto    | null = null;
    private latestPrivateState: PrivateStateDto | null = null;

    private subs: Subscription[] = [];

    constructor(
        private auth: AuthService,
        private ws: WebSocketService,
        private router: Router,
        private cdr: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        const user = this.auth.getUser();
        if (!user) {
            this.router.navigate(['/']);
            return;
        }
        this.myUserId = user.id;

        // The canvas only appears once Phaser's scene is ready and the first
        // game state is seeded — until then show a connecting overlay instead
        // of a blank black screen, and call out a cold-start backend after a
        // few seconds so a slow load doesn't look like it's just stuck.
        this.connectingTimeoutWarning = setTimeout(() => {
            this.connectingMsg = 'Still connecting — the server may be waking up from idle, this can take up to a minute…';
            this.cdr.detectChanges();
        }, 6000);

        const playResponse = (history.state as any)?.playResponse as PlayResponse | undefined;
        if (playResponse) {
            // The browser keeps history.state across reloads and back/forward
            // navigation, so this snapshot would otherwise be reused stale —
            // e.g. pressing Back after leaving would re-enter a lobby we're no
            // longer seated in. Consume it so those paths go through rejoin.
            history.replaceState({ ...history.state, playResponse: undefined }, '');
            this.auth.setActiveLobby(playResponse.lobbyId);
            this.initFromPlayResponse(playResponse);
            return;
        }

        // No router state — likely a page reload. Try to recover the session
        // from whichever lobby we last remember joining instead of bouncing
        // straight to /play.
        const activeLobbyId = this.auth.getActiveLobby();
        if (!activeLobbyId) {
            clearTimeout(this.connectingTimeoutWarning);
            this.router.navigate(['/play']);
            return;
        }

        this.subs.push(this.auth.rejoinLobby(activeLobbyId).subscribe({
            next: r => this.initFromPlayResponse(r),
            error: () => {
                clearTimeout(this.connectingTimeoutWarning);
                this.auth.clearActiveLobby();
                this.router.navigate(['/play']);
            },
        }));
    }

    private initFromPlayResponse(playResponse: PlayResponse): void {
        this.mySeat   = playResponse.seatNumber;
        this.lobbyId  = playResponse.lobbyId;
        this.tableBuyIn = playResponse.buyInAmount;

        this.startPhaser();

        // Emit identity + seed the initial state once the scene's create() has run.
        // This avoids the STOMP subscribe/send race condition — the REST response
        // already carries the current lobby snapshot, so we don't depend on the
        // WebSocket JOIN broadcast arriving after the subscription is registered.
        const identity    = { userId: this.myUserId, seat: this.mySeat };
        const restState   = playResponse.initialState;
        const restPrivate = playResponse.initialPrivateState;
        // Seed the scene once it is ready.  We prefer the freshest available
        // state: if a WebSocket broadcast arrived before create() finished we
        // use that; otherwise we fall back to the REST-response snapshot.
        this.sceneReadyHandler = () => {
            this.sceneReadyHandler = undefined;
            EventBus.emit('set-player-identity', identity);
            // Also seeds the action panel / rebuy logic, not just the scene.
            this.onGameState(this.latestGameState ?? restState);
            // Prefer a WS private state that beat the scene to it — it is newer
            // than the REST snapshot (which may be from the previous hand).
            const priv = this.latestPrivateState ?? restPrivate;
            if (priv) EventBus.emit('private-state', priv);

            clearTimeout(this.connectingTimeoutWarning);
            this.connected = true;
            this.cdr.detectChanges();
        };
        EventBus.once('current-scene-ready', this.sceneReadyHandler);

        const token = this.auth.getToken()!;
        this.ws.connect(playResponse.lobbyId, token, this.myUserId);

        this.subs.push(
            this.ws.gameState$.subscribe(state => {
                this.latestGameState = state;
                this.onGameState(state);
            }),
            this.ws.privateState$.subscribe(p => {
                this.latestPrivateState = p;
                EventBus.emit('private-state', p);
            }),
            this.ws.chat$.subscribe(msg => {
                const el = this.chatScroll?.nativeElement;
                const atBottom = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 24;
                this.chatMessages = [...this.chatMessages.slice(-49), msg];
                this.cdr.detectChanges();
                // Keep the newest message in view unless the user scrolled up to read history.
                if (el && atBottom) el.scrollTop = el.scrollHeight;
            }),
            this.ws.chatBlocked$.subscribe(reason => {
                this.chatError = reason;
                this.cdr.detectChanges();
                clearTimeout(this.chatErrorTimer);
                this.chatErrorTimer = setTimeout(() => {
                    this.chatError = '';
                    this.cdr.detectChanges();
                }, 4000);
            }),
            this.ws.kicked$.subscribe(() => {
                this.removedFromTable('Removed for Inactivity',
                    'You were removed from the table due to inactivity.');
            }),
            // The only per-user errors are join failures (lobby gone / not seated)
            this.ws.error$.subscribe(error => {
                if (!this.kickMessage) this.removedFromTable('No Longer Seated', error);
            }),
            // The server refuses CONNECT with an expired/invalid token; without this
            // the client would silently retry every 5s forever.
            this.ws.stompError$.subscribe(message => {
                if (this.kickMessage) return;
                if (!this.auth.isLoggedIn() || /token|authoriz|authenticat/i.test(message)) {
                    this.sessionExpired();
                }
            }),
        );
    }

    private onGameState(state: GameStateDto): void {
        this.gameState = state;
        // Any new state means the server has processed (or ignored) whatever we sent.
        this.actionInFlight = false;

        // Our seat is gone (e.g. evicted after the disconnect grace period, then
        // the STOMP client auto-reconnected) — don't leave the user on a ghost table.
        if (!state.players.some(p => p.userId === this.myUserId)) {
            if (!this.kickMessage) {
                this.removedFromTable('No Longer Seated',
                    'You are no longer seated at this table.');
            }
            return;
        }

        const already = this.myPlayer?.betThisRound ?? 0;
        this.raiseMin = state.currentBetAmount === 0
            ? state.bigBlind
            : state.currentBetAmount + state.lastRaiseSize;
        this.raiseMax = already + (this.myPlayer?.chips ?? 0);
        const myTurn = this.isMyTurn;
        // Start each new turn at the minimum raise rather than whatever was
        // left on the slider from a previous street/hand. Acting last on one
        // street and first on the next (e.g. the big blind checking its option,
        // heads-up or after the small blind folds) never leaves our turn, so
        // a street change counts as a new turn too.
        const newTurn = myTurn && (!this.wasMyTurn || state.phase !== this.myTurnPhase);
        const base = newTurn ? this.raiseMin : this.raiseAmount;
        this.wasMyTurn = myTurn;
        this.myTurnPhase = myTurn ? state.phase : '';
        this.raiseAmount = Math.min(Math.max(base, this.raiseMin), this.raiseMax);
        this.runPendingAction(state);

        // Rebuy popup: show once we're out of chips and not live in a hand —
        // at hand end, or while sitting out (a busted player is FOLDED from the
        // next deal on). Checking only HAND_COMPLETE never re-offered it after
        // a reload/reconnect, stranding the player at 0 chips with no way out.
        const me = this.myPlayer;
        if (me && me.chips === 0
            && (state.phase === 'HAND_COMPLETE' || state.phase === 'WAITING' || me.state === 'FOLDED')
            && !this.showRebuyPopup && !this.rebuyOpening && !this.kickMessage) {
            this.openRebuyPopup();
        } else if (me && me.chips > 0 && this.showRebuyPopup) {
            // Chips arrived (e.g. a rebuy whose state beat a stale broadcast) — not out anymore.
            this.clearRebuyTimer();
            this.showRebuyPopup = false;
        }

        EventBus.emit('game-state', state);
        this.cdr.detectChanges();
    }

    /** The JWT was rejected (expired mid-game) — nothing else will work until they sign in. */
    private sessionExpired(): void {
        this.auth.logout();
        this.removedFromTable('Session Expired', 'Please sign in again.');
    }

    private removedFromTable(title: string, message: string): void {
        this.auth.clearActiveLobby();
        this.clearRebuyTimer();
        this.showRebuyPopup = false;
        this.kickTitle   = title;
        this.kickMessage = message;
        this.cdr.detectChanges();
        clearTimeout(this.kickTimer);
        this.kickTimer = setTimeout(() => {
            this.ws.disconnect();
            this.router.navigate(['/play']);
        }, 4000);
    }

    ngOnDestroy(): void {
        this.subs.forEach(s => s.unsubscribe());
        this.clearRebuyTimer();
        clearTimeout(this.chatErrorTimer);
        clearTimeout(this.connectingTimeoutWarning);
        clearTimeout(this.kickTimer);
        if (this.sceneReadyHandler) {
            EventBus.off('current-scene-ready', this.sceneReadyHandler);
            this.sceneReadyHandler = undefined;
        }
        if (this.phaserWindowResizeListener) {
            window.removeEventListener('resize', this.phaserWindowResizeListener);
        }
        this.phaserGame?.destroy(true);
        if ((window as any).pokerGame === this.phaserGame) delete (window as any).pokerGame;
        this.ws.disconnect();
    }

    // ─── Phaser init ─────────────────────────────────────────────────────────────

    private startPhaser(): void {
        // HTML/CSS is natively DPR-aware; a plain canvas is not — the browser upscales it
        // on Retina/HiDPI displays unless canvas.width > canvas.clientWidth.
        // Fix: use NONE scale mode so Phaser owns no auto-resizing, create the canvas at
        // physical pixel dimensions (cssSize × dpr), then pin the CSS display size back to
        // CSS pixels.  The scene's coordinate space is in physical pixels, so s already
        // scales correctly — everything looks the same size on screen but renders sharp.
        const dpr      = Math.min(window.devicePixelRatio || 1, 2);
        const cssW     = window.innerWidth;
        const cssH     = window.innerHeight;
        const physW    = Math.floor(cssW * dpr);
        const physH    = Math.floor(cssH * dpr);

        this.phaserGame = new Phaser.Game({
            type: Phaser.AUTO,
            parent: this.gameContainer.nativeElement,
            scene: [PokerGameScene],
            backgroundColor: '#0d1b2a',
            // mipmapFilter only kicks in for power-of-two textures (e.g. the 128x128
            // college logos) — lets WebGL downsample them cleanly instead of a single
            // blurry bilinear sample when shrunk to the small in-game badge size.
            render: { antialias: true, roundPixels: true, mipmapFilter: 'LINEAR_MIPMAP_LINEAR' },
            width: physW,
            height: physH,
            scale: { mode: Phaser.Scale.NONE },
        });

        // NONE mode leaves canvas CSS width = canvas.width (physical pixels).
        // Override to CSS pixels so the browser renders at native resolution.
        const canvas = this.phaserGame.canvas;
        canvas.style.width  = cssW + 'px';
        canvas.style.height = cssH + 'px';

        const onResize = () => {
            const w = window.innerWidth;
            const h = window.innerHeight;
            // Re-read the DPR: browser zoom and moving to another monitor change it
            // (and fire resize) — a stale value leaves the canvas blurry or oversized.
            const dpr = Math.min(window.devicePixelRatio || 1, 2);
            this.phaserGame.scale.resize(Math.floor(w * dpr), Math.floor(h * dpr));
            this.phaserGame.canvas.style.width  = w + 'px';
            this.phaserGame.canvas.style.height = h + 'px';
        };
        window.addEventListener('resize', onResize);
        this.phaserWindowResizeListener = onResize;

        (window as any).pokerGame = this.phaserGame;
    }

    // ─── Computed state helpers ──────────────────────────────────────────────────

    get isMyTurn(): boolean {
        return !!this.gameState
            && this.gameState.currentPlayerSeat === this.mySeat
            && this.gameState.phase !== 'WAITING'
            && this.gameState.phase !== 'HAND_COMPLETE';
    }

    get myPlayer() {
        return this.gameState?.players.find(p => p.seat === this.mySeat) ?? null;
    }

    get callAmount(): number {
        if (!this.gameState) return 0;
        const already = this.myPlayer?.betThisRound ?? 0;
        const toCall = Math.max(0, this.gameState.currentBetAmount - already);
        return Math.min(toCall, this.myPlayer?.chips ?? 0);
    }

    get isCallAllIn(): boolean {
        if (!this.gameState || !this.myPlayer) return false;
        const already = this.myPlayer.betThisRound ?? 0;
        const toCall = Math.max(0, this.gameState.currentBetAmount - already);
        return toCall > 0 && toCall >= (this.myPlayer.chips ?? 0);
    }

    get canCheck(): boolean { return this.callAmount === 0; }

    /** True when every other live player is all-in — raising is pointless. */
    get canOnlyCall(): boolean {
        if (!this.gameState) return false;
        // A short all-in didn't reopen raising for a seat that already acted
        if (this.gameState.raiseClosedSeats?.includes(this.mySeat)) return true;
        const others = this.gameState.players.filter(p => p.seat !== this.mySeat);
        return others.some(p => p.state === 'ALL_IN')
            && others.every(p => p.state === 'FOLDED' || p.state === 'ALL_IN');
    }

    // ─── Actions ─────────────────────────────────────────────────────────────────

    fold():  void { this.send('FOLD',   0); }
    check(): void { this.send('CHECK',  0); }
    call():  void { this.send('CALL',   0); }
    raise(): void {
        if (!this.raiseValid) return;
        // Backend expects the increment over the current bet ("raise by");
        // the slider holds the total "raise to" amount.
        const increment = this.raiseAmount - (this.gameState?.currentBetAmount ?? 0);
        this.send('RAISE', increment);
    }
    allIn(): void { this.send('ALL_IN', 0); }

    private send(action: string, amount: number): void {
        if (this.actionInFlight || !this.isMyTurn) return;
        if (this.ws.sendAction(action, amount)) this.actionInFlight = true;
    }

    /** True when no one has bet yet this street — action is a bet, not a raise. */
    get isBet(): boolean { return (this.gameState?.currentBetAmount ?? 0) === 0; }

    get raiseValid(): boolean {
        return this.raiseAmount >= this.raiseMin && this.raiseAmount <= this.raiseMax;
    }

    clampRaise(): void {
        this.raiseAmount = Math.max(this.raiseMin,
            Math.min(this.raiseMax, Math.floor(this.raiseAmount || this.raiseMin)));
    }

    // ─── Pre-selected actions ────────────────────────────────────────────────────

    get canPreSelect(): boolean {
        return !!this.gameState
            && !this.isMyTurn
            && this.gameState.phase !== 'WAITING'
            && this.gameState.phase !== 'HAND_COMPLETE'
            // -1 = all-in run-out: no one acts again this hand
            && this.gameState.currentPlayerSeat !== -1
            && this.myPlayer?.state === 'ACTIVE';
    }

    togglePending(action: 'FOLD' | 'CHECK_FOLD'): void {
        this.pendingAction = this.pendingAction === action ? null : action;
    }

    private runPendingAction(state: GameStateDto): void {
        if (state.phase === 'WAITING' || state.phase === 'HAND_COMPLETE') {
            this.pendingAction = null;
            return;
        }
        if (!this.pendingAction || !this.isMyTurn) return;
        const action = this.pendingAction;
        this.pendingAction = null;
        if (action === 'FOLD') this.fold();
        else this.canCheck ? this.check() : this.fold();
    }

    // ─── Leave table ─────────────────────────────────────────────────────────────

    leaveTable(): void {
        this.auth.clearActiveLobby();
        this.ws.sendLeave();
        this.ws.disconnect();
        this.subs.forEach(s => s.unsubscribe());
        this.subs = [];
        EventBus.emit('reset-table');
        // Cash-out happens server-side after this STOMP leave; /play re-reads
        // the bank shortly after so it doesn't show the pre-cash-out balance.
        this.router.navigate(['/play'], { state: { leftTable: true } });
    }

    // ─── Rebuy ───────────────────────────────────────────────────────────────────

    private openRebuyPopup(): void {
        this.rebuyOpening = true;
        this.auth.getProfile().pipe(finalize(() => this.rebuyOpening = false)).subscribe({
            next: p => {
                if (this.kickMessage || (this.myPlayer?.chips ?? 0) > 0) return;
                this.bankBalance    = p.chips;
                this.rebuyAmount    = Math.min(this.rebuyMax, 200);
                this.rebuyCountdown = 30;
                this.rebuyError     = '';
                this.showRebuyPopup = true;
                this.cdr.detectChanges();
                this.startRebuyTimer();
            },
            error: e => {
                if (AuthService.isAuthError(e)) { this.sessionExpired(); return; }
                if (this.kickMessage || (this.myPlayer?.chips ?? 0) > 0) return;
                this.rebuyCountdown = 30;
                this.rebuyError     = '';
                this.showRebuyPopup = true;
                this.cdr.detectChanges();
                this.startRebuyTimer();
            },
        });
    }

    private startRebuyTimer(): void {
        this.clearRebuyTimer();
        this.rebuyTimer = setInterval(() => {
            this.rebuyCountdown--;
            if (this.rebuyCountdown <= 0) {
                this.clearRebuyTimer();
                this.declineRebuy();
            }
            this.cdr.detectChanges();
        }, 1000);
    }

    private clearRebuyTimer(): void {
        if (this.rebuyTimer) {
            clearInterval(this.rebuyTimer);
            this.rebuyTimer = undefined;
        }
    }

    confirmRebuy(): void {
        if (this.rebuyAmount <= 0 || this.rebuyAmount > this.rebuyMax) return;
        this.clearRebuyTimer();
        this.auth.rebuy(this.lobbyId, this.rebuyAmount).subscribe({
            next: r => {
                this.bankBalance    = r.bankBalance;
                this.showRebuyPopup = false;
                this.statusMsg      = `Rebought $${r.added}. Bank: $${r.bankBalance}`;
                this.cdr.detectChanges();
            },
            error: e => {
                if (AuthService.isAuthError(e)) { this.sessionExpired(); return; }
                this.statusMsg = e.error?.error ?? 'Rebuy failed.';
                this.rebuyError = this.statusMsg;
                // The countdown was stopped before the request — resume it so the
                // popup can't hang open forever with a frozen timer.
                this.startRebuyTimer();
                this.cdr.detectChanges();
            },
        });
    }

    declineRebuy(): void {
        this.clearRebuyTimer();
        this.showRebuyPopup = false;
        this.leaveTable();
    }

    // ─── Chat ────────────────────────────────────────────────────────────────────

    sendChat(): void {
        const msg = this.chatInput.trim();
        if (!msg) return;
        // Keep the text if it wasn't sent (socket reconnecting) instead of dropping it
        if (!this.ws.sendChat(msg)) {
            this.chatError = 'Reconnecting — message not sent.';
            clearTimeout(this.chatErrorTimer);
            this.chatErrorTimer = setTimeout(() => {
                this.chatError = '';
                this.cdr.detectChanges();
            }, 4000);
            return;
        }
        this.chatInput = '';
    }

    chatEnter(event: KeyboardEvent): void {
        if (event.key === 'Enter') this.sendChat();
    }
}
