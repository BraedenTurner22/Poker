export interface CardDto {
    rank: string;  // TWO, THREE, ... TEN, JACK, QUEEN, KING, ACE
    suit: string;  // CLUBS, DIAMONDS, HEARTS, SPADES
}

export interface PlayerStateDto {
    seat: number;
    userId: number;
    name: string;
    collegeName: string;
    collegeDomain: string;
    chips: number;
    state: 'ACTIVE' | 'FOLDED' | 'ALL_IN';
    betThisRound: number;
    holeCards: CardDto[];  // non-empty only at showdown / all-in run-out
}

export interface PotDto {
    amount: number;
    eligibleSeats: number[];
}

export interface GameStateDto {
    lobbyId: string;
    phase: 'WAITING' | 'PRE_FLOP' | 'FLOP' | 'TURN' | 'RIVER' | 'SHOWDOWN' | 'HAND_COMPLETE';
    board: CardDto[];
    pots: PotDto[];
    currentPlayerSeat: number;
    currentBetAmount: number;
    lastRaiseSize: number;
    bigBlind: number;
    smallBlindSeat: number;
    bigBlindSeat: number;
    turnDeadlineEpochMs: number;
    players: PlayerStateDto[];
    winnerSeats: number[];
    raiseClosedSeats: number[];  // seats that may only call/fold (short all-in didn't reopen raising)
}

export interface PrivateStateDto {
    holeCards: CardDto[];
}

export interface ChatBroadcast {
    userId: number;
    name: string;
    message: string;
    timestampMs: number;
}

export interface AuthResponse {
    token: string;
    id: number;
    name: string;
    email: string;
    collegeName: string;
    chips: number;
}

export interface PlayResponse {
    lobbyId: string;
    wsUrl: string;
    seatNumber: number;
    buyInAmount: number;
    initialState: GameStateDto;
    initialPrivateState: PrivateStateDto | null;
}

export interface UserProfile {
    id: number;
    name: string;
    email: string;
    collegeName: string;
    collegeDomain: string;
    chips: number;
    totalChipDifference: number;
}

export interface SchoolLeaderboardEntry {
    collegeName: string;
    collegeDomain: string;
    profit: number;
    rank: number;
}

export interface PlayerLeaderboardEntry {
    name: string;
    collegeName: string;
    collegeDomain: string;
    profit: number;
    rank: number;
}

export type LeaderboardRange = 'all' | 'month';

/** Maps backend rank/suit strings to the PNG filename key. */
export function cardTextureKey(rank: string, suit: string): string {
    const rankMap: Record<string, string> = {
        TWO: '2', THREE: '3', FOUR: '4', FIVE: '5', SIX: '6',
        SEVEN: '7', EIGHT: '8', NINE: '9', TEN: '10',
        JACK: 'jack', QUEEN: 'queen', KING: 'king', ACE: 'ace',
    };
    return `${suit.toLowerCase()}_${rankMap[rank] ?? rank.toLowerCase()}`;
}
