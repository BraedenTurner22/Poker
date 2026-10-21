import type { Mock } from "vitest";
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { of } from 'rxjs';

import { PokerGameComponent } from './poker-game.component';
import { AuthService } from '../../../services/auth.service';
import { GameStateDto } from '../../../types/game.types';

function state(phase: GameStateDto['phase'], myChips: number, myState: 'ACTIVE' | 'FOLDED' | 'ALL_IN'): GameStateDto {
    return {
        lobbyId: 'L', phase, board: [], pots: [], currentPlayerSeat: 1,
        currentBetAmount: 2, lastRaiseSize: 2, bigBlind: 2, smallBlindSeat: 1,
        bigBlindSeat: 2, turnDeadlineEpochMs: 0, winnerSeats: [], raiseClosedSeats: [],
        players: [
            { seat: 0, userId: 7, name: 'Me', collegeName: '', collegeDomain: '', chips: myChips,
                state: myState, betThisRound: 0, holeCards: [] },
            { seat: 1, userId: 8, name: 'A', collegeName: '', collegeDomain: '', chips: 100,
                state: 'ACTIVE', betThisRound: 1, holeCards: [] },
            { seat: 2, userId: 9, name: 'B', collegeName: '', collegeDomain: '', chips: 100,
                state: 'ACTIVE', betThisRound: 2, holeCards: [] },
        ],
    };
}

describe('PokerGameComponent', () => {
    let component: PokerGameComponent;
    let fixture: ComponentFixture<PokerGameComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PokerGameComponent],
            providers: [provideHttpClient(), provideRouter([])],
        })
            .compileComponents();

        fixture = TestBed.createComponent(PokerGameComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('action panel', () => {
        beforeEach(() => {
            component.myUserId = 7;
            component.mySeat = 0;
        });

        it('resets the raise slider when our turn carries over to the next street', () => {
            const pre = state('PRE_FLOP', 100, 'ACTIVE');
            pre.currentPlayerSeat = 0;
            (component as any).onGameState(pre);
            component.raiseAmount = 60;
            // Big blind checked its option and acts first on the flop
            const flop = state('FLOP', 100, 'ACTIVE');
            flop.currentPlayerSeat = 0;
            flop.currentBetAmount = 0;
            flop.players.forEach(p => p.betThisRound = 0);
            (component as any).onGameState(flop);
            expect(component.raiseAmount).toBe(2);
        });

        it('keeps the raise slider on a same-street rebroadcast', () => {
            const pre = state('PRE_FLOP', 100, 'ACTIVE');
            pre.currentPlayerSeat = 0;
            (component as any).onGameState(pre);
            component.raiseAmount = 60;
            (component as any).onGameState({ ...pre });
            expect(component.raiseAmount).toBe(60);
        });

        it('hides pre-select actions during an all-in run-out', () => {
            const runOut = state('FLOP', 100, 'ACTIVE');
            runOut.currentPlayerSeat = -1;
            (component as any).onGameState(runOut);
            expect(component.canPreSelect).toBe(false);
        });
    });

    describe('rebuy popup', () => {
        let profileSpy: Mock;

        beforeEach(() => {
            profileSpy = vi.spyOn(TestBed.inject(AuthService), 'getProfile').mockReturnValue(of({ chips: 5000 } as any));
            component.myUserId = 7;
            component.mySeat = 0;
        });

        afterEach(() => (component as any).clearRebuyTimer());

        it('is offered to a busted player sitting out a hand (e.g. after a reload)', () => {
            (component as any).onGameState(state('PRE_FLOP', 0, 'FOLDED'));
            expect(profileSpy).toHaveBeenCalledTimes(1);
            expect(component.showRebuyPopup).toBe(true);
        });

        it('caps the rebuy at the 100-big-blind table maximum, not the bank', () => {
            (component as any).onGameState(state('HAND_COMPLETE', 0, 'ALL_IN'));
            expect(component.rebuyMax).toBe(200);
            expect(component.rebuyAmount).toBeLessThanOrEqual(200);
        });

        it('is not offered to a player still live in the hand', () => {
            (component as any).onGameState(state('FLOP', 0, 'ALL_IN'));
            expect(profileSpy).not.toHaveBeenCalled();
        });
    });
});
