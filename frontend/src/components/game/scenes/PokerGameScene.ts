import Phaser from 'phaser';
import { EventBus } from '../EventBus';
import {
    GameStateDto, PrivateStateDto, PlayerStateDto, CardDto,
    cardTextureKey,
} from '../../../types/game.types';
import { collegeLogo } from '../../../types/college-logos';

// ─── Layout constants ──────────────────────────────────────────────────────────

const BASE_W = 1280;
const BASE_H = 720;

const CARD_SCALE       = 0.278;
const BOARD_CARD_SCALE = 0.299;
const BOARD_W = Math.round(234 * BOARD_CARD_SCALE);
const BOARD_H = Math.round(333 * BOARD_CARD_SCALE);

/** Seat positions as fractions of the canvas (slot 0 = local player, bottom-center). */
const SEAT_FRAC: { x: number; y: number }[] = [
    { x: 0.500, y: 0.822 },
    { x: 0.211, y: 0.740 },
    { x: 0.095, y: 0.480 },
    { x: 0.195, y: 0.220 },
    { x: 0.500, y: 0.170 },
    { x: 0.805, y: 0.220 },
    { x: 0.905, y: 0.480 },
    { x: 0.789, y: 0.740 },
];

/** Fractional table-center used for bet label positioning. */
const TABLE_CX = 0.500;
const TABLE_CY = 0.472;

const PANEL_W = 174;
const PANEL_H = 80;
const PANEL_RADIUS = 16;

// Distance from panel center to bet label.
// Card top edge sits at 56 + 46 = 102 design units above the seat center;
// using 140 gives ~38px clearance after the label half-height (~12px).
const BET_CLEAR_DIST = 140;

const TEXT_RES = Math.min(window.devicePixelRatio || 1, 3) * 2;
const FONT = 'Inter, "Segoe UI", system-ui, Arial, sans-serif';

/** Must match the server's TURN_TIMEOUT_SECONDS. */
const TURN_MS = 30000;
/** Room for the name between the logo and the panel's right edge (design units). */
const NAME_MAX_W = 116;

const SUITS = ['clubs', 'diamonds', 'hearts', 'spades'];
const RANKS = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'ace', 'jack', 'queen', 'king'];

// ─── Per-seat display structure ────────────────────────────────────────────────

interface SeatDisplay {
    container:     Phaser.GameObjects.Container;
    panel:         Phaser.GameObjects.Graphics;
    logoImage:     Phaser.GameObjects.Image;
    usernameText:  Phaser.GameObjects.Text;
    chipsText:     Phaser.GameObjects.Text;
    stateText:     Phaser.GameObjects.Text;
    badgeGraphics: Phaser.GameObjects.Graphics;
    badgeText:     Phaser.GameObjects.Text;
    card1:         Phaser.GameObjects.Image;
    card2:         Phaser.GameObjects.Image;
    timerArc:      Phaser.GameObjects.Graphics;
}

// ─── Scene ─────────────────────────────────────────────────────────────────────

export class PokerGameScene extends Phaser.Scene {
    private myUserId = -1;
    private mySeat   = -1;

    private gameState: GameStateDto | null = null;
    private holeCards: CardDto[] = [];

    private tableImage!:    Phaser.GameObjects.Image;
    private seatDisplays:   SeatDisplay[] = [];
    private boardSlots:     Phaser.GameObjects.Image[] = [];
    private boardOutlines:  Phaser.GameObjects.Graphics[] = [];
    private deckStack:      Phaser.GameObjects.Image[] = [];
    private potText!:       Phaser.GameObjects.Text;
    private phaseText!:     Phaser.GameObjects.Text;
    private winnerBanner!:  Phaser.GameObjects.Text;

    /** One bet-label per display slot; positioned in world space past the card area toward pot. */
    private betLabels: Phaser.GameObjects.Text[] = [];

    /** Phase from the last rendered state — used to detect street transitions. */
    private prevPhase = '';
    /** Bet amounts (display slot → amount) saved from the previous render pass. */
    private savedBets = new Map<number, number>();

    private lastBoardLen = 0;
    /** Board slot → card image still flying in from the deck. */
    private dealing = new Map<number, Phaser.GameObjects.Image>();
    /** Estimated server clock minus client clock (ms), from fresh turn deadlines. */
    private clockOffset: number | null = null;
    private lastDeadline = 0;
    private lastW = 0;
    private lastH = 0;
    private winnerBannerTimer: Phaser.Time.TimerEvent | null = null;

    /** Domains we have already attempted to load logos for. */
    private loadedLogoKeys = new Set<string>();

    /** EventBus handlers registered by this scene instance (removed on destroy). */
    private busHandlers: [string, (...args: any[]) => void][] = [];

    constructor() { super({ key: 'PokerGameScene' }); }

    // ─── Lifecycle ──────────────────────────────────────────────────────────────

    preload(): void {
        this.load.image('table', 'assets/Poker_Table.png');
        for (const suit of SUITS) {
            for (const rank of RANKS) {
                const key = `${suit}_${rank}`;
                this.load.image(key, `assets/svg_playing_cards/fronts/png_96_dpi/${key}.png`);
            }
        }
        this.load.image('card_back', 'assets/svg_playing_cards/backs/blue.svg');
    }

    create(): void {
        this.setupBackground();
        this.setupBoardArea();
        this.setupSeatDisplays();
        this.setupBetLabels();
        this.setupHUD();
        this.setupEventBusListeners();

        this.layout();
        this.scale.on('resize', () => this.layout());

        // Clean up EventBus listeners when this scene instance is destroyed so that
        // stale closures from a previous game session cannot intercept events intended
        // for the next scene (EventEmitter3 stops on exception, so a throwing stale
        // handler would silently block every live listener that follows it).
        // Only remove *this* scene's handlers: Phaser defers game destruction to
        // the next frame, so a blanket removeAllListeners here could also strip
        // the listeners a newer scene instance has already registered.
        this.events.once('destroy', () => {
            for (const [event, fn] of this.busHandlers) EventBus.off(event, fn, this);
            this.busHandlers = [];
        });

        EventBus.emit('current-scene-ready', this);
    }

    override update(_time: number, _delta: number): void {
        if (this.scale.width !== this.lastW || this.scale.height !== this.lastH) {
            this.layout();
        }
        this.updateTimerArcs();
    }

    // ─── Responsive layout ──────────────────────────────────────────────────────

    private get w(): number { return this.scale.width; }
    private get h(): number { return this.scale.height; }
    private get s(): number { return Math.min(this.w / BASE_W, this.h / BASE_H); }

    private layout(): void {
        const { w, h, s } = this;
        this.lastW = w;
        this.lastH = h;

        const tex   = this.textures.get('table').getSourceImage();
        const cover = Math.max(w / tex.width, h / tex.height);
        this.tableImage.setPosition(w / 2, h / 2)
            .setDisplaySize(tex.width * cover, tex.height * cover);

        const spacing = 80 * s;
        const boardY  = h * 0.472;
        for (let i = 0; i < 5; i++) {
            const x = w / 2 + (i - 2) * spacing;
            this.boardSlots[i].setPosition(x, boardY).setScale(BOARD_CARD_SCALE * s);

            const g = this.boardOutlines[i];
            g.clear();
            g.lineStyle(1.5, 0x88aacc, 0.35);
            g.strokeRoundedRect(
                x - (BOARD_W * s) / 2, boardY - (BOARD_H * s) / 2,
                BOARD_W * s, BOARD_H * s, 6 * s);
        }

        const deckX = w / 2 - 3.4 * spacing;
        for (let i = 0; i < this.deckStack.length; i++) {
            this.deckStack[i]
                .setPosition(deckX - i * 2.5 * s, boardY - i * 2.5 * s)
                .setScale(BOARD_CARD_SCALE * s);
        }

        this.potText.setPosition(w / 2, h * 0.372).setScale(s);
        this.phaseText.setPosition(w / 2, h * 0.333).setScale(s);
        this.winnerBanner.setPosition(w / 2, h / 2 - 40 * s).setScale(s);

        for (let d = 0; d < 8; d++) {
            this.seatDisplays[d].container
                .setPosition(SEAT_FRAC[d].x * w, SEAT_FRAC[d].y * h)
                .setScale(s);
        }

        this.layoutBetLabels();

        if (this.gameState) this.renderAll();
    }

    private layoutBetLabels(): void {
        const { w, h, s } = this;
        const potX = w * TABLE_CX;
        const potY = h * TABLE_CY;
        const clearDist = BET_CLEAR_DIST * s;

        for (let d = 0; d < 8; d++) {
            const sx = SEAT_FRAC[d].x * w;
            const sy = SEAT_FRAC[d].y * h;
            const dx = potX - sx;
            const dy = potY - sy;
            const len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1) {
                this.betLabels[d].setPosition(sx, sy).setScale(s);
                continue;
            }
            this.betLabels[d].setPosition(
                sx + (dx / len) * clearDist,
                sy + (dy / len) * clearDist,
            ).setScale(s);
        }
    }

    // ─── Background & board ─────────────────────────────────────────────────────

    private setupBackground(): void {
        this.tableImage = this.add.image(0, 0, 'table');
    }

    private setupBoardArea(): void {
        for (let i = 0; i < 5; i++) {
            this.boardOutlines.push(this.add.graphics());
            this.boardSlots.push(this.add.image(0, 0, 'card_back').setVisible(false));
        }
        for (let i = 0; i < 3; i++) {
            this.deckStack.push(this.add.image(0, 0, 'card_back').setVisible(false));
        }

        this.potText = this.add.text(0, 0, '', {
            fontFamily: FONT, fontSize: '24px', fontStyle: 'bold',
            color: '#ffd700', stroke: '#000000', strokeThickness: 4,
            resolution: TEXT_RES,
        }).setOrigin(0.5);

        this.phaseText = this.add.text(0, 0, '', {
            fontFamily: FONT, fontSize: '13px',
            color: '#9fb4cc', resolution: TEXT_RES,
        }).setOrigin(0.5);
    }

    private setupHUD(): void {
        this.winnerBanner = this.add.text(0, 0, '', {
            fontFamily: FONT, fontSize: '42px', fontStyle: 'bold',
            color: '#ffd700', stroke: '#000000', strokeThickness: 7,
            resolution: TEXT_RES,
        }).setOrigin(0.5).setDepth(10).setVisible(false);
    }

    // ─── Seat displays ──────────────────────────────────────────────────────────

    private setupSeatDisplays(): void {
        for (let d = 0; d < 8; d++) {
            const container = this.add.container(0, 0);
            const panel     = this.add.graphics();

            // Logo image — populated when the local PNG loads; invisible until then
            const logoImage = this.add.image(-PANEL_W / 2 + 22, -21, 'card_back')
                .setDisplaySize(30, 30)
                .setVisible(false);

            const usernameText = this.add.text(-43, -21, '', {
                fontFamily: FONT, fontSize: '13px', fontStyle: 'bold',
                color: '#e8eef7', resolution: TEXT_RES,
            }).setOrigin(0, 0.5);

            const chipsText = this.add.text(0, 1, '', {
                fontFamily: FONT, fontSize: '13px', fontStyle: 'bold',
                color: '#ffd700', resolution: TEXT_RES,
            }).setOrigin(0.5);

            const stateText = this.add.text(0, 19, '', {
                fontFamily: FONT, fontSize: '11px',
                color: '#8aa0bb', resolution: TEXT_RES,
            }).setOrigin(0.5);

            const badgeGraphics = this.add.graphics();

            const badgeText = this.add.text(0, 0, '', {
                fontFamily: FONT, fontSize: '9px', fontStyle: 'bold',
                color: '#1a1a1a',
                resolution: TEXT_RES,
            }).setOrigin(0.5).setVisible(false);

            const cardOffsetY = -(PANEL_H / 2 + 16);
            const card1 = this.add.image(-34, cardOffsetY, 'card_back')
                .setScale(CARD_SCALE).setVisible(false);
            const card2 = this.add.image(31, cardOffsetY, 'card_back')
                .setScale(CARD_SCALE).setVisible(false);

            const timerArc = this.add.graphics();

            container.add([card1, card2, panel, logoImage,
                           usernameText, chipsText, stateText,
                           badgeGraphics, badgeText, timerArc]);

            const sd: SeatDisplay = {
                container, panel, logoImage,
                usernameText, chipsText, stateText,
                badgeGraphics, badgeText, card1, card2, timerArc,
            };
            this.seatDisplays.push(sd);
            this.drawSeatPanel(sd, 0x223855, 1.5, true);
        }
    }

    private setupBetLabels(): void {
        for (let d = 0; d < 8; d++) {
            const lbl = this.add.text(0, 0, '', {
                fontFamily: FONT, fontSize: '20px', fontStyle: 'bold',
                color: '#ffd700', stroke: '#000000', strokeThickness: 5,
                resolution: TEXT_RES,
            }).setOrigin(0.5).setVisible(false).setDepth(5);
            this.betLabels.push(lbl);
        }
    }

    private drawSeatPanel(sd: SeatDisplay, strokeColor: number,
            strokeWidth: number, dim: boolean): void {
        const g = sd.panel;
        const x = -PANEL_W / 2, y = -PANEL_H / 2;
        g.clear();
        g.fillStyle(0x000000, dim ? 0.25 : 0.45);
        g.fillRoundedRect(x + 3, y + 5, PANEL_W, PANEL_H, PANEL_RADIUS);
        g.fillStyle(dim ? 0x0b1626 : 0x122036, dim ? 0.55 : 0.96);
        g.fillRoundedRect(x, y, PANEL_W, PANEL_H, PANEL_RADIUS);
        g.lineStyle(strokeWidth, strokeColor, dim ? 0.5 : 1);
        g.strokeRoundedRect(x, y, PANEL_W, PANEL_H, PANEL_RADIUS);
    }

    // ─── EventBus ───────────────────────────────────────────────────────────────

    private listen(event: string, fn: (...args: any[]) => void): void {
        EventBus.on(event, fn, this);
        this.busHandlers.push([event, fn]);
    }

    private setupEventBusListeners(): void {
        this.listen('set-player-identity', (data: { userId: number; seat: number }) => {
            this.myUserId = data.userId;
            this.mySeat   = data.seat;
        });

        this.listen('game-state', (state: GameStateDto) => {
            const prevPhase = this.gameState?.phase;
            this.gameState = state;
            this.sampleClockOffset(state);
            // A new hand (or WAITING) drops last hand's hole cards so they don't
            // show face-up until the new private state lands. Safe because the
            // server preserves publish order: public state always precedes that
            // hand's private state (WebSocketConfig.setPreservePublishOrder).
            if (state.phase === 'WAITING'
                || (state.phase === 'PRE_FLOP' && prevPhase !== undefined && prevPhase !== 'PRE_FLOP')) {
                this.holeCards = [];
            }
            this.renderAll();
        });

        this.listen('private-state', (state: PrivateStateDto) => {
            this.holeCards = state.holeCards;
            this.renderMyCards();
        });

        this.listen('reset-table', () => {
            this.gameState    = null;
            this.holeCards    = [];
            this.mySeat       = -1;
            this.lastBoardLen = 0;
            this.cancelDeals();
            this.prevPhase    = '';
            this.savedBets.clear();
            this.seatDisplays.forEach((sd, d) => {
                this.renderEmptySeat(sd, d);
            });
            this.boardSlots.forEach(slot => slot.setVisible(false));
            this.boardOutlines.forEach(g => g.setVisible(true));
            this.deckStack.forEach(card => card.setVisible(false));
            this.potText.setText('');
            this.phaseText.setText('');
            this.winnerBanner.setVisible(false);
        });
    }

    // ─── Full render ────────────────────────────────────────────────────────────

    private renderAll(): void {
        if (!this.gameState) return;
        const newPhase = this.gameState.phase;
        const handJustEnded = newPhase === 'HAND_COMPLETE' && this.prevPhase !== 'HAND_COMPLETE';

        const bets = new Map<number, number>();
        for (let d = 0; d < 8; d++) {
            const actual = this.displayToActualSeat(d);
            const p      = this.gameState.players.find(pl => pl.seat === actual);
            const bet    = p ? this.displayedBet(p) : 0;
            if (bet > 0) bets.set(d, bet);
        }
        // Sweep bets into the pot whenever the table's bets clear: a new street,
        // the start of an all-in run-out (round reset, phase unchanged) or the
        // hand ending (incl. a fold-win) — not on every broadcast.
        if (this.savedBets.size > 0 && bets.size === 0) {
            this.animateBetsIntoPot();
        }
        this.savedBets = bets;
        this.prevPhase = newPhase;

        this.renderBoard();
        this.renderPlayers();
        this.renderPot();
        this.renderPhase();
        this.renderWinnerBanner(handJustEnded);
    }

    /**
     * A fold-win leaves round contributions in place but has already paid the
     * pot out, so bets aren't shown on a finished hand (they'd double-count).
     */
    private displayedBet(p: PlayerStateDto): number {
        return this.gameState?.phase === 'HAND_COMPLETE' ? 0 : p.betThisRound;
    }

    // ─── Bet collect animation ───────────────────────────────────────────────────

    private animateBetsIntoPot(): void {
        const potX = this.potText.x;
        const potY = this.potText.y;

        for (const [displaySlot, betAmount] of this.savedBets) {
            if (betAmount <= 0) continue;
            const lbl = this.betLabels[displaySlot];
            if (!lbl.visible) continue;

            const temp = this.add.text(lbl.x, lbl.y, `$${betAmount}`, {
                fontFamily: FONT, fontSize: '15px', fontStyle: 'bold',
                color: '#ffd700', stroke: '#000000', strokeThickness: 3,
                resolution: TEXT_RES,
            }).setOrigin(0.5).setDepth(15);

            this.tweens.add({
                targets: temp,
                x: potX,
                y: potY,
                alpha: 0,
                scaleX: 0.5,
                scaleY: 0.5,
                duration: 380,
                delay: displaySlot * 40,
                ease: 'Cubic.easeIn',
                onComplete: () => temp.destroy(),
            });
        }
    }

    // ─── Board cards ────────────────────────────────────────────────────────────

    private renderBoard(): void {
        const board  = this.gameState!.board;
        const inHand = this.gameState!.phase !== 'WAITING';

        this.deckStack.forEach(card => card.setVisible(inHand));

        if (board.length < this.lastBoardLen) {
            this.lastBoardLen = 0;
            this.cancelDeals();
        }

        for (let i = 0; i < 5; i++) {
            const slot    = this.boardSlots[i];
            const outline = this.boardOutlines[i];
            if (i < board.length) {
                const key = cardTextureKey(board[i].rank, board[i].suit);
                outline.setVisible(false);
                if (i < this.lastBoardLen) {
                    // Still flying in — the deal tween reveals it when it lands.
                    if (!this.dealing.has(i)) slot.setTexture(key).setVisible(true);
                } else {
                    this.dealBoardCard(i, key, (i - this.lastBoardLen) * 180);
                }
            } else {
                slot.setVisible(false);
                outline.setVisible(true);
            }
        }
        this.lastBoardLen = board.length;
    }

    private dealBoardCard(slotIndex: number, textureKey: string, delayMs: number): void {
        const slot  = this.boardSlots[slotIndex];
        slot.setVisible(false);
        const deck  = this.deckStack[0];
        const scale = BOARD_CARD_SCALE * this.s;
        const moving = this.add.image(deck.x, deck.y, 'card_back')
            .setScale(scale).setDepth(8);
        this.dealing.set(slotIndex, moving);

        this.tweens.add({
            targets: moving, x: slot.x, y: slot.y,
            duration: 280, delay: delayMs, ease: 'Cubic.easeOut',
            onComplete: () => {
                this.tweens.add({
                    targets: moving, scaleX: 0, duration: 90, ease: 'Linear',
                    onComplete: () => {
                        moving.setTexture(textureKey);
                        this.tweens.add({
                            targets: moving, scaleX: scale, duration: 90, ease: 'Linear',
                            onComplete: () => {
                                slot.setTexture(textureKey).setVisible(true);
                                this.dealing.delete(slotIndex);
                                moving.destroy();
                            },
                        });
                    },
                });
            },
        });
    }

    /**
     * Drops deal animations from a board that's been cleared (new hand / table
     * reset). Tweens pause while the tab is hidden, so without this a stale
     * deal could land later and paint last hand's card onto the new board.
     */
    private cancelDeals(): void {
        for (const moving of this.dealing.values()) {
            this.tweens.killTweensOf(moving);
            moving.destroy();
        }
        this.dealing.clear();
    }

    // ─── Players ────────────────────────────────────────────────────────────────

    private renderPlayers(): void {
        const state = this.gameState!;
        const byActualSeat = new Map<number, PlayerStateDto>();
        for (const p of state.players) byActualSeat.set(p.seat, p);

        for (let d = 0; d < 8; d++) {
            const actual = this.displayToActualSeat(d);
            const sd     = this.seatDisplays[d];
            const player = byActualSeat.get(actual);

            if (!player) {
                this.renderEmptySeat(sd, d);
                continue;
            }

            const handOver    = state.phase === 'WAITING' || state.phase === 'HAND_COMPLETE';
            const isActive    = player.state === 'ACTIVE';
            const isFolded    = player.state === 'FOLDED';
            const isAllIn     = player.state === 'ALL_IN' && !handOver;
            const isActingNow = state.currentPlayerSeat === player.seat && !handOver;

            const borderColor = isActingNow ? 0xffd700
                               : isFolded   ? 0x2a3a50
                               : isAllIn    ? 0xff7a26
                               :              0x3c5e8f;
            this.drawSeatPanel(sd, borderColor, isActingNow ? 3 : 1.5, isFolded);

            this.drawLogoCircle(sd, player.collegeDomain, player.collegeName, isFolded);

            this.setNameText(sd, player.name);
            sd.usernameText.setColor(isFolded ? '#5a6a80' : '#e8eef7');
            sd.chipsText.setText(`$${player.chips}`)
                .setColor(isFolded ? '#5a6a80' : '#ffd700');
            // A FOLDED seat with 0 chips is a busted player sitting the hand out
            // (you can't fold with nothing behind — you'd be all-in).
            const isOut = player.chips === 0 && (handOver || isFolded);
            sd.stateText.setText(
                isAllIn ? 'ALL IN' : isOut ? 'OUT OF MONEY' : isActive ? '' : 'FOLDED'
            ).setColor(isAllIn ? '#ff9944' : isOut ? '#f87171' : '#5a6a80');

            const badge = player.seat === state.smallBlindSeat ? 'SB'
                        : player.seat === state.bigBlindSeat   ? 'BB'
                        : '';
            const BX = PANEL_W / 2 - 2, BY = -PANEL_H / 2 + 2, BR = 11;
            sd.badgeGraphics.clear();
            if (badge) {
                const fillColor = badge === 'BB' ? 0xffd700 : 0x2266dd;
                sd.badgeGraphics.fillStyle(fillColor, 1);
                sd.badgeGraphics.fillCircle(BX, BY, BR);
                sd.badgeGraphics.lineStyle(1.5, 0x000000, 0.25);
                sd.badgeGraphics.strokeCircle(BX, BY, BR);
            }
            sd.badgeText
                .setPosition(BX, BY)
                .setText(badge)
                .setColor(badge === 'BB' ? '#1a1a1a' : '#ffffff')
                .setVisible(badge !== '');

            // Cards: revealed at showdown/run-out via public holeCards;
            // otherwise show own face cards or backs for opponents.
            const isMe           = player.userId === this.myUserId;
            const showCards      = !isFolded && state.phase !== 'WAITING';
            const revealedCards  = player.holeCards?.length === 2;
            if (showCards) {
                if (revealedCards) {
                    sd.card1.setTexture(cardTextureKey(player.holeCards[0].rank, player.holeCards[0].suit)).setVisible(true);
                    sd.card2.setTexture(cardTextureKey(player.holeCards[1].rank, player.holeCards[1].suit)).setVisible(true);
                } else if (isMe && this.holeCards.length === 2) {
                    sd.card1.setTexture(cardTextureKey(this.holeCards[0].rank, this.holeCards[0].suit)).setVisible(true);
                    sd.card2.setTexture(cardTextureKey(this.holeCards[1].rank, this.holeCards[1].suit)).setVisible(true);
                } else {
                    sd.card1.setTexture('card_back').setVisible(true);
                    sd.card2.setTexture('card_back').setVisible(true);
                }
            } else {
                sd.card1.setVisible(false);
                sd.card2.setVisible(false);
            }

            const bet = this.displayedBet(player);
            this.betLabels[d].setText(bet > 0 ? `$${bet}` : '').setVisible(bet > 0);
        }
    }

    /** Sets the seat name, ellipsizing it so long names stay inside the panel. */
    private setNameText(sd: SeatDisplay, name: string): void {
        const t = sd.usernameText;
        if (t.getData('fullName') === name) return;
        t.setData('fullName', name);
        t.setText(name);
        for (let n = name.length - 1; n > 0 && t.width > NAME_MAX_W; n--) {
            t.setText(name.slice(0, n).trimEnd() + '…');
        }
    }

    /** Shows the school logo PNG if loaded; otherwise shows nothing. */
    private drawLogoCircle(sd: SeatDisplay, domain: string, _name: string, dim: boolean): void {
        sd.logoImage.setVisible(false);
        if (!domain) return;

        const key = `logo_${domain}`;
        this.tryLoadLogo(domain, key);

        if (this.textures.exists(key)) {
            sd.logoImage.setTexture(key)
                .setPosition(-PANEL_W / 2 + 22, -21)
                .setDisplaySize(30, 30)
                .setAlpha(dim ? 0.4 : 1)
                .setVisible(true);
        }
    }

    /** One-shot load of the school logo PNG. No-ops if domain has no mapped file. */
    private tryLoadLogo(domain: string, key: string): void {
        if (this.loadedLogoKeys.has(key)) return;
        this.loadedLogoKeys.add(key);
        if (this.textures.exists(key)) return;

        const path = collegeLogo(domain);
        if (!path) return;

        this.load.image(key, path);
        this.load.once('filecomplete-image-' + key, () => {
            // Don't call tex.setFilter() here — it forces flat LINEAR min/mag
            // filtering, which overrides the LINEAR_MIPMAP_LINEAR minification
            // filter the WebGL texture manager auto-applies to this power-of-two
            // texture. Mipmapping is what makes the small in-game badge crisp.
            if (this.gameState) this.renderPlayers();
        });
        if (!this.load.isLoading()) this.load.start();
    }

    private renderEmptySeat(sd: SeatDisplay, displaySlot: number): void {
        this.drawSeatPanel(sd, 0x223855, 1, true);
        sd.logoImage.setVisible(false);
        sd.usernameText.setText('').setData('fullName', '');
        sd.chipsText.setText('');
        sd.stateText.setText('');
        sd.badgeGraphics.clear();
        sd.badgeText.setVisible(false);
        sd.card1.setVisible(false);
        sd.card2.setVisible(false);
        sd.timerArc.clear();
        this.betLabels[displaySlot].setText('').setVisible(false);
    }

    private renderMyCards(): void {
        if (this.mySeat === -1 || this.holeCards.length < 2) return;
        const sd = this.seatDisplays[0];
        sd.card1.setTexture(cardTextureKey(this.holeCards[0].rank, this.holeCards[0].suit)).setVisible(true);
        sd.card2.setTexture(cardTextureKey(this.holeCards[1].rank, this.holeCards[1].suit)).setVisible(true);
    }

    // ─── Pot & phase ────────────────────────────────────────────────────────────

    private renderPot(): void {
        const total = this.gameState!.pots.reduce((sum, p) => sum + p.amount, 0);
        this.potText.setText(total > 0 ? `Pot  $${total}` : '');
    }

    private renderPhase(): void {
        const phaseLabel: Record<string, string> = {
            WAITING: 'Waiting for players…',
            PRE_FLOP: 'Pre-Flop', FLOP: 'Flop', TURN: 'Turn',
            RIVER: 'River', SHOWDOWN: 'Showdown', HAND_COMPLETE: '',
        };
        this.phaseText.setText(phaseLabel[this.gameState!.phase] ?? '');
    }

    private renderWinnerBanner(handJustEnded: boolean): void {
        const state = this.gameState!;
        if (state.phase !== 'HAND_COMPLETE') {
            this.winnerBanner.setVisible(false);
            return;
        }
        // A later HAND_COMPLETE broadcast can drop a winner whose seat was just
        // vacated (disconnected all-in) — leave the banner up until its timeout.
        if (state.winnerSeats.length === 0) return;
        // Only (re)show on the transition into HAND_COMPLETE — later broadcasts
        // in the same phase (rebuys, departures) and resizes re-run renderAll()
        // and would otherwise re-pop the banner after its 4s timeout.
        if (!handJustEnded) return;
        const winners = state.players
            .filter(p => state.winnerSeats.includes(p.seat))
            .map(p => p.name);
        const verb = winners.length === 1 ? 'wins' : 'win';
        this.winnerBanner.setText(`🏆  ${winners.join(' & ')} ${verb}!`).setVisible(true);

        if (this.winnerBannerTimer) {
            this.winnerBannerTimer.remove(false);
        }
        this.winnerBannerTimer = this.time.delayedCall(4000, () => {
            this.winnerBanner.setVisible(false);
            this.winnerBannerTimer = null;
        });
    }

    // ─── Timer arcs ─────────────────────────────────────────────────────────────

    private updateTimerArcs(): void {
        for (let d = 0; d < 8; d++) {
            const sd = this.seatDisplays[d];
            sd.timerArc.clear();
            if (!this.gameState) continue;
            const actual = this.displayToActualSeat(d);
            if (this.gameState.currentPlayerSeat !== actual) continue;
            if (this.gameState.turnDeadlineEpochMs <= 0) continue;

            const serverNow = Date.now() + (this.clockOffset ?? 0);
            const remaining = Math.max(0, this.gameState.turnDeadlineEpochMs - serverNow);
            const fraction  = Math.min(1, remaining / TURN_MS);
            if (fraction <= 0) continue;

            const barW  = PANEL_W - 24;
            const barY  = PANEL_H / 2 - 11;
            const fillW = Math.max(0, barW * fraction);
            const color = fraction < 0.25 ? 0xe2483d : 0xffd700;

            sd.timerArc.fillStyle(0x000000, 0.45);
            sd.timerArc.fillRoundedRect(-barW / 2, barY, barW, 6, 3);
            if (fillW > 6) {
                sd.timerArc.fillStyle(color, 1);
                sd.timerArc.fillRoundedRect(-barW / 2, barY, fillW, 6, 3);
            }
        }
    }

    /**
     * The deadline is server-clock time, so a skewed client clock would show the
     * bar empty or stuck full. Every new deadline is broadcast the moment it's
     * set (server now + TURN_MS), so each one bounds the offset from below
     * (minus latency, or more for a mid-turn join); the max is the best estimate.
     */
    private sampleClockOffset(state: GameStateDto): void {
        const deadline = state.turnDeadlineEpochMs;
        if (deadline <= 0 || deadline === this.lastDeadline) return;
        this.lastDeadline = deadline;
        const sample = deadline - TURN_MS - Date.now();
        this.clockOffset = this.clockOffset === null ? sample : Math.max(this.clockOffset, sample);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private displayToActualSeat(displaySlot: number): number {
        if (this.mySeat === -1) return displaySlot;
        return (this.mySeat + displaySlot) % 8;
    }
}
