package me.pintoadmin.pintoFBF;

import me.pintoadmin.pintoBridge.*;
import org.bukkit.*;
import org.bukkit.block.*;
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

    public BridgeGame_26_3(PintoBridge pintoBridge, String name) {
        this.pintoBridge = pintoBridge;
        this.config = pintoBridge.getConfig();
        this.name = name;
    }

    @Override
    public void init() {

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

    @Override
    public boolean isPlaying() {
        return false;
    }
}
