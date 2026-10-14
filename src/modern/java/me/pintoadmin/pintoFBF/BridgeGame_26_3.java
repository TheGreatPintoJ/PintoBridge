package me.pintoadmin.pintoFBF;

import me.pintoadmin.pintoBridge.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;

import java.util.*;

public class BridgeGame_26_3 extends BridgeGame {
    private final PintoBridge pintoBridge;
    private final FileConfiguration config;
    private final ConfigurationSection gameCfg;
    private final String name;
    private World world;
    private boolean playing = false;
    private boolean ended = false;
    private List<Map<?, ?>> teamsMaps;
    private List<Team> teams;
    private Scoreboard scoreboard;
    private boolean enabled;
    private List<ItemStack> startingItems;
    private List<Material> breakables;
    private Vector facingLocation;
    private Vector endLocation;
    private String endWorldName;
    private int deathHeight;
    private boolean canDrop;
    private int playersPerTeam;
    private Map<String, Vector> teamSpawns = new HashMap<>();
    private Map<String, Vector[]> teamPortals = new HashMap<>();
    private Set<UUID> spectators;
    private static final int MAX_BLOCK_CHANGES = 10000; // Prevent unbounded growth
    private final Map<String, Boolean> oldGamerules = new HashMap<>();
    private final Map<UUID, ItemStack[]> oldInventories = new HashMap<>();
    private final List<BukkitTask> runningTasks = new ArrayList<>();

    private String boardTitle;
    private String boardIP;
    private String boardTitleColor;
    private String boardIPColor;

    public BridgeGame_26_3(PintoBridge pintoBridge, String name) {
        this.pintoBridge = pintoBridge;
        this.config = pintoBridge.getConfig();
        this.gameCfg = config.getConfigurationSection(name);
        this.name = name;
    }

    @Override
    public void init() {
        playing = false;
        teamsMaps = gameCfg.getMapList("teams");
        teams = new ArrayList<>();
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        enabled = gameCfg.getBoolean("enabled");
        List<String> startingItemsStr = gameCfg.getStringList("startingitems");
        List<String> breakablesStr = gameCfg.getStringList("startingitems");
        facingLocation = getFacing(5d);
        deathHeight = gameCfg.getInt("deathheight", -64);
        canDrop = gameCfg.getBoolean("candropitems", false);
        playersPerTeam = gameCfg.getInt("playersperteam", 1);

        ConfigurationSection endSection = gameCfg.getConfigurationSection("endlocation");
        endWorldName = endSection != null ? endSection.getString("world", "world") : "world";
        endLocation = getVector("endlocation");

        ConfigurationSection boardSection = gameCfg.getConfigurationSection("scoreboard");
        if (boardSection == null) {
            pintoBridge.getLogger()
                    .severe("No 'scoreboard' section for game " + name + " - using defaults");
            boardSection = config.createSection(name + ".scoreboard");
        }
        boardTitle = boardSection.getString("title", "SERVER");
        boardIP = boardSection.getString("ip", "");
        boardTitleColor = boardSection.getString("titlecolor", "AQUA");
        boardIPColor = boardSection.getString("ipcolor", "YELLOW");

        startingItems = new ArrayList<>();
        breakables = new ArrayList<>();
        spectators = new LinkedHashSet<>();
        teamSpawns = new HashMap<>();
    }

    @Override
    public void start() {

    }

    @Override
    public void end(boolean forceEnd) {

    }

    @Override
    public void join(Player player) {

    }

    @Override
    public void leave(Player player) {

    }

    private void undoBlockChanges() {
        LinkedList<BlockState> changes = new LinkedList<>();
        for (int i = 1; i <= blockChanges.size(); i++) { // Workaround reverse list
            changes.add(blockChanges.get(blockChanges.size() - i));
        }

        for (BlockState entry : changes) {
            entry.update(true, false);

            blockChanges.remove(entry);
        }
    }


    private Vector getVector(String path) {
        ConfigurationSection section = gameCfg.getConfigurationSection(path);
        if (section == null) return null;
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        return new Vector(x, y, z);
    }

    private Vector getFacing(Double yVal) {
        ConfigurationSection facingLocation = gameCfg.getConfigurationSection("facinglocation");
        if (facingLocation == null) return null;
        double x = facingLocation.getDouble("x");
        double z = facingLocation.getDouble("z");
        return new Vector(x, yVal, z);
    }

    @Override
    public boolean isPlaying() {
        return false;
    }
}
