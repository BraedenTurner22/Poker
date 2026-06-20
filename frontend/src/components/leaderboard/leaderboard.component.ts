import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { collegeLogo } from '../../types/college-logos';
import { LeaderboardRange } from '../../types/game.types';

interface SchoolEntry { collegeName: string; collegeDomain: string; profit: number; rank: number; }
interface PlayerEntry  { name: string; collegeName: string; collegeDomain: string; profit: number; rank: number; }

@Component({
    selector: 'app-leaderboard',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './leaderboard.component.html',
    styleUrls: ['./leaderboard.component.css'],
})
export class LeaderboardComponent implements OnInit {
    range: LeaderboardRange = 'all';
    isLoggedIn = false;
    loading = true;

    schoolTop10: SchoolEntry[] = [];
    mySchool: SchoolEntry | null = null;

    playerTop10: PlayerEntry[] = [];
    myEntry: PlayerEntry | null = null;

    schoolPlayerTop10: PlayerEntry[] = [];
    mySchoolEntry: PlayerEntry | null = null;

    constructor(
        private auth: AuthService,
        private router: Router,
        private cdr: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        this.isLoggedIn = this.auth.isLoggedIn();
        this.load();
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

    private load(): void {
        this.loading = true;
        this.auth.getSchoolLeaderboard(this.range).subscribe({
            next: (r: any) => {
                this.schoolTop10 = r.top10 ?? [];
                this.mySchool    = r.mySchool ?? null;
                this.loading     = false;
                this.cdr.detectChanges();
            },
            error: () => { this.loading = false; this.cdr.detectChanges(); },
        });
        this.auth.getPlayerLeaderboard(this.range).subscribe({
            next: (r: any) => {
                this.playerTop10 = r.top10 ?? [];
                this.myEntry     = r.me ?? null;
                this.cdr.detectChanges();
            },
        });
        if (this.isLoggedIn) {
            this.auth.getSchoolPlayerLeaderboard(this.range).subscribe({
                next: (r: any) => {
                    this.schoolPlayerTop10 = r.top10 ?? [];
                    this.mySchoolEntry     = r.me ?? null;
                    this.cdr.detectChanges();
                },
            });
        } else {
            this.schoolPlayerTop10 = [];
            this.mySchoolEntry = null;
        }
    }
}
