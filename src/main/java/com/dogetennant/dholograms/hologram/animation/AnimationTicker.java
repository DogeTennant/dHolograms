package com.dogetennant.dholograms.hologram.animation;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.line.HologramLine;
import org.bukkit.scheduler.BukkitTask;

public class AnimationTicker {

    private final DHolograms plugin;
    private BukkitTask task;

    public AnimationTicker(DHolograms plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
            if (hologram.isDisabled() || hologram.hasFlag(com.dogetennant.dholograms.hologram.HologramFlag.DISABLE_ANIMATIONS)) continue;
            hologram.forEachLine(HologramLine::tick);
        }
    }
}
