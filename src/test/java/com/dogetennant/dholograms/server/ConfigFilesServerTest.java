package com.dogetennant.dholograms.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.storage.YmlStorage;

import java.io.IOException;
import java.nio.file.Files;

import static com.dogetennant.dholograms.server.TestServer.messages;
import static org.assertj.core.api.Assertions.assertThat;

/** config.yml and holograms.yml as admins edit them by hand. */
class ConfigFilesServerTest {

    private static final String TYPO = "\nbroken: [no closing bracket\n";

    private TestServer mc;

    @BeforeEach
    void setUp() {
        mc = TestServer.start();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void startsWithTheBundledConfig() {
        assertThat(mc.plugin.isEnabled()).isTrue();
        assertThat(mc.file("config.yml")).exists();
        assertThat(mc.file("holograms.yml")).exists();
        assertThat(mc.plugin.getLang().getActiveCode()).isEqualTo("en_us");
    }

    /** docs/problems/dholograms-settings-file-typos.md */
    @Test
    void aTypoInConfigYmlAtStartKeepsDHologramsOffInsteadOfRunningOnTheBuiltInSettings() throws IOException {
        mc.replace("config.yml", "type: yml", "type: mysql");
        String broken = Files.readString(mc.file("config.yml")) + TYPO;
        Files.writeString(mc.file("config.yml"), broken);

        mc.restart();

        assertThat(mc.plugin.isEnabled()).isFalse(); // not on holograms.yml instead of the database
        assertThat(Files.readString(mc.file("config.yml"))).isEqualTo(broken);
    }

    /** docs/problems/dholograms-settings-file-typos.md */
    @Test
    void aTypoInHologramsYmlAtStartKeepsDHologramsOffSoNoSaveWritesOverIt() throws IOException {
        String broken = """
                holograms:
                  spawn:
                    world: world
                    x: 10.0
                    y: 70.0
                    z: -5.0
                broken: [no closing bracket
                """;
        Files.writeString(mc.file("holograms.yml"), broken);

        mc.restart();

        assertThat(mc.plugin.isEnabled()).isFalse();
        assertThat(Files.readString(mc.file("holograms.yml"))).isEqualTo(broken);
    }

    /** docs/problems/dholograms-settings-file-typos.md */
    @Test
    void aReloadWithATypoInConfigYmlKeepsTheSettingsFromBefore() throws IOException {
        PlayerMock admin = mc.admin();
        mc.replace("config.yml", "language: en_us", "language: cs_cz");
        admin.performCommand("dh reload");
        String broken = Files.readString(mc.file("config.yml")) + TYPO;
        Files.writeString(mc.file("config.yml"), broken);
        messages(admin);

        admin.performCommand("dh reload");

        assertThat(mc.plugin.getLang().getActiveCode()).isEqualTo("cs_cz");
        assertThat(messages(admin)).singleElement().asString().contains("config.yml");
        assertThat(Files.readString(mc.file("config.yml"))).isEqualTo(broken);
    }

    /** docs/problems/dholograms-reload-silent-about-storage-changes.md */
    @Test
    void aReloadAfterAStorageChangeSaysItNeedsARestart() throws IOException {
        PlayerMock admin = mc.admin();
        admin.performCommand("dh reload");
        assertThat(messages(admin)).containsExactly("[dHolograms] Configuration, language files and holograms reloaded.");
        mc.replace("config.yml", "type: yml", "type: mysql");

        admin.performCommand("dh reload");

        assertThat(messages(admin)).containsExactly("[dHolograms] Configuration, language files and holograms reloaded.",
                "[dHolograms] The storage settings (storage in config.yml) changed. dHolograms keeps using the"
                        + " old storage until the server restarts.");
        assertThat(mc.plugin.getStorage()).isInstanceOf(YmlStorage.class); // still holograms.yml
    }

    /** docs/problems/dholograms-reload-ignores-holograms-yml.md */
    @Test
    void aReloadWithATypoInHologramsYmlKeepsTheHologramsAndSavesNothingOverIt() throws IOException {
        PlayerMock admin = mc.admin();
        admin.performCommand("dh create spawn Hello");
        mc.saved();
        String broken = Files.readString(mc.file("holograms.yml")) + TYPO;
        Files.writeString(mc.file("holograms.yml"), broken);
        messages(admin);

        admin.performCommand("dh reload");
        admin.performCommand("dh addline spawn Second"); // in game, not over the file
        mc.saved();

        assertThat(messages(admin)).first().asString().contains("holograms.yml has a mistake");
        Hologram spawn = mc.hologram("spawn");
        assertThat(spawn.getLines()).hasSize(2);
        assertThat(Files.readString(mc.file("holograms.yml"))).isEqualTo(broken);
    }

    /** docs/problems/dholograms-language-command-not-kept.md */
    @Test
    void theLanguageCommandWithATypoInConfigYmlChangesNothing() throws IOException {
        PlayerMock admin = mc.admin();
        String broken = Files.readString(mc.file("config.yml")) + TYPO;
        Files.writeString(mc.file("config.yml"), broken);
        messages(admin);

        admin.performCommand("dh language cs_cz");

        assertThat(messages(admin)).singleElement().asString().contains("config.yml has a mistake");
        assertThat(mc.plugin.getLang().getActiveCode()).isEqualTo("en_us");
        assertThat(Files.readString(mc.file("config.yml"))).isEqualTo(broken);
    }
}
