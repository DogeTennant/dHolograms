package com.dogetennant.dholograms.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Registers dHolograms as a PAPI expansion and provides a safe resolver.
 * This class is only loaded when PAPI is present (DHolograms guards the
 * {@code new PAPIHook().register()} call with a plugin-present check).
 */
public class PAPIHook extends PlaceholderExpansion {

    private static boolean enabled = false;

    public static boolean isEnabled() { return enabled; }

    /** Called after {@code new PAPIHook().register()} succeeds to flip the enabled flag. */
    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    /**
     * Resolve PlaceholderAPI placeholders in {@code text} for {@code player}.
     * Only call this when {@code isEnabled()} is true.
     */
    public static String resolve(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    @Override
    public @NotNull String getIdentifier() { return "dholograms"; }

    @Override
    public @NotNull String getAuthor() { return "dogetennant"; }

    @Override
    public @NotNull String getVersion() { return "1.0.0"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // Future: expose hologram data as placeholders
        return null;
    }
}
