package me.pintoadmin.pintoBridge;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

public abstract class BridgeGame {
    public final LinkedList<BlockState> blockChanges = new LinkedList<>();

    public enum PlayerState {
        PLAYING,
        SPECTATING,
        DEAD,
        LEFT,
        NOBREAKRES,
        NOBREAK,
        NOPLACE
    }

    public final Map<UUID, List<PlayerState>> playerStates = new LinkedHashMap<>();

    // GAME METHODS
    public abstract void init();

    public abstract void start();

    public abstract void end(boolean forceEnd);

    public abstract void join(Player player);

    public abstract void leave(Player player, World toWorld);

    public abstract void score(Player player);

    public abstract void onDeath(Player player, PlayerDeathEvent event);

    public abstract void giveItems(Player player, String teamStr);

    public abstract void giveItems(Team team);

    public abstract void updateScoreboard();

    public abstract void setPlayerToSpectate(Player player);

    public abstract void addBlockChange(BlockState beforeBlockState);

    // GETTERS
    public abstract String getName();

    public abstract boolean isPlaying();

    public abstract boolean isEnded();

    public abstract List<String> getTeams();

    public abstract List<Team> getBukkitTeams();

    public abstract Scoreboard getScoreboard();

    public abstract List<ItemStack> getStartingItems();

    public abstract List<Material> getBreakables();

    public abstract Location getEndLocation();

    public abstract int getDeathHeight();

    public abstract boolean canDrop();

    public abstract Vector getSpawnLoc(String team);

    public abstract List<Player> getSpectators();

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
