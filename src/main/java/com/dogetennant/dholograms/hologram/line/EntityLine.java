package com.dogetennant.dholograms.hologram.line;

import com.dogetennant.dholograms.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class EntityLine extends HologramLine {

    private String rawContent;
    /** Degrees to rotate around Y axis per tick. 0 = no rotation. */
    private float rotationSpeed = 0.0f;
    private float currentAngle = 0.0f;

    private BlockDisplay display;

    public EntityLine(Hologram hologram, int index, String rawContent, double heightOffset) {
        super(hologram, index, heightOffset);
        this.rawContent = rawContent;
    }

    @Override
    public void spawn(Location location) {
        if (isSpawned()) despawn();
        Material mat = Material.matchMaterial(rawContent);
        if (mat == null || !mat.isBlock()) mat = Material.STONE;
        final Material finalMat = mat;

        display = location.getWorld().spawn(location, BlockDisplay.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setBlock(finalMat.createBlockData());
            applyTransform(entity);
        });
    }

    @Override
    public void tick() {
        if (rotationSpeed == 0 || !isSpawned()) return;
        currentAngle = (currentAngle + rotationSpeed) % 360;
        applyTransform(display);
    }

    private void applyTransform(BlockDisplay entity) {
        float rad = (float) Math.toRadians(currentAngle);
        // Center the block: half-size block centered at entity position
        entity.setTransformation(new Transformation(
                new Vector3f(-0.25f, 0, -0.25f),
                new AxisAngle4f(rad, 0, 1, 0),
                new Vector3f(0.5f, 0.5f, 0.5f),
                new AxisAngle4f(0, 0, 0, 1)
        ));
    }

    @Override
    public void despawn() {
        if (display != null && !display.isDead()) display.remove();
        display = null;
    }

    @Override public void update(Player viewer) {}

    @Override public LineType getType() { return LineType.ENTITY; }
    @Override public String getRawContent() { return rawContent; }
    @Override public void setRawContent(String content) { this.rawContent = content; }
    @Override public Entity getEntity() { return display; }

    public float getRotationSpeed() { return rotationSpeed; }
    public void setRotationSpeed(float rotationSpeed) { this.rotationSpeed = rotationSpeed; }
}
