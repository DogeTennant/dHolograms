package com.dogetennant.dholograms.listener;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class ChunkListener implements Listener {

    private final DHolograms plugin;

    public ChunkListener(DHolograms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();

        for (Hologram h : plugin.getHologramManager().getAllHolograms()) {
            Location loc = h.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(event.getWorld())) continue;

            int hcx = loc.getBlockX() >> 4;
            int hcz = loc.getBlockZ() >> 4;

            if (hcx == cx && hcz == cz) {
                // Respawn entities for this hologram and reapply visibility
                h.reload();
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    h.applyVisibilityTo(p);
                }
            }
        }
    }
}
