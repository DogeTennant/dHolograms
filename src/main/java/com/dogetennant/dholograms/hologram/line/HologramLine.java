package com.dogetennant.dholograms.hologram.line;

import com.dogetennant.dholograms.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public abstract class HologramLine {

    protected final Hologram hologram;
    protected int index;
    // Delta applied on top of the auto-calculated Y position. Positive = up.
    protected double heightOffset;
    // Delta applied on top of the hologram X position. Positive = east.
    protected double xOffset;
    // Delta applied on top of the hologram Z position. Positive = south.
    protected double zOffset;

    protected String viewPermission = null;

    protected HologramLine(Hologram hologram, int index, double heightOffset) {
        this.hologram = hologram;
        this.index = index;
        this.heightOffset = heightOffset;
    }

    public abstract void spawn(Location location);

    public abstract void despawn();

    /** Update displayed content for a specific viewer (used for PAPI expansion). */
    public abstract void update(Player viewer);

    public abstract LineType getType();

    public abstract String getRawContent();

    public abstract void setRawContent(String content);

    public abstract Entity getEntity();

    /** Called every tick by AnimationTicker. Override for animated behaviour. */
    public void tick() {}

    public boolean isSpawned() {
        Entity e = getEntity();
        return e != null && !e.isDead();
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public double getHeightOffset() { return heightOffset; }
    public void setHeightOffset(double heightOffset) { this.heightOffset = heightOffset; }

    public double getXOffset() { return xOffset; }
    public void setXOffset(double xOffset) { this.xOffset = xOffset; }

    public double getZOffset() { return zOffset; }
    public void setZOffset(double zOffset) { this.zOffset = zOffset; }

    public String getViewPermission() { return viewPermission; }
    public void setViewPermission(String perm) { this.viewPermission = perm; }

    public Hologram getHologram() { return hologram; }
}
