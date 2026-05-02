package com.dogetennant.dholograms.util;

import com.dogetennant.dholograms.DHolograms;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

public class LangManager {

    private final DHolograms plugin;
    private YamlConfiguration lang;
    private String activeCode;

    public LangManager(DHolograms plugin) {
        this.plugin = plugin;
    }

    public boolean load(String code) {
        File file = new File(plugin.getDataFolder(), "translations/" + code + ".yml");
        if (!file.exists()) {
            // Try to extract from jar
            InputStream stream = plugin.getResource("translations/" + code + ".yml");
            if (stream == null) return false;
            plugin.saveResource("translations/" + code + ".yml", false);
            file = new File(plugin.getDataFolder(), "translations/" + code + ".yml");
        }
        try {
            lang = YamlConfiguration.loadConfiguration(file);
            // Merge defaults from jar so new keys are always present
            InputStream defaults = plugin.getResource("translations/" + code + ".yml");
            if (defaults != null) {
                YamlConfiguration defaultLang = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaults, StandardCharsets.UTF_8));
                lang.setDefaults(defaultLang);
            }
            activeCode = code;
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load language file: " + code, e);
            return false;
        }
    }

    public String getActiveCode() { return activeCode; }

    /** Get a raw string from the language file, substituting {key} placeholders. */
    public String getRaw(String key, Map<String, String> placeholders) {
        String prefix = getRawNoPrefix("prefix");
        String value = getRawNoPrefix(key);
        if (value == null) value = "<red>[dHolograms] Missing lang key: " + key;
        value = value.replace("{prefix}", prefix != null ? prefix : "");
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return value;
    }

    public String getRaw(String key) {
        return getRaw(key, null);
    }

    private String getRawNoPrefix(String key) {
        return lang != null ? lang.getString(key) : null;
    }

    /** Parse and send a language message to a CommandSender. */
    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(MsgUtil.parse(getRaw(key, placeholders)));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, null);
    }

    /** Return a parsed Component for a lang key. */
    public Component get(String key, Map<String, String> placeholders) {
        return MsgUtil.parse(getRaw(key, placeholders));
    }

    public Component get(String key) {
        return get(key, null);
    }

    public YamlConfiguration getConfig() { return lang; }
}
