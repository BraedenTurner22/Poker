import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { environment } from '../environments/environment';
import {
    GameStateDto, PrivateStateDto, ChatBroadcast,
} from '../types/game.types';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
    private client!: Client;
    private subs: StompSubscription[] = [];

    readonly gameState$ = new Subject<GameStateDto>();
    readonly privateState$ = new Subject<PrivateStateDto>();
    readonly chat$ = new Subject<ChatBroadcast>();
    readonly chatBlocked$ = new Subject<string>();
    readonly kicked$ = new Subject<string>();
    /** Server-side errors for this user (e.g. "You are not seated in this lobby."). */
    readonly error$ = new Subject<string>();
    /** STOMP ERROR frames — the server rejects a CONNECT with an invalid/expired JWT. */
    readonly stompError$ = new Subject<string>();

    private lobbyId = '';

    connect(lobbyId: string, token: string, userId: number): void {
        // Tear down any previous client first — otherwise it keeps running
        // (and auto-reconnecting) alongside the new one, still subscribed to
        // the old lobby and feeding the same subjects.
        this.disconnect();
        this.lobbyId = lobbyId;

        const client = new Client({
            brokerURL: environment.wsUrl,
            connectHeaders: { Authorization: `Bearer ${token}` },
            reconnectDelay: 5000,
            // Matches the server's STOMP heart-beat (WebSocketConfig.HEARTBEAT =
            // 10000,0): it pings every 10s and doesn't want pings back. No server
            // data for ~20s means the socket is dead (sleep, wifi change) — drop
            // it right away and reconnect, rather than waiting on a close
            // handshake that a dead connection never completes.
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 0,
            discardWebsocketOnCommFailure: true,
            onConnect: () => this.onConnected(client, lobbyId, userId),
            onStompError: frame => {
                console.error('STOMP error', frame);
                if (client === this.client) this.stompError$.next(frame.headers['message'] ?? '');
            },
        });
        this.client = client;

        client.activate();
    }

    private onConnected(client: Client, lobbyId: string, userId: number): void {
        // A late onConnect from a client that has since been replaced must not
        // subscribe the current client to its (old) lobby.
        if (client !== this.client) return;

        // Clear stale subscriptions from a previous session before re-subscribing.
        // This fires on initial connect AND on each auto-reconnect.
        this.subs.forEach(s => s.unsubscribe());
        this.subs = [];

        this.subs.push(
            this.client.subscribe(`/topic/game/${lobbyId}/state`, (msg: IMessage) => {
                this.gameState$.next(JSON.parse(msg.body) as GameStateDto);
            }),
            this.client.subscribe(`/topic/game/${lobbyId}/private/${userId}`, (msg: IMessage) => {
                this.privateState$.next(JSON.parse(msg.body) as PrivateStateDto);
            }),
            this.client.subscribe(`/topic/game/${lobbyId}/chat`, (msg: IMessage) => {
                this.chat$.next(JSON.parse(msg.body) as ChatBroadcast);
            }),
            this.client.subscribe(`/topic/game/${lobbyId}/chat-blocked/${userId}`, (msg: IMessage) => {
                const body = JSON.parse(msg.body) as { reason: string };
                this.chatBlocked$.next(body.reason ?? 'Message blocked.');
            }),
            this.client.subscribe(`/topic/game/${lobbyId}/kick/${userId}`, (msg: IMessage) => {
                const body = JSON.parse(msg.body) as { reason: string };
                this.kicked$.next(body.reason ?? 'kicked');
            }),
            this.client.subscribe(`/topic/game/${lobbyId}/error/${userId}`, (msg: IMessage) => {
                const body = JSON.parse(msg.body) as { error: string };
                this.error$.next(body.error ?? 'Something went wrong.');
            }),
        );

        // Register this WebSocket session with the lobby
        this.client.publish({ destination: `/app/game/${lobbyId}/join`, body: '' });
    }

    /** Returns false if the action could not be sent (not connected). */
    sendAction(action: string, amount: number): boolean {
        if (!this.client?.connected) return false;
        this.client.publish({
            destination: `/app/game/${this.lobbyId}/action`,
            body: JSON.stringify({ action, amount }),
        });
        return true;
    }

    sendLeave(): void {
        if (!this.client?.connected) return;
        this.client.publish({
            destination: `/app/game/${this.lobbyId}/leave`,
            body: '',
        });
    }

    /** Returns false if the message could not be sent (not connected). */
    sendChat(message: string): boolean {
        if (!this.client?.connected) return false;
        this.client.publish({
            destination: `/app/game/${this.lobbyId}/chat`,
            body: JSON.stringify({ message }),
        });
        return true;
    }

    disconnect(): void {
        this.subs.forEach(s => s.unsubscribe());
        this.subs = [];
        this.client?.deactivate();
    }

    ngOnDestroy(): void {
        this.disconnect();
    }
}
