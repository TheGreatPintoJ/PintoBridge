package me.pintoadmin.pintoBridge;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class PintoBridge extends JavaPlugin {
    private final Map<String, BridgeGame> games = new HashMap<>(); // world to game instance

    private static final String MODERN_METHODS = "me.pintoadmin.pintoBridge.Methods_26_3";
    private static final String LEGACY_METHODS = "me.pintoadmin.pintoBridge.Methods_1_8";

    /** BlockData arrived with the 1.13 flattening, which is exactly the line the two split on. */
    private static final String FLATTENING_MARKER = "org.bukkit.block.data.BlockData";

    /** EntityType.ITEM was renamed from DROPPED_ITEM in 1.20.5 - the real floor. */
    private static final String MODERN_MARKER =
            "org.bukkit.inventory.meta.components.FoodComponent";

    private static String CONFIG_NAME = "";

    public ConfigLoader configLoader;
    public Methods methods;

    // Store listener references for cleanup
    private GameListeners gameListeners;
    private WorldChangeListener worldChangeListener;
    private BlockBreakListener blockBreakListener;
    private BlockPlaceListener blockPlaceListener;

    @Override
    public void onEnable() {
        if (methods == null) methods = loadMethods();
        // Save default config
        File configFile = new File(getDataFolder() + "/config.yml");
        if (!configFile.exists()) {
            saveResource(CONFIG_NAME, false);
            try {
                Files.move(
                        Paths.get(getDataFolder() + "/" + CONFIG_NAME),
                        Paths.get(getDataFolder() + "/config.yml"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().severe("Could not save configuration file: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        // Load classes
        FileConfiguration config = getConfig();
        configLoader = new ConfigLoader(this, config);

        // Load config games
        configLoader.start();

        // Initialize Game objects for each game/world
        for (String gameName : configLoader.getEnabledGames()) {
            getLogger().warning("Enabled games: " + configLoader.getEnabledGames());
            games.put(gameName, methods.createGame(this, gameName));
        }

        // Listeners
        gameListeners = new GameListeners(this);
        worldChangeListener = new WorldChangeListener(this);
        blockBreakListener = new BlockBreakListener(this);
        blockPlaceListener = new BlockPlaceListener(this);
    }

    @Override
    public void onDisable() {
        for (Map.Entry<String, BridgeGame> entry : games.entrySet()) {
            String key = entry.getKey();
            BridgeGame game = entry.getValue();
            if (game.isPlaying()) {
                game.end(true);
            }
        }
        // Clear game map
        games.clear();

        // Unregister all event listeners to prevent memory leaks
        HandlerList.unregisterAll(gameListeners);
        HandlerList.unregisterAll(worldChangeListener);
        HandlerList.unregisterAll(blockBreakListener);
        HandlerList.unregisterAll(blockPlaceListener);

        getLogger().info("PintoBridge plugin disabled and cleaned up");
    }

    public void debug(String... messages) {
        String finalString = "[DEBUG]";
        for (String msg : messages) {
            finalString += " " + msg;
        }
        getLogger().info(finalString);
    }

    public BridgeGame getGame(String name) {
        return games.get(name);
    }

    private Methods loadMethods() {
        boolean flattened = classExists(FLATTENING_MARKER);
        boolean modern = classExists(MODERN_MARKER);
        if (flattened && !modern) {
            throw new IllegalStateException(
                    "PintoBridge supports 1.8.x and 1.20.5+; this server is in between.");
        }
        String impl = modern ? MODERN_METHODS : LEGACY_METHODS;
        CONFIG_NAME = modern ? "modern_config.yml" : "legacy_config.yml";
        try {
            Methods loaded = (Methods) Class.forName(impl).getConstructor().newInstance();
            getLogger().info("Running in " + (modern ? "1.20.5+" : "1.8.x") + " mode");
            return loaded;
        } catch (Throwable t) {
            // Nothing works without this, so fail loudly here rather than with an NPE later.
            throw new IllegalStateException("PintoBridge could not load " + impl, t);
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static PintoBridge getInstance() {
        return getPlugin(PintoBridge.class);
    }
}
