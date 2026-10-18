import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../environments/environment';
import { AuthResponse, LeaderboardRange, PlayResponse, UserProfile } from '../types/game.types';

const API = environment.apiUrl;
const TOKEN_KEY = 'cs_token';
const USER_KEY  = 'cs_user';
const ACTIVE_LOBBY_KEY = 'cs_active_lobby';

/** Google Cloud OAuth 2.0 Web Client ID (public — no secret needed for Google Identity Services). */
export const GOOGLE_CLIENT_ID = '493873089862-khumi4iputos1vef7uk945j3tah1bkbc.apps.googleusercontent.com';

@Injectable({ providedIn: 'root' })
export class AuthService {
    constructor(private http: HttpClient) {}

    googleLogin(idToken: string): Observable<AuthResponse> {
        return this.http.post<AuthResponse>(`${API}/auth/google`, { idToken })
            .pipe(tap(r => this.store(r)));
    }

    joinLobby(stakes: string, buyInAmount: number): Observable<PlayResponse> {
        return this.http.post<PlayResponse>(
            `${API}/game/play`,
            { stakes, schoolLobby: false, buyInAmount },
            { headers: { Authorization: `Bearer ${this.getToken()}` } }
        );
    }

    rebuy(lobbyId: string, amount: number): Observable<{ added: number; bankBalance: number }> {
        return this.http.post<{ added: number; bankBalance: number }>(
            `${API}/game/rebuy`,
            { lobbyId, amount },
            { headers: { Authorization: `Bearer ${this.getToken()}` } }
        );
    }

    /** Recovers an already-seated player's session after a page reload. */
    rejoinLobby(lobbyId: string): Observable<PlayResponse> {
        return this.http.get<PlayResponse>(
            `${API}/game/rejoin/${lobbyId}`,
            { headers: { Authorization: `Bearer ${this.getToken()}` } }
        );
    }

    setActiveLobby(lobbyId: string): void {
        localStorage.setItem(ACTIVE_LOBBY_KEY, lobbyId);
    }

    getActiveLobby(): string | null {
        return localStorage.getItem(ACTIVE_LOBBY_KEY);
    }

    clearActiveLobby(): void {
        localStorage.removeItem(ACTIVE_LOBBY_KEY);
    }

    getProfile(): Observable<UserProfile> {
        return this.http.get<UserProfile>(`${API}/users/me`,
            { headers: { Authorization: `Bearer ${this.getToken()}` } });
    }

    private authHeaders(): { [header: string]: string } {
        const headers: { [header: string]: string } = {};
        const token = this.getToken();
        if (token) headers['Authorization'] = `Bearer ${token}`;
        return headers;
    }

    getSchoolLeaderboard(range: LeaderboardRange): Observable<any> {
        return this.http.get(`${API}/leaderboard/schools`, { headers: this.authHeaders(), params: { range } });
    }

    getPlayerLeaderboard(range: LeaderboardRange): Observable<any> {
        return this.http.get(`${API}/leaderboard/players`, { headers: this.authHeaders(), params: { range } });
    }

    getSchoolPlayerLeaderboard(range: LeaderboardRange): Observable<any> {
        return this.http.get(`${API}/leaderboard/players/school`, { headers: this.authHeaders(), params: { range } });
    }

    getToken(): string | null {
        return localStorage.getItem(TOKEN_KEY);
    }

    getUser(): AuthResponse | null {
        const raw = localStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
    }

    /**
     * True only for a stored, unexpired JWT. An expired token used to count
     * as "logged in" — every authenticated call then 401/403'd while the UI
     * kept showing the logged-in state (and the sign-in button never
     * appeared), so expired sessions are cleared here instead.
     */
    isLoggedIn(): boolean {
        const token = this.getToken();
        if (!token) return false;
        if (AuthService.isTokenExpired(token)) {
            this.logout();
            return false;
        }
        return true;
    }

    /**
     * True for a 401/403 from an authenticated call — the stored token was
     * rejected (Spring Security answers a missing/invalid/expired JWT with
     * 403, the controllers use 401 for "User not found").
     */
    static isAuthError(e: { status?: number } | null | undefined): boolean {
        return e?.status === 401 || e?.status === 403;
    }

    private static isTokenExpired(token: string): boolean {
        try {
            const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
            const payload = JSON.parse(atob(part));
            return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now();
        } catch {
            return true;   // malformed token — treat as unusable
        }
    }

    logout(): void {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        localStorage.removeItem(ACTIVE_LOBBY_KEY);
    }

    private store(r: AuthResponse): void {
        localStorage.setItem(TOKEN_KEY, r.token);
        localStorage.setItem(USER_KEY, JSON.stringify(r));
    }
}
