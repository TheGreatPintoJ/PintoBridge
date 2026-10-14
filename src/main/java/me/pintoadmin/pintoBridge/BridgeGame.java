package me.pintoadmin.pintoBridge;

import org.bukkit.block.*;
import org.bukkit.entity.Player;

import java.util.*;

public abstract class BridgeGame {
    public final LinkedList<BlockState> blockChanges = new LinkedList<>();

    public abstract void init();
    public abstract void start();
    public abstract void end(boolean forceEnd);
    public abstract void join(Player player);
    public abstract void leave(Player player);

    public abstract boolean isPlaying();
}
