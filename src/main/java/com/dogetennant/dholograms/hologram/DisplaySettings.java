package com.dogetennant.dholograms.hologram;

import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

public class DisplaySettings {

    private Display.Billboard billboard = Display.Billboard.CENTER;
    private TextDisplay.TextAlignment alignment = TextDisplay.TextAlignment.CENTER;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private float scaleZ = 1.0f;
    // Stored as ARGB int; -1 = use client default (no override)
    private int backgroundColor = -1;
    private boolean shadow = false;
    private double range = 64.0;
    // -1 = same as display range (no separate update radius)
    private double updateRange = -1.0;
    private double lineHeight = 0.3;
    private int lineWidth = 200;
    // Yaw in degrees applied to display entities (useful with FIXED billboard)
    private float facing = 0.0f;
    // When true, lines stack upward from the anchor Y instead of downward
    private boolean downOrigin = false;
    // When true, all text lines share one unified background entity per page
    private boolean unifiedBackground = true;

    public DisplaySettings() {}

    public DisplaySettings copy() {
        DisplaySettings copy = new DisplaySettings();
        copy.billboard = this.billboard;
        copy.alignment = this.alignment;
        copy.scaleX = this.scaleX;
        copy.scaleY = this.scaleY;
        copy.scaleZ = this.scaleZ;
        copy.backgroundColor = this.backgroundColor;
        copy.shadow = this.shadow;
        copy.range = this.range;
        copy.updateRange = this.updateRange;
        copy.lineHeight = this.lineHeight;
        copy.lineWidth = this.lineWidth;
        copy.facing = this.facing;
        copy.downOrigin = this.downOrigin;
        copy.unifiedBackground = this.unifiedBackground;
        return copy;
    }

    // Serialise to a single pipe-delimited string for MySQL storage
    public String serialize() {
        return "billboard=" + billboard.name()
                + "|alignment=" + alignment.name()
                + "|scaleX=" + scaleX
                + "|scaleY=" + scaleY
                + "|scaleZ=" + scaleZ
                + "|backgroundColor=" + backgroundColor
                + "|shadow=" + shadow
                + "|range=" + range
                + "|updateRange=" + updateRange
                + "|lineHeight=" + lineHeight
                + "|lineWidth=" + lineWidth
                + "|facing=" + facing
                + "|downOrigin=" + downOrigin
                + "|unifiedBackground=" + unifiedBackground;
    }

    public static DisplaySettings deserialize(String raw) {
        DisplaySettings ds = new DisplaySettings();
        if (raw == null || raw.isEmpty()) return ds;
        for (String part : raw.split("\\|")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            switch (kv[0]) {
                case "billboard" -> {
                    try { ds.billboard = Display.Billboard.valueOf(kv[1]); } catch (IllegalArgumentException ignored) {}
                }
                case "alignment" -> {
                    try { ds.alignment = TextDisplay.TextAlignment.valueOf(kv[1]); } catch (IllegalArgumentException ignored) {}
                }
                case "scaleX" -> { try { ds.scaleX = Float.parseFloat(kv[1]); } catch (NumberFormatException ignored) {} }
                case "scaleY" -> { try { ds.scaleY = Float.parseFloat(kv[1]); } catch (NumberFormatException ignored) {} }
                case "scaleZ" -> { try { ds.scaleZ = Float.parseFloat(kv[1]); } catch (NumberFormatException ignored) {} }
                case "backgroundColor" -> { try { ds.backgroundColor = Integer.parseInt(kv[1]); } catch (NumberFormatException ignored) {} }
                case "shadow" -> ds.shadow = Boolean.parseBoolean(kv[1]);
                case "range" -> { try { ds.range = Double.parseDouble(kv[1]); } catch (NumberFormatException ignored) {} }
                case "updateRange" -> { try { ds.updateRange = Double.parseDouble(kv[1]); } catch (NumberFormatException ignored) {} }
                case "lineHeight" -> { try { ds.lineHeight = Double.parseDouble(kv[1]); } catch (NumberFormatException ignored) {} }
                case "lineWidth" -> { try { ds.lineWidth = Integer.parseInt(kv[1]); } catch (NumberFormatException ignored) {} }
                case "facing" -> { try { ds.facing = Float.parseFloat(kv[1]); } catch (NumberFormatException ignored) {} }
                case "downOrigin" -> ds.downOrigin = Boolean.parseBoolean(kv[1]);
                case "unifiedBackground" -> ds.unifiedBackground = Boolean.parseBoolean(kv[1]);
            }
        }
        return ds;
    }

    public Display.Billboard getBillboard() { return billboard; }
    public void setBillboard(Display.Billboard billboard) { this.billboard = billboard; }

    public TextDisplay.TextAlignment getAlignment() { return alignment; }
    public void setAlignment(TextDisplay.TextAlignment alignment) { this.alignment = alignment; }

    public float getScaleX() { return scaleX; }
    public void setScaleX(float scaleX) { this.scaleX = scaleX; }

    public float getScaleY() { return scaleY; }
    public void setScaleY(float scaleY) { this.scaleY = scaleY; }

    public float getScaleZ() { return scaleZ; }
    public void setScaleZ(float scaleZ) { this.scaleZ = scaleZ; }

    public int getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(int backgroundColor) { this.backgroundColor = backgroundColor; }

    public boolean isShadow() { return shadow; }
    public void setShadow(boolean shadow) { this.shadow = shadow; }

    public double getRange() { return range; }
    public void setRange(double range) { this.range = range; }

    public double getUpdateRange() { return updateRange; }
    public void setUpdateRange(double updateRange) { this.updateRange = updateRange; }

    public double getLineHeight() { return lineHeight; }
    public void setLineHeight(double lineHeight) { this.lineHeight = lineHeight; }

    public int getLineWidth() { return lineWidth; }
    public void setLineWidth(int lineWidth) { this.lineWidth = lineWidth; }

    public float getFacing() { return facing; }
    public void setFacing(float facing) { this.facing = facing; }

    public boolean isDownOrigin() { return downOrigin; }
    public void setDownOrigin(boolean downOrigin) { this.downOrigin = downOrigin; }

    public boolean isUnifiedBackground() { return unifiedBackground; }
    public void setUnifiedBackground(boolean unifiedBackground) { this.unifiedBackground = unifiedBackground; }
}
