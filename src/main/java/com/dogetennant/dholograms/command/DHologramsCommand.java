package com.dogetennant.dholograms.command;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.DisplaySettings;
import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.HologramFlag;
import com.dogetennant.dholograms.hologram.line.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class DHologramsCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN = "dholograms.admin";

    private static final Set<String> PLAYER_ONLY = Set.of("create", "move", "tp", "near");

    private static final List<String> ALL_SUBS = List.of(
            "create", "delete", "list", "info", "move", "tp",
            "addline", "setline", "removeline", "insertline",
            "lineheight", "linexoffset", "linezoffset", "lineanim",
            "rename", "display", "show", "hide", "clone", "near",
            "tag", "page", "click", "linerotation",
            "disable", "enable", "swaplines", "linepermission",
            "center", "lineinfo", "flag", "reload", "language", "help"
    );

    private static final List<String> ANIM_TYPES       = List.of("rainbow", "wave", "burn", "typewriter", "scroll", "none");
    private static final List<String> CLICK_TYPES      = List.of("right", "left", "shift-right", "shift-left");
    private static final List<String> DISPLAY_SETTINGS = List.of("billboard", "align", "scale", "background", "shadow", "range", "updaterange", "linewidth", "facing", "downorigin");
    private static final List<String> BILLBOARDS       = List.of("FIXED", "VERTICAL", "HORIZONTAL", "CENTER");
    private static final List<String> ALIGNMENTS       = List.of("LEFT", "CENTER", "RIGHT");

    private final DHolograms plugin;

    public DHologramsCommand(DHolograms plugin) { this.plugin = plugin; }


    // Dispatch


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { cmdHelp(sender, args); return true; }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (!sender.hasPermission(PERM_ADMIN)) { plugin.getLang().send(sender, "no-permission"); return true; }
        if (PLAYER_ONLY.contains(sub) && !(sender instanceof Player)) {
            plugin.getLang().send(sender, "player-only"); return true;
        }

        switch (sub) {
            case "create"         -> cmdCreate(sender, args);
            case "delete"         -> cmdDelete(sender, args);
            case "list"           -> cmdList(sender, args);
            case "info"           -> cmdInfo(sender, args);
            case "move"           -> cmdMove(sender, args);
            case "tp"             -> cmdTp(sender, args);
            case "addline"        -> cmdAddLine(sender, args);
            case "setline"        -> cmdSetLine(sender, args);
            case "removeline"     -> cmdRemoveLine(sender, args);
            case "insertline"     -> cmdInsertLine(sender, args);
            case "lineheight"     -> cmdLineHeight(sender, args);
            case "linexoffset"    -> cmdLineXOffset(sender, args);
            case "linezoffset"    -> cmdLineZOffset(sender, args);
            case "lineanim"       -> cmdLineAnim(sender, args);
            case "rename"         -> cmdRename(sender, args);
            case "display"        -> cmdDisplay(sender, args);
            case "show"           -> cmdShow(sender, args);
            case "hide"           -> cmdHide(sender, args);
            case "clone"          -> cmdClone(sender, args);
            case "near"           -> cmdNear(sender, args);
            case "tag"            -> cmdTag(sender, args);
            case "page"           -> cmdPage(sender, args);
            case "click"          -> cmdClick(sender, args);
            case "linerotation"   -> cmdLineRotation(sender, args);
            case "disable"        -> cmdDisable(sender, args);
            case "enable"         -> cmdEnable(sender, args);
            case "swaplines"      -> cmdSwapLines(sender, args);
            case "linepermission" -> cmdLinePermission(sender, args);
            case "center"         -> cmdCenter(sender, args);
            case "lineinfo"       -> cmdLineInfo(sender, args);
            case "flag"           -> cmdFlag(sender, args);
            case "reload"         -> cmdReload(sender, args);
            case "language"       -> cmdLanguage(sender, args);
            case "help"           -> cmdHelp(sender, args);
            default               -> plugin.getLang().send(sender, "unknown-subcommand");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (!sender.hasPermission(PERM_ADMIN)) return List.of();
        return ALL_SUBS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission(PERM_ADMIN)) return List.of();

        return switch (sub) {
            case "delete", "info", "move", "tp", "addline",
                    "rename", "clone", "center"    -> tabHolograms(args, 2);
            case "disable"                         -> tabByState(args, false);
            case "enable"                          -> tabByState(args, true);
            case "setline", "removeline", "insertline",
                    "lineheight", "linexoffset", "linezoffset",
                    "lineanim", "linerotation", "swaplines",
                    "linepermission", "lineinfo"   -> tabLineCommands(args);
            case "display"                         -> tabDisplay(args);
            case "show", "hide"                    -> tabShowHide(args);
            case "near"                            -> args.length == 2 ? List.of("10", "20", "50") : List.of();
            case "tag"                             -> tabTag(args);
            case "page"                            -> tabPage(args);
            case "click"                           -> tabClick(args);
            case "flag"                            -> tabFlag(args);
            case "language"                        -> tabLanguage(args);
            default                                -> List.of();
        };
    }


    // Command handlers


    private void cmdCreate(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "/dh create <name> [text...]"); return; }
        String name = args[1];
        if (plugin.getHologramManager().exists(name)) {
            plugin.getLang().send(sender, "hologram-already-exists", Map.of("name", name)); return;
        }
        Hologram h = plugin.getHologramManager().createHologram(name, ((Player) sender).getEyeLocation());
        if (args.length >= 3) {
            h.addLine(buildLine(h, 0, join(args, 2), 0.0));
            plugin.getHologramManager().saveHologram(h);
        }
        plugin.getLang().send(sender, "hologram-created", Map.of("name", h.getName()));
    }

    private void cmdDelete(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "/dh delete <name>"); return; }
        String name = args[1];
        if (!plugin.getHologramManager().exists(name)) {
            plugin.getLang().send(sender, "hologram-not-found", Map.of("name", name)); return;
        }
        plugin.getHologramManager().deleteHologram(name);
        plugin.getLang().send(sender, "hologram-deleted", Map.of("name", name));
    }

    private void cmdList(CommandSender sender, String[] args) {
        Collection<Hologram> all = plugin.getHologramManager().getAllHolograms();
        if (all.isEmpty()) { plugin.getLang().send(sender, "hologram-list-empty"); return; }
        plugin.getLang().send(sender, "hologram-list-header", Map.of("count", String.valueOf(all.size())));
        for (Hologram h : all) {
            Location loc = h.getLocation();
            plugin.getLang().send(sender, "hologram-list-entry", Map.of(
                    "name", h.getName(),
                    "world", loc.getWorld() != null ? loc.getWorld().getName() : "?",
                    "x", String.format("%.1f", loc.getX()),
                    "y", String.format("%.1f", loc.getY()),
                    "z", String.format("%.1f", loc.getZ())
            ));
        }
    }

    private void cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "/dh info <name>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        Location loc = h.getLocation();
        plugin.getLang().send(sender, "hologram-info-header", Map.of("name", h.getName()));
        plugin.getLang().send(sender, "hologram-info-location", Map.of(
                "world", loc.getWorld() != null ? loc.getWorld().getName() : "?",
                "x", String.format("%.2f", loc.getX()),
                "y", String.format("%.2f", loc.getY()),
                "z", String.format("%.2f", loc.getZ())
        ));
        plugin.getLang().send(sender, "hologram-info-lines", Map.of("count", String.valueOf(h.getAllLines().size())));
        if (h.getPageCount() > 1)
            plugin.getLang().send(sender, "hologram-info-pages", Map.of("count", String.valueOf(h.getPageCount())));
        plugin.getLang().send(sender, "hologram-info-range",
                Map.of("range", String.format("%.1f", h.getDisplaySettings().getRange())));
        plugin.getLang().send(sender, "hologram-info-refresh",
                Map.of("ticks", String.valueOf(h.getRefreshTicks())));
        plugin.getLang().send(sender, "hologram-info-tag",
                Map.of("tag", h.getTag() != null ? h.getTag() : "<none>"));
        plugin.getLang().send(sender, "hologram-info-permission",
                Map.of("permission", h.getViewPermission() != null ? h.getViewPermission() : "<none>"));
    }

    private void cmdMove(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "/dh move <name>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        h.setLocation(((Player) sender).getEyeLocation());
        respawn(h);
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "hologram-moved", Map.of("name", h.getName()));
    }

    private void cmdTp(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "/dh tp <name>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        ((Player) sender).teleport(h.getLocation());
        plugin.getLang().send(sender, "hologram-teleport", Map.of("name", h.getName()));
    }

    private void cmdAddLine(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/dh addline <name> <content>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        h.addLine(buildLine(h, h.getLines().size(), join(args, 2), 0.0));
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "line-added", Map.of("name", h.getName()));
    }

    private void cmdSetLine(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/dh setline <name> <#> <content>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int idx = lineIdx(sender, h, args[2]); if (idx < 0) return;
        double offset = h.getLines().get(idx).getHeightOffset();
        h.setLine(idx, buildLine(h, idx, join(args, 3), offset));
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "line-set", Map.of("name", h.getName(), "index", String.valueOf(idx + 1)));
    }

    private void cmdRemoveLine(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/dh removeline <name> <#>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int idx = lineIdx(sender, h, args[2]); if (idx < 0) return;
        h.removeLine(idx);
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "line-removed", Map.of("name", h.getName(), "index", String.valueOf(idx + 1)));
    }

    private void cmdInsertLine(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/dh insertline <name> <#> <content>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int idx;
        try { idx = Integer.parseInt(args[2]) - 1; }
        catch (NumberFormatException e) {
            plugin.getLang().send(sender, "line-invalid-index",
                    Map.of("index", args[2], "count", String.valueOf(h.getLines().size()))); return;
        }
        h.insertLine(idx, buildLine(h, idx, join(args, 3), 0.0));
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "line-inserted", Map.of("name", h.getName(), "index", String.valueOf(idx + 1)));
    }

    private void cmdLineHeight(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/dh lineheight <name> <#> <height>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int idx = lineIdx(sender, h, args[2]); if (idx < 0) return;
        double height;
        try { height = Double.parseDouble(args[3]); }
        catch (NumberFormatException e) {
            plugin.getLang().send(sender, "display-invalid-value", Map.of("value", args[3], "setting", "height")); return;
        }
        h.getLines().get(idx).setHeightOffset(height);
        h.recalculatePositions();
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "line-height-set",
                Map.of("index", String.valueOf(idx + 1), "height", String.valueOf(height)));
    }

    private void cmdLineXOffset(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/dh linexoffset <name> <#> <blocks>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int idx = lineIdx(sender, h, args[2]); if (idx < 0) return;
        double offset;
        try { offset = Double.parseDouble(args[3]); }
        catch (NumberFormatException e) {
            plugin.getLang().send(sender, "display-invalid-value", Map.of("value", args[3], "setting", "x-offset")); return;
        }
        h.getLines().get(idx).setXOffset(offset);
        h.recalculatePositions();
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "line-xoffset-set",
                Map.of("index", String.valueOf(idx + 1), "offset", String.valueOf(offset)));
    }

    private void cmdLineZOffset(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/dh linezoffset <name> <#> <blocks>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int idx = lineIdx(sender, h, args[2]); if (idx < 0) return;
        double offset;
        try { offset = Double.parseDouble(args[3]); }
        catch (NumberFormatException e) {
            plugin.getLang().send(sender, "display-invalid-value", Map.of("value", args[3], "setting", "z-offset")); return;
        }
        h.getLines().get(idx).setZOffset(offset);
        h.recalculatePositions();
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "line-zoffset-set",
                Map.of("index", String.valueOf(idx + 1), "offset", String.valueOf(offset)));
    }

    private void cmdLineAnim(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/dh lineanim <name> <#> <type> [params]"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int idx = lineIdx(sender, h, args[2]); if (idx < 0) return;
        HologramLine line = h.getLines().get(idx);
        if (!(line instanceof TextLine tl)) {
            plugin.getLang().send(sender, "anim-not-applicable", Map.of("index", String.valueOf(idx + 1))); return;
        }
        String typeName = args[3].toLowerCase();
        AnimationType type = AnimationType.fromString(typeName);
        if (type == AnimationType.NONE || typeName.equals("none")) {
            tl.setAnimationType(AnimationType.NONE);
            plugin.getHologramManager().saveHologram(h);
            respawn(h);
            plugin.getLang().send(sender, "anim-cleared", Map.of("index", String.valueOf(idx + 1))); return;
        }
        if (type == AnimationType.WAVE || type == AnimationType.BURN) {
            if (args.length < 6) {
                usage(sender, "/dh lineanim <name> <#> " + typeName + " <#color1> <#color2> [speed]"); return;
            }
            int c1 = TextLine.parseColorToRgb(args[4]), c2 = TextLine.parseColorToRgb(args[5]);
            if (c1 == -1 || c2 == -1) {
                plugin.getLang().send(sender, "anim-invalid-color", Map.of("value", c1 == -1 ? args[4] : args[5])); return;
            }
            tl.setAnimColor1(c1); tl.setAnimColor2(c2);
            if (args.length >= 7) tl.setAnimSpeed(parseSpeed(args[6], tl.getAnimSpeed()));
        } else if (args.length >= 5) {
            tl.setAnimSpeed(parseSpeed(args[4], tl.getAnimSpeed()));
        }
        tl.setAnimationType(type);
        plugin.getHologramManager().saveHologram(h);
        respawn(h);
        plugin.getLang().send(sender, "anim-set",
                Map.of("index", String.valueOf(idx + 1), "type", type.name().toLowerCase()));
    }

    private void cmdRename(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/dh rename <name> <newname>"); return; }
        if (plugin.getHologramManager().getHologram(args[1]) == null) {
            plugin.getLang().send(sender, "hologram-not-found", Map.of("name", args[1])); return;
        }
        if (plugin.getHologramManager().exists(args[2])) {
            plugin.getLang().send(sender, "hologram-already-exists", Map.of("name", args[2])); return;
        }
        plugin.getHologramManager().renameHologram(args[1], args[2]);
        plugin.getLang().send(sender, "hologram-renamed", Map.of("name", args[1], "newname", args[2]));
    }

    private void cmdDisplay(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/dh display <name> <setting> <value>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        DisplaySettings ds = h.getDisplaySettings();
        String setting = args[2].toLowerCase();
        boolean needReload = true;
        switch (setting) {
            case "billboard" -> {
                try {
                    Display.Billboard bb = Display.Billboard.valueOf(args[3].toUpperCase());
                    ds.setBillboard(bb);
                    plugin.getLang().send(sender, "display-billboard-set", Map.of("value", bb.name()));
                } catch (IllegalArgumentException e) {
                    plugin.getLang().send(sender, "display-invalid-value",
                            Map.of("value", args[3], "setting", "billboard")); return;
                }
            }
            case "align" -> {
                try {
                    TextDisplay.TextAlignment align = TextDisplay.TextAlignment.valueOf(args[3].toUpperCase());
                    ds.setAlignment(align);
                    plugin.getLang().send(sender, "display-alignment-set", Map.of("value", align.name()));
                } catch (IllegalArgumentException e) {
                    plugin.getLang().send(sender, "display-invalid-value",
                            Map.of("value", args[3], "setting", "align")); return;
                }
            }
            case "scale" -> {
                if (args.length < 6) { usage(sender, "/dh display <name> scale <x> <y> <z>"); return; }
                try {
                    float sx = Float.parseFloat(args[3]), sy = Float.parseFloat(args[4]), sz = Float.parseFloat(args[5]);
                    ds.setScaleX(sx); ds.setScaleY(sy); ds.setScaleZ(sz);
                    plugin.getLang().send(sender, "display-scale-set",
                            Map.of("x", args[3], "y", args[4], "z", args[5]));
                } catch (NumberFormatException e) {
                    plugin.getLang().send(sender, "display-invalid-value",
                            Map.of("value", args[3], "setting", "scale")); return;
                }
            }
            case "background" -> {
                String val = args[3];
                if (val.equalsIgnoreCase("none") || val.equalsIgnoreCase("default")) {
                    ds.setBackgroundColor(-1);
                } else {
                    try {
                        String hex = val.startsWith("#") ? val.substring(1) : val;
                        if (hex.length() == 6) hex = "FF" + hex;
                        if (hex.length() != 8) {
                            plugin.getLang().send(sender, "display-invalid-value",
                                    Map.of("value", val, "setting", "background")); return;
                        }
                        ds.setBackgroundColor((int) Long.parseLong(hex, 16));
                    } catch (NumberFormatException e) {
                        plugin.getLang().send(sender, "display-invalid-value",
                                Map.of("value", val, "setting", "background")); return;
                    }
                }
                plugin.getLang().send(sender, "display-background-set", Map.of("value", val));
            }
            case "shadow" -> {
                ds.setShadow(Boolean.parseBoolean(args[3]));
                plugin.getLang().send(sender, "display-shadow-set", Map.of("value", args[3]));
            }
            case "range" -> {
                try {
                    double range = Double.parseDouble(args[3]);
                    ds.setRange(range);
                    plugin.getLang().send(sender, "display-range-set", Map.of("value", String.valueOf(range)));
                    needReload = false;
                } catch (NumberFormatException e) {
                    plugin.getLang().send(sender, "display-invalid-value",
                            Map.of("value", args[3], "setting", "range")); return;
                }
            }
            case "linewidth" -> {
                try {
                    int lw = Integer.parseInt(args[3]);
                    if (lw < 1) throw new NumberFormatException();
                    ds.setLineWidth(lw);
                    plugin.getLang().send(sender, "display-linewidth-set", Map.of("value", args[3]));
                } catch (NumberFormatException e) {
                    plugin.getLang().send(sender, "display-invalid-value",
                            Map.of("value", args[3], "setting", "linewidth")); return;
                }
            }
            case "facing" -> {
                try {
                    float yaw = Float.parseFloat(args[3]);
                    ds.setFacing(yaw);
                    plugin.getLang().send(sender, "display-facing-set", Map.of("value", args[3]));
                } catch (NumberFormatException e) {
                    plugin.getLang().send(sender, "display-invalid-value",
                            Map.of("value", args[3], "setting", "facing")); return;
                }
            }
            case "downorigin" -> {
                ds.setDownOrigin(Boolean.parseBoolean(args[3]));
                plugin.getLang().send(sender, "display-downorigin-set", Map.of("value", args[3]));
            }
            case "updaterange" -> {
                try {
                    double ur = Double.parseDouble(args[3]);
                    ds.setUpdateRange(ur <= 0 ? -1.0 : ur);
                    plugin.getLang().send(sender, "display-updaterange-set", Map.of("value", args[3]));
                    needReload = false;
                } catch (NumberFormatException e) {
                    plugin.getLang().send(sender, "display-invalid-value",
                            Map.of("value", args[3], "setting", "updaterange")); return;
                }
            }
            default -> {
                plugin.getLang().send(sender, "display-invalid-value",
                        Map.of("value", setting, "setting", "setting name")); return;
            }
        }
        plugin.getHologramManager().saveHologram(h);
        if (needReload) h.reload();
        for (Player p : plugin.getServer().getOnlinePlayers()) h.applyVisibilityTo(p);
    }

    private void cmdShow(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/dh show <name> <player>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { plugin.getLang().send(sender, "hologram-not-found", Map.of("name", args[2])); return; }
        h.showTo(target);
        plugin.getLang().send(sender, "hologram-shown", Map.of("name", h.getName(), "player", target.getName()));
    }

    private void cmdHide(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/dh hide <name> <player>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { plugin.getLang().send(sender, "hologram-not-found", Map.of("name", args[2])); return; }
        h.hideFrom(target);
        plugin.getLang().send(sender, "hologram-hidden", Map.of("name", h.getName(), "player", target.getName()));
    }

    private void cmdClone(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/dh clone <name> <newname>"); return; }
        Hologram src = hologram(sender, args[1]); if (src == null) return;
        String newName = args[2];
        if (plugin.getHologramManager().exists(newName)) {
            plugin.getLang().send(sender, "hologram-already-exists", Map.of("name", newName)); return;
        }
        Hologram clone = plugin.getHologramManager().createHologram(newName, src.getLocation());
        clone.setDisplaySettings(src.getDisplaySettings().copy());
        clone.setRefreshTicks(src.getRefreshTicks());
        clone.setViewPermission(src.getViewPermission());
        clone.setTag(src.getTag());
        for (int p = 0; p < src.getPageCount(); p++) {
            while (clone.getPageCount() <= p) clone.addPage();
            for (HologramLine line : src.getPage(p)) {
                HologramLine nl = buildLine(clone, clone.getPage(p).size(), line.getRawContent(), line.getHeightOffset());
                nl.setXOffset(line.getXOffset());
                nl.setZOffset(line.getZOffset());
                if (line instanceof TextLine srcTl && nl instanceof TextLine newTl) {
                    newTl.setAnimationType(srcTl.getAnimationType());
                    newTl.setAnimColor1(srcTl.getAnimColor1());
                    newTl.setAnimColor2(srcTl.getAnimColor2());
                    newTl.setAnimSpeed(srcTl.getAnimSpeed());
                }
                clone.addLine(p, nl);
            }
        }
        plugin.getHologramManager().saveHologram(clone);
        plugin.getLang().send(sender, "hologram-cloned", Map.of("name", src.getName(), "newname", newName));
    }

    private void cmdNear(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "/dh near <distance>"); return; }
        double radius;
        try {
            radius = Double.parseDouble(args[1]);
            if (radius <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            plugin.getLang().send(sender, "display-invalid-value",
                    Map.of("value", args[1], "setting", "distance")); return;
        }
        Location origin = ((Player) sender).getLocation();
        double radiusSq = radius * radius;
        record Entry(Hologram hologram, double distance) {}
        List<Entry> nearby = new ArrayList<>();
        for (Hologram h : plugin.getHologramManager().getAllHolograms()) {
            Location loc = h.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(origin.getWorld())) continue;
            double d = origin.distanceSquared(loc);
            if (d <= radiusSq) nearby.add(new Entry(h, Math.sqrt(d)));
        }
        if (nearby.isEmpty()) {
            plugin.getLang().send(sender, "near-empty", Map.of("distance", String.valueOf((int) radius))); return;
        }
        nearby.sort(Comparator.comparingDouble(Entry::distance));
        plugin.getLang().send(sender, "near-header",
                Map.of("count", String.valueOf(nearby.size()), "distance", String.valueOf((int) radius)));
        for (Entry e : nearby) {
            Location loc = e.hologram().getLocation();
            plugin.getLang().send(sender, "near-entry", Map.of(
                    "name", e.hologram().getName(),
                    "distance", String.format("%.1f", e.distance()),
                    "x", String.format("%.1f", loc.getX()),
                    "y", String.format("%.1f", loc.getY()),
                    "z", String.format("%.1f", loc.getZ())
            ));
        }
    }

    private void cmdTag(CommandSender sender, String[] args) {
        if (args.length < 2) { tagUsage(sender); return; }
        switch (args[1].toLowerCase()) {
            case "list" -> {
                if (args.length < 3) { tagUsage(sender); return; }
                String tag = args[2];
                List<Hologram> tagged = plugin.getHologramManager().getByTag(tag);
                if (tagged.isEmpty()) { plugin.getLang().send(sender, "tag-list-empty", Map.of("tag", tag)); return; }
                plugin.getLang().send(sender, "tag-list-header",
                        Map.of("tag", tag, "count", String.valueOf(tagged.size())));
                for (Hologram h : tagged) plugin.getLang().send(sender, "tag-list-entry", Map.of("name", h.getName()));
            }
            case "delete" -> {
                if (args.length < 3) { tagUsage(sender); return; }
                String tag = args[2];
                List<Hologram> tagged = plugin.getHologramManager().getByTag(tag);
                if (tagged.isEmpty()) { plugin.getLang().send(sender, "tag-list-empty", Map.of("tag", tag)); return; }
                int count = tagged.size();
                List<String> names = tagged.stream().map(Hologram::getName).toList();
                for (String name : names) plugin.getHologramManager().deleteHologram(name);
                plugin.getLang().send(sender, "tag-deleted", Map.of("tag", tag, "count", String.valueOf(count)));
            }
            default -> {
                if (args.length < 3) { tagUsage(sender); return; }
                Hologram h = hologram(sender, args[1]); if (h == null) return;
                String tag = args[2];
                if (tag.equalsIgnoreCase("none") || tag.equalsIgnoreCase("clear")) {
                    h.setTag(null);
                    plugin.getHologramManager().saveHologram(h);
                    plugin.getLang().send(sender, "tag-cleared", Map.of("name", h.getName()));
                } else {
                    h.setTag(tag);
                    plugin.getHologramManager().saveHologram(h);
                    plugin.getLang().send(sender, "tag-set", Map.of("name", h.getName(), "tag", tag));
                }
            }
        }
    }

    private void cmdPage(CommandSender sender, String[] args) {
        if (args.length < 3) { pageUsage(sender); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        switch (args[2].toLowerCase()) {
            case "add" -> {
                h.addPage();
                plugin.getHologramManager().saveHologram(h);
                plugin.getLang().send(sender, "page-added",
                        Map.of("name", h.getName(), "count", String.valueOf(h.getPageCount())));
            }
            case "remove" -> {
                if (args.length < 4) { pageUsage(sender); return; }
                int pi; try { pi = Integer.parseInt(args[3]) - 1; } catch (NumberFormatException e) { pageUsage(sender); return; }
                if (h.getPageCount() <= 1) { plugin.getLang().send(sender, "page-cannot-remove-last"); return; }
                if (!h.removePage(pi)) {
                    plugin.getLang().send(sender, "page-invalid-index",
                            Map.of("index", args[3], "count", String.valueOf(h.getPageCount()))); return;
                }
                plugin.getHologramManager().saveHologram(h);
                plugin.getLang().send(sender, "page-removed", Map.of("name", h.getName(), "index", args[3]));
            }
            case "info" -> {
                plugin.getLang().send(sender, "page-info-header",
                        Map.of("name", h.getName(), "count", String.valueOf(h.getPageCount())));
                for (int i = 0; i < h.getPageCount(); i++)
                    plugin.getLang().send(sender, "page-info-entry",
                            Map.of("index", String.valueOf(i + 1), "lines", String.valueOf(h.getPage(i).size())));
            }
            default -> {
                int pi; try { pi = Integer.parseInt(args[2]) - 1; } catch (NumberFormatException e) { pageUsage(sender); return; }
                if (pi < 0 || pi >= h.getPageCount()) {
                    plugin.getLang().send(sender, "page-invalid-index",
                            Map.of("index", args[2], "count", String.valueOf(h.getPageCount()))); return;
                }
                if (args.length < 4) { pageUsage(sender); return; }
                cmdPageLineAction(sender, h, pi, args);
            }
        }
    }

    private void cmdPageLineAction(CommandSender sender, Hologram h, int pageIndex, String[] args) {
        List<HologramLine> page = h.getPage(pageIndex);
        switch (args[3].toLowerCase()) {
            case "addline" -> {
                if (args.length < 5) { usage(sender, "/dh page <name> <#page> addline <content>"); return; }
                h.addLine(pageIndex, buildLine(h, page.size(), join(args, 4), 0.0));
                plugin.getHologramManager().saveHologram(h);
                plugin.getLang().send(sender, "line-added", Map.of("name", h.getName()));
            }
            case "setline" -> {
                if (args.length < 6) { usage(sender, "/dh page <name> <#page> setline <#line> <content>"); return; }
                int li = pageLineIdx(sender, page, args[4]); if (li < 0) return;
                double offset = page.get(li).getHeightOffset();
                h.setLine(pageIndex, li, buildLine(h, li, join(args, 5), offset));
                plugin.getHologramManager().saveHologram(h);
                plugin.getLang().send(sender, "line-set", Map.of("name", h.getName(), "index", String.valueOf(li + 1)));
            }
            case "removeline" -> {
                if (args.length < 5) { usage(sender, "/dh page <name> <#page> removeline <#line>"); return; }
                int li = pageLineIdx(sender, page, args[4]); if (li < 0) return;
                h.removeLine(pageIndex, li);
                plugin.getHologramManager().saveHologram(h);
                plugin.getLang().send(sender, "line-removed", Map.of("name", h.getName(), "index", String.valueOf(li + 1)));
            }
            case "insertline" -> {
                if (args.length < 6) { usage(sender, "/dh page <name> <#page> insertline <#line> <content>"); return; }
                int li; try { li = Integer.parseInt(args[4]) - 1; } catch (NumberFormatException e) {
                    plugin.getLang().send(sender, "line-invalid-index",
                            Map.of("index", args[4], "count", String.valueOf(page.size()))); return;
                }
                h.insertLine(pageIndex, li, buildLine(h, Math.max(0, li), join(args, 5), 0.0));
                plugin.getHologramManager().saveHologram(h);
                plugin.getLang().send(sender, "line-inserted", Map.of("name", h.getName(), "index", String.valueOf(li + 1)));
            }
            default -> pageUsage(sender);
        }
    }

    private void cmdClick(CommandSender sender, String[] args) {
        if (args.length < 3) { clickUsage(sender); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        String sub = args[2].toLowerCase();
        if (sub.equals("clear")) {
            String rawType = args.length > 3 ? args[3] : "all";
            String type = normalizeClickType(rawType);
            if (type == null) { plugin.getLang().send(sender, "click-invalid-type", Map.of("value", rawType)); return; }
            if (type.equals("ALL")) h.clearAllClickActions(); else h.clearClickAction(type);
            clickFinish(h);
            plugin.getLang().send(sender, "click-cleared",
                    Map.of("name", h.getName(), "type", type.toLowerCase().replace("_", "-"))); return;
        }
        if (!sub.equals("set") || args.length < 5) { clickUsage(sender); return; }
        String type = normalizeClickType(args[3]);
        if (type == null || type.equals("ALL")) {
            plugin.getLang().send(sender, "click-invalid-type", Map.of("value", args[3])); return;
        }
        String actions = join(args, 4);
        h.setClickAction(type, actions);
        clickFinish(h);
        plugin.getLang().send(sender, "click-set",
                Map.of("name", h.getName(), "type", type.toLowerCase().replace("_", "-"), "actions", actions));
    }

    private void cmdLineRotation(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/dh linerotation <name> <#> <deg/tick> [bob]"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int idx = lineIdx(sender, h, args[2]); if (idx < 0) return;
        float speed;
        try { speed = Float.parseFloat(args[3]); }
        catch (NumberFormatException e) {
            plugin.getLang().send(sender, "display-invalid-value",
                    Map.of("value", args[3], "setting", "rotation speed")); return;
        }
        HologramLine line = h.getLines().get(idx);
        if (!(line instanceof ItemLine il)) {
            plugin.getLang().send(sender, "linerotation-not-applicable",
                    Map.of("index", String.valueOf(idx + 1))); return;
        }
        il.setRotationSpeed(speed);
        if (args.length >= 5) {
            float bob; try { bob = Float.parseFloat(args[4]); }
            catch (NumberFormatException e) {
                plugin.getLang().send(sender, "display-invalid-value",
                        Map.of("value", args[4], "setting", "bob amplitude")); return;
            }
            il.setBobAmplitude(bob);
        }
        if (il.isSpawned()) { il.despawn(); il.spawn(h.getLineLocation(idx)); }
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "linerotation-set",
                Map.of("index", String.valueOf(idx + 1), "speed", String.valueOf(speed)));
    }

    private void cmdDisable(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "/dh disable <name>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        if (h.isDisabled()) {
            plugin.getLang().send(sender, "hologram-already-disabled", Map.of("name", h.getName())); return;
        }
        h.setDisabled(true);
        for (Player p : plugin.getServer().getOnlinePlayers()) h.hideFrom(p);
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "hologram-disabled", Map.of("name", h.getName()));
    }

    private void cmdEnable(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "/dh enable <name>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        if (!h.isDisabled()) {
            plugin.getLang().send(sender, "hologram-not-disabled", Map.of("name", h.getName())); return;
        }
        h.setDisabled(false);
        for (Player p : plugin.getServer().getOnlinePlayers()) h.applyVisibilityTo(p);
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "hologram-enabled", Map.of("name", h.getName()));
    }

    private void cmdSwapLines(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/dh swaplines <name> <#1> <#2>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int a, b;
        try { a = Integer.parseInt(args[2]) - 1; b = Integer.parseInt(args[3]) - 1; }
        catch (NumberFormatException e) { usage(sender, "/dh swaplines <name> <#1> <#2>"); return; }
        int lineCount = h.getPage(0).size();
        if (a < 0 || a >= lineCount || b < 0 || b >= lineCount) {
            plugin.getLang().send(sender, "line-invalid-index",
                    Map.of("index", String.valueOf(Math.max(a, b) + 1), "count", String.valueOf(lineCount))); return;
        }
        h.swapLines(0, a, b);
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "lines-swapped",
                Map.of("name", h.getName(), "a", String.valueOf(a + 1), "b", String.valueOf(b + 1)));
    }

    private void cmdLinePermission(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/dh linepermission <name> <#> [permission]"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int li; try { li = Integer.parseInt(args[2]) - 1; }
        catch (NumberFormatException e) { usage(sender, "/dh linepermission <name> <#> [permission]"); return; }
        List<HologramLine> page = h.getPage(0);
        if (li < 0 || li >= page.size()) {
            plugin.getLang().send(sender, "line-invalid-index",
                    Map.of("index", String.valueOf(li + 1), "count", String.valueOf(page.size()))); return;
        }
        HologramLine line = page.get(li);
        if (args.length < 4) {
            line.setViewPermission(null);
            plugin.getHologramManager().saveHologram(h);
            plugin.getLang().send(sender, "line-permission-cleared",
                    Map.of("index", String.valueOf(li + 1), "name", h.getName()));
        } else {
            line.setViewPermission(args[3]);
            plugin.getHologramManager().saveHologram(h);
            plugin.getLang().send(sender, "line-permission-set",
                    Map.of("index", String.valueOf(li + 1), "name", h.getName(), "permission", args[3]));
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) h.applyVisibilityTo(p);
    }

    private void cmdCenter(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "/dh center <name>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        h.centerOnBlock();
        respawn(h);
        plugin.getHologramManager().saveHologram(h);
        plugin.getLang().send(sender, "hologram-centered", Map.of("name", h.getName()));
    }

    private void cmdLineInfo(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/dh lineinfo <name> <#>"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        int li; try { li = Integer.parseInt(args[2]) - 1; }
        catch (NumberFormatException e) { usage(sender, "/dh lineinfo <name> <#>"); return; }
        List<HologramLine> page = h.getPage(0);
        if (li < 0 || li >= page.size()) {
            plugin.getLang().send(sender, "line-invalid-index",
                    Map.of("index", String.valueOf(li + 1), "count", String.valueOf(page.size()))); return;
        }
        HologramLine line = page.get(li);
        plugin.getLang().send(sender, "line-info-header",
                Map.of("name", h.getName(), "index", String.valueOf(li + 1)));
        plugin.getLang().send(sender, "line-info-type", Map.of("type", line.getType().name()));
        plugin.getLang().send(sender, "line-info-content", Map.of("content", line.getRawContent()));
        plugin.getLang().send(sender, "line-info-height",
                Map.of("height", String.format("%.3f", line.getHeightOffset())));
        if (line.getXOffset() != 0 || line.getZOffset() != 0)
            plugin.getLang().send(sender, "line-info-offset",
                    Map.of("x", String.format("%.3f", line.getXOffset()), "z", String.format("%.3f", line.getZOffset())));
        if (line.getViewPermission() != null && !line.getViewPermission().isEmpty())
            plugin.getLang().send(sender, "line-info-permission", Map.of("permission", line.getViewPermission()));
        if (line instanceof TextLine tl && tl.getAnimationType() != AnimationType.NONE)
            plugin.getLang().send(sender, "line-info-anim",
                    Map.of("type", tl.getAnimationType().name(), "speed", String.valueOf(tl.getAnimSpeed())));
        if (line instanceof ItemLine il)
            plugin.getLang().send(sender, "line-info-rotation",
                    Map.of("speed", String.format("%.2f", il.getRotationSpeed()),
                           "bob",   String.format("%.3f", il.getBobAmplitude())));
    }

    private void cmdFlag(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/dh flag <name> add|remove|list [flag]"); return; }
        Hologram h = hologram(sender, args[1]); if (h == null) return;
        switch (args[2].toLowerCase()) {
            case "list" -> {
                Set<HologramFlag> flags = h.getFlags();
                if (flags.isEmpty()) {
                    plugin.getLang().send(sender, "flag-list-empty", Map.of("name", h.getName()));
                } else {
                    plugin.getLang().send(sender, "flag-list-header", Map.of("name", h.getName()));
                    for (HologramFlag f : flags)
                        sender.sendMessage(Component.text("  - " + f.name(), NamedTextColor.AQUA));
                }
            }
            case "add" -> {
                if (args.length < 4) { usage(sender, "/dh flag <name> add <flag>"); return; }
                HologramFlag flag = HologramFlag.fromString(args[3]);
                if (flag == null) { plugin.getLang().send(sender, "flag-invalid", Map.of("value", args[3])); return; }
                h.addFlag(flag);
                plugin.getHologramManager().saveHologram(h);
                plugin.getLang().send(sender, "flag-added", Map.of("name", h.getName(), "flag", flag.name()));
            }
            case "remove" -> {
                if (args.length < 4) { usage(sender, "/dh flag <name> remove <flag>"); return; }
                HologramFlag flag = HologramFlag.fromString(args[3]);
                if (flag == null) { plugin.getLang().send(sender, "flag-invalid", Map.of("value", args[3])); return; }
                h.removeFlag(flag);
                plugin.getHologramManager().saveHologram(h);
                plugin.getLang().send(sender, "flag-removed", Map.of("name", h.getName(), "flag", flag.name()));
            }
            default -> usage(sender, "/dh flag <name> add|remove|list [flag]");
        }
    }

    private void cmdReload(CommandSender sender, String[] args) {
        plugin.reloadConfig();
        plugin.getLang().load(plugin.getConfig().getString("language", "en_us"));
        plugin.getHologramManager().respawnAll();
        plugin.getLang().send(sender, "reload-success");
    }

    private void cmdLanguage(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "/dh language <code>"); return; }
        String code = args[1].toLowerCase();
        if (!plugin.getLang().load(code)) {
            plugin.getLang().send(sender, "language-not-found", Map.of("code", code)); return;
        }
        plugin.getLang().send(sender, "language-changed", Map.of("code", code));
    }

    private static final int HELP_PAGE_SIZE = 8;

    private void cmdHelp(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) { try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {} }
        ConfigurationSection helpCmds = plugin.getLang().getConfig().getConfigurationSection("help-commands");
        List<String> keys = helpCmds != null ? new ArrayList<>(helpCmds.getKeys(false)) : List.of();
        int total = Math.max(1, (int) Math.ceil((double) keys.size() / HELP_PAGE_SIZE));
        page = Math.max(1, Math.min(page, total));
        sender.sendMessage(plugin.getLang().get("help-header",
                Map.of("page", String.valueOf(page), "total", String.valueOf(total))));
        int start = (page - 1) * HELP_PAGE_SIZE;
        for (int i = start; i < Math.min(start + HELP_PAGE_SIZE, keys.size()); i++) {
            String key = keys.get(i);
            List<String> entry = plugin.getLang().getConfig().getStringList("help-commands." + key);
            if (entry.size() < 2) continue;
            String argStr = entry.get(0);
            String displayArgs = argStr.contains(" ") ? argStr.substring(argStr.indexOf(' ') + 1) : "";
            sender.sendMessage(plugin.getLang().get("help-entry", Map.of(
                    "command", "/dh " + key,
                    "args", displayArgs.isEmpty() ? "" : " " + displayArgs,
                    "description", entry.get(1)
            )));
        }
        if (page < total)
            sender.sendMessage(plugin.getLang().get("help-footer", Map.of("next", String.valueOf(page + 1))));
    }


    // Tab completers


    private List<String> tabHolograms(String[] args, int pos) {
        if (args.length != pos) return List.of();
        String prefix = args[pos - 1].toLowerCase();
        return plugin.getHologramManager().getAllHolograms().stream()
                .map(Hologram::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }

    private List<String> tabByState(String[] args, boolean disabled) {
        if (args.length != 2) return List.of();
        String prefix = args[1].toLowerCase();
        return plugin.getHologramManager().getAllHolograms().stream()
                .filter(h -> h.isDisabled() == disabled)
                .map(Hologram::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }

    private List<String> tabLineCommands(String[] args) {
        if (args.length == 2) return tabHolograms(args, 2);
        if (args.length == 3) {
            Hologram h = plugin.getHologramManager().getHologram(args[1]);
            if (h == null) return List.of();
            List<String> nums = new ArrayList<>();
            for (int i = 1; i <= h.getLines().size(); i++) nums.add(String.valueOf(i));
            return nums.stream().filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
        }
        String sub = args[0].toLowerCase();
        if (args.length == 4) {
            if (sub.equals("linexoffset") || sub.equals("linezoffset"))
                return List.of("0", "-1", "-0.5", "0.5", "1");
            if (sub.equals("lineanim"))
                return ANIM_TYPES.stream().filter(t -> t.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
            if (sub.equals("linerotation"))
                return List.of("0", "1", "2", "3", "5", "10").stream()
                        .filter(s -> s.startsWith(args[3])).collect(Collectors.toList());
        }
        if (sub.equals("lineanim")) {
            boolean twoColor = args[3].equalsIgnoreCase("wave") || args[3].equalsIgnoreCase("burn");
            if (args.length == 5 && twoColor)
                return List.of("#FF5555", "#55FF55", "#5555FF", "#FFFF55", "#FF55FF", "#55FFFF", "white", "red", "blue");
            if (args.length == 6 && twoColor)
                return List.of("#5555FF", "#55FFFF", "#55FF55", "blue", "aqua", "green");
            if (args.length == 5 && List.of("rainbow", "typewriter", "scroll").contains(args[3].toLowerCase()))
                return List.of("1", "2", "3", "5");
            if (args.length == 7 && twoColor)
                return List.of("1", "2", "3", "5");
        }
        if (sub.equals("linerotation") && args.length == 5)
            return List.of("0", "0.05", "0.08", "0.1", "0.15").stream()
                    .filter(s -> s.startsWith(args[4])).collect(Collectors.toList());
        return List.of();
    }

    private List<String> tabDisplay(String[] args) {
        if (args.length == 2) return tabHolograms(args, 2);
        if (args.length == 3)
            return DISPLAY_SETTINGS.stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        if (args.length == 4) return switch (args[2].toLowerCase()) {
            case "billboard"   -> BILLBOARDS.stream().filter(s -> s.startsWith(args[3].toUpperCase())).collect(Collectors.toList());
            case "align"       -> ALIGNMENTS.stream().filter(s -> s.startsWith(args[3].toUpperCase())).collect(Collectors.toList());
            case "shadow", "downorigin" -> List.of("true", "false");
            case "background"  -> List.of("none", "#FF0000", "#00FF00", "#0000FF");
            case "linewidth"   -> List.of("200", "400", "600", "1000");
            case "facing"      -> List.of("0", "90", "180", "270");
            case "updaterange" -> List.of("16", "32", "48", "64");
            default            -> List.of();
        };
        return List.of();
    }

    private List<String> tabShowHide(String[] args) {
        if (args.length == 2) return tabHolograms(args, 2);
        if (args.length == 3) {
            String prefix = args[2].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private List<String> tabTag(String[] args) {
        if (args.length == 2) {
            List<String> opts = new ArrayList<>(List.of("list", "delete"));
            plugin.getHologramManager().getAllHolograms().forEach(h -> opts.add(h.getName()));
            return opts.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3) {
            String sub = args[1].toLowerCase();
            if (sub.equals("list") || sub.equals("delete")) {
                return plugin.getHologramManager().getAllHolograms().stream()
                        .map(Hologram::getTag).filter(Objects::nonNull)
                        .filter(t -> t.toLowerCase().startsWith(args[2].toLowerCase()))
                        .distinct().collect(Collectors.toList());
            }
            List<String> sug = new ArrayList<>(List.of("none"));
            plugin.getHologramManager().getAllHolograms().stream()
                    .map(Hologram::getTag).filter(Objects::nonNull).distinct().forEach(sug::add);
            return sug.stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        return List.of();
    }

    private List<String> tabPage(String[] args) {
        if (args.length == 2) return tabHolograms(args, 2);
        if (args.length == 3) {
            List<String> opts = new ArrayList<>(List.of("add", "remove", "info"));
            Hologram h = plugin.getHologramManager().getHologram(args[1]);
            if (h != null) for (int i = 1; i <= h.getPageCount(); i++) opts.add(String.valueOf(i));
            return opts.stream().filter(o -> o.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("remove")) {
            Hologram h = plugin.getHologramManager().getHologram(args[1]);
            if (h != null) {
                List<String> pages = new ArrayList<>();
                for (int i = 1; i <= h.getPageCount(); i++) pages.add(String.valueOf(i));
                return pages.stream().filter(p -> p.startsWith(args[3])).collect(Collectors.toList());
            }
        }
        if (args.length == 4)
            return List.of("addline", "setline", "removeline", "insertline").stream()
                    .filter(s -> s.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
        return List.of();
    }

    private List<String> tabClick(String[] args) {
        if (args.length == 2) return tabHolograms(args, 2);
        if (args.length == 3)
            return List.of("set", "clear").stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        if (args.length == 4) {
            List<String> opts = args[2].equalsIgnoreCase("clear")
                    ? List.of("right", "left", "shift-right", "shift-left", "all") : CLICK_TYPES;
            return opts.stream().filter(s -> s.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
        }
        return List.of();
    }

    private List<String> tabFlag(String[] args) {
        if (args.length == 2) return tabHolograms(args, 2);
        if (args.length == 3)
            return List.of("add", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        if (args.length == 4 && !args[2].equalsIgnoreCase("list"))
            return Arrays.stream(HologramFlag.values()).map(HologramFlag::name)
                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase())).collect(Collectors.toList());
        return List.of();
    }

    private List<String> tabLanguage(String[] args) {
        if (args.length != 2) return List.of();
        File transDir = new File(plugin.getDataFolder(), "translations");
        if (!transDir.exists()) return List.of();
        File[] files = transDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return List.of();
        return Arrays.stream(files).map(f -> f.getName().replace(".yml", ""))
                .filter(n -> n.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
    }


    // Helpers


    private Hologram hologram(CommandSender sender, String name) {
        Hologram h = plugin.getHologramManager().getHologram(name);
        if (h == null) plugin.getLang().send(sender, "hologram-not-found", Map.of("name", name));
        return h;
    }

    private int lineIdx(CommandSender sender, Hologram h, String raw) {
        int idx;
        try { idx = Integer.parseInt(raw) - 1; }
        catch (NumberFormatException e) {
            plugin.getLang().send(sender, "line-invalid-index",
                    Map.of("index", raw, "count", String.valueOf(h.getLines().size())));
            return -1;
        }
        if (idx < 0 || idx >= h.getLines().size()) {
            plugin.getLang().send(sender, "line-invalid-index",
                    Map.of("index", raw, "count", String.valueOf(h.getLines().size())));
            return -1;
        }
        return idx;
    }

    private int pageLineIdx(CommandSender sender, List<HologramLine> page, String raw) {
        int idx;
        try { idx = Integer.parseInt(raw) - 1; }
        catch (NumberFormatException e) {
            plugin.getLang().send(sender, "line-invalid-index",
                    Map.of("index", raw, "count", String.valueOf(page.size())));
            return -1;
        }
        if (idx < 0 || idx >= page.size()) {
            plugin.getLang().send(sender, "line-invalid-index",
                    Map.of("index", raw, "count", String.valueOf(page.size())));
            return -1;
        }
        return idx;
    }

    private void respawn(Hologram h) {
        h.reload();
        for (Player p : plugin.getServer().getOnlinePlayers()) h.applyVisibilityTo(p);
    }

    private void clickFinish(Hologram h) {
        h.reload();
        for (Player p : plugin.getServer().getOnlinePlayers()) h.applyVisibilityTo(p);
        plugin.getHologramManager().saveHologram(h);
    }

    private void usage(CommandSender sender, String str) {
        plugin.getLang().send(sender, "invalid-usage", Map.of("usage", str));
    }

    private void tagUsage(CommandSender s) {
        usage(s, "/dh tag <name> <tag|none>  |  /dh tag list <tag>  |  /dh tag delete <tag>");
    }

    private void pageUsage(CommandSender s) {
        usage(s, "/dh page <name> <add|remove <#>|info|<#page> <addline|setline|removeline|insertline> ...>");
    }

    private void clickUsage(CommandSender s) {
        usage(s, "/dh click <name> set <right|left|shift-right|shift-left> <actions> | clear [type|all]");
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private static HologramLine buildLine(Hologram hologram, int index, String content, double offset) {
        if (content.toLowerCase().startsWith("item:")) {
            ItemLine il = new ItemLine(hologram, index, content.substring(5).trim(), offset);
            il.setRotationSpeed(3.0f);
            il.setBobAmplitude(0.08f);
            return il;
        }
        return new TextLine(hologram, index, content, offset);
    }

    private static int parseSpeed(String s, int fallback) {
        try { return Math.max(1, Integer.parseInt(s)); } catch (NumberFormatException e) { return fallback; }
    }

    private static String normalizeClickType(String input) {
        return switch (input.toLowerCase().replace("-", "_")) {
            case "right"       -> "RIGHT";
            case "left"        -> "LEFT";
            case "shift_right" -> "SHIFT_RIGHT";
            case "shift_left"  -> "SHIFT_LEFT";
            case "all"         -> "ALL";
            default            -> null;
        };
    }
}
