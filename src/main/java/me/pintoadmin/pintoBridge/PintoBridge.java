package me.pintoadmin.pintoBridge;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class PintoBridge extends JavaPlugin {
    private final Map<String, ? extends BridgeGame> games = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
    }

    @Override
    public void onDisable() {
        for (Map.Entry<String, ? extends BridgeGame> entry : games.entrySet()) {
            String key = entry.getKey();
            BridgeGame game = entry.getValue();
            if (game.isPlaying()) {
                game.end(true);
            }
        }
    }
}
