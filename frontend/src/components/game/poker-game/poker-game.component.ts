import {
    Component, OnInit, OnDestroy, ElementRef, ViewChild, ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import Phaser from 'phaser';
import { Subscription } from 'rxjs';
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

    /** Short, badge-friendly form of the lobby ID — full UUID is shown on hover. */
    get shortLobbyId(): string {
        return this.lobbyId.length > 8 ? this.lobbyId.slice(0, 8) : this.lobbyId;
    }

    // ─── Action panel ────────────────────────────────────────────────────────────
    raiseAmount = 0;
    raiseMin    = 0;
    raiseMax    = 0;

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

    // ─── Inactivity kick ─────────────────────────────────────────────────────────
    kickMessage = '';

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
        this.myUserId = this.auth.getUser()!.id;

        const playResponse = (history.state as any)?.playResponse as PlayResponse | undefined;
        if (playResponse) {
            this.auth.setActiveLobby(playResponse.lobbyId);
            this.initFromPlayResponse(playResponse);
            return;
        }

        // No router state — likely a page reload. Try to recover the session
        // from whichever lobby we last remember joining instead of bouncing
        // straight to /play.
        const activeLobbyId = this.auth.getActiveLobby();
        if (!activeLobbyId) {
            this.router.navigate(['/play']);
            return;
        }

        this.auth.rejoinLobby(activeLobbyId).subscribe({
            next: r => this.initFromPlayResponse(r),
            error: () => {
                this.auth.clearActiveLobby();
                this.router.navigate(['/play']);
            },
        });
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
        EventBus.once('current-scene-ready', () => {
            EventBus.emit('set-player-identity', identity);
            EventBus.emit('game-state', this.latestGameState ?? restState);
            const priv = restPrivate ?? this.latestPrivateState;
            if (priv) EventBus.emit('private-state', priv);
        });

        const token = this.auth.getToken()!;
        this.ws.connect(playResponse.lobbyId, token, this.myUserId);

        this.subs.push(
            this.ws.gameState$.subscribe(state => {
                this.latestGameState = state;
                this.gameState = state;
                const already = this.myPlayer?.betThisRound ?? 0;
                this.raiseMin = state.currentBetAmount === 0
                    ? state.bigBlind
                    : state.currentBetAmount + state.lastRaiseSize;
                this.raiseMax = already + (this.myPlayer?.chips ?? 0);
                this.raiseAmount = Math.min(
                    Math.max(this.raiseAmount, this.raiseMin), this.raiseMax);
                this.runPendingAction(state);

                // Rebuy popup: show when hand ends and we have no chips
                if (state.phase === 'HAND_COMPLETE'
                    && (this.myPlayer?.chips ?? 1) === 0
                    && !this.showRebuyPopup) {
                    this.openRebuyPopup();
                }

                EventBus.emit('game-state', state);
                this.cdr.detectChanges();
            }),
            this.ws.privateState$.subscribe(p => {
                this.latestPrivateState = p;
                EventBus.emit('private-state', p);
            }),
            this.ws.chat$.subscribe(msg => {
                this.chatMessages = [...this.chatMessages.slice(-49), msg];
                this.cdr.detectChanges();
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
                this.auth.clearActiveLobby();
                this.kickMessage = 'You were removed from the table due to inactivity.';
                this.cdr.detectChanges();
                setTimeout(() => {
                    this.ws.disconnect();
                    this.router.navigate(['/play']);
                }, 4000);
            }),
        );
    }

    ngOnDestroy(): void {
        this.subs.forEach(s => s.unsubscribe());
        this.clearRebuyTimer();
        clearTimeout(this.chatErrorTimer);
        if (this.phaserWindowResizeListener) {
            window.removeEventListener('resize', this.phaserWindowResizeListener);
        }
        this.phaserGame?.destroy(true);
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
        const others = this.gameState.players.filter(p => p.seat !== this.mySeat);
        return others.some(p => p.state === 'ALL_IN')
            && others.every(p => p.state === 'FOLDED' || p.state === 'ALL_IN');
    }

    // ─── Actions ─────────────────────────────────────────────────────────────────

    fold():  void { this.ws.sendAction('FOLD',   0); }
    check(): void { this.ws.sendAction('CHECK',  0); }
    call():  void { this.ws.sendAction('CALL',   0); }
    raise(): void {
        const increment = this.raiseAmount - (this.gameState?.currentBetAmount ?? 0);
        this.ws.sendAction('RAISE', increment);
    }
    allIn(): void { this.ws.sendAction('ALL_IN', 0); }

    /** True when no one has bet yet this street — action is a bet, not a raise. */
    get isBet(): boolean { return (this.gameState?.currentBetAmount ?? 0) === 0; }

    get raiseValid(): boolean {
        return this.raiseAmount >= this.raiseMin && this.raiseAmount <= this.raiseMax;
    }

    clampRaise(): void {
        this.raiseAmount = Math.max(this.raiseMin, Math.min(this.raiseMax, this.raiseAmount || this.raiseMin));
    }

    // ─── Pre-selected actions ────────────────────────────────────────────────────

    get canPreSelect(): boolean {
        return !!this.gameState
            && !this.isMyTurn
            && this.gameState.phase !== 'WAITING'
            && this.gameState.phase !== 'HAND_COMPLETE'
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
        this.router.navigate(['/play']);
    }

    // ─── Rebuy ───────────────────────────────────────────────────────────────────

    private openRebuyPopup(): void {
        this.auth.getProfile().subscribe({
            next: p => {
                this.bankBalance    = p.chips;
                this.rebuyAmount    = Math.min(p.chips, 200);
                this.rebuyCountdown = 30;
                this.showRebuyPopup = true;
                this.cdr.detectChanges();
                this.startRebuyTimer();
            },
            error: () => {
                this.rebuyCountdown = 30;
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
        if (this.rebuyAmount <= 0 || this.rebuyAmount > this.bankBalance) return;
        this.clearRebuyTimer();
        this.auth.rebuy(this.lobbyId, this.rebuyAmount).subscribe({
            next: r => {
                this.bankBalance    = r.bankBalance;
                this.showRebuyPopup = false;
                this.statusMsg      = `Rebought $${r.added}. Bank: $${r.bankBalance}`;
                this.cdr.detectChanges();
            },
            error: e => {
                this.statusMsg = e.error?.error ?? 'Rebuy failed.';
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
        this.ws.sendChat(msg);
        this.chatInput = '';
    }

    chatEnter(event: KeyboardEvent): void {
        if (event.key === 'Enter') this.sendChat();
    }
}
