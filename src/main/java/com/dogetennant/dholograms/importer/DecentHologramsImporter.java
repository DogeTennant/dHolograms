package com.dogetennant.dholograms.importer;

import com.dogetennant.dholograms.hologram.DisplaySettings;
import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.HologramFlag;
import com.dogetennant.dholograms.hologram.line.AnimationType;
import com.dogetennant.dholograms.hologram.line.HologramLine;
import com.dogetennant.dholograms.hologram.line.ItemLine;
import com.dogetennant.dholograms.hologram.line.TextLine;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyFormat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns DecentHolograms holograms ({@code plugins/DecentHolograms/holograms/*.yml}) into dHolograms
 * ones. Only the files are read, so DecentHolograms does not have to be installed - and should be
 * removed afterwards, or both show the same holograms.
 *
 * <p>Kept: location, pages, text (colour formats and DecentHolograms' custom replacements
 * converted), items and heads, the spacing (DecentHolograms gives every line its own height; here
 * it becomes each line's offset), x/z offsets, line and hologram permissions, view and update
 * range, update interval, facing, flags, enabled, and click actions. Left out, each with a
 * warning: mob lines, per-page actions (dHolograms has one set per hologram: the first page's),
 * animations dHolograms does not have, item NBT and HeadDatabase heads. DecentHolograms 2.10
 * "displays" ({@code displays/}) are a different format and are only counted.
 */
public final class DecentHologramsImporter {

    // MySQLStorage's column sizes: longer would make the save fail
    static final int MAX_NAME = 64;
    static final int MAX_PERMISSION = 128;
    static final int MAX_ACTIONS = 512;

    private static final List<String> CLICK_TYPES = List.of("LEFT", "RIGHT", "SHIFT_LEFT", "SHIFT_RIGHT");
    private static final Pattern GRADIENT = Pattern.compile("<#([0-9a-fA-F]{6})>(.*?)</#([0-9a-fA-F]{6})>");
    private static final Pattern IRIDIUM_GRADIENT = Pattern.compile("<GRADIENT:([0-9a-fA-F]{6})>(.*?)</GRADIENT:([0-9a-fA-F]{6})>", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAINBOW = Pattern.compile("<RAINBOW\\d*>(.*?)</RAINBOW>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOLID = Pattern.compile("<SOLID:([0-9a-fA-F]{6})>", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMP_HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern BRACE_HEX = Pattern.compile("#?\\{#?([0-9a-fA-F]{6})}");
    private static final Pattern BUKKIT_HEX = Pattern.compile("&x((?:&[0-9a-fA-F]){6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANIMATION = Pattern.compile("<#ANIM:([^>]+)>(.*?)</#ANIM>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DH_PLACEHOLDER = Pattern.compile("\\{[a-z_]+(?::[^}]*)?}");
    private static final Map<String, String> PLACEHOLDERS = Map.of(
            "{player}", "%player_name%",
            "{online}", "%server_online%",
            "{max_online}", "%server_max_players%");

    /**
     * DecentHolograms' {@code config.yml} defaults (used where a file leaves a value out),
     * its custom replacements, and how a sound name becomes a sound key.
     */
    public record Options(double textHeight, double iconHeight, double headHeight, double smallheadHeight,
                          int displayRange, int updateRange, int updateInterval, boolean downOrigin,
                          Map<String, String> replacements, Function<String, String> soundKeys) {

        /** DecentHolograms' own defaults, with its default replacements. */
        public static Options defaults(Function<String, String> soundKeys) {
            return from(new YamlConfiguration(), soundKeys);
        }

        /** Reads DecentHolograms' config.yml (an empty one gives its defaults). */
        public static Options from(YamlConfiguration config, Function<String, String> soundKeys) {
            Map<String, String> replacements = new LinkedHashMap<>();
            ConfigurationSection section = config.getConfigurationSection("custom-replacements");
            if (section != null) {
                for (String key : section.getKeys(false)) replacements.put(key, section.getString(key, ""));
            } else {
                replacements.putAll(Map.of("[x]", "█", "[X]", "█", "[/]", "▌", "[,]", "░", "[,,]", "▒",
                        "[,,,]", "▓", "[p]", "•", "[P]", "•", "[|]", "⎹"));
            }
            return new Options(
                    config.getDouble("defaults.height.text", 0.3),
                    config.getDouble("defaults.height.icon", 0.6),
                    config.getDouble("defaults.height.head", 0.75),
                    config.getDouble("defaults.height.smallhead", 0.6),
                    config.getInt("defaults.display-range", 48),
                    config.getInt("defaults.update-range", 48),
                    config.getInt("defaults.update-interval", 20),
                    config.getBoolean("defaults.down-origin", false),
                    replacements, soundKeys);
        }
    }

    /** One converted hologram, or why it was left out. */
    public record Converted(Hologram hologram, String skipped, List<String> warnings) {}

    /** What an import did, for the admin. {@code displays}: DecentHolograms displays not imported. */
    public record Report(List<String> imported, List<String> skipped, List<String> warnings, int displays) {}

    private DecentHologramsImporter() {}

    /**
     * Imports every hologram of the DecentHolograms data folder {@code decentFolder}. {@code exists}
     * says whether dHolograms already has a hologram of that name (those are left alone); {@code add}
     * registers a converted one. Null when there is no {@code holograms} folder.
     */
    public static Report importFolder(File decentFolder, Predicate<String> exists, Consumer<Hologram> add) {
        File config = new File(decentFolder, "config.yml");
        Options options = Options.from(config.isFile() ? YamlConfiguration.loadConfiguration(config) : new YamlConfiguration(),
                DecentHologramsImporter::bukkitSoundKey);
        return importFolder(decentFolder, options, exists, add);
    }

    static Report importFolder(File decentFolder, Options options, Predicate<String> exists, Consumer<Hologram> add) {
        File[] files = new File(decentFolder, "holograms").listFiles((dir, file) -> file.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return null;
        List<String> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Arrays.sort(files);
        for (File file : files) {
            String name = nameOf(file.getName());
            if (name.isEmpty()) {
                skipped.add(file.getName() + ": no name");
                continue;
            }
            if (exists.test(name)) {
                skipped.add(name + ": a dHolograms hologram of that name exists already");
                continue;
            }
            Converted result;
            try {
                result = convert(name, YamlConfiguration.loadConfiguration(file), options);
            } catch (RuntimeException e) {
                skipped.add(name + ": " + e);
                continue;
            }
            warnings.addAll(result.warnings());
            if (result.hologram() == null) {
                skipped.add(name + ": " + result.skipped());
                continue;
            }
            add.accept(result.hologram());
            imported.add(name);
        }
        File[] displays = new File(decentFolder, "displays").listFiles((dir, file) -> file.toLowerCase(Locale.ROOT).endsWith(".yml"));
        return new Report(imported, skipped, warnings, displays == null ? 0 : displays.length);
    }

    /** A file's hologram name, as DecentHolograms reads it (without {@code hologram_} and {@code .yml}), made safe. */
    static String nameOf(String fileName) {
        String name = fileName.substring(0, fileName.length() - 4);
        if (name.toLowerCase(Locale.ROOT).startsWith("hologram_") && name.length() > "hologram_".length()) {
            name = name.substring("hologram_".length());
        }
        // a dot would split the key in holograms.yml; spaces would not work in commands
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.length() > MAX_NAME ? safe.substring(0, MAX_NAME) : safe;
    }

    /** One DecentHolograms file as a dHolograms hologram (not registered, saved or spawned). */
    public static Converted convert(String name, YamlConfiguration file, Options options) {
        List<String> warnings = new ArrayList<>();
        String locationText = file.getString("location");
        Location location = location(locationText);
        if (location == null) {
            return new Converted(null, "location '" + locationText + "' has no loaded world", warnings);
        }

        Hologram hologram = new Hologram(name, location);
        DisplaySettings ds = hologram.getDisplaySettings();
        ds.setLineHeight(options.textHeight());
        ds.setRange(file.getInt("display-range", options.displayRange()));
        ds.setUpdateRange(file.getInt("update-range", options.updateRange()));
        ds.setFacing((float) file.getDouble("facing", 0.0));
        ds.setDownOrigin(false);             // the offsets below put every line where it was
        ds.setUnifiedBackground(false);      // one display per line, so each keeps its own place
        ds.setLineWidth(2000);               // DecentHolograms' lines never wrap
        hologram.setRefreshTicks(file.getInt("update-interval", options.updateInterval()));
        hologram.setDisabled(file.isBoolean("enabled") && !file.getBoolean("enabled"));
        String permission = permission(file.getString("permission"), name, warnings);
        if (permission != null) hologram.setViewPermission(permission);
        for (String flag : file.getStringList("flags")) {
            HologramFlag converted = HologramFlag.fromString(flag);
            if (converted != null) hologram.addFlag(converted);
            else warnings.add(name + ": flag " + flag + " left out");
        }
        boolean downOrigin = file.isBoolean("down-origin") ? file.getBoolean("down-origin") : options.downOrigin();

        Map<String, String> actions = new LinkedHashMap<>();
        int pageNumber = 0;
        int target = 0;
        for (Map<?, ?> page : pages(file)) {
            pageNumber++;
            List<HologramLine> lines = lines(hologram, lines(page), downOrigin, options, warnings);
            if (lines.isEmpty()) {
                warnings.add(name + ": page " + pageNumber + " has nothing dHolograms can show - left out");
                continue;
            }
            for (HologramLine line : lines) hologram.addLineInternal(target, line);   // no spawning yet
            target++;
            Map<String, String> pageActions = actions(page.get("actions"), name, options, warnings);
            if (pageActions.isEmpty()) continue;
            if (actions.isEmpty()) actions.putAll(pageActions);
            else if (!actions.equals(pageActions)) {
                warnings.add(name + ": page " + pageNumber + " has its own click actions; dHolograms has one set per hologram (the first page's kept)");
            }
        }
        actions.forEach(hologram::setClickAction);
        if (target == 0) return new Converted(null, "no lines dHolograms can show", warnings);
        return new Converted(hologram, null, warnings);
    }

    /** {@code world:x:y:z} (commas as decimal points allowed, world by name or UUID). */
    static Location location(String text) {
        if (text == null || text.isBlank()) return null;
        String[] parts = text.replace(",", ".").split(":");
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            try {
                world = Bukkit.getWorld(UUID.fromString(parts[0]));
            } catch (IllegalArgumentException ignored) {
                // a name
            }
        }
        if (world == null) return null;
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The pages as maps; the oldest files had {@code lines.1}, {@code lines.2} ... and no pages. */
    private static List<Map<?, ?>> pages(YamlConfiguration file) {
        if (file.contains("pages")) return file.getMapList("pages");
        ConfigurationSection old = file.getConfigurationSection("lines");
        if (old == null) return List.of();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 1; old.isConfigurationSection(String.valueOf(i)); i++) {
            lines.add(old.getConfigurationSection(String.valueOf(i)).getValues(false));
        }
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("lines", lines);
        return List.of(page);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lines(Map<?, ?> page) {
        if (!(page.get("lines") instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object line : list) {
            if (line instanceof Map<?, ?> map) out.add((Map<String, Object>) map);
        }
        return out;
    }

    /**
     * DecentHolograms puts each line {@code height} below the one before it, starting at the
     * location (or one page height above it with down-origin on). dHolograms puts line i at
     * i line heights below the location plus the line's offset, so the offset is what puts every
     * line exactly where it was.
     */
    private static List<HologramLine> lines(Hologram hologram, List<Map<String, Object>> raws,
                                            boolean downOrigin, Options options, List<String> warnings) {
        double pageHeight = 0;
        for (Map<String, Object> raw : raws) pageHeight += height(raw, options);
        double y = downOrigin ? pageHeight : 0;            // this line, relative to the location
        List<HologramLine> out = new ArrayList<>();
        for (Map<String, Object> raw : raws) {
            String content = String.valueOf(raw.getOrDefault("content", ""));
            HologramLine line = line(hologram, out.size(), content, options, warnings);
            if (line != null) {
                line.setHeightOffset(Math.round((y + out.size() * options.textHeight()) * 10_000) / 10_000.0);
                line.setXOffset(number(raw.get("offsetX")));
                line.setZOffset(number(raw.get("offsetZ")));
                String permission = permission(raw.get("permission") instanceof String p ? p : null, hologram.getName(), warnings);
                if (permission != null) line.setViewPermission(permission);
                if (raw.get("flags") instanceof List<?> flags && !flags.isEmpty()) {
                    warnings.add(hologram.getName() + ": line '" + content + "' has its own flags " + flags + "; dHolograms has flags per hologram only");
                }
                out.add(line);
            }
            y -= height(raw, options);
        }
        return out;
    }

    /** A line's height: the saved one (DecentHolograms saves it for every line), else its type's default. */
    private static double height(Map<String, Object> line, Options options) {
        if (line.get("height") instanceof Number number) return number.doubleValue();
        String content = String.valueOf(line.getOrDefault("content", "")).toUpperCase(Locale.ROOT);
        if (content.startsWith("#ICON:")) return options.iconHeight();
        if (content.startsWith("#SMALLHEAD:")) return options.smallheadHeight();
        if (content.startsWith("#HEAD:")) return options.headHeight();
        return options.textHeight();
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static String permission(String permission, String name, List<String> warnings) {
        if (permission == null || permission.isBlank()) return null;
        if (permission.length() > MAX_PERMISSION) {
            warnings.add(name + ": permission '" + permission + "' is longer than " + MAX_PERMISSION + " characters - left out");
            return null;
        }
        return permission.trim();
    }

    /** A DecentHolograms line as a dHolograms one; null (with a warning) when there is none. */
    static HologramLine line(Hologram hologram, int index, String content, Options options, List<String> warnings) {
        String upper = content.toUpperCase(Locale.ROOT);
        if (upper.startsWith("#ICON:")) {
            ItemLine icon = new ItemLine(hologram, index, item(content.substring(6), hologram, warnings), 0);
            icon.setRotationSpeed(3.0f);          // DecentHolograms' icons float and turn
            icon.setBobAmplitude(0.08f);
            return icon;
        }
        if (upper.startsWith("#SMALLHEAD:")) return new ItemLine(hologram, index, item(content.substring(11), hologram, warnings), 0);
        if (upper.startsWith("#HEAD:")) return new ItemLine(hologram, index, item(content.substring(6), hologram, warnings), 0);
        if (upper.startsWith("#ENTITY:")) {
            warnings.add(hologram.getName() + ": mob line '" + content.trim() + "' left out (dHolograms' entity lines are blocks)");
            return null;
        }
        TextLine text = new TextLine(hologram, index, "", 0);
        Matcher animation = ANIMATION.matcher(content);
        if (animation.matches()) {
            animate(text, animation.group(1), hologram.getName(), warnings);
            content = animation.group(2);
        } else if (animation.find()) {
            warnings.add(hologram.getName() + ": animation inside the line '" + content + "' left out");
            content = ANIMATION.matcher(content).replaceAll("$2");
        }
        text.setRawContent(placeholders(text(replace(content, options)), hologram.getName(), warnings));
        return text;
    }

    /** {@code name[:colour1,colour2]}: DecentHolograms' animations that dHolograms also has keep going. */
    private static void animate(TextLine text, String spec, String name, List<String> warnings) {
        String[] parts = spec.split(":", 2);
        AnimationType type = AnimationType.fromString(parts[0].trim());
        if (type == AnimationType.NONE) {
            warnings.add(name + ": animation '" + parts[0].trim() + "' left out (dHolograms has rainbow, wave, burn, typewriter, scroll)");
            return;
        }
        text.setAnimationType(type);
        if (parts.length < 2) return;
        String[] colours = parts[1].split(",");
        int first = colour(colours[0]);
        if (first >= 0) text.setAnimColor1(first);
        if (colours.length > 1) {
            int second = colour(colours[1]);
            if (second >= 0) text.setAnimColor2(second);
        }
    }

    /** {@code &e}, {@code #RRGGBB}, {@code &#RRGGBB} or a colour name as RGB; -1 when it is none. */
    static int colour(String raw) {
        String value = raw.trim();
        if (value.length() == 2 && (value.charAt(0) == '&' || value.charAt(0) == '§')) {
            LegacyFormat format = LegacyComponentSerializer.parseChar(value.charAt(1));
            TextColor colour = format != null ? format.color() : null;
            return colour != null ? colour.value() : -1;
        }
        if (value.startsWith("&#")) value = value.substring(1);
        return TextLine.parseColorToRgb(value);
    }

    private static String replace(String content, Options options) {
        String out = content;
        for (Map.Entry<String, String> replacement : options.replacements().entrySet()) {
            out = out.replace(replacement.getKey(), replacement.getValue());
        }
        return out;
    }

    /**
     * DecentHolograms' colour formats in dHolograms' (MiniMessage and &-codes): gradients
     * {@code <#AAAAAA>text</#BBBBBB>} and {@code <GRADIENT:AAAAAA>text</GRADIENT:BBBBBB>},
     * {@code <RAINBOW1>text</RAINBOW>}, hex {@code &#RRGGBB}, {@code {#RRGGBB}}, {@code <SOLID:RRGGBB>}
     * and Bukkit's {@code &x&R&R&G&G&B&B}.
     */
    static String text(String content) {
        String text = content.replace('§', '&');
        text = BUKKIT_HEX.matcher(text).replaceAll(match -> "<#" + match.group(1).replace("&", "") + ">");
        text = GRADIENT.matcher(text).replaceAll("<gradient:#$1:#$3>$2</gradient>");
        text = IRIDIUM_GRADIENT.matcher(text).replaceAll("<gradient:#$1:#$3>$2</gradient>");
        text = RAINBOW.matcher(text).replaceAll("<rainbow>$1</rainbow>");
        text = SOLID.matcher(text).replaceAll("<#$1>");
        text = AMP_HEX.matcher(text).replaceAll("<#$1>");
        return BRACE_HEX.matcher(text).replaceAll("<#$1>");
    }

    /** DecentHolograms' own placeholders as PlaceholderAPI ones; a warning for the rest. */
    private static String placeholders(String text, String name, List<String> warnings) {
        String out = text;
        for (Map.Entry<String, String> placeholder : PLACEHOLDERS.entrySet()) {
            if (out.contains(placeholder.getKey())) {
                out = out.replace(placeholder.getKey(), placeholder.getValue());
                warnings.add(name + ": " + placeholder.getKey() + " is now " + placeholder.getValue()
                        + " (needs PlaceholderAPI with its " + (placeholder.getKey().equals("{player}") ? "Player" : "Server") + " expansion)");
            }
        }
        Matcher other = DH_PLACEHOLDER.matcher(out);
        while (other.find()) {
            warnings.add(name + ": " + other.group() + " is a DecentHolograms placeholder dHolograms does not know - shown as it is");
        }
        return out;
    }

    /**
     * DecentHolograms' item text ({@code MATERIAL[:data] (extras) {nbt} !ENCHANTED}) as dHolograms'.
     * A head's extras are a player name (up to 16 characters) or a skin texture.
     */
    static String item(String raw, Hologram hologram, List<String> warnings) {
        String text = raw;
        String extras = null;
        int open = text.indexOf('(');
        int close = text.lastIndexOf(')');
        if (open > 0 && close > open) {
            extras = text.substring(open + 1, close).trim();
            text = text.substring(0, open) + text.substring(close + 1);
        }
        int nbtStart = text.indexOf('{');
        int nbtEnd = text.lastIndexOf('}');
        if (nbtStart > 0 && nbtEnd > nbtStart) {
            warnings.add(hologram.getName() + ": item NBT left out (" + raw.trim() + ")");
            text = text.substring(0, nbtStart) + text.substring(nbtEnd + 1);
        }
        text = text.replace("!ENCHANTED", "");
        String material = text.trim().split(" ", 2)[0].split(":", 2)[0].toUpperCase(Locale.ROOT);
        if (material.isEmpty()) material = "STONE";
        if (extras == null || extras.isEmpty() || !(material.contains("HEAD") || material.contains("SKULL"))) return material;
        if (extras.startsWith("HEADDATABASE_") || extras.contains("%") || extras.contains("{")) {
            warnings.add(hologram.getName() + ": head '" + extras + "' needs HeadDatabase or the viewer - a plain head instead");
            return "PLAYER_HEAD";
        }
        return extras.length() <= 16 ? "#SKULL:" + extras : "#SKULL:texture:" + extras;
    }

    /** Click actions of one page the dHolograms way: one string per click type, {@code ;} between. */
    private static Map<String, String> actions(Object raw, String name, Options options, List<String> warnings) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> byClick)) return out;
        for (String click : CLICK_TYPES) {
            if (!(byClick.get(click) instanceof List<?> list)) continue;
            List<String> converted = new ArrayList<>();
            for (Object action : list) {
                String one = action(String.valueOf(action), name, options, warnings);
                if (one != null) converted.add(one);
            }
            if (converted.isEmpty()) continue;
            String joined = String.join(";", converted);
            if (joined.length() > MAX_ACTIONS) {
                warnings.add(name + ": " + click + " actions are longer than " + MAX_ACTIONS + " characters - left out");
                continue;
            }
            out.put(click, joined);
        }
        return out;
    }

    /** One {@code TYPE:data} action; null for one with nothing to do. Both plugins replace {player} in actions. */
    static String action(String action, String name, Options options, List<String> warnings) {
        String[] parts = action.split(":", 2);
        String type = parts[0].trim().toUpperCase(Locale.ROOT);
        String data = parts.length > 1 ? parts[1].trim() : "";
        if (data.contains(";")) {
            warnings.add(name + ": action '" + action + "' contains ';', which dHolograms reads as the start of a new action");
        }
        if (data.contains("%")) {
            warnings.add(name + ": action '" + action + "' has PlaceholderAPI placeholders; dHolograms fills in only {player} in actions");
        }
        return switch (type) {
            case "NONE" -> null;
            case "COMMAND" -> {
                // DecentHolograms chats it (so a command is written with a slash); dHolograms runs it as a command
                if (!data.startsWith("/")) {
                    warnings.add(name + ": action '" + action + "' sent a chat message in DecentHolograms; dHolograms runs /" + data);
                }
                yield "COMMAND:" + (data.startsWith("/") ? data.substring(1) : data);
            }
            case "CONSOLE" -> "CONSOLE:" + (data.startsWith("/") ? data.substring(1) : data);
            case "MESSAGE" -> "MESSAGE:" + text(replace(data, options));
            case "SOUND" -> {
                String[] sound = data.split(":", 2);
                String key = options.soundKeys().apply(sound[0].trim());
                if (key == null) {
                    warnings.add(name + ": sound '" + sound[0].trim() + "' is not a sound this server knows - kept as it is");
                    key = sound[0].trim().toLowerCase(Locale.ROOT);
                }
                yield "SOUND:" + key + (sound.length > 1 ? ":" + sound[1] : "");
            }
            case "PERMISSION", "CONNECT", "TELEPORT", "PAGE" -> type + ":" + data;
            // dHolograms' NEXT_PAGE/PREV_PAGE without a name turn this hologram's page
            case "NEXT_PAGE", "PREV_PAGE" -> data.isEmpty() ? type : type + ":" + data;
            default -> {
                warnings.add(name + ": action '" + action + "' is not one dHolograms knows - left out");
                yield null;
            }
        };
    }

    /**
     * A sound as dHolograms plays it: a game sound's key without {@code minecraft:} (dHolograms
     * splits the action at every {@code :}), like {@code entity.experience_orb.pickup}.
     * DecentHolograms takes Bukkit's names ({@code ENTITY_EXPERIENCE_ORB_PICKUP}): the key in
     * capitals with {@code _} for {@code .}. Null when the game has no such sound.
     */
    static String bukkitSoundKey(String sound) {
        String wanted = sound.toLowerCase(Locale.ROOT);
        NamespacedKey asKey = NamespacedKey.fromString("minecraft:" + wanted);
        if (asKey != null && Registry.SOUNDS.get(asKey) != null) return wanted;
        return Registry.SOUNDS.keyStream()
                .filter(key -> key.getNamespace().equals(NamespacedKey.MINECRAFT))
                .map(NamespacedKey::getKey)
                .filter(key -> key.replace('.', '_').equals(wanted))
                .findFirst().orElse(null);
    }
}
