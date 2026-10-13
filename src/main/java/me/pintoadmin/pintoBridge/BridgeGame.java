package me.pintoadmin.pintoBridge;

import org.bukkit.entity.Player;

public abstract class BridgeGame {
    public abstract void init();
    public abstract void start();
    public abstract void end(boolean forceEnd);
    public abstract void join(Player player);
    public abstract void leave(Player player);

    public abstract boolean isPlaying();
}
