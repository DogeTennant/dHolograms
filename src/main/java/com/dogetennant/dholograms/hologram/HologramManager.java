package com.dogetennant.dholograms.hologram;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.HologramFlag;
import com.dogetennant.dholograms.storage.StorageProvider;
import org.bukkit.entity.Player;

import java.util.*;

public class HologramManager {

    private final DHolograms plugin;
    private final StorageProvider storage;
    private final Map<String, Hologram> holograms = new LinkedHashMap<>();

    private final Map<UUID, Hologram> prevNavRegistry = new HashMap<>();
    private final Map<UUID, Hologram> nextNavRegistry = new HashMap<>();
    private final Map<UUID, Hologram> clickRegistry = new HashMap<>();

    public HologramManager(DHolograms plugin, StorageProvider storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void loadAll() {
        List<Hologram> loaded = storage.loadAll();
        for (Hologram h : loaded) {
            holograms.put(h.getName().toLowerCase(Locale.ROOT), h);
            h.spawn();
        }
        plugin.getLogger().info("Loaded " + holograms.size() + " hologram(s).");
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            updateVisibility(p);
        }
    }

    public Hologram getHologram(String name) {
        return holograms.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String name) {
        return holograms.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Hologram> getAllHolograms() {
        return Collections.unmodifiableCollection(holograms.values());
    }

    public Hologram createHologram(String name, org.bukkit.Location location) {
        Hologram hologram = new Hologram(name, location);
        hologram.getDisplaySettings().setLineHeight(
                plugin.getConfig().getDouble("holograms.default-line-height", 0.3));
        hologram.getDisplaySettings().setRange(
                plugin.getConfig().getDouble("holograms.default-range", 64.0));
        hologram.setRefreshTicks(
                plugin.getConfig().getInt("holograms.refresh-ticks", 20));
        holograms.put(name.toLowerCase(Locale.ROOT), hologram);
        storage.save(hologram);
        return hologram;
    }

    public boolean renameHologram(String oldName, String newName) {
        Hologram h = holograms.remove(oldName.toLowerCase(Locale.ROOT));
        if (h == null) return false;
        storage.delete(h.getName());
        h.setName(newName);
        holograms.put(newName.toLowerCase(Locale.ROOT), h);
        storage.save(h);
        return true;
    }

    public void deleteHologram(String name) {
        Hologram h = holograms.remove(name.toLowerCase(Locale.ROOT));
        if (h != null) {
            h.despawn();
            storage.delete(h.getName());
        }
    }

    public void saveHologram(Hologram hologram) {
        storage.save(hologram);
    }

    public void despawnAll() {
        for (Hologram h : holograms.values()) h.despawn();
    }

    public void respawnAll() {
        for (Hologram h : holograms.values()) {
            h.reload();
            for (Player p : plugin.getServer().getOnlinePlayers()) h.applyVisibilityTo(p);
        }
    }

    public void updateVisibility(Player player) {
        for (Hologram h : holograms.values()) h.applyVisibilityTo(player);
    }

    public void removeViewer(Player player) {
        UUID id = player.getUniqueId();
        for (Hologram h : holograms.values()) h.removeViewer(id);
    }

    public void updateContent(Player player) {
        for (Hologram h : holograms.values()) {
            if (h.isDisabled()) continue;
            if (h.hasFlag(HologramFlag.DISABLE_UPDATING)) continue;
            if (h.isVisibleTo(player) && h.isInUpdateRange(player)) {
                int page = h.getViewerPage(player);
                h.getPage(page).forEach(line -> line.update(player));
            }
        }
    }

    public List<Hologram> getByTag(String tag) {
        List<Hologram> result = new ArrayList<>();
        for (Hologram h : holograms.values()) {
            if (tag.equalsIgnoreCase(h.getTag())) result.add(h);
        }
        return result;
    }

    // Nav entity registry

    public void registerNavEntities(Hologram h) {
        if (h.getPrevNavEntity() != null) prevNavRegistry.put(h.getPrevNavEntity().getUniqueId(), h);
        if (h.getNextNavEntity() != null) nextNavRegistry.put(h.getNextNavEntity().getUniqueId(), h);
    }

    public void unregisterNavEntities(Hologram h) {
        if (h.getPrevNavEntity() != null) prevNavRegistry.remove(h.getPrevNavEntity().getUniqueId());
        if (h.getNextNavEntity() != null) nextNavRegistry.remove(h.getNextNavEntity().getUniqueId());
    }

    public Hologram getHologramByPrevNav(UUID entityId) { return prevNavRegistry.get(entityId); }
    public Hologram getHologramByNextNav(UUID entityId) { return nextNavRegistry.get(entityId); }

    public void registerClickEntity(Hologram h) {
        if (h.getClickEntity() != null) clickRegistry.put(h.getClickEntity().getUniqueId(), h);
    }

    public void unregisterClickEntity(Hologram h) {
        if (h.getClickEntity() != null) clickRegistry.remove(h.getClickEntity().getUniqueId());
    }

    public Hologram getHologramByClickEntity(UUID entityId) { return clickRegistry.get(entityId); }
}
