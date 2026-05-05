package com.dogetennant.dholograms.hologram.line;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.DisplaySettings;
import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.placeholder.PAPIHook;
import com.dogetennant.dholograms.util.MsgUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class TextLine extends HologramLine {

    private String rawContent;

    // When true, this line's entity is managed by the hologram's unified display
    private boolean unified = false;

    // Animation
    private AnimationType animationType = AnimationType.NONE;
    private int animColor1 = 0xFF5555;
    private int animColor2 = 0x5555FF;
    /** Speed factor: higher = slower. Used differently per type. */
    private int animSpeed = 2;
    private int animTick = 0;

    private TextDisplay display;

    public TextLine(Hologram hologram, int index, String rawContent, double heightOffset) {
        super(hologram, index, heightOffset);
        this.rawContent = rawContent;
    }

    @Override
    public void spawn(Location location) {
        if (unified) return;
        if (isSpawned()) despawn();
        DisplaySettings ds = hologram.getDisplaySettings();

        display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
            DHolograms.getInstance().getManagedSpawns().add(entity);
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setBillboard(ds.getBillboard());
            entity.setAlignment(ds.getAlignment());
            entity.setShadowed(ds.isShadow());
            entity.setLineWidth(ds.getLineWidth());
            applyBackground(entity, ds.getBackgroundColor());
            applyScale(entity, ds);
            entity.text(animationType == AnimationType.NONE
                    ? MsgUtil.parse(rawContent)
                    : buildAnimatedComponent());
        });
    }

    @Override
    public void tick() {
        if (animationType == AnimationType.NONE) return;
        animTick++;
        if (unified || !isSpawned()) return;
        display.text(buildAnimatedComponent());
    }

    @Override
    public void update(Player viewer) {
        if (unified || !isSpawned() || animationType != AnimationType.NONE) return;
        boolean skipPAPI = hologram.hasFlag(com.dogetennant.dholograms.hologram.HologramFlag.DISABLE_PLACEHOLDERS);
        String resolved = (!skipPAPI && PAPIHook.isEnabled()) ? PAPIHook.resolve(viewer, rawContent) : rawContent;
        display.text(MsgUtil.parse(resolved));
    }

    /** Returns the current rendered component for this line — used by the unified background entity. */
    public Component getCurrentComponent(Player player) {
        if (animationType != AnimationType.NONE) return buildAnimatedComponent();
        boolean skipPAPI = hologram.hasFlag(com.dogetennant.dholograms.hologram.HologramFlag.DISABLE_PLACEHOLDERS);
        String resolved = (player != null && !skipPAPI && PAPIHook.isEnabled())
                ? PAPIHook.resolve(player, rawContent)
                : rawContent;
        return MsgUtil.parse(resolved);
    }

    // Animation builders

    private Component buildAnimatedComponent() {
        String text = plainText();
        if (text.isEmpty()) return Component.empty();
        return switch (animationType) {
            case RAINBOW    -> buildRainbow(text);
            case WAVE       -> buildWave(text);
            case BURN       -> buildBurn(text);
            case TYPEWRITER -> buildTypewriter(text);
            case SCROLL     -> buildScroll(text);
            default         -> MsgUtil.parse(rawContent);
        };
    }

    private String plainText() {
        return PlainTextComponentSerializer.plainText().serialize(MsgUtil.parse(rawContent));
    }

    private Component buildRainbow(String text) {
        var b = Component.text();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float hue = ((float) i / n + (float) animTick / (animSpeed * 50f)) % 1f;
            b.append(Component.text(String.valueOf(text.charAt(i))).color(TextColor.color(hsvToRgb(hue))));
        }
        return b.build();
    }

    private Component buildWave(String text) {
        var b = Component.text();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float phase = ((float) i / n + (float) animTick / (animSpeed * 30f)) % 1f;
            float t = (float) Math.sin(phase * 2 * Math.PI) * 0.5f + 0.5f;
            b.append(Component.text(String.valueOf(text.charAt(i))).color(TextColor.color(lerpColor(animColor1, animColor2, t))));
        }
        return b.build();
    }

    private Component buildBurn(String text) {
        int period = animSpeed * 60;
        float front = (float) (animTick % period) / period;
        var b = Component.text();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float pos = n > 1 ? (float) i / (n - 1) : 0f;
            float t = Math.max(0f, Math.min(1f, (front - pos) * n + 0.5f));
            b.append(Component.text(String.valueOf(text.charAt(i))).color(TextColor.color(lerpColor(animColor1, animColor2, t))));
        }
        return b.build();
    }

    private Component buildTypewriter(String text) {
        int n = text.length();
        int totalTicks = n * animSpeed;
        int pauseTicks  = animSpeed * 20;
        int step = animTick % (totalTicks + pauseTicks);
        int visible = Math.min(step / animSpeed, n);
        return visible <= 0 ? Component.text(" ") : Component.text(text.substring(0, visible));
    }

    private Component buildScroll(String text) {
        String padded = text + "    ";
        int window = Math.max(3, text.length() * 2 / 3);
        int offset = (animTick / Math.max(1, animSpeed)) % padded.length();
        var sb = new StringBuilder(window);
        for (int i = 0; i < window; i++) sb.append(padded.charAt((offset + i) % padded.length()));
        return Component.text(sb.toString());
    }

    // Colour helpers

    private static int hsvToRgb(float h) {
        int hi = (int) (h * 6) % 6;
        float f = h * 6 - (float) Math.floor(h * 6);
        int p = 0, q = (int) ((1 - f) * 255), t = (int) (f * 255), v = 255;
        return switch (hi) {
            case 0 -> (v << 16) | (t << 8) | p;
            case 1 -> (q << 16) | (v << 8) | p;
            case 2 -> (p << 16) | (v << 8) | t;
            case 3 -> (p << 16) | (q << 8) | v;
            case 4 -> (t << 16) | (p << 8) | v;
            default -> (v << 16) | (p << 8) | q;
        };
    }

    static int lerpColor(int c1, int c2, float t) {
        int r = (int) ((c1 >> 16 & 0xFF) + ((c2 >> 16 & 0xFF) - (c1 >> 16 & 0xFF)) * t);
        int g = (int) ((c1 >> 8  & 0xFF) + ((c2 >> 8  & 0xFF) - (c1 >> 8  & 0xFF)) * t);
        int b = (int) ((c1       & 0xFF) + ((c2       & 0xFF) - (c1       & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    // Parses #RRGGBB or named Adventure colours. Returns -1 on failure.
    public static int parseColorToRgb(String s) {
        if (s.startsWith("#")) {
            TextColor tc = TextColor.fromHexString(s);
            return tc != null ? tc.value() : -1;
        }
        var named = net.kyori.adventure.text.format.NamedTextColor.NAMES.value(s.toLowerCase());
        return named != null ? named.value() : -1;
    }

    // Display settings helpers

    private void applyScale(TextDisplay entity, DisplaySettings ds) {
        entity.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(ds.getScaleX(), ds.getScaleY(), ds.getScaleZ()),
                new AxisAngle4f(0, 0, 0, 1)));
    }

    private void applyBackground(TextDisplay entity, int argb) {
        if (argb == -1) {
            entity.setDefaultBackground(true);
        } else {
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(
                    (argb >> 24) & 0xFF, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF));
        }
    }

    public void refreshDisplaySettings() {
        if (!isSpawned()) return;
        DisplaySettings ds = hologram.getDisplaySettings();
        display.setBillboard(ds.getBillboard());
        display.setAlignment(ds.getAlignment());
        display.setShadowed(ds.isShadow());
        display.setLineWidth(ds.getLineWidth());
        applyBackground(display, ds.getBackgroundColor());
        applyScale(display, ds);
    }

    @Override public void despawn() {
        if (display != null && !display.isDead()) display.remove();
        display = null;
    }

    @Override public LineType getType() { return LineType.TEXT; }
    @Override public String getRawContent() { return rawContent; }
    @Override public void setRawContent(String content) { this.rawContent = content; }
    @Override public Entity getEntity() { return display; }

    public AnimationType getAnimationType() { return animationType; }
    public void setAnimationType(AnimationType t) { this.animationType = t; }

    public int getAnimColor1() { return animColor1; }
    public void setAnimColor1(int c) { this.animColor1 = c; }

    public int getAnimColor2() { return animColor2; }
    public void setAnimColor2(int c) { this.animColor2 = c; }

    public int getAnimSpeed() { return animSpeed; }
    public void setAnimSpeed(int s) { this.animSpeed = Math.max(1, s); }

    public boolean isUnified() { return unified; }
    public void setUnified(boolean unified) { this.unified = unified; }
}
