package com.dogetennant.dholograms.listener;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.HologramFlag;
import com.dogetennant.dholograms.util.MsgUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InteractionListener implements Listener {

    private final DHolograms plugin;
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();

    public InteractionListener(DHolograms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction)) return;

        UUID entityId = entity.getUniqueId();
        Player player = event.getPlayer();

        Hologram prev = plugin.getHologramManager().getHologramByPrevNav(entityId);
        if (prev != null) {
            event.setCancelled(true);
            prev.prevPage(player);
            return;
        }

        Hologram next = plugin.getHologramManager().getHologramByNextNav(entityId);
        if (next != null) {
            event.setCancelled(true);
            next.nextPage(player);
            return;
        }

        Hologram clicked = plugin.getHologramManager().getHologramByClickEntity(entityId);
        if (clicked == null || !clicked.hasClickActions()) return;
        event.setCancelled(true);
        if (clicked.hasFlag(HologramFlag.DISABLE_ACTIONS)) return;

        String clickType = player.isSneaking() ? "SHIFT_RIGHT" : "RIGHT";
        String actions = clicked.getClickAction(clickType);
        if (actions == null || actions.isEmpty()) return;

        if (!checkAndUpdateCooldown(player)) return;
        executeActions(player, actions, clicked);
    }

    @EventHandler
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Interaction)) return;

        UUID entityId = entity.getUniqueId();
        event.setCancelled(true);

        Hologram prev = plugin.getHologramManager().getHologramByPrevNav(entityId);
        if (prev != null) { prev.prevPage(player); return; }

        Hologram next = plugin.getHologramManager().getHologramByNextNav(entityId);
        if (next != null) { next.nextPage(player); return; }

        Hologram clicked = plugin.getHologramManager().getHologramByClickEntity(entityId);
        if (clicked == null || !clicked.hasClickActions()) return;
        if (clicked.hasFlag(HologramFlag.DISABLE_ACTIONS)) return;

        String clickType = player.isSneaking() ? "SHIFT_LEFT" : "LEFT";
        String actions = clicked.getClickAction(clickType);
        if (actions == null || actions.isEmpty()) return;

        if (!checkAndUpdateCooldown(player)) return;
        executeActions(player, actions, clicked);
    }

    private boolean checkAndUpdateCooldown(Player player) {
        int cooldownTicks = plugin.getConfig().getInt("holograms.click-cooldown-ticks", 10);
        if (cooldownTicks <= 0) return true;
        long now = plugin.getServer().getCurrentTick();
        Long last = clickCooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownTicks) return false;
        clickCooldowns.put(player.getUniqueId(), now);
        return true;
    }

    /**
     * Executes a semicolon-separated action string. Action prefixes:
     *   PERMISSION:<node>             - stop if player lacks permission
     *   MESSAGE:<text>                - send MiniMessage text
     *   SOUND:<key>[:<vol>:<pitch>]   - play sound
     *   COMMAND:<cmd>                 - run as player
     *   CONSOLE:<cmd>                 - run as console
     *   CONNECT:<server>              - BungeeCord server switch
     *   TELEPORT:[world:]x:y:z[:yaw:pitch]
     *   NEXT_PAGE[:<hologram>]        - advance page
     *   PREV_PAGE[:<hologram>]        - previous page
     *   PAGE:[<hologram>:]<page>      - jump to specific page (1-indexed)
     */
    private void executeActions(Player player, String raw, Hologram h) {
        for (String part : raw.split(";")) {
            part = part.trim().replace("{player}", player.getName());
            if (part.isEmpty()) continue;

            if (part.startsWith("PERMISSION:")) {
                if (!player.hasPermission(part.substring(11).trim())) return;
            } else if (part.startsWith("MESSAGE:")) {
                player.sendMessage(MsgUtil.parse(part.substring(8)));
            } else if (part.startsWith("SOUND:")) {
                playSound(player, part.substring(6));
            } else if (part.startsWith("CONSOLE:")) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), part.substring(8));
            } else if (part.startsWith("COMMAND:")) {
                player.performCommand(part.substring(8));
            } else if (part.startsWith("CONNECT:")) {
                connectToServer(player, part.substring(8).trim());
            } else if (part.startsWith("TELEPORT:")) {
                teleportPlayer(player, part.substring(9).trim());
            } else if (part.startsWith("NEXT_PAGE")) {
                String target = part.length() > 9 && part.charAt(9) == ':' ? part.substring(10).trim() : null;
                Hologram holo = target != null ? plugin.getHologramManager().getHologram(target) : h;
                if (holo != null) holo.nextPage(player);
            } else if (part.startsWith("PREV_PAGE")) {
                String target = part.length() > 9 && part.charAt(9) == ':' ? part.substring(10).trim() : null;
                Hologram holo = target != null ? plugin.getHologramManager().getHologram(target) : h;
                if (holo != null) holo.prevPage(player);
            } else if (part.startsWith("PAGE:")) {
                handlePageAction(player, h, part.substring(5).trim());
            } else {
                player.performCommand(part);
            }
        }
    }

    private void teleportPlayer(Player player, String spec) {
        String[] parts = spec.split(":");
        if (parts.length < 3) return;
        try {
            boolean firstIsWorld;
            try { Double.parseDouble(parts[0]); firstIsWorld = false; }
            catch (NumberFormatException e) { firstIsWorld = true; }

            World world;
            int c;
            if (firstIsWorld) {
                world = Bukkit.getWorld(parts[0]);
                c = 1;
            } else {
                world = player.getWorld();
                c = 0;
            }
            if (world == null || parts.length < c + 3) return;

            double x = Double.parseDouble(parts[c]);
            double y = Double.parseDouble(parts[c + 1]);
            double z = Double.parseDouble(parts[c + 2]);
            float yaw   = parts.length > c + 3 ? Float.parseFloat(parts[c + 3]) : player.getLocation().getYaw();
            float pitch = parts.length > c + 4 ? Float.parseFloat(parts[c + 4]) : player.getLocation().getPitch();
            player.teleport(new Location(world, x, y, z, yaw, pitch));
        } catch (NumberFormatException ignored) {}
    }

    private void connectToServer(Player player, String server) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        try {
            out.writeUTF("Connect");
            out.writeUTF(server);
        } catch (IOException ignored) {}
        player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
    }

    private void handlePageAction(Player player, Hologram h, String spec) {
        // PAGE:<page>  or  PAGE:<hologram>:<page>
        int lastColon = spec.lastIndexOf(':');
        try {
            if (lastColon < 0) {
                h.setViewerPage(player, Integer.parseInt(spec) - 1);
            } else {
                String holoName = spec.substring(0, lastColon);
                int page = Integer.parseInt(spec.substring(lastColon + 1)) - 1;
                Hologram holo = plugin.getHologramManager().getHologram(holoName);
                if (holo != null) holo.setViewerPage(player, page);
            }
        } catch (NumberFormatException ignored) {}
    }

    private void playSound(Player player, String spec) {
        String[] parts = spec.split(":");
        float volume = parts.length > 1 ? parseFloat(parts[1], 1f) : 1f;
        float pitch  = parts.length > 2 ? parseFloat(parts[2], 1f) : 1f;
        player.playSound(player.getLocation(), parts[0], volume, pitch);
    }

    private float parseFloat(String s, float fallback) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return fallback; }
    }
}
