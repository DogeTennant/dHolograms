package com.dogetennant.dholograms.listener;

import com.dogetennant.dholograms.DHolograms;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

public class EntitySpawnListener implements Listener {

    private final DHolograms plugin;

    public EntitySpawnListener(DHolograms plugin) {
        this.plugin = plugin;
    }

    // Runs after Multiverse-Core (LOW priority) and other world-guard plugins so we can
    // un-cancel the spawn for entities that dHolograms itself registered.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (plugin.getManagedSpawns().remove(event.getEntity())) {
            event.setCancelled(false);
        }
    }
}
