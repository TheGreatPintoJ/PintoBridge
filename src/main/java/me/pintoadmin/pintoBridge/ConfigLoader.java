package me.pintoadmin.pintoBridge;

import java.util.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;

public class ConfigLoader {
    private final PintoBridge pintoFBF;
    private final FileConfiguration config;

    public ConfigLoader(PintoBridge pintoFBF, FileConfiguration config) {
        this.pintoFBF = pintoFBF;
        this.config = config;
    }

    public void start() {}

    public Map<String, Boolean> getGamerules(String world) {
        Map<String, Boolean> result = new HashMap<>();
        ConfigurationSection worldSection = config.getConfigurationSection(world);
        if (worldSection == null) return new HashMap<>();
        ConfigurationSection gamerules = worldSection.getConfigurationSection("gamerules");
        if (gamerules == null) return new HashMap<>();
        for (String key : gamerules.getKeys(false)) {
            Object value = gamerules.get(key);
            if (value instanceof Boolean) {
                result.put(key, (Boolean) value);
            }
        }
        return result;
    }

    public List<String> getEnabledGames() {
        List<String> list = new ArrayList<>();
        for (String key : config.getKeys(false))
            if (config.isConfigurationSection(key))
                if (config.getBoolean(key + ".enabled", false)) list.add(key);
        return list;
    }

    public List<String> getAllGames() {
        List<String> list = new ArrayList<>();
        for (String key : config.getKeys(false))
            if (config.isConfigurationSection(key)) list.add(key);
        return list;
    }

    private List<ConfigurationSection> getAllSections(FileConfiguration config) {
        List<ConfigurationSection> finalList = new ArrayList<>();
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section != null && section.contains("enabled")) {
                finalList.add(section);
            }
        }
        return finalList;
    }

    public void set(String path, Object value) {
        config.set(path, value);
        pintoFBF.saveConfig();
    }
}
