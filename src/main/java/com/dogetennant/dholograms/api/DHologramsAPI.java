package com.dogetennant.dholograms.api;

import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.HologramManager;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Public API for dHolograms. Other plugins should use this class rather than
 * accessing internal classes directly.
 *
 * <p>Example usage:
 * <pre>{@code
 *   Hologram h = DHologramsAPI.createHologram("myhologram", player.getLocation());
 * }</pre>
 */
public final class DHologramsAPI {

    private static HologramManager manager;

    private DHologramsAPI() {}

    /** Internal: called by the plugin on enable. */
    public static void init(HologramManager hologramManager) {
        manager = hologramManager;
    }

    /** Internal: called on disable. */
    public static void shutdown() {
        manager = null;
    }

    private static void checkReady() {
        if (manager == null) throw new IllegalStateException("dHolograms is not enabled.");
    }

    @Nullable
    public static Hologram getHologram(@NotNull String name) {
        checkReady();
        return manager.getHologram(name);
    }

    @NotNull
    public static Collection<Hologram> getAllHolograms() {
        checkReady();
        return manager.getAllHolograms();
    }

    public static boolean hologramExists(@NotNull String name) {
        checkReady();
        return manager.exists(name);
    }

    @NotNull
    public static Hologram createHologram(@NotNull String name, @NotNull Location location) {
        checkReady();
        if (manager.exists(name)) throw new IllegalArgumentException("Hologram already exists: " + name);
        return manager.createHologram(name, location);
    }

    public static void deleteHologram(@NotNull String name) {
        checkReady();
        manager.deleteHologram(name);
    }

    public static void saveHologram(@NotNull Hologram hologram) {
        checkReady();
        manager.saveHologram(hologram);
    }

    @NotNull
    public static HologramManager getHologramManager() {
        checkReady();
        return manager;
    }
}
