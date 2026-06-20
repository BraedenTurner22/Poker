import { Routes } from '@angular/router';
import { MainMenuComponent } from '../components/main-menu/main-menu.component';
import { TableSelectComponent } from '../components/table-select/table-select.component';
import { PokerGameComponent } from '../components/game/poker-game/poker-game.component';
import { LeaderboardComponent } from '../components/leaderboard/leaderboard.component';

export const routes: Routes = [
    { path: '',             component: MainMenuComponent },
    { path: 'play',         component: TableSelectComponent },
    { path: 'game',         component: PokerGameComponent },
    { path: 'leaderboard',  component: LeaderboardComponent },
    { path: '**',           redirectTo: '' },
];
