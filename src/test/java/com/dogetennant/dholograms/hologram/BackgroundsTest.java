package com.dogetennant.dholograms.hologram;

import org.bukkit.Color;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Text display backgrounds: only no box at all keeps water and glass visible behind a hologram. */
class BackgroundsTest {

    private static final Logger LOG = Logger.getLogger("dHolograms-test");

    @AfterEach
    void serverDefaultBack() {
        Backgrounds.configure("none", LOG);
    }

    @Test
    void whatAnAdminCanType() {
        assertThat(Backgrounds.parse("none")).isEqualTo(Backgrounds.NONE);
        assertThat(Backgrounds.parse("NONE")).isEqualTo(Backgrounds.NONE);
        assertThat(Backgrounds.parse("default")).isEqualTo(Backgrounds.DEFAULT);
        assertThat(Backgrounds.parse("game")).isEqualTo(Backgrounds.GAME);
        assertThat(Backgrounds.parse("#FF0000")).isEqualTo(0xFFFF0000);
        assertThat(Backgrounds.parse("#80000000")).isEqualTo(0x80000000);
        assertThat(Backgrounds.parse("80000000")).isEqualTo(0x80000000);
    }

    @Test
    void anOpaqueWhiteIsNotMistakenForDefault() {
        // -1 is the stored "default"; #FFFFFFFF used to turn into the game's grey box
        assertThat(Backgrounds.parse("#FFFFFFFF")).isNotEqualTo(Backgrounds.DEFAULT).isEqualTo(0xFFFFFFFE);
        assertThat(Backgrounds.parse("#FFFFFF")).isEqualTo(0xFFFFFFFE);
    }

    @Test
    void aColourWithoutAlphaIsNoBox() {
        assertThat(Backgrounds.parse("#00123456")).isEqualTo(Backgrounds.NONE);
        assertThat(Backgrounds.normalise(0x00000001)).isEqualTo(Backgrounds.NONE);   // never the game's box by accident
    }

    @Test
    void nonsenseIsRefused() {
        assertThat(Backgrounds.parse("blue")).isNull();
        assertThat(Backgrounds.parse("#12345")).isNull();
        assertThat(Backgrounds.parse("#GGGGGG")).isNull();
    }

    @Test
    void noneDrawsNothing() {
        TextDisplay display = mock(TextDisplay.class);

        Backgrounds.apply(display, Backgrounds.NONE);

        verify(display).setDefaultBackground(false);
        verify(display).setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
    }

    @Test
    void defaultFollowsTheServerSetting() {
        TextDisplay none = mock(TextDisplay.class);
        Backgrounds.apply(none, Backgrounds.DEFAULT);
        verify(none).setBackgroundColor(Color.fromARGB(0, 0, 0, 0));

        Backgrounds.configure("game", LOG);
        TextDisplay game = mock(TextDisplay.class);
        Backgrounds.apply(game, Backgrounds.DEFAULT);
        verify(game).setDefaultBackground(true);
        verify(game, never()).setBackgroundColor(any());

        Backgrounds.configure("#40000000", LOG);
        TextDisplay tinted = mock(TextDisplay.class);
        Backgrounds.apply(tinted, Backgrounds.DEFAULT);
        verify(tinted).setBackgroundColor(Color.fromARGB(0x40, 0, 0, 0));
    }

    @Test
    void aBrokenServerSettingMeansNone() {
        Backgrounds.configure("rubbish", LOG);
        TextDisplay display = mock(TextDisplay.class);

        Backgrounds.apply(display, Backgrounds.DEFAULT);

        verify(display).setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
    }

    @Test
    void aChosenColourIsUsed() {
        TextDisplay display = mock(TextDisplay.class);

        Backgrounds.apply(display, 0x80FF8800);

        verify(display).setDefaultBackground(false);
        verify(display).setBackgroundColor(Color.fromARGB(0x80, 0xFF, 0x88, 0x00));
    }

    @Test
    void newHologramsFollowTheServerDefaultAndKeepIt() {
        DisplaySettings settings = new DisplaySettings();
        assertThat(settings.getBackgroundColor()).isEqualTo(Backgrounds.DEFAULT);

        settings.setBackgroundColor(Backgrounds.GAME);

        assertThat(DisplaySettings.deserialize(settings.serialize()).getBackgroundColor()).isEqualTo(Backgrounds.GAME);
    }
}
