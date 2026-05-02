package com.dogetennant.dholograms.storage;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.DisplaySettings;
import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.HologramFlag;
import com.dogetennant.dholograms.hologram.line.*;
import com.dogetennant.dholograms.hologram.line.AnimationType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class MySQLStorage implements StorageProvider {

    private static final String FRAMES_DELIMITER = "|||";

    private final DHolograms plugin;
    private HikariDataSource dataSource;

    public MySQLStorage(DHolograms plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("storage.mysql");
        if (cfg == null) throw new IllegalStateException("MySQL config section missing");

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mysql://" + cfg.getString("host", "localhost")
                + ":" + cfg.getInt("port", 3306)
                + "/" + cfg.getString("database", "dholograms")
                + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8");
        hikari.setUsername(cfg.getString("username", "root"));
        hikari.setPassword(cfg.getString("password", ""));
        hikari.setMaximumPoolSize(cfg.getInt("pool-size", 5));
        hikari.setConnectionTimeout(cfg.getLong("connection-timeout", 30000));
        hikari.setIdleTimeout(cfg.getLong("idle-timeout", 600000));
        hikari.setMaxLifetime(cfg.getLong("max-lifetime", 1800000));
        hikari.setPoolName("dHolograms-Pool");

        dataSource = new HikariDataSource(hikari);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS dh_holograms (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(64) UNIQUE NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    view_permission VARCHAR(128),
                    tag VARCHAR(64),
                    refresh_ticks INT NOT NULL DEFAULT 20,
                    display_settings TEXT NOT NULL,
                    click_command VARCHAR(512),
                    click_console TINYINT(1) NOT NULL DEFAULT 0,
                    disabled TINYINT(1) NOT NULL DEFAULT 0,
                    flags VARCHAR(256)
                )
                """);
            try { stmt.execute("ALTER TABLE dh_holograms ADD COLUMN click_command VARCHAR(512)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_holograms ADD COLUMN click_console TINYINT(1) NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_holograms ADD COLUMN disabled TINYINT(1) NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_holograms ADD COLUMN flags VARCHAR(256)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_holograms ADD COLUMN click_right VARCHAR(512)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_holograms ADD COLUMN click_left VARCHAR(512)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_holograms ADD COLUMN click_shift_right VARCHAR(512)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_holograms ADD COLUMN click_shift_left VARCHAR(512)"); } catch (SQLException ignored) {}
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS dh_lines (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    hologram_id INT NOT NULL,
                    page_index INT NOT NULL DEFAULT 0,
                    line_index INT NOT NULL,
                    type VARCHAR(16) NOT NULL,
                    content TEXT NOT NULL,
                    height_offset DOUBLE NOT NULL DEFAULT 0.0,
                    rotation_speed FLOAT NOT NULL DEFAULT 0.0,
                    bob_amplitude FLOAT NOT NULL DEFAULT 0.0,
                    x_offset DOUBLE NOT NULL DEFAULT 0.0,
                    z_offset DOUBLE NOT NULL DEFAULT 0.0,
                    anim_type VARCHAR(16) NOT NULL DEFAULT 'NONE',
                    anim_color1 INT NOT NULL DEFAULT 16732245,
                    anim_color2 INT NOT NULL DEFAULT 5592575,
                    anim_speed INT NOT NULL DEFAULT 2,
                    view_permission VARCHAR(128),
                    FOREIGN KEY (hologram_id) REFERENCES dh_holograms(id) ON DELETE CASCADE
                )
                """);
            try { stmt.execute("ALTER TABLE dh_lines ADD COLUMN page_index INT NOT NULL DEFAULT 0 AFTER hologram_id"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_lines ADD COLUMN rotation_speed FLOAT NOT NULL DEFAULT 0.0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_lines ADD COLUMN bob_amplitude FLOAT NOT NULL DEFAULT 0.0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_lines ADD COLUMN x_offset DOUBLE NOT NULL DEFAULT 0.0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_lines ADD COLUMN z_offset DOUBLE NOT NULL DEFAULT 0.0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_lines ADD COLUMN anim_type VARCHAR(16) NOT NULL DEFAULT 'NONE'"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_lines ADD COLUMN anim_color1 INT NOT NULL DEFAULT 16732245"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_lines ADD COLUMN anim_color2 INT NOT NULL DEFAULT 5592575"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_lines ADD COLUMN anim_speed INT NOT NULL DEFAULT 2"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE dh_lines ADD COLUMN view_permission VARCHAR(128)"); } catch (SQLException ignored) {}
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    @Override
    public List<Hologram> loadAll() {
        List<Hologram> result = new ArrayList<>();
        String sql = "SELECT * FROM dh_holograms";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String worldName = rs.getString("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Skipping hologram '" + name + "': world '" + worldName + "' not found.");
                    continue;
                }
                Location loc = new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
                Hologram hologram = new Hologram(name, loc);
                hologram.setRefreshTicks(rs.getInt("refresh_ticks"));
                hologram.setViewPermission(rs.getString("view_permission"));
                hologram.setTag(rs.getString("tag"));
                hologram.setDisplaySettings(DisplaySettings.deserialize(rs.getString("display_settings")));
                loadClickActions(hologram, rs);
                hologram.setDisabled(rs.getBoolean("disabled"));
                String flagsStr = rs.getString("flags");
                if (flagsStr != null && !flagsStr.isEmpty()) {
                    for (String fs : flagsStr.split(",")) {
                        HologramFlag hf = HologramFlag.fromString(fs.trim());
                        if (hf != null) hologram.addFlag(hf);
                    }
                }

                loadLines(conn, hologram, id);
                result.add(hologram);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load holograms from MySQL", e);
        }
        return result;
    }

    private void loadLines(Connection conn, Hologram hologram, int hologramId) throws SQLException {
        String sql = "SELECT * FROM dh_lines WHERE hologram_id = ? ORDER BY page_index ASC, line_index ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hologramId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LineType type;
                    try {
                        type = LineType.valueOf(rs.getString("type"));
                    } catch (IllegalArgumentException e) {
                        type = LineType.TEXT;
                    }
                    String content = rs.getString("content");
                    double offset = rs.getDouble("height_offset");
                    int idx = rs.getInt("line_index");
                    int pageIndex = rs.getInt("page_index");

                    float rotSpeed = rs.getFloat("rotation_speed");
                    String viewPerm = rs.getString("view_permission");
                    HologramLine line = switch (type) {
                        case TEXT -> {
                            TextLine tl = new TextLine(hologram, idx, content, offset);
                            tl.setXOffset(rs.getDouble("x_offset"));
                            tl.setZOffset(rs.getDouble("z_offset"));
                            if (viewPerm != null && !viewPerm.isEmpty()) tl.setViewPermission(viewPerm);
                            AnimationType animType = AnimationType.fromString(rs.getString("anim_type"));
                            tl.setAnimationType(animType);
                            if (animType != AnimationType.NONE) {
                                tl.setAnimColor1(rs.getInt("anim_color1"));
                                tl.setAnimColor2(rs.getInt("anim_color2"));
                                tl.setAnimSpeed(rs.getInt("anim_speed"));
                            }
                            yield tl;
                        }
                        case ITEM -> {
                            ItemLine il = new ItemLine(hologram, idx, content, offset);
                            il.setXOffset(rs.getDouble("x_offset"));
                            il.setZOffset(rs.getDouble("z_offset"));
                            if (viewPerm != null && !viewPerm.isEmpty()) il.setViewPermission(viewPerm);
                            il.setRotationSpeed(rotSpeed);
                            il.setBobAmplitude(rs.getFloat("bob_amplitude"));
                            yield il;
                        }
                        case ENTITY -> {
                            EntityLine el = new EntityLine(hologram, idx, content, offset);
                            el.setXOffset(rs.getDouble("x_offset"));
                            el.setZOffset(rs.getDouble("z_offset"));
                            if (viewPerm != null && !viewPerm.isEmpty()) el.setViewPermission(viewPerm);
                            if (rotSpeed != 0) el.setRotationSpeed(rotSpeed);
                            yield el;
                        }
                    };
                    hologram.addLineInternal(pageIndex, line);
                }
            }
        }
    }

    @Override
    public void save(Hologram hologram) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dataSource.getConnection()) {
                int hologramId = upsertHologram(conn, hologram);
                deleteLines(conn, hologramId);
                insertLines(conn, hologramId, hologram);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save hologram '" + hologram.getName() + "'", e);
            }
        });
    }

    private int upsertHologram(Connection conn, Hologram hologram) throws SQLException {
        String sql = """
                INSERT INTO dh_holograms (name, world, x, y, z, view_permission, tag, refresh_ticks, display_settings,
                  click_right, click_left, click_shift_right, click_shift_left, disabled, flags)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z),
                  view_permission=VALUES(view_permission), tag=VALUES(tag),
                  refresh_ticks=VALUES(refresh_ticks), display_settings=VALUES(display_settings),
                  click_right=VALUES(click_right), click_left=VALUES(click_left),
                  click_shift_right=VALUES(click_shift_right), click_shift_left=VALUES(click_shift_left),
                  disabled=VALUES(disabled), flags=VALUES(flags)
                """;
        Location loc = hologram.getLocation();
        java.util.Map<String, String> ca = hologram.getClickActions();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, hologram.getName());
            ps.setString(2, loc.getWorld() != null ? loc.getWorld().getName() : "world");
            ps.setDouble(3, loc.getX());
            ps.setDouble(4, loc.getY());
            ps.setDouble(5, loc.getZ());
            ps.setString(6, hologram.getViewPermission());
            ps.setString(7, hologram.getTag());
            ps.setInt(8, hologram.getRefreshTicks());
            ps.setString(9, hologram.getDisplaySettings().serialize());
            ps.setString(10, ca.get("RIGHT"));
            ps.setString(11, ca.get("LEFT"));
            ps.setString(12, ca.get("SHIFT_RIGHT"));
            ps.setString(13, ca.get("SHIFT_LEFT"));
            ps.setBoolean(14, hologram.isDisabled());
            String flagsStr = hologram.getFlags().stream()
                    .map(HologramFlag::name)
                    .collect(java.util.stream.Collectors.joining(","));
            ps.setString(15, flagsStr.isEmpty() ? null : flagsStr);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        // Fetch existing id if upsert didn't generate a new one
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM dh_holograms WHERE name = ?")) {
            ps.setString(1, hologram.getName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        throw new SQLException("Could not determine hologram id for: " + hologram.getName());
    }

    private void loadClickActions(Hologram hologram, ResultSet rs) throws SQLException {
        String right      = rs.getString("click_right");
        String left       = rs.getString("click_left");
        String shiftRight = rs.getString("click_shift_right");
        String shiftLeft  = rs.getString("click_shift_left");

        if (right      != null) hologram.setClickAction("RIGHT",       right);
        if (left       != null) hologram.setClickAction("LEFT",        left);
        if (shiftRight != null) hologram.setClickAction("SHIFT_RIGHT", shiftRight);
        if (shiftLeft  != null) hologram.setClickAction("SHIFT_LEFT",  shiftLeft);

        // Migrate old single click_command column
        if (!hologram.hasClickActions()) {
            String old = rs.getString("click_command");
            if (old != null) {
                boolean console = rs.getBoolean("click_console");
                hologram.setClickAction("RIGHT", console ? "CONSOLE:" + old : "COMMAND:" + old);
            }
        }
    }

    private void deleteLines(Connection conn, int hologramId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM dh_lines WHERE hologram_id = ?")) {
            ps.setInt(1, hologramId);
            ps.executeUpdate();
        }
    }

    private void insertLines(Connection conn, int hologramId, Hologram hologram) throws SQLException {
        String sql = "INSERT INTO dh_lines (hologram_id, page_index, line_index, type, content, height_offset, rotation_speed, bob_amplitude, x_offset, z_offset, anim_type, anim_color1, anim_color2, anim_speed, view_permission) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int p = 0; p < hologram.getPageCount(); p++) {
                List<HologramLine> page = hologram.getPage(p);
                for (int i = 0; i < page.size(); i++) {
                    HologramLine line = page.get(i);
                    ps.setInt(1, hologramId);
                    ps.setInt(2, p);
                    ps.setInt(3, i);
                    ps.setString(4, line.getType().name());
                    ps.setString(5, line.getRawContent());
                    ps.setDouble(6, line.getHeightOffset());
                    float rotSpeed = line instanceof ItemLine il ? il.getRotationSpeed()
                            : line instanceof EntityLine el ? el.getRotationSpeed() : 0;
                    ps.setFloat(7, rotSpeed);
                    float bobAmp = line instanceof ItemLine il2 ? il2.getBobAmplitude() : 0;
                    ps.setFloat(8, bobAmp);
                    ps.setDouble(9, line.getXOffset());
                    ps.setDouble(10, line.getZOffset());
                    if (line instanceof TextLine tl2) {
                        ps.setString(11, tl2.getAnimationType().name());
                        ps.setInt(12, tl2.getAnimColor1());
                        ps.setInt(13, tl2.getAnimColor2());
                        ps.setInt(14, tl2.getAnimSpeed());
                    } else {
                        ps.setString(11, "NONE");
                        ps.setInt(12, 0xFF5555);
                        ps.setInt(13, 0x5555FF);
                        ps.setInt(14, 2);
                    }
                    String vp = line.getViewPermission();
                    if (vp != null && !vp.isEmpty()) ps.setString(15, vp);
                    else ps.setNull(15, Types.VARCHAR);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    @Override
    public void delete(String name) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM dh_holograms WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete hologram '" + name + "'", e);
            }
        });
    }
}
