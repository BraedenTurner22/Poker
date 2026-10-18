import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { of } from 'rxjs';

import { PokerGameComponent } from './poker-game.component';
import { AuthService } from '../../../services/auth.service';
import { GameStateDto } from '../../../types/game.types';

function state(phase: GameStateDto['phase'], myChips: number,
               myState: 'ACTIVE' | 'FOLDED' | 'ALL_IN'): GameStateDto {
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

  describe('rebuy popup', () => {
    let profileSpy: jasmine.Spy;

    beforeEach(() => {
      profileSpy = spyOn(TestBed.inject(AuthService), 'getProfile')
        .and.returnValue(of({ chips: 5000 } as any));
      component.myUserId = 7;
      component.mySeat = 0;
    });

    afterEach(() => (component as any).clearRebuyTimer());

    it('is offered to a busted player sitting out a hand (e.g. after a reload)', () => {
      (component as any).onGameState(state('PRE_FLOP', 0, 'FOLDED'));
      expect(profileSpy).toHaveBeenCalledTimes(1);
      expect(component.showRebuyPopup).toBeTrue();
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
