package com.dogetennant.dholograms.storage;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.DisplaySettings;
import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.HologramFlag;
import com.dogetennant.dholograms.hologram.line.*;
import com.dogetennant.dholograms.hologram.line.AnimationType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class YmlStorage implements StorageProvider {

    private final DHolograms plugin;
    private File file;
    private YamlConfiguration config;

    public YmlStorage(DHolograms plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        file = new File(plugin.getDataFolder(), "holograms.yml");
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not create holograms.yml", e); }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void close() {}

    @Override
    public List<Hologram> loadAll() {
        List<Hologram> result = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("holograms");
        if (section == null) return result;

        for (String name : section.getKeys(false)) {
            ConfigurationSection h = section.getConfigurationSection(name);
            if (h == null) continue;

            String worldName = h.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Skipping hologram '" + name + "': world '" + worldName + "' not found.");
                continue;
            }

            Location loc = new Location(world, h.getDouble("x"), h.getDouble("y"), h.getDouble("z"));
            Hologram hologram = new Hologram(name, loc);
            hologram.setRefreshTicks(h.getInt("refresh-ticks", 20));
            hologram.setViewPermission(h.getString("view-permission", null));
            hologram.setTag(h.getString("tag", null));
            hologram.setDisplaySettings(DisplaySettings.deserialize(h.getString("display-settings", "")));
            loadClickActions(hologram, h);
            hologram.setDisabled(h.getBoolean("disabled", false));
            String flagsStr = h.getString("flags", "");
            if (flagsStr != null && !flagsStr.isEmpty()) {
                for (String fs : flagsStr.split(",")) {
                    HologramFlag hf = HologramFlag.fromString(fs.trim());
                    if (hf != null) hologram.addFlag(hf);
                }
            }

            // Multi-page format: "pages" key is a list of lists of maps
            Object pagesObj = h.get("pages");
            if (pagesObj instanceof List<?> pagesList) {
                int pageIndex = 0;
                for (Object pageObj : pagesList) {
                    if (pageObj instanceof List<?> lineList) {
                        for (Object lineObj : lineList) {
                            if (lineObj instanceof Map<?, ?> lineMap) {
                                HologramLine line = deserializeLine(hologram, lineMap);
                                if (line != null) hologram.addLineInternal(pageIndex, line);
                            }
                        }
                    }
                    pageIndex++;
                }
            } else {
                // Backward-compat: old "lines" key → page 0
                List<Map<?, ?>> lineList = h.getMapList("lines");
                for (Map<?, ?> lineMap : lineList) {
                    HologramLine line = deserializeLine(hologram, lineMap);
                    if (line != null) hologram.addLineInternal(0, line);
                }
            }

            result.add(hologram);
        }
        return result;
    }

    private void loadClickActions(Hologram hologram, ConfigurationSection h) {
        String right      = h.getString("click-right", null);
        String left       = h.getString("click-left", null);
        String shiftRight = h.getString("click-shift-right", null);
        String shiftLeft  = h.getString("click-shift-left", null);

        if (right      != null) hologram.setClickAction("RIGHT",       right);
        if (left       != null) hologram.setClickAction("LEFT",        left);
        if (shiftRight != null) hologram.setClickAction("SHIFT_RIGHT", shiftRight);
        if (shiftLeft  != null) hologram.setClickAction("SHIFT_LEFT",  shiftLeft);

        // Migrate old single click-command key
        if (!hologram.hasClickActions()) {
            String old = h.getString("click-command", null);
            if (old != null) {
                boolean console = h.getBoolean("click-console", false);
                hologram.setClickAction("RIGHT", console ? "CONSOLE:" + old : "COMMAND:" + old);
            }
        }
    }

    private HologramLine deserializeLine(Hologram hologram, Map<?, ?> map) {
        String typeStr = getString(map, "type", "TEXT");
        LineType type;
        try { type = LineType.valueOf(typeStr); }
        catch (IllegalArgumentException e) { type = LineType.TEXT; }

        String content = getString(map, "content", "");
        double offset = getDouble(map, "height-offset", 0.0);
        double xOffset = getDouble(map, "x-offset", 0.0);
        double zOffset = getDouble(map, "z-offset", 0.0);
        String viewPerm = getString(map, "view-permission", null);

        return switch (type) {
            case TEXT -> {
                TextLine tl = new TextLine(hologram, 0, content, offset);
                tl.setXOffset(xOffset);
                tl.setZOffset(zOffset);
                if (viewPerm != null && !viewPerm.isEmpty()) tl.setViewPermission(viewPerm);
                AnimationType animType = AnimationType.fromString(getString(map, "anim-type", "NONE"));
                tl.setAnimationType(animType);
                if (animType != AnimationType.NONE) {
                    int c1 = getInt(map, "anim-color1", 0xFF5555);
                    int c2 = getInt(map, "anim-color2", 0x5555FF);
                    int speed = getInt(map, "anim-speed", 2);
                    tl.setAnimColor1(c1);
                    tl.setAnimColor2(c2);
                    tl.setAnimSpeed(speed);
                }
                yield tl;
            }
            case ITEM -> {
                ItemLine il = new ItemLine(hologram, 0, content, offset);
                il.setXOffset(xOffset);
                il.setZOffset(zOffset);
                if (viewPerm != null && !viewPerm.isEmpty()) il.setViewPermission(viewPerm);
                float rs = (float) getDouble(map, "rotation-speed", 0.0);
                il.setRotationSpeed(rs);
                float ba = (float) getDouble(map, "bob-amplitude", 0.0);
                il.setBobAmplitude(ba);
                yield il;
            }
            case ENTITY -> {
                EntityLine el = new EntityLine(hologram, 0, content, offset);
                el.setXOffset(xOffset);
                el.setZOffset(zOffset);
                if (viewPerm != null && !viewPerm.isEmpty()) el.setViewPermission(viewPerm);
                float rs = (float) getDouble(map, "rotation-speed", 0.0);
                if (rs != 0) el.setRotationSpeed(rs);
                yield el;
            }
        };
    }

    @Override
    public void save(Hologram hologram) {
        String path = "holograms." + hologram.getName();
        Location loc = hologram.getLocation();

        config.set(path + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".refresh-ticks", hologram.getRefreshTicks());
        config.set(path + ".view-permission", hologram.getViewPermission());
        config.set(path + ".tag", hologram.getTag());
        config.set(path + ".display-settings", hologram.getDisplaySettings().serialize());
        java.util.Map<String, String> ca = hologram.getClickActions();
        config.set(path + ".click-right",       ca.get("RIGHT"));
        config.set(path + ".click-left",        ca.get("LEFT"));
        config.set(path + ".click-shift-right", ca.get("SHIFT_RIGHT"));
        config.set(path + ".click-shift-left",  ca.get("SHIFT_LEFT"));
        config.set(path + ".click-command",  null);
        config.set(path + ".click-console",  null);
        config.set(path + ".disabled", hologram.isDisabled() ? true : null);
        String flagsStr = hologram.getFlags().stream()
                .map(HologramFlag::name)
                .collect(java.util.stream.Collectors.joining(","));
        config.set(path + ".flags", flagsStr.isEmpty() ? null : flagsStr);

        // Save as list-of-lists-of-maps (one list per page)
        List<List<Map<String, Object>>> pagesList = new ArrayList<>();
        for (int p = 0; p < hologram.getPageCount(); p++) {
            List<Map<String, Object>> lineList = new ArrayList<>();
            for (HologramLine line : hologram.getPage(p)) {
                Map<String, Object> lineMap = new LinkedHashMap<>();
                lineMap.put("type", line.getType().name());
                lineMap.put("content", line.getRawContent());
                lineMap.put("height-offset", line.getHeightOffset());
                if (line.getXOffset() != 0) lineMap.put("x-offset", line.getXOffset());
                if (line.getZOffset() != 0) lineMap.put("z-offset", line.getZOffset());
                if (line.getViewPermission() != null && !line.getViewPermission().isEmpty())
                    lineMap.put("view-permission", line.getViewPermission());
                if (line instanceof TextLine tl) {
                    if (tl.getAnimationType() != AnimationType.NONE) {
                        lineMap.put("anim-type", tl.getAnimationType().name());
                        lineMap.put("anim-color1", tl.getAnimColor1());
                        lineMap.put("anim-color2", tl.getAnimColor2());
                        lineMap.put("anim-speed", tl.getAnimSpeed());
                    }
                } else if (line instanceof ItemLine il) {
                    if (il.getRotationSpeed() != 0) lineMap.put("rotation-speed", (double) il.getRotationSpeed());
                    if (il.getBobAmplitude() != 0) lineMap.put("bob-amplitude", (double) il.getBobAmplitude());
                } else if (line instanceof EntityLine el && el.getRotationSpeed() != 0) {
                    lineMap.put("rotation-speed", (double) el.getRotationSpeed());
                }
                lineList.add(lineMap);
            }
            pagesList.add(lineList);
        }
        config.set(path + ".pages", pagesList);
        config.set(path + ".lines", null); // remove old single-page key if present

        saveAsync();
    }

    @Override
    public void delete(String name) {
        config.set("holograms." + name, null);
        saveAsync();
    }

    private void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { config.save(file); }
            catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Failed to save holograms.yml", e); }
        });
    }

    // Helpers for Map<?,?> deserialization

    private String getString(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? v.toString() : def;
    }

    private double getDouble(Map<?, ?> map, String key, double def) {
        Object v = map.get(key);
        if (v == null) return def;
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    private int getInt(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
    }
}
