package com.dogetennant.dholograms.hologram;

import org.bukkit.Color;
import org.bukkit.entity.TextDisplay;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Text display backgrounds, as stored in {@link DisplaySettings} and applied to the entities.
 *
 * <p>A text display's background box is drawn before the game draws see-through blocks, and it
 * hides water, glass, ice and the like behind it. Only a background that is fully transparent -
 * not drawn at all - leaves them visible, which is why {@code none} is the server default.
 *
 * <p>Stored values: {@link #DEFAULT} follows {@code holograms.default-background}, {@link #NONE}
 * is no box, {@link #GAME} is the game's own box (the client's text background opacity), anything
 * else is an ARGB colour. Every colour without alpha draws nothing, so {@code GAME} can borrow one
 * of them, and an opaque white is stored a shade off because -1 already means default.
 */
public final class Backgrounds {

    public static final int DEFAULT = -1;
    public static final int NONE = 0;
    public static final int GAME = 1;

    /** What {@link #DEFAULT} stands for (never DEFAULT itself). */
    private static volatile int serverDefault = NONE;

    private Backgrounds() {}

    /** Reads {@code holograms.default-background}: none, game, #RRGGBB or #AARRGGBB. */
    public static void configure(String value, Logger logger) {
        Integer parsed = parse(value == null ? "none" : value);
        if (parsed == null || parsed == DEFAULT) {
            logger.warning("holograms.default-background '" + value + "' is not none, game or a #AARRGGBB colour - using none.");
            parsed = NONE;
        }
        serverDefault = parsed;
    }

    /**
     * A background as an admin types it: {@code none}, {@code default}, {@code game},
     * {@code #RRGGBB} or {@code #AARRGGBB}. Null when it is none of them.
     */
    public static Integer parse(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "none", "transparent" -> { return NONE; }
            case "default" -> { return DEFAULT; }
            case "game" -> { return GAME; }
            default -> { }
        }
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() == 6) hex = "ff" + hex;
        if (hex.length() != 8) return null;
        try {
            return normalise((int) Long.parseLong(hex, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A colour as it is stored: no alpha is no box, opaque white moves off -1. */
    public static int normalise(int argb) {
        if ((argb >>> 24) == 0) return NONE;
        if (argb == DEFAULT) return 0xFFFFFFFE;
        return argb;
    }

    /** Puts the stored background on a text display. */
    public static void apply(TextDisplay display, int stored) {
        int background = stored == DEFAULT ? serverDefault : stored;
        if (background == GAME) {
            display.setDefaultBackground(true);
            return;
        }
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB((background >>> 24) & 0xFF,
                (background >> 16) & 0xFF, (background >> 8) & 0xFF, background & 0xFF));
    }
}
