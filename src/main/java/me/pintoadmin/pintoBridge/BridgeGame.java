package me.pintoadmin.pintoBridge;

import java.util.*;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;

public abstract class BridgeGame {
    public final LinkedList<BlockState> blockChanges = new LinkedList<>();

    public abstract void init();

    public abstract void start();

    public abstract void end(boolean forceEnd);

    public abstract void join(Player player);

    public abstract void leave(Player player);

    public abstract boolean isPlaying();

    private static final Map<String, ChatColor> TEAM_COLORS = new HashMap<>();

    static {
        for (ChatColor c : ChatColor.values()) if (c.isColor()) TEAM_COLORS.put(c.name(), c);
        TEAM_COLORS.put("PURPLE", ChatColor.DARK_PURPLE);
        TEAM_COLORS.put("ORANGE", ChatColor.GOLD);
        TEAM_COLORS.put("LIME", ChatColor.GREEN);
        TEAM_COLORS.put("CYAN", ChatColor.DARK_AQUA);
        TEAM_COLORS.put("TEAL", ChatColor.DARK_AQUA);
        TEAM_COLORS.put("NAVY", ChatColor.DARK_BLUE);
        TEAM_COLORS.put("MAROON", ChatColor.DARK_RED);
        TEAM_COLORS.put("OLIVE", ChatColor.DARK_GREEN);
        TEAM_COLORS.put("SILVER", ChatColor.GRAY);
        TEAM_COLORS.put("FUCHSIA", ChatColor.LIGHT_PURPLE);
    }

    public static ChatColor teamColor(String teamStr) {
        ChatColor c = TEAM_COLORS.get(teamStr.toUpperCase(Locale.ROOT));
        if (c == null) {
            PintoBridge.getInstance()
                    .getLogger()
                    .warning("Unknown team colour '" + teamStr + "', using WHITE");
            return ChatColor.WHITE;
        }
        return c;
    }
}
