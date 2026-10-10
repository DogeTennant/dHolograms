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
    private final SaveQueue queue;
    private HikariDataSource dataSource;

    public MySQLStorage(DHolograms plugin) {
        this.plugin = plugin;
        this.queue = new SaveQueue(plugin.getLogger());
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
        queue.close(30, java.util.concurrent.TimeUnit.SECONDS);
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

    /**
     * One hologram as table rows, taken on the server thread: the save thread never reads a
     * hologram while a command is changing it.
     */
    private record Snapshot(String name, Object[] hologram, List<Object[]> lines) {}

    @Override
    public void save(Hologram hologram) {
        Snapshot snapshot = snapshot(hologram);
        queue.submit(() -> write(snapshot));
    }

    private Snapshot snapshot(Hologram hologram) {
        Location loc = hologram.getLocation();
        java.util.Map<String, String> ca = hologram.getClickActions();
        String flagsStr = hologram.getFlags().stream()
                .map(HologramFlag::name)
                .collect(java.util.stream.Collectors.joining(","));
        Object[] row = {
                hologram.getName(),
                loc.getWorld() != null ? loc.getWorld().getName() : "world",
                loc.getX(), loc.getY(), loc.getZ(),
                hologram.getViewPermission(),
                hologram.getTag(),
                hologram.getRefreshTicks(),
                hologram.getDisplaySettings().serialize(),
                ca.get("RIGHT"), ca.get("LEFT"), ca.get("SHIFT_RIGHT"), ca.get("SHIFT_LEFT"),
                hologram.isDisabled(),
                flagsStr.isEmpty() ? null : flagsStr
        };
        List<Object[]> lines = new ArrayList<>();
        for (int p = 0; p < hologram.getPageCount(); p++) {
            List<HologramLine> page = hologram.getPage(p);
            for (int i = 0; i < page.size(); i++) {
                HologramLine line = page.get(i);
                float rotSpeed = line instanceof ItemLine il ? il.getRotationSpeed()
                        : line instanceof EntityLine el ? el.getRotationSpeed() : 0;
                float bobAmp = line instanceof ItemLine il2 ? il2.getBobAmplitude() : 0;
                TextLine text = line instanceof TextLine tl ? tl : null;
                String vp = line.getViewPermission();
                lines.add(new Object[] {
                        p, i, line.getType().name(), line.getRawContent(), line.getHeightOffset(),
                        rotSpeed, bobAmp, line.getXOffset(), line.getZOffset(),
                        text != null ? text.getAnimationType().name() : "NONE",
                        text != null ? text.getAnimColor1() : 0xFF5555,
                        text != null ? text.getAnimColor2() : 0x5555FF,
                        text != null ? text.getAnimSpeed() : 2,
                        vp != null && !vp.isEmpty() ? vp : null
                });
            }
        }
        return new Snapshot(hologram.getName(), row, lines);
    }

    /**
     * The hologram row and all its lines in one transaction. The lines are deleted and written
     * again, and a save that failed after the delete used to leave half a hologram (or none).
     */
    private void write(Snapshot snapshot) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int hologramId = upsertHologram(conn, snapshot);
                deleteLines(conn, hologramId);
                insertLines(conn, hologramId, snapshot);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save hologram '" + snapshot.name()
                    + "' - the stored one is unchanged", e);
        }
    }

    /** Binds values from parameter {@code first} on; a null is bound as a NULL string. */
    private static void bind(PreparedStatement ps, int first, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) ps.setNull(first + i, Types.VARCHAR);
            else ps.setObject(first + i, values[i]);
        }
    }

    private int upsertHologram(Connection conn, Snapshot snapshot) throws SQLException {
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
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, 1, snapshot.hologram());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        // Fetch existing id if upsert didn't generate a new one
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM dh_holograms WHERE name = ?")) {
            ps.setString(1, snapshot.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        throw new SQLException("Could not determine hologram id for: " + snapshot.name());
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

    private void insertLines(Connection conn, int hologramId, Snapshot snapshot) throws SQLException {
        String sql = "INSERT INTO dh_lines (hologram_id, page_index, line_index, type, content, height_offset, rotation_speed, bob_amplitude, x_offset, z_offset, anim_type, anim_color1, anim_color2, anim_speed, view_permission) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] line : snapshot.lines()) {
                ps.setInt(1, hologramId);
                bind(ps, 2, line);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void delete(String name) {
        queue.submit(() -> {
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
