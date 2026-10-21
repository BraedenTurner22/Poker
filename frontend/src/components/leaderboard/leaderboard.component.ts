import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subscription, catchError, forkJoin, of } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { collegeLogo } from '../../types/college-logos';
import { LeaderboardRange } from '../../types/game.types';

interface SchoolEntry { collegeName: string; collegeDomain: string; profit: number; rank: number; }
interface PlayerEntry  { name: string; collegeName: string; collegeDomain: string; profit: number; rank: number; }

@Component({
    selector: 'app-leaderboard',
    imports: [],
    templateUrl: './leaderboard.component.html',
    styleUrls: ['./leaderboard.component.css']
})
export class LeaderboardComponent implements OnInit, OnDestroy {
    range: LeaderboardRange = 'all';
    isLoggedIn = false;
    loading = true;
    /** Both public boards failed (e.g. backend down) — don't present that as "No data yet." */
    loadError = false;

    schoolTop10: SchoolEntry[] = [];
    mySchool: SchoolEntry | null = null;

    playerTop10: PlayerEntry[] = [];
    myEntry: PlayerEntry | null = null;

    schoolPlayerTop10: PlayerEntry[] = [];
    mySchoolEntry: PlayerEntry | null = null;

    private loadSub?: Subscription;

    constructor(
        private auth: AuthService,
        private router: Router,
        private cdr: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        this.isLoggedIn = this.auth.isLoggedIn();
        this.load();
    }

    ngOnDestroy(): void {
        this.loadSub?.unsubscribe();
    }

    setRange(r: LeaderboardRange): void {
        if (this.range === r) return;
        this.range = r;
        this.load();
    }

    back(): void { this.router.navigate(['/']); }

    logoUrl(domain: string): string {
        return collegeLogo(domain) ?? '';
    }

    formatProfit(n: number): string {
        return n >= 0 ? `+$${n}` : `-$${Math.abs(n)}`;
    }

    /**
     * Loads all three boards for the current range together. Previously each
     * request was fired independently and never cancelled: a slow response
     * for the previous range could land after the new one and overwrite it
     * (e.g. "Past Month" selected but all-time players shown), and the
     * spinner cleared as soon as just the schools board arrived. Now the
     * previous range's in-flight requests are cancelled and the boards are
     * swapped in at once.
     */
    private load(): void {
        this.loading = true;
        this.loadSub?.unsubscribe();
        const orNull = (o: Observable<any>) => o.pipe(catchError(() => of(null)));
        this.loadSub = forkJoin({
            schools:       orNull(this.auth.getSchoolLeaderboard(this.range)),
            players:       orNull(this.auth.getPlayerLeaderboard(this.range)),
            schoolPlayers: this.isLoggedIn
                ? orNull(this.auth.getSchoolPlayerLeaderboard(this.range))
                : of(null),
        }).subscribe(({ schools, players, schoolPlayers }) => {
            this.schoolTop10       = schools?.top10 ?? [];
            this.mySchool          = schools?.mySchool ?? null;
            this.playerTop10       = players?.top10 ?? [];
            this.myEntry           = players?.me ?? null;
            this.schoolPlayerTop10 = schoolPlayers?.top10 ?? [];
            this.mySchoolEntry     = schoolPlayers?.me ?? null;
            this.loadError         = !schools && !players;
            this.loading           = false;
            this.cdr.detectChanges();
        });
    }
}
