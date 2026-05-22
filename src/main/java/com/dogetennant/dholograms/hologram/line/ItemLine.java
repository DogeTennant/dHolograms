package com.dogetennant.dholograms.hologram.line;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.net.URL;
import java.util.Base64;
import java.util.UUID;

public class ItemLine extends HologramLine {

    private String rawContent;
    /** Degrees to rotate around Y axis per tick. 0 = no rotation. */
    private float rotationSpeed = 0.0f;
    private float currentAngle = 0.0f;
    /** Vertical bob amplitude in blocks. 0 = no bob. */
    private float bobAmplitude = 0.0f;
    private int tickCount = 0;
    private static final int BOB_PERIOD = 60;

    private ItemDisplay display;

    public ItemLine(Hologram hologram, int index, String rawContent, double heightOffset) {
        super(hologram, index, heightOffset);
        this.rawContent = rawContent;
    }

    @Override
    public void spawn(Location location) {
        if (isSpawned()) despawn();
        final ItemStack item = buildItem();

        display = location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            DHolograms.getInstance().getManagedSpawns().add(entity);
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setItemStack(item);
            if (rotationSpeed != 0 || bobAmplitude != 0) {
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                applyTransform(entity);
            } else {
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            }
        });
    }

    /** Builds the ItemStack from rawContent. Supports #SKULL:&lt;player&gt; and #SKULL:texture:&lt;base64&gt;. */
    private ItemStack buildItem() {
        if (rawContent.startsWith("#SKULL:")) {
            String arg = rawContent.substring(7).trim();
            if (arg.startsWith("texture:")) {
                return buildSkullFromTexture(arg.substring(8).trim());
            }
            return buildSkullByName(arg);
        }
        Material mat = Material.matchMaterial(rawContent);
        if (mat == null) mat = Material.STONE;
        return new ItemStack(mat);
    }

    private ItemStack buildSkullByName(String playerName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack buildSkullFromTexture(String base64) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;
        try {
            String json = new String(Base64.getDecoder().decode(base64));
            int urlStart = json.indexOf("\"url\":\"") + 7;
            int urlEnd = json.indexOf("\"", urlStart);
            if (urlStart > 6 && urlEnd > urlStart) {
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(new URL(json.substring(urlStart, urlEnd)));
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
            }
        } catch (Exception ignored) {}
        skull.setItemMeta(meta);
        return skull;
    }

    @Override
    public void tick() {
        if ((rotationSpeed == 0 && bobAmplitude == 0) || !isSpawned()) return;
        if (rotationSpeed != 0) currentAngle = (currentAngle + rotationSpeed) % 360;
        tickCount++;
        applyTransform(display);
    }

    private void applyTransform(ItemDisplay entity) {
        float rad = (float) Math.toRadians(currentAngle);
        float bobY = (float) (bobAmplitude * Math.sin(2 * Math.PI * tickCount / BOB_PERIOD));
        entity.setTransformation(new Transformation(
                new Vector3f(0, bobY, 0),
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

    @Override public LineType getType() { return LineType.ITEM; }
    @Override public String getRawContent() { return rawContent; }
    @Override public void setRawContent(String content) { this.rawContent = content; }
    @Override public Entity getEntity() { return display; }

    public float getRotationSpeed() { return rotationSpeed; }
    public void setRotationSpeed(float rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
        if (rotationSpeed == 0 && bobAmplitude == 0 && isSpawned()) {
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        }
    }

    public float getBobAmplitude() { return bobAmplitude; }
    public void setBobAmplitude(float bobAmplitude) {
        this.bobAmplitude = bobAmplitude;
        if (rotationSpeed == 0 && bobAmplitude == 0 && isSpawned()) {
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        }
    }
}
