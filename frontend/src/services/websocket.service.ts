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

    private lobbyId = '';

    connect(lobbyId: string, token: string, userId: number): void {
        this.lobbyId = lobbyId;

        this.client = new Client({
            brokerURL: environment.wsUrl,
            connectHeaders: { Authorization: `Bearer ${token}` },
            reconnectDelay: 5000,
            onConnect: () => this.onConnected(lobbyId, userId),
            onStompError: frame =>
                console.error('STOMP error', frame),
        });

        this.client.activate();
    }

    private onConnected(lobbyId: string, userId: number): void {
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
        );

        // Register this WebSocket session with the lobby
        this.client.publish({ destination: `/app/game/${lobbyId}/join`, body: '' });
    }

    sendAction(action: string, amount: number): void {
        if (!this.client?.connected) return;
        this.client.publish({
            destination: `/app/game/${this.lobbyId}/action`,
            body: JSON.stringify({ action, amount }),
        });
    }

    sendLeave(): void {
        if (!this.client?.connected) return;
        this.client.publish({
            destination: `/app/game/${this.lobbyId}/leave`,
            body: '',
        });
    }

    sendChat(message: string): void {
        if (!this.client?.connected) return;
        this.client.publish({
            destination: `/app/game/${this.lobbyId}/chat`,
            body: JSON.stringify({ message }),
        });
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
