package com.dogetennant.dholograms.hologram;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.line.HologramLine;
import com.dogetennant.dholograms.hologram.line.TextLine;
import com.dogetennant.dholograms.hologram.line.AnimationType;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.EnumSet;
import java.util.function.Consumer;

public class Hologram {

    private String name;
    private Location location;
    private final List<List<HologramLine>> pages = new ArrayList<>();
    private DisplaySettings displaySettings = new DisplaySettings();
    private int refreshTicks = 20;
    private String viewPermission = null;
    private String tag = null;
    private final Set<UUID> hiddenFrom = new HashSet<>();
    private final Map<UUID, Integer> viewerPages = new HashMap<>();

    // Click-to-execute (keyed by click type: RIGHT / LEFT / SHIFT_RIGHT / SHIFT_LEFT)
    private final Map<String, String> clickActions = new LinkedHashMap<>();
    private Interaction clickEntity;

    // Nav entities (non-null only when pages.size() >= 2)
    private Interaction prevNavEntity;
    private Interaction nextNavEntity;
    private TextDisplay navDisplay;

    // Flags & disabled state
    private boolean disabled = false;
    private final Set<HologramFlag> flags = EnumSet.noneOf(HologramFlag.class);

    // Unified background entities — one per page (null if page has no text lines or unified mode is off)
    private final List<TextDisplay> unifiedDisplays = new ArrayList<>();

    public Hologram(String name, Location location) {
        this.name = name;
        this.location = location.clone();
        pages.add(new ArrayList<>());
    }

    // Entity lifecycle

    public void spawn() {
        unifiedDisplays.clear();
        for (int p = 0; p < pages.size(); p++) {
            List<HologramLine> page = pages.get(p);
            for (HologramLine line : page) {
                if (line instanceof TextLine tl) tl.setUnified(displaySettings.isUnifiedBackground());
            }
            for (int i = 0; i < page.size(); i++) {
                page.get(i).spawn(getLineLocation(p, i));
            }
            unifiedDisplays.add(null);
            if (displaySettings.isUnifiedBackground()) spawnUnifiedDisplay(p);
        }
        if (pages.size() > 1) spawnNavEntities();
        if (!clickActions.isEmpty()) spawnClickEntity();
    }

    public void despawn() {
        for (List<HologramLine> page : pages) {
            for (HologramLine line : page) line.despawn();
        }
        despawnUnifiedDisplays();
        despawnNavEntities();
        despawnClickEntity();
        viewerPages.clear();
    }

    public void reload() {
        despawn();
        viewerPages.clear();
        spawn();
    }

    // Click entity

    private void spawnClickEntity() {
        if (location.getWorld() == null) return;
        int maxLines = pages.stream().mapToInt(List::size).max().orElse(1);
        float height = (float) ((maxLines - 1) * displaySettings.getLineHeight() + 0.8);
        double bottomY = location.getY() - (maxLines - 1) * displaySettings.getLineHeight() - 0.4;
        Location clickLoc = new Location(location.getWorld(), location.getX(), bottomY, location.getZ());
        clickEntity = location.getWorld().spawn(clickLoc, Interaction.class, e -> {
            DHolograms.getInstance().getManagedSpawns().add(e);
            e.setInteractionWidth(2.0f);
            e.setInteractionHeight(height);
            e.setResponsive(false);
            e.setPersistent(false);
        });
        DHolograms.getInstance().getHologramManager().registerClickEntity(this);
    }

    public void updateClickEntitySize() {
        if (clickEntity == null || clickEntity.isDead() || clickActions.isEmpty()) return;
        int maxLines = pages.stream().mapToInt(List::size).max().orElse(1);
        float height = (float) ((maxLines - 1) * displaySettings.getLineHeight() + 0.8);
        double bottomY = location.getY() - (maxLines - 1) * displaySettings.getLineHeight() - 0.4;
        clickEntity.setInteractionWidth(2.0f);
        clickEntity.setInteractionHeight(height);
        clickEntity.teleport(new Location(location.getWorld(), location.getX(), bottomY, location.getZ()));
    }

    private void despawnClickEntity() {
        DHolograms plugin = DHolograms.getInstance();
        if (plugin != null && plugin.getHologramManager() != null) {
            plugin.getHologramManager().unregisterClickEntity(this);
        }
        if (clickEntity != null && !clickEntity.isDead()) clickEntity.remove();
        clickEntity = null;
    }

    // Unified background display

    private void spawnUnifiedDisplay(int pageIndex) {
        while (unifiedDisplays.size() <= pageIndex) unifiedDisplays.add(null);
        TextDisplay existing = unifiedDisplays.get(pageIndex);
        if (existing != null && !existing.isDead()) existing.remove();

        List<HologramLine> page = pages.get(pageIndex);
        Location loc = null;
        for (int i = 0; i < page.size(); i++) {
            if (page.get(i) instanceof TextLine) {
                loc = getLineLocation(pageIndex, i);
                break;
            }
        }
        if (loc == null || loc.getWorld() == null) {
            unifiedDisplays.set(pageIndex, null);
            return;
        }

        Component combined = buildCombinedText(pageIndex, null);
        DisplaySettings ds = displaySettings;
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, entity -> {
            DHolograms.getInstance().getManagedSpawns().add(entity);
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setBillboard(ds.getBillboard());
            entity.setAlignment(ds.getAlignment());
            entity.setShadowed(ds.isShadow());
            entity.setLineWidth(ds.getLineWidth());
            applyUnifiedBackground(entity, ds.getBackgroundColor());
            applyUnifiedScale(entity, ds);
            entity.text(combined);
        });
        unifiedDisplays.set(pageIndex, display);
    }

    private void despawnUnifiedDisplays() {
        for (TextDisplay d : unifiedDisplays) {
            if (d != null && !d.isDead()) d.remove();
        }
        unifiedDisplays.clear();
    }

    private Component buildCombinedText(int pageIndex, Player player) {
        List<HologramLine> page = pages.get(pageIndex);
        var b = Component.text();
        boolean first = true;
        for (HologramLine line : page) {
            if (!(line instanceof TextLine tl)) continue;
            if (!first) b.append(Component.newline());
            first = false;
            // Respect per-line view permissions: blank line for lines the player can't see
            String perm = tl.getViewPermission();
            if (player != null && perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
                b.append(Component.empty());
            } else {
                b.append(tl.getCurrentComponent(player));
            }
        }
        return b.build();
    }

    /** Called by HologramManager's content-update loop to refresh PAPI placeholders. */
    public void updateUnifiedDisplay(int pageIndex, Player player) {
        if (!displaySettings.isUnifiedBackground()) return;
        if (pageIndex < 0 || pageIndex >= unifiedDisplays.size()) return;
        TextDisplay unified = unifiedDisplays.get(pageIndex);
        if (unified == null || unified.isDead()) return;
        unified.text(buildCombinedText(pageIndex, player));
    }

    /** Called by AnimationTicker every game tick to push animated content to unified entities. */
    public void tickUnifiedDisplays() {
        if (!displaySettings.isUnifiedBackground()) return;
        for (int p = 0; p < pages.size(); p++) {
            if (p >= unifiedDisplays.size()) break;
            TextDisplay unified = unifiedDisplays.get(p);
            if (unified == null || unified.isDead()) continue;
            boolean hasAnimation = false;
            for (HologramLine line : pages.get(p)) {
                if (line instanceof TextLine tl && tl.getAnimationType() != AnimationType.NONE) {
                    hasAnimation = true;
                    break;
                }
            }
            if (hasAnimation) unified.text(buildCombinedText(p, null));
        }
    }

    /** Rebuilds the unified display text for a page after structural line changes. */
    private void rebuildUnifiedDisplay(int pageIndex) {
        while (unifiedDisplays.size() <= pageIndex) unifiedDisplays.add(null);
        TextDisplay unified = unifiedDisplays.get(pageIndex);
        if (unified == null || unified.isDead()) {
            spawnUnifiedDisplay(pageIndex);
        } else {
            unified.text(buildCombinedText(pageIndex, null));
        }
    }

    private void applyUnifiedBackground(TextDisplay entity, int argb) {
        if (argb == -1) {
            entity.setDefaultBackground(true);
        } else {
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(
                    (argb >> 24) & 0xFF, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF));
        }
    }

    private void applyUnifiedScale(TextDisplay entity, DisplaySettings ds) {
        entity.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(ds.getScaleX(), ds.getScaleY(), ds.getScaleZ()),
                new AxisAngle4f(0, 0, 0, 1)));
    }

    // Nav entities

    private void spawnNavEntities() {
        Location navLoc = getNavLocation();
        if (navLoc.getWorld() == null) return;

        navDisplay = navLoc.getWorld().spawn(navLoc, TextDisplay.class, d -> {
            DHolograms.getInstance().getManagedSpawns().add(d);
            d.text(Component.text("◀  ▶"));
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.setGravity(false);
            d.setBillboard(Display.Billboard.CENTER);
        });

        Location prevLoc = navLoc.clone().add(-0.4, 0, 0);
        prevNavEntity = prevLoc.getWorld().spawn(prevLoc, Interaction.class, e -> {
            DHolograms.getInstance().getManagedSpawns().add(e);
            e.setInteractionWidth(0.4f);
            e.setInteractionHeight(0.4f);
            e.setResponsive(false);
            e.setPersistent(false);
        });

        Location nextLoc = navLoc.clone().add(0.4, 0, 0);
        nextNavEntity = nextLoc.getWorld().spawn(nextLoc, Interaction.class, e -> {
            DHolograms.getInstance().getManagedSpawns().add(e);
            e.setInteractionWidth(0.4f);
            e.setInteractionHeight(0.4f);
            e.setResponsive(false);
            e.setPersistent(false);
        });

        DHolograms.getInstance().getHologramManager().registerNavEntities(this);
    }

    private void despawnNavEntities() {
        DHolograms plugin = DHolograms.getInstance();
        if (plugin != null && plugin.getHologramManager() != null) {
            plugin.getHologramManager().unregisterNavEntities(this);
        }
        if (navDisplay != null && !navDisplay.isDead()) navDisplay.remove();
        if (prevNavEntity != null && !prevNavEntity.isDead()) prevNavEntity.remove();
        if (nextNavEntity != null && !nextNavEntity.isDead()) nextNavEntity.remove();
        navDisplay = null;
        prevNavEntity = null;
        nextNavEntity = null;
    }

    private Location getNavLocation() {
        int maxLines = pages.stream().mapToInt(List::size).max().orElse(0);
        double dir = displaySettings.isDownOrigin() ? 1.0 : -1.0;
        double navY = location.getY() + (dir * maxLines * displaySettings.getLineHeight()) - (dir * 0.3);
        Location loc = new Location(location.getWorld(), location.getX(), navY, location.getZ());
        loc.setYaw(displaySettings.getFacing());
        return loc;
    }

    // Visibility

    public void showTo(Player player) {
        hiddenFrom.remove(player.getUniqueId());
        if (isInRange(player)) applyPageVisibility(player);
    }

    public void hideFrom(Player player) {
        hiddenFrom.add(player.getUniqueId());
        DHolograms plugin = DHolograms.getInstance();
        for (TextDisplay unified : unifiedDisplays) {
            if (unified != null && !unified.isDead()) player.hideEntity(plugin, unified);
        }
        for (List<HologramLine> page : pages) {
            for (HologramLine line : page) {
                if (line.isSpawned()) player.hideEntity(plugin, line.getEntity());
            }
        }
        hideNavFrom(plugin, player);
        if (clickEntity != null && !clickEntity.isDead()) player.hideEntity(plugin, clickEntity);
    }

    public boolean isVisibleTo(Player player) {
        if (disabled) return false;
        if (hiddenFrom.contains(player.getUniqueId())) return false;
        if (viewPermission != null && !viewPermission.isEmpty() && !player.hasPermission(viewPermission)) return false;
        return isInRange(player);
    }

    private boolean isInRange(Player player) {
        if (location.getWorld() == null || !location.getWorld().equals(player.getWorld())) return false;
        double range = displaySettings.getRange();
        return player.getLocation().distanceSquared(location) <= range * range;
    }

    public boolean isInUpdateRange(Player player) {
        double ur = displaySettings.getUpdateRange();
        if (ur <= 0) return isInRange(player); // -1 = same as display range
        if (location.getWorld() == null || !location.getWorld().equals(player.getWorld())) return false;
        return player.getLocation().distanceSquared(location) <= ur * ur;
    }

    public void applyVisibilityTo(Player player) {
        if (isVisibleTo(player)) {
            applyPageVisibility(player);
        } else {
            DHolograms plugin = DHolograms.getInstance();
            for (TextDisplay unified : unifiedDisplays) {
                if (unified != null && !unified.isDead()) player.hideEntity(plugin, unified);
            }
            for (List<HologramLine> page : pages) {
                for (HologramLine line : page) {
                    if (line.isSpawned()) player.hideEntity(plugin, line.getEntity());
                }
            }
            hideNavFrom(plugin, player);
            if (clickEntity != null && !clickEntity.isDead()) player.hideEntity(plugin, clickEntity);
        }
    }

    private void applyPageVisibility(Player player) {
        DHolograms plugin = DHolograms.getInstance();
        int currentPage = viewerPages.getOrDefault(player.getUniqueId(), 0);
        for (int p = 0; p < pages.size(); p++) {
            // Unified background entity visibility
            if (p < unifiedDisplays.size()) {
                TextDisplay unified = unifiedDisplays.get(p);
                if (unified != null && !unified.isDead()) {
                    if (p == currentPage) player.showEntity(plugin, unified);
                    else player.hideEntity(plugin, unified);
                }
            }
            // Individual line entities (TextLines in unified mode have no entity so isSpawned() is false)
            for (HologramLine line : pages.get(p)) {
                if (line.isSpawned()) {
                    String linePerm = line.getViewPermission();
                    boolean lineVisible = p == currentPage
                            && (linePerm == null || linePerm.isEmpty() || player.hasPermission(linePerm));
                    if (lineVisible) player.showEntity(plugin, line.getEntity());
                    else player.hideEntity(plugin, line.getEntity());
                }
            }
        }
        if (pages.size() > 1) {
            if (navDisplay != null && !navDisplay.isDead()) player.showEntity(plugin, navDisplay);
            if (prevNavEntity != null && !prevNavEntity.isDead()) player.showEntity(plugin, prevNavEntity);
            if (nextNavEntity != null && !nextNavEntity.isDead()) player.showEntity(plugin, nextNavEntity);
        }
        if (clickEntity != null && !clickEntity.isDead()) player.showEntity(plugin, clickEntity);
    }

    private void hideNavFrom(DHolograms plugin, Player player) {
        if (navDisplay != null && !navDisplay.isDead()) player.hideEntity(plugin, navDisplay);
        if (prevNavEntity != null && !prevNavEntity.isDead()) player.hideEntity(plugin, prevNavEntity);
        if (nextNavEntity != null && !nextNavEntity.isDead()) player.hideEntity(plugin, nextNavEntity);
    }

    // Page navigation

    public void nextPage(Player player) {
        int current = viewerPages.getOrDefault(player.getUniqueId(), 0);
        setViewerPage(player, (current + 1) % pages.size());
    }

    public void prevPage(Player player) {
        int current = viewerPages.getOrDefault(player.getUniqueId(), 0);
        setViewerPage(player, (current - 1 + pages.size()) % pages.size());
    }

    public void setViewerPage(Player player, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        viewerPages.put(player.getUniqueId(), pageIndex);
        applyPageVisibility(player);
        player.sendActionBar(DHolograms.getInstance().getLang().get("page-turned",
                Map.of("current", String.valueOf(pageIndex + 1), "total", String.valueOf(pages.size()))));
    }

    public int getViewerPage(Player player) {
        return viewerPages.getOrDefault(player.getUniqueId(), 0);
    }

    // Page management

    public int getPageCount() { return pages.size(); }

    public List<HologramLine> getPage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return List.of();
        return Collections.unmodifiableList(pages.get(pageIndex));
    }

    public List<HologramLine> getAllLines() {
        List<HologramLine> all = new ArrayList<>();
        for (List<HologramLine> page : pages) all.addAll(page);
        return all;
    }

    public void forEachLine(Consumer<HologramLine> action) {
        for (List<HologramLine> page : pages) {
            for (HologramLine line : page) action.accept(line);
        }
    }

    public void removeViewer(UUID playerId) {
        viewerPages.remove(playerId);
    }

    public void addPage() {
        pages.add(new ArrayList<>());
        if (pages.size() == 2) {
            spawnNavEntities();
            DHolograms plugin = DHolograms.getInstance();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (isVisibleTo(p)) {
                    if (navDisplay != null) p.showEntity(plugin, navDisplay);
                    if (prevNavEntity != null) p.showEntity(plugin, prevNavEntity);
                    if (nextNavEntity != null) p.showEntity(plugin, nextNavEntity);
                }
            }
        }
    }

    public boolean removePage(int pageIndex) {
        if (pages.size() <= 1 || pageIndex < 0 || pageIndex >= pages.size()) return false;
        List<HologramLine> removed = pages.remove(pageIndex);
        for (HologramLine line : removed) line.despawn();
        if (pageIndex < unifiedDisplays.size()) {
            TextDisplay u = unifiedDisplays.remove(pageIndex);
            if (u != null && !u.isDead()) u.remove();
        }
        viewerPages.replaceAll((uuid, p) -> Math.min(p, pages.size() - 1));
        if (pages.size() == 1) despawnNavEntities();
        DHolograms plugin = DHolograms.getInstance();
        for (Player p : plugin.getServer().getOnlinePlayers()) applyVisibilityTo(p);
        return true;
    }

    // Line locations

    public Location getLineLocation(int pageIndex, int lineIndex) {
        HologramLine line = pages.get(pageIndex).get(lineIndex);
        double dir = displaySettings.isDownOrigin() ? 1.0 : -1.0;
        double autoY = location.getY() + (dir * lineIndex * displaySettings.getLineHeight());
        Location loc = new Location(location.getWorld(),
                location.getX() + line.getXOffset(),
                autoY + line.getHeightOffset(),
                location.getZ() + line.getZOffset());
        loc.setYaw(displaySettings.getFacing());
        return loc;
    }

    public Location getLineLocation(int index) {
        return getLineLocation(0, index);
    }

    // Line management - page-0 backward-compat wrappers

    public void addLineInternal(HologramLine line) { addLineInternal(0, line); }
    public void addLine(HologramLine line) { addLine(0, line); }
    public void removeLine(int index) { removeLine(0, index); }
    public void setLine(int index, HologramLine newLine) { setLine(0, index, newLine); }
    public void insertLine(int index, HologramLine line) { insertLine(0, index, line); }
    public void recalculatePositions() { recalculatePositions(0); }

    // Line management - page-aware

    public void addLineInternal(int pageIndex, HologramLine line) {
        while (pages.size() <= pageIndex) pages.add(new ArrayList<>());
        List<HologramLine> page = pages.get(pageIndex);
        line.setIndex(page.size());
        page.add(line);
    }

    public void addLine(int pageIndex, HologramLine line) {
        while (pages.size() <= pageIndex) pages.add(new ArrayList<>());
        List<HologramLine> page = pages.get(pageIndex);
        int idx = page.size();
        line.setIndex(idx);
        page.add(line);
        if (displaySettings.isUnifiedBackground() && line instanceof TextLine tl) {
            tl.setUnified(true);
            rebuildUnifiedDisplay(pageIndex);
            DHolograms plugin = DHolograms.getInstance();
            for (Player p : plugin.getServer().getOnlinePlayers()) applyVisibilityTo(p);
        } else {
            line.spawn(getLineLocation(pageIndex, idx));
            showLineToEligiblePlayers(pageIndex, line);
        }
        updateClickEntitySize();
    }

    public void removeLine(int pageIndex, int lineIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        List<HologramLine> page = pages.get(pageIndex);
        if (lineIndex < 0 || lineIndex >= page.size()) return;
        boolean wasText = page.get(lineIndex) instanceof TextLine;
        page.get(lineIndex).despawn();
        page.remove(lineIndex);
        reindex(page, lineIndex);
        recalculatePositions(pageIndex);
        if (displaySettings.isUnifiedBackground() && wasText) rebuildUnifiedDisplay(pageIndex);
        updateClickEntitySize();
    }

    public void setLine(int pageIndex, int lineIndex, HologramLine newLine) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        List<HologramLine> page = pages.get(pageIndex);
        if (lineIndex < 0 || lineIndex >= page.size()) return;
        boolean oldWasText = page.get(lineIndex) instanceof TextLine;
        page.get(lineIndex).despawn();
        newLine.setIndex(lineIndex);
        page.set(lineIndex, newLine);
        if (displaySettings.isUnifiedBackground() && (newLine instanceof TextLine || oldWasText)) {
            if (newLine instanceof TextLine tl) tl.setUnified(true);
            rebuildUnifiedDisplay(pageIndex);
            DHolograms plugin = DHolograms.getInstance();
            for (Player p : plugin.getServer().getOnlinePlayers()) applyVisibilityTo(p);
        } else {
            newLine.spawn(getLineLocation(pageIndex, lineIndex));
            showLineToEligiblePlayers(pageIndex, newLine);
        }
    }

    public void insertLine(int pageIndex, int lineIndex, HologramLine line) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        List<HologramLine> page = pages.get(pageIndex);
        int clamped = Math.max(0, Math.min(lineIndex, page.size()));
        page.add(clamped, line);
        reindex(page, clamped);
        if (displaySettings.isUnifiedBackground()) {
            reloadUnifiedPage(pageIndex);
        } else {
            reloadPage(pageIndex);
        }
        DHolograms plugin = DHolograms.getInstance();
        for (Player p : plugin.getServer().getOnlinePlayers()) applyVisibilityTo(p);
    }

    public void recalculatePositions(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        List<HologramLine> page = pages.get(pageIndex);
        for (int i = 0; i < page.size(); i++) {
            HologramLine line = page.get(i);
            if (line.isSpawned()) line.getEntity().teleport(getLineLocation(pageIndex, i));
        }
    }

    private void reloadPage(int pageIndex) {
        List<HologramLine> page = pages.get(pageIndex);
        for (HologramLine line : page) line.despawn();
        for (int i = 0; i < page.size(); i++) page.get(i).spawn(getLineLocation(pageIndex, i));
    }

    private void reloadUnifiedPage(int pageIndex) {
        List<HologramLine> page = pages.get(pageIndex);
        for (HologramLine line : page) line.despawn();
        if (pageIndex < unifiedDisplays.size()) {
            TextDisplay old = unifiedDisplays.get(pageIndex);
            if (old != null && !old.isDead()) old.remove();
            unifiedDisplays.set(pageIndex, null);
        }
        for (HologramLine line : page) {
            if (line instanceof TextLine tl) tl.setUnified(true);
        }
        for (int i = 0; i < page.size(); i++) page.get(i).spawn(getLineLocation(pageIndex, i));
        spawnUnifiedDisplay(pageIndex);
    }

    private void reindex(List<HologramLine> page, int fromIndex) {
        for (int i = fromIndex; i < page.size(); i++) page.get(i).setIndex(i);
    }

    public boolean swapLines(int pageIndex, int a, int b) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return false;
        List<HologramLine> page = pages.get(pageIndex);
        if (a < 0 || b < 0 || a >= page.size() || b >= page.size() || a == b) return false;
        Collections.swap(page, a, b);
        page.get(a).setIndex(a);
        page.get(b).setIndex(b);
        recalculatePositions(pageIndex);
        if (displaySettings.isUnifiedBackground()) rebuildUnifiedDisplay(pageIndex);
        return true;
    }

    public void centerOnBlock() {
        Location loc = location.clone();
        loc.setX(Math.floor(loc.getX()) + 0.5);
        loc.setZ(Math.floor(loc.getZ()) + 0.5);
        setLocation(loc);
    }

    private void showLineToEligiblePlayers(int pageIndex, HologramLine line) {
        if (!line.isSpawned()) return;
        DHolograms plugin = DHolograms.getInstance();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (isVisibleTo(p) && viewerPages.getOrDefault(p.getUniqueId(), 0) == pageIndex) {
                p.showEntity(plugin, line.getEntity());
            }
        }
    }

    // Getters / Setters

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Location getLocation() { return location.clone(); }
    public void setLocation(Location location) { this.location = location.clone(); }

    public List<HologramLine> getLines() { return getPage(0); }

    public DisplaySettings getDisplaySettings() { return displaySettings; }
    public void setDisplaySettings(DisplaySettings settings) { this.displaySettings = settings; }

    public int getRefreshTicks() { return refreshTicks; }
    public void setRefreshTicks(int ticks) { this.refreshTicks = ticks; }

    public String getViewPermission() { return viewPermission; }
    public void setViewPermission(String perm) { this.viewPermission = perm; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public Set<UUID> getHiddenFrom() { return hiddenFrom; }

    public void setClickAction(String type, String actions) {
        if (actions == null || actions.isBlank()) clickActions.remove(type.toUpperCase());
        else clickActions.put(type.toUpperCase(), actions);
    }

    public String getClickAction(String type) { return clickActions.get(type.toUpperCase()); }
    public void clearClickAction(String type) { clickActions.remove(type.toUpperCase()); }
    public void clearAllClickActions() { clickActions.clear(); }
    public boolean hasClickActions() { return !clickActions.isEmpty(); }
    public Map<String, String> getClickActions() { return Collections.unmodifiableMap(clickActions); }

    public Interaction getClickEntity() { return clickEntity; }
    public Interaction getPrevNavEntity() { return prevNavEntity; }
    public Interaction getNextNavEntity() { return nextNavEntity; }

    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }

    public boolean hasFlag(HologramFlag flag) { return flags.contains(flag); }
    public void addFlag(HologramFlag flag) { flags.add(flag); }
    public void removeFlag(HologramFlag flag) { flags.remove(flag); }
    public Set<HologramFlag> getFlags() { return Collections.unmodifiableSet(flags); }
    public void setFlags(Set<HologramFlag> newFlags) { flags.clear(); flags.addAll(newFlags); }
}
