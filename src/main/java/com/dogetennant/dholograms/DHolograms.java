package com.dogetennant.dholograms;

import com.dogetennant.dholograms.api.DHologramsAPI;
import com.dogetennant.dholograms.command.DHologramsCommand;
import com.dogetennant.dholograms.hologram.HologramManager;
import com.dogetennant.dholograms.hologram.animation.AnimationTicker;
import com.dogetennant.dholograms.listener.ChunkListener;
import com.dogetennant.dholograms.listener.EntitySpawnListener;
import com.dogetennant.dholograms.listener.InteractionListener;
import com.dogetennant.dholograms.listener.PlayerListener;
import com.dogetennant.dholograms.placeholder.PAPIHook;
import com.dogetennant.dholograms.storage.MySQLStorage;
import com.dogetennant.dholograms.storage.StorageProvider;
import com.dogetennant.dholograms.storage.YmlStorage;
import com.dogetennant.dholograms.util.LangManager;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class DHolograms extends JavaPlugin {

    private static DHolograms instance;

    private LangManager lang;
    private StorageProvider storage;
    private HologramManager hologramManager;
    private AnimationTicker animationTicker;
    private BukkitTask refreshTask;
    private PAPIHook papiHook;
    // Entities registered here during world.spawn() have their spawn un-cancelled at HIGHEST priority,
    // bypassing plugins like Multiverse that block misc entity spawning.
    private final Set<Entity> managedSpawns = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;
        // A run stopped before (the same instance enabled again) left its parts behind; onDisable
        // must only shut down what this run starts - it also runs when this run stays off.
        storage = null;
        hologramManager = null;
        animationTicker = null;
        refreshTask = null;
        papiHook = null;
        saveDefaultConfig();
        // A config.yml with a YAML error reads as empty: dHolograms would run on the built-in
        // settings, holograms.yml instead of the database among them
        if (!isConfigReadable()) {
            getLogger().severe("config.yml has a mistake (YAML error, see above), so dHolograms stays off"
                    + " instead of running on the built-in settings. Fix it and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        reloadConfig();
        com.dogetennant.dholograms.hologram.Backgrounds.configure(
                getConfig().getString("holograms.default-background", "none"), getLogger());

        // Language
        lang = new LangManager(this);
        String langCode = getConfig().getString("language", "en_us");
        if (!lang.load(langCode)) {
            getLogger().warning("Language '" + langCode + "' not found, falling back to en_us.");
            lang.load("en_us");
        }

        // Storage
        String storageType = getConfig().getString("storage.type", "yml");
        if ("mysql".equalsIgnoreCase(storageType)) {
            storage = new MySQLStorage(this);
        } else {
            storage = new YmlStorage(this);
        }
        try {
            storage.init();
        } catch (Exception e) {
            if (storage instanceof YmlStorage) {
                getLogger().severe(e.getMessage() + " - dHolograms stays off, so no hologram save can"
                        + " write over it. Fix it and restart the server.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getLogger().log(Level.SEVERE, "Failed to initialise storage. Falling back to YML.", e);
            storage = new YmlStorage(this);
            try { storage.init(); } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "YML storage also failed. Disabling plugin.", ex);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        // PAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiHook = new PAPIHook();
            papiHook.register();
            PAPIHook.enable();
            getLogger().info("PlaceholderAPI integration enabled.");
        }

        // Hologram manager. The holograms are read on the first server tick, when every plugin has
        // started: a world plugin (Multiverse) loads its worlds in its own start, maybe after this one
        hologramManager = new HologramManager(this, storage);
        getServer().getScheduler().runTask(this, hologramManager::loadAll);

        // API
        DHologramsAPI.init(hologramManager);

        // Animation ticker (runs every tick for frame advancement)
        animationTicker = new AnimationTicker(this);
        animationTicker.start();

        // Periodic refresh task: PAPI content + visibility re-check
        int refreshTicks = getConfig().getInt("holograms.refresh-ticks", 20);
        refreshTask = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                hologramManager.updateVisibility(player);
                hologramManager.updateContent(player);
            }
        }, refreshTicks, refreshTicks);

        // Listeners
        getServer().getPluginManager().registerEvents(new EntitySpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractionListener(this), this);

        // Command
        DHologramsCommand commandHandler = new DHologramsCommand(this);
        getCommand("dholograms").setExecutor(commandHandler);
        getCommand("dholograms").setTabCompleter(commandHandler);

        // BungeeCord plugin messaging (used by CONNECT click action)
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        getLogger().info("dHolograms enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
        if (papiHook != null) { papiHook.unregister(); PAPIHook.disable(); }
        if (animationTicker != null) animationTicker.stop();
        if (refreshTask != null) refreshTask.cancel();
        if (hologramManager != null) hologramManager.despawnAll();
        if (storage != null) storage.close();
        DHologramsAPI.shutdown();
        instance = null;
    }

    /**
     * Whether config.yml can be read. A YAML error is logged: Bukkit would read the file as empty,
     * so the built-in settings would replace every one in it.
     */
    public boolean isConfigReadable() {
        try {
            new YamlConfiguration().load(new File(getDataFolder(), "config.yml"));
            return true;
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("config.yml cannot be read, so its settings are not used: " + e.getMessage());
            return false;
        }
    }

    /**
     * Changes one option, in the settings in use and in config.yml on disk; everything else in the
     * file stays as it is there. A file with a YAML error is left alone.
     *
     * @return false when config.yml has an error (nothing changed)
     */
    public boolean setConfigValue(String path, Object value) {
        File file = new File(getDataFolder(), "config.yml");
        YamlConfiguration onDisk = new YamlConfiguration();
        try {
            onDisk.load(file);
            onDisk.set(path, value);
            onDisk.save(file);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("config.yml cannot be changed, so it is left as it is: " + e.getMessage());
            return false;
        }
        getConfig().set(path, value);
        return true;
    }

    public static DHolograms getInstance() { return instance; }
    public LangManager getLang() { return lang; }
    public HologramManager getHologramManager() { return hologramManager; }
    public StorageProvider getStorage() { return storage; }
    public Set<Entity> getManagedSpawns() { return managedSpawns; }
}
