package com.collegeshowdown.poker_project.domain.lobby;

/**
 * The two kinds of poker lobbies supported by the system.
 */
public enum LobbyType {
    /**
     * A lobby restricted to university players.
     */
    UNIVERSITY,

    /**
     * A lobby open to all players.
     */
    GLOBAL,

    /**
     * custom code (implement later)
     */
    CUSTOM;
}
