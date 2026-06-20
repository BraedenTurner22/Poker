import { Component, OnInit, AfterViewInit, ElementRef, ViewChild, ChangeDetectorRef } from '@angular/core';
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
export class TableSelectComponent implements OnInit, AfterViewInit {
    readonly stakes = STAKES;

    isLoggedIn = false;

    @ViewChild('googleBtn') googleBtnRef?: ElementRef<HTMLDivElement>;

    // Table selection
    selectedStake: StakeOption = STAKES[0];
    buyInAmount   = STAKES[0].minBuyIn;
    bankBalance   = 0;

    statusMsg = '';
    joining   = false;
    private joinTimeoutWarning?: ReturnType<typeof setTimeout>;

    constructor(
        private auth: AuthService,
        private router: Router,
        private cdr: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        this.isLoggedIn = this.auth.isLoggedIn();
        if (this.isLoggedIn) this.loadProfile();
    }

    ngAfterViewInit(): void {
        if (!this.isLoggedIn) this.renderGoogleButton();
    }

    // ─── Auth ────────────────────────────────────────────────────────────────────

    /** GIS's script tag loads async; retry briefly until it's ready. */
    private renderGoogleButton(attempt = 0): void {
        if (typeof google !== 'undefined' && google.accounts?.id && this.googleBtnRef) {
            google.accounts.id.initialize({
                client_id: GOOGLE_CLIENT_ID,
                callback: (resp: { credential: string }) => this.onGoogleCredential(resp.credential),
            });
            google.accounts.id.renderButton(this.googleBtnRef.nativeElement, {
                theme: 'filled_blue', size: 'large', text: 'continue_with', width: 280,
            });
            return;
        }
        if (attempt > 50) return;
        setTimeout(() => this.renderGoogleButton(attempt + 1), 100);
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
                this.statusMsg = e.error?.error ?? 'Could not join table.';
                this.cdr.detectChanges();
            },
        });
    }

    back(): void { this.router.navigate(['/']); }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private loadProfile(): void {
        this.auth.getProfile().subscribe({
            next: p => {
                this.bankBalance = p.chips;
                this.resetBuyIn();
                this.cdr.detectChanges();
            },
        });
    }

    clampBuyIn(): void {
        const min = this.selectedStake.minBuyIn;
        const max = this.effectiveMax;
        this.buyInAmount = Math.max(min, Math.min(max, this.buyInAmount || min));
    }

    private resetBuyIn(): void {
        const min = this.selectedStake.minBuyIn;
        const max = this.effectiveMax;
        this.buyInAmount = max >= min ? max : min;
    }
}
