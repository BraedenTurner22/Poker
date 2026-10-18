import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { MainMenuComponent } from '../components/main-menu/main-menu.component';
import { TableSelectComponent } from '../components/table-select/table-select.component';
import { PokerGameComponent } from '../components/game/poker-game/poker-game.component';
import { LeaderboardComponent } from '../components/leaderboard/leaderboard.component';
import { AuthService } from '../services/auth.service';

/**
 * /game requires a session — PokerGameComponent reads `getUser()!.id` on
 * init, so opening /game logged out (or with an expired token) used to throw
 * and leave a blank page. Send those users to /play to sign in instead.
 */
const requireLogin = () =>
    inject(AuthService).isLoggedIn() || inject(Router).createUrlTree(['/play']);

export const routes: Routes = [
    { path: '',             component: MainMenuComponent },
    { path: 'play',         component: TableSelectComponent },
    { path: 'game',         component: PokerGameComponent, canActivate: [requireLogin] },
    { path: 'leaderboard',  component: LeaderboardComponent },
    { path: '**',           redirectTo: '' },
];
