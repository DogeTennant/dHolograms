package com.dogetennant.dholograms.server;

import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.line.HologramLine;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.file.Files;

import static com.dogetennant.dholograms.server.TestServer.messages;
import static org.assertj.core.api.Assertions.assertThat;

/** Holograms made, changed, shown and clicked on a running server with holograms.yml. */
class HologramsServerTest {

    private TestServer mc;
    private PlayerMock admin;

    @BeforeEach
    void setUp() {
        mc = TestServer.start();
        admin = mc.admin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private <E extends Event> E fire(E event) {
        mc.server.getPluginManager().callEvent(event);
        return event;
    }

    /**
     * The entity of the hologram's first item line. Text lines of a page share one text display
     * (the unified background, on by default), so they have no entity of their own.
     */
    private static Entity itemLine(Hologram hologram) {
        return hologram.getLines().stream().filter(HologramLine::isSpawned).findFirst().orElseThrow().getEntity();
    }

    @Test
    void aHologramIsShownToPlayersNearbyAndKeptOverARestart() {
        admin.performCommand("dh create spawn <gold>Welcome");
        admin.performCommand("dh addline spawn item:DIAMOND");
        PlayerMock alex = mc.player("Alex");
        mc.ticks(5); // a joining player's holograms are shown a moment later

        assertThat(mc.hologram("spawn").isVisibleTo(alex)).isTrue();
        assertThat(alex.canSee(itemLine(mc.hologram("spawn")))).isTrue();

        mc.restart();

        Hologram spawn = mc.hologram("spawn");
        assertThat(spawn.getLines()).extracting(HologramLine::getRawContent)
                .containsExactly("<gold>Welcome", "DIAMOND");
        assertThat(itemLine(spawn)).isNotNull(); // shown again
    }

    @Test
    void renameCloneAndDeleteAreKeptOverARestart() {
        admin.performCommand("dh create spawn Hello");
        admin.performCommand("dh rename spawn lobby");
        admin.performCommand("dh clone lobby shop");
        admin.performCommand("dh create old Bye");
        admin.performCommand("dh delete old");

        mc.restart();

        assertThat(mc.plugin.getHologramManager().getAllHolograms()).extracting(Hologram::getName)
                .containsExactlyInAnyOrder("lobby", "shop");
    }

    @Test
    void aRightClickRunsTheHologramsClickActions() {
        admin.performCommand("dh create spawn Hello");
        admin.performCommand("dh click spawn set right MESSAGE:<green>Hi {player}");
        PlayerMock alex = mc.player("Alex");

        PlayerInteractAtEntityEvent click = fire(new PlayerInteractAtEntityEvent(alex,
                mc.hologram("spawn").getClickEntity(), new Vector()));

        assertThat(click.isCancelled()).isTrue();
        assertThat(messages(alex)).containsExactly("Hi Alex");
    }

    @Test
    void theArrowsTurnThePagesOfAHologramForThatPlayer() {
        admin.performCommand("dh create spawn One");
        admin.performCommand("dh page spawn add");
        admin.performCommand("dh page spawn 2 addline Two");
        PlayerMock alex = mc.player("Alex");
        Hologram spawn = mc.hologram("spawn");

        fire(new PlayerInteractAtEntityEvent(alex, spawn.getNextNavEntity(), new Vector()));

        assertThat(spawn.getViewerPage(alex)).isEqualTo(1);
        assertThat(spawn.getViewerPage(admin)).isZero();
    }

    /** docs/problems/dholograms-dot-in-name-breaks-holograms-yml.md */
    @Test
    void aNameWithADotIsRefusedForANewACopiedAndARenamedHologram() {
        admin.performCommand("dh create spawn Hello");
        messages(admin);

        admin.performCommand("dh create spawn.top Hello");
        admin.performCommand("dh clone spawn spawn.copy");
        admin.performCommand("dh rename spawn spawn.old");

        assertThat(messages(admin)).containsExactly(
                "[dHolograms] spawn.top cannot be a hologram name: use letters, digits, _ and - only.",
                "[dHolograms] spawn.copy cannot be a hologram name: use letters, digits, _ and - only.",
                "[dHolograms] spawn.old cannot be a hologram name: use letters, digits, _ and - only.");
        assertThat(mc.plugin.getHologramManager().getAllHolograms()).extracting(Hologram::getName)
                .containsExactly("spawn");
    }

    @Test
    void namesWithLettersDigitsUnderscoresAndDashesAreKeptOverARestart() {
        admin.performCommand("dh create Shop_2-vip Hello");

        mc.restart();

        assertThat(mc.hologram("Shop_2-vip").getName()).isEqualTo("Shop_2-vip");
    }

    /** docs/problems/dholograms-reload-ignores-holograms-yml.md */
    @Test
    void aReloadReadsHologramsYmlAgainAndTheNextEditKeepsTheHandEdit() throws IOException {
        admin.performCommand("dh create spawn Hello");
        mc.saved();
        mc.replace("holograms.yml", "content: Hello", "content: Hi there");
        messages(admin);

        admin.performCommand("dh reload");
        String shown = mc.hologram("spawn").getLines().get(0).getRawContent();
        admin.performCommand("dh addline spawn Second");
        mc.saved();

        assertThat(shown).isEqualTo("Hi there");
        assertThat(messages(admin)).first().isEqualTo("[dHolograms] Configuration, language files and holograms reloaded.");
        assertThat(Files.readString(mc.file("holograms.yml"))).contains("content: Hi there", "content: Second");
    }

    @Test
    void aReloadKeepsWhoAHologramIsHiddenFrom() {
        admin.performCommand("dh create spawn item:DIAMOND");
        PlayerMock alex = mc.player("Alex");
        admin.performCommand("dh hide spawn Alex");

        admin.performCommand("dh reload");
        mc.ticks(20);

        assertThat(mc.hologram("spawn").isVisibleTo(alex)).isFalse();
        assertThat(mc.hologram("spawn").isVisibleTo(admin)).isTrue();
    }

    /** docs/problems/dholograms-enable-keeps-hologram-hidden.md */
    @Test
    void disableThenEnableShowsTheHologramAgainToEveryoneOnline() {
        admin.performCommand("dh create spawn item:DIAMOND");
        PlayerMock alex = mc.player("Alex");
        mc.ticks(5);
        Hologram spawn = mc.hologram("spawn");

        admin.performCommand("dh disable spawn");
        boolean seenWhileDisabled = alex.canSee(itemLine(spawn));
        admin.performCommand("dh enable spawn");
        mc.ticks(20); // the regular visibility check

        assertThat(seenWhileDisabled).isFalse();
        assertThat(spawn.isVisibleTo(alex)).isTrue();
        assertThat(alex.canSee(itemLine(spawn))).isTrue();
        assertThat(spawn.isVisibleTo(admin)).isTrue();
    }

    /** docs/problems/dholograms-cancels-hits-on-other-interactions.md */
    @Test
    void aHitOnAnotherPluginsInteractionEntityIsLeftToThatPlugin() {
        PlayerMock alex = mc.player("Alex");
        // e.g. the hitbox of a furniture plugin's chair
        Interaction chair = mc.world().spawn(new Location(mc.world(), 3.5, 64, 5.5), Interaction.class);

        EntityDamageByEntityEvent hit = fire(new EntityDamageByEntityEvent(alex, chair,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0));

        assertThat(hit.isCancelled()).isFalse();
    }

    @Test
    void aHitOnAHologramRunsItsLeftClickActions() {
        admin.performCommand("dh create spawn Hello");
        admin.performCommand("dh click spawn set left MESSAGE:Left {player}");
        PlayerMock alex = mc.player("Alex");

        EntityDamageByEntityEvent hit = fire(new EntityDamageByEntityEvent(alex,
                mc.hologram("spawn").getClickEntity(), EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0));

        assertThat(hit.isCancelled()).isTrue();
        assertThat(messages(alex)).containsExactly("Left Alex");
    }

    private void hologramInArena() throws IOException {
        Files.writeString(mc.file("holograms.yml"), """
                holograms:
                  arena-top:
                    world: arena
                    x: 5.0
                    y: 70.0
                    z: 5.0
                    pages:
                    - - type: TEXT
                        content: Welcome to the arena
                        height-offset: 0.0
                """);
    }

    /** docs/problems/dholograms-world-loaded-later.md */
    @Test
    void aHologramInAWorldAnotherPluginLoadsAtStartIsShown() throws IOException {
        hologramInArena();

        // "arena" is loaded by a world plugin (Multiverse) that starts after dHolograms
        mc.restart(() -> fire(new WorldLoadEvent(mc.server.addSimpleWorld("arena"))));

        assertThat(mc.hologram("arena-top").getLines()).extracting(HologramLine::getRawContent)
                .containsExactly("Welcome to the arena");
    }

    /** docs/problems/dholograms-world-loaded-later.md */
    @Test
    void aHologramInAWorldLoadedWhileTheServerRunsIsShownAfterAReload() throws IOException {
        hologramInArena();
        mc.restart();
        assertThat(mc.hologram("arena-top")).isNull(); // no world "arena" yet

        World arena = mc.server.addSimpleWorld("arena"); // /mv load arena
        fire(new WorldLoadEvent(arena));
        admin.performCommand("dh reload");

        assertThat(mc.hologram("arena-top")).isNotNull();
    }

    /** docs/problems/dholograms-language-command-not-kept.md */
    @Test
    void theLanguageCommandIsKeptInConfigYmlOverARestart() throws IOException {
        mc.replace("config.yml", "click-cooldown-ticks: 10", "click-cooldown-ticks: 15"); // a hand edit

        admin.performCommand("dh language cs_cz");
        mc.restart();

        assertThat(mc.plugin.getLang().getActiveCode()).isEqualTo("cs_cz");
        assertThat(Files.readString(mc.file("config.yml"))).contains("language: cs_cz", "click-cooldown-ticks: 15");
    }

    /** docs/problems/dholograms-show-unknown-player-message.md */
    @Test
    void showingAHologramToSomeoneOfflineSaysTheyAreNotOnline() {
        admin.performCommand("dh create spawn Hello");
        messages(admin);

        admin.performCommand("dh show spawn Nobody");
        admin.performCommand("dh hide spawn Nobody");

        assertThat(messages(admin)).containsExactly("[dHolograms] Player Nobody is not online.",
                "[dHolograms] Player Nobody is not online.");
    }
}
