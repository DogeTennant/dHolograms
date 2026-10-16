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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Holograms in holograms.yml (the default storage). */
class YmlStorageTest {

    @TempDir
    Path dataFolder;

    private final DHolograms plugin = mock(DHolograms.class);
    private final World world = mock(World.class);
    private YmlStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("dHolograms-test"));
        Server server = mock(Server.class);
        when(server.getLogger()).thenReturn(Logger.getLogger("server-test"));
        when(server.getWorld("world")).thenReturn(world);
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
        when(world.getName()).thenReturn("world");
        storage = open();
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    private YmlStorage open() {
        YmlStorage opened = new YmlStorage(plugin);
        try {
            opened.init();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return opened;
    }

    /** docs/problems/dholograms-settings-file-typos.md */
    @Test
    void aTypoInHologramsYmlStopsTheStorageSoTheNextSaveCannotWriteOverIt() throws Exception {
        Path file = dataFolder.resolve("holograms.yml");
        String broken = """
                holograms:
                  spawn:
                    world: world
                    x: 10.0
                    y: 70.0
                    z: -5.0
                broken: [no closing bracket
                """;
        Files.writeString(file, broken);

        // before: read as "no holograms", and the next hologram saved wrote only itself into the file
        assertThatThrownBy(() -> new YmlStorage(plugin).init()).hasMessageContaining("holograms.yml");
        assertThat(Files.readString(file)).isEqualTo(broken);
    }

    private List<Hologram> afterRestart() {
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
    void aHologramAndItsLinesAreKept() {
        storage.save(hologram("spawn", "<gold>Welcome", "to the server"));

        List<Hologram> loaded = afterRestart();

        assertThat(loaded).extracting(Hologram::getName).containsExactly("spawn");
        assertThat(lines(loaded.get(0))).containsExactly("<gold>Welcome", "to the server");
    }

    @Test
    void aRenameLeavesOneHologram() {
        Hologram h = hologram("spawn", "a");
        storage.save(h);

        storage.delete("spawn");                // what HologramManager#renameHologram does
        h.setName("hub");
        storage.save(h);

        assertThat(afterRestart()).extracting(Hologram::getName).containsExactly("hub");
    }

    @Test
    void manySavesInARowLeaveAWholeFile() throws Exception {
        for (int i = 0; i < 200; i++) storage.save(hologram("h" + i, "line " + i, "x".repeat(i)));

        storage.close();

        YamlConfiguration file = YamlConfiguration.loadConfiguration(dataFolder.resolve("holograms.yml").toFile());
        assertThat(file.getConfigurationSection("holograms").getKeys(false)).hasSize(200);
        assertThat(Files.exists(dataFolder.resolve("holograms.yml.tmp"))).isFalse();
        storage = open();
        assertThat(storage.loadAll()).hasSize(200);
    }

    @Test
    void aSaveRightBeforeTheStopIsOnDisk() {
        storage.save(hologram("spawn", "a"));

        storage.close();                         // plugin disable

        assertThat(YamlConfiguration.loadConfiguration(dataFolder.resolve("holograms.yml").toFile())
                .contains("holograms.spawn")).isTrue();
    }
}
