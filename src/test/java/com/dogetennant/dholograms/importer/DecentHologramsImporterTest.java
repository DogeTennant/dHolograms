package com.dogetennant.dholograms.importer;

import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.HologramFlag;
import com.dogetennant.dholograms.hologram.line.AnimationType;
import com.dogetennant.dholograms.hologram.line.HologramLine;
import com.dogetennant.dholograms.hologram.line.ItemLine;
import com.dogetennant.dholograms.hologram.line.TextLine;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** DecentHolograms files (as DecentHolograms saves them) into dHolograms holograms. */
class DecentHologramsImporterTest {

    private static final DecentHologramsImporter.Options OPTIONS = DecentHologramsImporter.Options.defaults(
            sound -> sound.equals("ENTITY_EXPERIENCE_ORB_PICKUP") ? "entity.experience_orb.pickup" : null);

    private final World world = mock(World.class);

    @TempDir
    Path plugins;

    @BeforeEach
    void setUp() throws Exception {
        Server server = mock(Server.class);
        when(server.getLogger()).thenReturn(Logger.getLogger("server-test"));
        when(server.getWorld("world")).thenReturn(world);
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
        when(world.getName()).thenReturn("world");
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    private static DecentHologramsImporter.Converted convert(String text) throws InvalidConfigurationException {
        return DecentHologramsImporter.convert("test", yaml(text), OPTIONS);
    }

    private static double y(Hologram hologram, int page, int line) {
        return hologram.getLineLocation(page, line).getY();
    }

    @Test
    void hologramSettingsAreKept() throws Exception {
        Hologram h = convert("""
                location: world:10.5:65.0:-3,25
                enabled: true
                display-range: 32
                update-range: 24
                update-interval: 40
                facing: 90.0
                down-origin: false
                pages:
                - lines:
                  - content: Hello
                    height: 0.3
                """).hologram();

        assertThat(h.getLocation().getWorld()).isSameAs(world);
        assertThat(h.getLocation().getX()).isEqualTo(10.5);
        assertThat(h.getLocation().getY()).isEqualTo(65.0);
        assertThat(h.getLocation().getZ()).isEqualTo(-3.25);      // DecentHolograms allows a comma
        assertThat(h.getDisplaySettings().getRange()).isEqualTo(32);
        assertThat(h.getDisplaySettings().getUpdateRange()).isEqualTo(24);
        assertThat(h.getRefreshTicks()).isEqualTo(40);
        assertThat(h.getDisplaySettings().getFacing()).isEqualTo(90f);
        assertThat(h.getDisplaySettings().isUnifiedBackground()).isFalse();
        assertThat(h.isDisabled()).isFalse();
        assertThat(h.getPage(0)).singleElement().isInstanceOf(TextLine.class)
                .extracting(HologramLine::getRawContent).isEqualTo("Hello");
    }

    @Test
    void coloursAndPlaceholdersAreConverted() throws Exception {
        DecentHologramsImporter.Converted result = convert("""
                location: world:0:70:0
                pages:
                - lines:
                  - content: '&6&lWelcome &#FF8800{player}'
                    height: 0.3
                  - content: '<#FF0000>Gradient</#0000FF> {#00FF00}green &x&1&2&3&4&5&6six [x] §cred'
                    height: 0.3
                  - content: '<GRADIENT:FFFFFF>old</GRADIENT:000000> <RAINBOW1>rainbow</RAINBOW> <SOLID:ABCDEF>solid {online}/{max_online} {status:lobby}'
                    height: 0.3
                """);
        List<HologramLine> lines = result.hologram().getPage(0);

        assertThat(lines.get(0).getRawContent()).isEqualTo("&6&lWelcome <#FF8800>%player_name%");
        assertThat(lines.get(1).getRawContent())
                .isEqualTo("<gradient:#FF0000:#0000FF>Gradient</gradient> <#00FF00>green <#123456>six █ &cred");
        assertThat(lines.get(2).getRawContent()).isEqualTo(
                "<gradient:#FFFFFF:#000000>old</gradient> <rainbow>rainbow</rainbow> <#ABCDEF>solid %server_online%/%server_max_players% {status:lobby}");
        assertThat(result.warnings())
                .anyMatch(w -> w.contains("{player} is now %player_name%"))
                .anyMatch(w -> w.contains("{status:lobby}"));
    }

    @Test
    void linesStayWhereTheyWereWithMixedHeights() throws Exception {
        // DecentHolograms: each line its height below the previous one
        Hologram h = convert("""
                location: world:0:100:0
                pages:
                - lines:
                  - content: Title
                    height: 0.3
                  - content: '#ICON: DIAMOND'
                    height: 0.6
                  - content: Under the icon
                    height: 0.3
                  - content: Last
                    height: 0.5
                """).hologram();

        assertThat(y(h, 0, 0)).isCloseTo(100.0, within(1e-9));
        assertThat(y(h, 0, 1)).isCloseTo(99.7, within(1e-9));
        assertThat(y(h, 0, 2)).isCloseTo(99.1, within(1e-9));
        assertThat(y(h, 0, 3)).isCloseTo(98.8, within(1e-9));
    }

    @Test
    void downOriginStacksUpFromTheLocation() throws Exception {
        Hologram h = convert("""
                location: world:0:100:0
                down-origin: true
                pages:
                - lines:
                  - content: Top
                    height: 0.3
                  - content: '#HEAD: PLAYER_HEAD'
                    height: 0.75
                  - content: Bottom
                    height: 0.3
                """).hologram();

        // the stack's top is its height (1.35) above the location
        assertThat(y(h, 0, 0)).isCloseTo(101.35, within(1e-9));
        assertThat(y(h, 0, 1)).isCloseTo(101.05, within(1e-9));
        assertThat(y(h, 0, 2)).isCloseTo(100.3, within(1e-9));
        assertThat(h.getDisplaySettings().isDownOrigin()).isFalse();   // the offsets do it
    }

    @Test
    void aMobLineIsLeftOutButKeepsItsSpace() throws Exception {
        DecentHologramsImporter.Converted result = convert("""
                location: world:0:100:0
                pages:
                - lines:
                  - content: Above
                    height: 0.3
                  - content: '#ENTITY: ZOMBIE'
                    height: 1.95
                  - content: Below
                    height: 0.3
                """);
        Hologram h = result.hologram();

        assertThat(h.getPage(0)).extracting(HologramLine::getRawContent).containsExactly("Above", "Below");
        assertThat(y(h, 0, 1)).isCloseTo(97.75, within(1e-9));
        assertThat(result.warnings()).anyMatch(w -> w.contains("#ENTITY: ZOMBIE"));
    }

    @Test
    void itemsAndHeads() throws Exception {
        String texture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWJjIn19fQ==";
        DecentHologramsImporter.Converted result = convert("""
                location: world:0:70:0
                pages:
                - lines:
                  - content: '#ICON: DIAMOND_SWORD !ENCHANTED'
                  - content: '#HEAD: PLAYER_HEAD (Notch)'
                  - content: '#SMALLHEAD: PLAYER_HEAD (%s)'
                  - content: '#HEAD: PLAYER_HEAD (HEADDATABASE_1234)'
                  - content: '#ICON: STONE:3 {CustomModelData:5}'
                  - content: '#HEAD: PLAYER_HEAD ({player})'
                """.formatted(texture));
        List<HologramLine> lines = result.hologram().getPage(0);

        assertThat(lines).allMatch(line -> line instanceof ItemLine);
        assertThat(lines).extracting(HologramLine::getRawContent).containsExactly(
                "DIAMOND_SWORD", "#SKULL:Notch", "#SKULL:texture:" + texture, "PLAYER_HEAD", "STONE", "PLAYER_HEAD");
        assertThat(((ItemLine) lines.get(0)).getRotationSpeed()).isEqualTo(3.0f);     // an icon turns
        assertThat(((ItemLine) lines.get(1)).getRotationSpeed()).isZero();            // a head does not
        assertThat(result.warnings())
                .anyMatch(w -> w.contains("HEADDATABASE_1234"))
                .anyMatch(w -> w.contains("NBT"))
                .anyMatch(w -> w.contains("({player})") || w.contains("'{player}'"));
    }

    @Test
    void clickActionsAreConverted() throws Exception {
        DecentHologramsImporter.Converted result = convert("""
                location: world:0:70:0
                pages:
                - lines:
                  - content: Click me
                  actions:
                    RIGHT:
                    - COMMAND:/spawn
                    - SOUND:ENTITY_EXPERIENCE_ORB_PICKUP:1:2
                    LEFT:
                    - CONSOLE:give {player} diamond 1
                    - MESSAGE:&aHi &#00FF00{player}
                    SHIFT_RIGHT:
                    - NONE
                    SHIFT_LEFT:
                    - NEXT_PAGE:board
                    - COMMAND:hello
                """);
        Hologram h = result.hologram();

        assertThat(h.getClickAction("RIGHT")).isEqualTo("COMMAND:spawn;SOUND:entity.experience_orb.pickup:1:2");
        // dHolograms fills in {player} in actions itself
        assertThat(h.getClickAction("LEFT")).isEqualTo("CONSOLE:give {player} diamond 1;MESSAGE:&aHi <#00FF00>{player}");
        assertThat(h.getClickAction("SHIFT_RIGHT")).isNull();
        assertThat(h.getClickAction("SHIFT_LEFT")).isEqualTo("NEXT_PAGE:board;COMMAND:hello");
        assertThat(result.warnings()).anyMatch(w -> w.contains("COMMAND:hello") && w.contains("chat"));
    }

    @Test
    void anUnknownSoundIsKeptAndReported() throws Exception {
        DecentHologramsImporter.Converted result = convert("""
                location: world:0:70:0
                pages:
                - lines:
                  - content: x
                  actions:
                    RIGHT:
                    - SOUND:NOT_A_SOUND
                """);

        assertThat(result.hologram().getClickAction("RIGHT")).isEqualTo("SOUND:not_a_sound");
        assertThat(result.warnings()).anyMatch(w -> w.contains("NOT_A_SOUND"));
    }

    @Test
    void pagesComeAlongWithTheFirstPagesActions() throws Exception {
        DecentHologramsImporter.Converted result = convert("""
                location: world:0:70:0
                pages:
                - lines:
                  - content: Page one
                  actions:
                    RIGHT:
                    - NEXT_PAGE:test
                - lines:
                  - content: Page two
                  - content: Second line
                  actions:
                    RIGHT:
                    - PREV_PAGE:test
                """);
        Hologram h = result.hologram();

        assertThat(h.getPageCount()).isEqualTo(2);
        assertThat(h.getPage(1)).extracting(HologramLine::getRawContent).containsExactly("Page two", "Second line");
        assertThat(h.getClickAction("RIGHT")).isEqualTo("NEXT_PAGE:test");
        assertThat(result.warnings()).anyMatch(w -> w.contains("page 2 has its own click actions"));
    }

    @Test
    void theOldestFormatWithoutPages() throws Exception {
        Hologram h = convert("""
                location: world:0:70:0
                lines:
                  '1':
                    content: '&aOld one'
                  '2':
                    content: '#ICON: APPLE'
                  '3':
                    content: After the icon
                """).hologram();

        assertThat(h.getPage(0)).extracting(HologramLine::getRawContent).containsExactly("&aOld one", "APPLE", "After the icon");
        // no heights saved: DecentHolograms' defaults (text 0.3, icon 0.6)
        assertThat(y(h, 0, 1)).isCloseTo(69.7, within(1e-9));
        assertThat(y(h, 0, 2)).isCloseTo(69.1, within(1e-9));
    }

    @Test
    void flagsPermissionsAnimationsAndDisabled() throws Exception {
        DecentHologramsImporter.Converted result = convert("""
                location: world:0:70:0
                enabled: false
                permission: vip.see
                flags:
                - DISABLE_ANIMATIONS
                - SOMETHING_ELSE
                pages:
                - lines:
                  - content: '<#ANIM:wave:&e,&c>Shiny</#ANIM>'
                    permission: staff.only
                    offsetX: 0.5
                    offsetZ: -0.25
                  - content: '<#ANIM:colors>Party</#ANIM>'
                """);
        Hologram h = result.hologram();
        TextLine wave = (TextLine) h.getPage(0).get(0);
        TextLine colours = (TextLine) h.getPage(0).get(1);

        assertThat(h.isDisabled()).isTrue();
        assertThat(h.getViewPermission()).isEqualTo("vip.see");
        assertThat(h.getFlags()).containsExactly(HologramFlag.DISABLE_ANIMATIONS);
        assertThat(wave.getRawContent()).isEqualTo("Shiny");
        assertThat(wave.getAnimationType()).isEqualTo(AnimationType.WAVE);
        assertThat(wave.getAnimColor1()).isEqualTo(0xFFFF55);
        assertThat(wave.getAnimColor2()).isEqualTo(0xFF5555);
        assertThat(wave.getViewPermission()).isEqualTo("staff.only");
        assertThat(wave.getXOffset()).isEqualTo(0.5);
        assertThat(wave.getZOffset()).isEqualTo(-0.25);
        assertThat(colours.getRawContent()).isEqualTo("Party");
        assertThat(colours.getAnimationType()).isEqualTo(AnimationType.NONE);
        assertThat(result.warnings())
                .anyMatch(w -> w.contains("SOMETHING_ELSE"))
                .anyMatch(w -> w.contains("'colors'"));
    }

    @Test
    void whatTheDatabaseCouldNotSaveIsLeftOut() throws Exception {
        // MySQLStorage: actions up to 512 characters, permissions up to 128
        DecentHologramsImporter.Converted result = convert("""
                location: world:0:70:0
                permission: %s
                pages:
                - lines:
                  - content: x
                  actions:
                    RIGHT:
                    - MESSAGE:%s
                    LEFT:
                    - COMMAND:/spawn
                """.formatted("p".repeat(129), "m".repeat(600)));
        Hologram h = result.hologram();

        assertThat(h.getViewPermission()).isNull();
        assertThat(h.getClickAction("RIGHT")).isNull();
        assertThat(h.getClickAction("LEFT")).isEqualTo("COMMAND:spawn");
        assertThat(result.warnings()).hasSize(2);
    }

    @Test
    void aHologramInAWorldThatIsNotLoadedIsSkipped() throws Exception {
        DecentHologramsImporter.Converted result = convert("""
                location: world_nether:0:70:0
                pages:
                - lines:
                  - content: x
                """);

        assertThat(result.hologram()).isNull();
        assertThat(result.skipped()).contains("world_nether");
    }

    @Test
    void namesAsDecentHologramsReadsThem() {
        assertThat(DecentHologramsImporter.nameOf("hologram_spawn.yml")).isEqualTo("spawn");
        assertThat(DecentHologramsImporter.nameOf("lobby.yml")).isEqualTo("lobby");
        assertThat(DecentHologramsImporter.nameOf("my holo.v2.yml")).isEqualTo("my_holo_v2");
        assertThat(DecentHologramsImporter.nameOf("hologram_.yml")).isEqualTo("hologram_");
        assertThat(DecentHologramsImporter.nameOf("x".repeat(80) + ".yml")).hasSize(64);
    }

    @Test
    void aWholeFolder() throws IOException {
        Path decent = plugins.resolve("DecentHolograms");
        write(decent.resolve("config.yml"), """
                defaults:
                  height:
                    text: 0.25
                custom-replacements:
                  '[heart]': '❤'
                """);
        write(decent.resolve("holograms/hologram_spawn.yml"), """
                location: world:0:70:0
                pages:
                - lines:
                  - content: 'I [heart] it'
                  - content: Second
                """);
        write(decent.resolve("holograms/lobby.yml"), """
                location: world:0:70:0
                pages:
                - lines:
                  - content: taken
                """);
        write(decent.resolve("holograms/nether.yml"), """
                location: world_nether:0:70:0
                pages:
                - lines:
                  - content: elsewhere
                """);
        write(decent.resolve("holograms/notes.txt"), "not a hologram");
        write(decent.resolve("displays/banner.yml"), "type: TEXT\n");
        List<Hologram> added = new ArrayList<>();

        DecentHologramsImporter.Report report = DecentHologramsImporter.importFolder(
                decent.toFile(), Set.of("lobby")::contains, added::add);

        assertThat(report.imported()).containsExactly("spawn");
        assertThat(report.skipped()).hasSize(2)
                .anyMatch(s -> s.startsWith("lobby: ") && s.contains("exists already"))
                .anyMatch(s -> s.startsWith("nether: ") && s.contains("world_nether"));
        assertThat(report.displays()).isEqualTo(1);
        Hologram spawn = added.getFirst();
        assertThat(spawn.getName()).isEqualTo("spawn");
        assertThat(spawn.getPage(0).getFirst().getRawContent()).isEqualTo("I ❤ it");   // its own replacements
        assertThat(spawn.getDisplaySettings().getLineHeight()).isEqualTo(0.25);         // its own default height
        assertThat(y(spawn, 0, 1)).isCloseTo(69.75, within(1e-9));
    }

    @Test
    void noDecentHologramsFolder() {
        assertThat(DecentHologramsImporter.importFolder(plugins.resolve("DecentHolograms").toFile(), name -> false, h -> {}))
                .isNull();
    }

    private static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }
}
