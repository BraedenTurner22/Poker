package com.collegeshowdown.poker_project.runtime;

import com.collegeshowdown.poker_project.model.Player;

public class ConnectedPlayer {
    public final Player player;
    public final Object connection; // TODO - figure this out

    public ConnectedPlayer(Player player, Object connection) {
        this.player = player;
        this.connection = connection;
    }
}
