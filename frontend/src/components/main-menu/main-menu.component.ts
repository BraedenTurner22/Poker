import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { UserProfile } from '../../types/game.types';

@Component({
    selector: 'app-main-menu',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './main-menu.component.html',
    styleUrl: './main-menu.component.css',
})
export class MainMenuComponent implements OnInit {
    isLoggedIn = false;
    profile: UserProfile | null = null;
    showProfile = false;

    constructor(private router: Router, private auth: AuthService) {}

    ngOnInit(): void {
        this.isLoggedIn = this.auth.isLoggedIn();
        if (this.isLoggedIn) {
            this.auth.getProfile().subscribe({
                next: p => { this.profile = p; },
                // A rejected/stale token used to be swallowed here, leaving
                // the header stuck on "…" with a dead profile button and
                // every Play attempt failing — drop the session instead.
                error: e => {
                    if (AuthService.isAuthError(e) || e?.status === 404) this.logout();
                },
            });
        }
    }

    play(): void        { this.router.navigate(['/play']); }
    leaderboard(): void { this.router.navigate(['/leaderboard']); }
    login(): void       { this.router.navigate(['/play']); }
    signup(): void      { this.router.navigate(['/play']); }

    openProfile(): void {
        if (!this.profile) return;
        this.showProfile = true;
    }

    closeProfile(): void { this.showProfile = false; }

    logout(): void {
        this.auth.logout();
        this.isLoggedIn = false;
        this.profile = null;
    }
}
