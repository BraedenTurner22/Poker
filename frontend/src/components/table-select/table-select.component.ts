import { Component, OnInit, AfterViewInit, OnDestroy, ElementRef, ViewChild, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService, GOOGLE_CLIENT_ID } from '../../services/auth.service';
import { PlayResponse } from '../../types/game.types';

declare const google: any;

interface StakeOption {
    value: 'LOW' | 'MEDIUM' | 'HIGH';
    label: string;
    blinds: string;
    minBuyIn: number;
    maxBuyIn: number;
    description: string;
    color: string;
}

const STAKES: StakeOption[] = [
    {
        value: 'LOW',    label: 'Low Stakes',    blinds: '1 / 2',
        minBuyIn: 40,   maxBuyIn: 200,
        description: 'Perfect for warming up',
        color: '#1a8a4a',
    },
    {
        value: 'MEDIUM', label: 'Medium Stakes',  blinds: '2 / 5',
        minBuyIn: 100,  maxBuyIn: 500,
        description: 'The classic college game',
        color: '#1a5c8a',
    },
    {
        value: 'HIGH',   label: 'High Stakes',    blinds: '5 / 10',
        minBuyIn: 200,  maxBuyIn: 1000,
        description: 'For the bold and brave',
        color: '#8a1a1a',
    },
];

@Component({
    selector: 'app-table-select',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './table-select.component.html',
    styleUrls: ['./table-select.component.css'],
})
export class TableSelectComponent implements OnInit, AfterViewInit, OnDestroy {
    readonly stakes = STAKES;

    isLoggedIn = false;
    /**
     * Still seated somewhere (e.g. pressed Back out of /game). The server
     * answers any Sit Down with that existing seat, whatever stakes were
     * picked — and with the stack on the table the bank can be below every
     * minimum, disabling Sit Down entirely — so offer the way back directly.
     */
    hasActiveLobby = false;

    @ViewChild('googleBtn') googleBtnRef?: ElementRef<HTMLDivElement>;

    // Table selection
    selectedStake: StakeOption = STAKES[0];
    buyInAmount   = STAKES[0].minBuyIn;
    bankBalance   = 0;

    statusMsg = '';
    joining   = false;
    private joinTimeoutWarning?: ReturnType<typeof setTimeout>;
    private gisRetryTimer?: ReturnType<typeof setTimeout>;
    private profileRefreshTimer?: ReturnType<typeof setTimeout>;
    private destroyed = false;

    constructor(
        private auth: AuthService,
        private router: Router,
        private cdr: ChangeDetectorRef,
        private zone: NgZone,
    ) {}

    ngOnInit(): void {
        this.isLoggedIn = this.auth.isLoggedIn();
        this.hasActiveLobby = this.isLoggedIn && !!this.auth.getActiveLobby();
        if (this.isLoggedIn) {
            this.loadProfile();
            // Leaving a table cashes out over STOMP and navigates here at once,
            // so the first profile read can beat the deposit — read it again.
            if (history.state?.leftTable) {
                this.profileRefreshTimer = setTimeout(() => this.loadProfile(), 1500);
            }
        }
    }

    ngAfterViewInit(): void {
        if (!this.isLoggedIn) this.renderGoogleButton();
    }

    ngOnDestroy(): void {
        // Without this, a retry-poll still pending from a visit that left
        // before GIS loaded would later call initialize() with a callback
        // bound to this dead component — overriding the live page's callback
        // so signing in there silently did nothing.
        this.destroyed = true;
        clearTimeout(this.gisRetryTimer);
        clearTimeout(this.joinTimeoutWarning);
        clearTimeout(this.profileRefreshTimer);
    }

    // ─── Auth ────────────────────────────────────────────────────────────────────

    /** GIS's script tag loads async; retry briefly until it's ready. */
    private renderGoogleButton(attempt = 0): void {
        if (this.destroyed) return;
        if (typeof google !== 'undefined' && google.accounts?.id && this.googleBtnRef) {
            google.accounts.id.initialize({
                client_id: GOOGLE_CLIENT_ID,
                // GIS invokes this from its own script context, outside
                // Angular's zone — re-enter it so bindings update normally.
                callback: (resp: { credential: string }) =>
                    this.zone.run(() => this.onGoogleCredential(resp.credential)),
            });
            google.accounts.id.renderButton(this.googleBtnRef.nativeElement, {
                theme: 'filled_blue', size: 'large', text: 'continue_with', width: 280,
            });
            return;
        }
        if (attempt > 50) {
            // Previously gave up silently, leaving an empty sign-in card.
            this.statusMsg = 'Google sign-in failed to load. Please refresh the page.';
            this.cdr.detectChanges();
            return;
        }
        this.gisRetryTimer = setTimeout(() => this.renderGoogleButton(attempt + 1), 100);
    }

    private onGoogleCredential(idToken: string): void {
        this.statusMsg = 'Signing in…';
        this.auth.googleLogin(idToken).subscribe({
            next: r => {
                this.isLoggedIn  = true;
                this.bankBalance = r.chips;
                this.resetBuyIn();
                this.statusMsg   = '';
                this.cdr.detectChanges();
            },
            error: e => {
                this.statusMsg = e.error?.error ?? 'Google sign-in failed.';
                this.cdr.detectChanges();
            },
        });
    }

    // ─── Table selection ─────────────────────────────────────────────────────────

    selectStake(s: StakeOption): void {
        this.selectedStake = s;
        this.resetBuyIn();
    }

    get effectiveMax(): number {
        return Math.min(this.selectedStake.maxBuyIn, this.bankBalance);
    }

    get buyInValid(): boolean {
        return this.buyInAmount >= this.selectedStake.minBuyIn
            && this.buyInAmount <= this.effectiveMax;
    }

    joinTable(): void {
        // Guards against a second join firing while the first is still in
        // flight (e.g. a frustrated re-click during a slow/cold-starting
        // backend) — without this, two concurrent join requests for the same
        // account could both land before either had a chance to seat.
        if (!this.buyInValid || this.joining) return;
        this.joining = true;
        this.statusMsg = 'Finding a table…';

        // The free-tier backend can take 30-60s to wake up from idle; let the
        // user know what's happening instead of leaving them staring at a
        // spinner that looks stuck.
        this.joinTimeoutWarning = setTimeout(() => {
            this.statusMsg = 'Still working — the server may be waking up from idle, this can take up to a minute…';
            this.cdr.detectChanges();
        }, 6000);

        this.auth.joinLobby(this.selectedStake.value, this.buyInAmount).subscribe({
            next: (r: PlayResponse) => {
                clearTimeout(this.joinTimeoutWarning);
                this.auth.setActiveLobby(r.lobbyId);
                this.router.navigate(['/game'], { state: { playResponse: r } });
            },
            error: e => {
                clearTimeout(this.joinTimeoutWarning);
                this.joining = false;
                if (AuthService.isAuthError(e)) {
                    this.sessionExpired();
                    return;
                }
                this.statusMsg = e.error?.error ?? 'Could not join table.';
                this.cdr.detectChanges();
            },
        });
    }

    /** /game with no router state rejoins the remembered lobby (or bounces back here if the seat is gone). */
    returnToTable(): void { this.router.navigate(['/game']); }

    back(): void { this.router.navigate(['/']); }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private loadProfile(): void {
        this.auth.getProfile().subscribe({
            next: p => {
                this.bankBalance = p.chips;
                this.resetBuyIn();
                this.cdr.detectChanges();
            },
            // Previously unhandled: a rejected token left the page showing a
            // $0 bank and "you need at least $40" with no way to sign in.
            error: e => {
                if (AuthService.isAuthError(e) || e?.status === 404) {
                    this.sessionExpired();
                } else {
                    this.statusMsg = 'Could not load your bank balance.';
                    this.cdr.detectChanges();
                }
            },
        });
    }

    /** Stored session was rejected by the server — drop it and show sign-in again. */
    private sessionExpired(): void {
        this.auth.logout();
        this.isLoggedIn = false;
        this.hasActiveLobby = false;
        this.statusMsg = 'Your session has expired. Please sign in again.';
        this.cdr.detectChanges();   // renders the sign-in card so #googleBtn exists
        clearTimeout(this.gisRetryTimer);
        this.renderGoogleButton();
    }

    clampBuyIn(): void {
        const min = this.selectedStake.minBuyIn;
        const max = this.effectiveMax;
        this.buyInAmount = Math.max(min, Math.min(max, Math.floor(this.buyInAmount || min)));
    }

    private resetBuyIn(): void {
        const min = this.selectedStake.minBuyIn;
        const max = this.effectiveMax;
        this.buyInAmount = max >= min ? max : min;
    }
}
