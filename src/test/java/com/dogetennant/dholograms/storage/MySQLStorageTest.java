package com.dogetennant.dholograms.storage;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.line.HologramLine;
import com.dogetennant.dholograms.hologram.line.TextLine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Holograms in MySQL, against a real MySQL 8.4 in Docker through the plugin's own MySQLStorage.
 * Skipped where Docker is not available. Every test gets its own database.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySQLStorageTest {

    private static MySQLContainer mysql;

    private final DHolograms plugin = mock(DHolograms.class);
    private final World world = mock(World.class);
    private MySQLStorage storage;

    private static synchronized MySQLContainer container() {
        if (mysql == null) {
            MySQLContainer started = new MySQLContainer("mysql:8.4")
                    .withTmpFs(Map.of("/var/lib/mysql", "rw"))
                    .withCommand("--innodb-flush-log-at-trx-commit=0", "--sync-binlog=0", "--skip-log-bin",
                            "--innodb-doublewrite=OFF", "--innodb-flush-method=nosync");
            started.start();
            mysql = started;
        }
        return mysql;
    }

    @BeforeEach
    void setUp() throws Exception {
        MySQLContainer server = container();
        String database = "t" + UUID.randomUUID().toString().replace("-", "");
        // root logs in once over a URL that fetches the server key; the plugin's URL then uses the cache
        try (Connection c = DriverManager.getConnection("jdbc:mysql://" + server.getHost() + ":"
                + server.getMappedPort(3306) + "/?useSSL=false&allowPublicKeyRetrieval=true", "root", server.getPassword());
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE " + database);
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.mysql.host", server.getHost());
        config.set("storage.mysql.port", server.getMappedPort(3306));
        config.set("storage.mysql.database", database);
        config.set("storage.mysql.username", "root");
        config.set("storage.mysql.password", server.getPassword());
        config.set("storage.mysql.pool-size", 2);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("dHolograms-test"));

        Server bukkit = mock(Server.class);
        when(bukkit.getLogger()).thenReturn(Logger.getLogger("server-test"));
        when(bukkit.getWorld("world")).thenReturn(world);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            call.getArgument(1, Runnable.class).run();
            return mock(BukkitTask.class);
        });
        when(bukkit.getScheduler()).thenReturn(scheduler);
        when(plugin.getServer()).thenReturn(bukkit);
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, bukkit);
        when(world.getName()).thenReturn("world");

        storage = open();
    }

    @AfterEach
    void tearDown() {
        if (storage != null) storage.close();
    }

    private MySQLStorage open() throws Exception {
        MySQLStorage opened = new MySQLStorage(plugin);
        opened.init();
        return opened;
    }

    /** What a restart finds: the storage closed (everything written) and opened again. */
    private List<Hologram> afterRestart() throws Exception {
        storage.close();
        storage = open();
        return storage.loadAll();
    }

    private Hologram hologram(String name, String... lines) {
        Hologram h = new Hologram(name, new Location(world, 10, 70, -5));
        for (int i = 0; i < lines.length; i++) h.addLineInternal(new TextLine(h, i, lines[i], 0));
        return h;
    }

    private static List<String> lines(Hologram h) {
        return h.getAllLines().stream().map(HologramLine::getRawContent).toList();
    }

    @Test
    void aHologramAndItsLinesAreKept() throws Exception {
        storage.save(hologram("spawn", "<gold>Welcome", "to the server"));

        List<Hologram> loaded = afterRestart();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getName()).isEqualTo("spawn");
        assertThat(lines(loaded.get(0))).containsExactly("<gold>Welcome", "to the server");
    }

    @Test
    void savingAgainReplacesTheLines() throws Exception {
        Hologram h = hologram("spawn", "one", "two");
        storage.save(h);
        h.removeLine(0, 0);
        h.addLineInternal(new TextLine(h, 1, "three", 0));

        storage.save(h);

        assertThat(lines(afterRestart().get(0))).containsExactly("two", "three");
    }

    @Test
    void aSaveThatFailsHalfwayKeepsWhatWasThere() throws Exception {
        Hologram h = hologram("spawn", "Welcome", "to the server");
        storage.save(h);
        // new lines, the last of which cannot be stored: longer than the column holds
        h.removeLine(0, 1);
        h.removeLine(0, 0);
        h.addLineInternal(new TextLine(h, 0, "Hello again", 0));
        h.addLineInternal(new TextLine(h, 1, "x".repeat(70_000), 0));

        storage.save(h);

        // the old lines used to be deleted before the new ones failed: half of the new hologram
        assertThat(lines(afterRestart().get(0))).containsExactly("Welcome", "to the server");
    }

    @Test
    void aDeletedHologramIsGone() throws Exception {
        storage.save(hologram("spawn", "a"));
        storage.save(hologram("shop", "b"));

        storage.delete("spawn");

        assertThat(afterRestart()).extracting(Hologram::getName).containsExactly("shop");
    }
}
