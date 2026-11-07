package com.smpcore;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Tiny YAML file wrapper. */
public final class Store {

    private final SMPCore plugin;
    private final File file;
    private final YamlConfiguration cfg;

    public Store(SMPCore plugin, String name) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), name);
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration get() {
        return cfg;
    }

    public synchronized void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
