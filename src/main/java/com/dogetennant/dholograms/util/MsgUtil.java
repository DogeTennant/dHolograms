package com.dogetennant.dholograms.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.regex.Pattern;

public final class MsgUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Matches &0-9, &a-f, &k-o, &r (case-insensitive)
    private static final Pattern LEGACY_PATTERN =
            Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private static final Map<Character, String> LEGACY_TO_MM = Map.ofEntries(
            Map.entry('0', "black"),
            Map.entry('1', "dark_blue"),
            Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"),
            Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"),
            Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"),
            Map.entry('a', "green"),
            Map.entry('b', "aqua"),
            Map.entry('c', "red"),
            Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"),
            Map.entry('f', "white"),
            Map.entry('k', "obfuscated"),
            Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"),
            Map.entry('n', "underlined"),
            Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    private MsgUtil() {}

    /**
     * Parse a MiniMessage string (optionally with legacy &amp; codes) into a Component.
     * Legacy codes like &amp;a, &amp;c are converted to their MiniMessage equivalents
     * before parsing, so they can be freely mixed with native MiniMessage tags.
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (text.contains("&")) {
            text = convertLegacyCodes(text);
        }
        return MM.deserialize(text);
    }

    /** Strip all MiniMessage formatting tags, returning plain text. */
    public static String strip(String text) {
        if (text == null) return "";
        return MM.stripTags(text);
    }

    /**
     * Replace legacy &amp;x codes with their MiniMessage &lt;tag&gt; equivalents.
     * This is a simple text substitution that preserves existing MiniMessage tags
     * (unlike running the string through LegacyComponentSerializer which treats
     * angle-bracket tags as literal characters).
     */
    private static String convertLegacyCodes(String text) {
        return LEGACY_PATTERN.matcher(text).replaceAll(match -> {
            char code = Character.toLowerCase(match.group(1).charAt(0));
            String tag = LEGACY_TO_MM.get(code);
            return tag != null ? "<" + tag + ">" : match.group(0);
        });
    }
}
