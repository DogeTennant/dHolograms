package com.dogetennant.dholograms.hologram.line;

import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.hologram.line.StyledLetters.Letter;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** An animation changes the colours or the letters shown, and keeps the line's other formatting. */
class TextLineAnimationTest {

    private static final int GREEN = NamedTextColor.GREEN.value();

    private static TextLine animated(String text, AnimationType type) {
        TextLine line = new TextLine(mock(Hologram.class), 0, text, 0);
        line.setAnimationType(type);
        line.setAnimSpeed(1);
        return line;
    }

    /** The letters shown after the ticks, without the spaces a scroll adds. */
    private static List<Letter> shown(TextLine line, int ticks) {
        for (int i = 0; i < ticks; i++) line.tick();
        return StyledLetters.of(line.getCurrentComponent(null)).stream()
                .filter(letter -> !letter.text().isBlank())
                .toList();
    }

    private static String plain(TextLine line) {
        return PlainTextComponentSerializer.plainText().serialize(line.getCurrentComponent(null));
    }

    private static boolean has(Letter letter, TextDecoration decoration) {
        return letter.style().decoration(decoration) == TextDecoration.State.TRUE;
    }

    private static Integer colour(Letter letter) {
        TextColor colour = letter.style().color();
        return colour == null ? null : colour.value();
    }

    @ParameterizedTest
    @EnumSource(value = AnimationType.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    void everyAnimationKeepsBold(AnimationType type) {
        List<Letter> letters = shown(animated("<bold>Welcome", type), 10);

        assertThat(letters).isNotEmpty().allMatch(letter -> has(letter, TextDecoration.BOLD));
    }

    @ParameterizedTest
    @EnumSource(value = AnimationType.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    void everyAnimationKeepsTheBoldOfALegacyCode(AnimationType type) {
        List<Letter> letters = shown(animated("&lWelcome", type), 10);

        assertThat(letters).isNotEmpty().allMatch(letter -> has(letter, TextDecoration.BOLD));
    }

    @ParameterizedTest
    @EnumSource(value = AnimationType.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    void everyAnimationKeepsTheOtherFormatting(AnimationType type) {
        List<Letter> letters = shown(animated("<italic><underlined>Welcome", type), 10);

        assertThat(letters).isNotEmpty().allMatch(letter ->
                has(letter, TextDecoration.ITALIC) && has(letter, TextDecoration.UNDERLINED));
    }

    @Test
    void formattingStaysOnThePartItWasOn() {
        List<Letter> letters = shown(animated("<bold>We</bold>lcome", AnimationType.RAINBOW), 1);

        assertThat(letters).hasSize(7);
        assertThat(letters.subList(0, 2)).allMatch(letter -> has(letter, TextDecoration.BOLD));
        assertThat(letters.subList(2, 7)).noneMatch(letter -> has(letter, TextDecoration.BOLD));
    }

    @ParameterizedTest
    @EnumSource(value = AnimationType.class, names = {"RAINBOW", "WAVE", "BURN"})
    void aColourAnimationChoosesTheColours(AnimationType type) {
        List<Letter> letters = shown(animated("<green>Welcome", type), 10);

        assertThat(letters).hasSize(7).noneMatch(letter -> Integer.valueOf(GREEN).equals(colour(letter)));
    }

    @ParameterizedTest
    @EnumSource(value = AnimationType.class, names = {"TYPEWRITER", "SCROLL"})
    void theOtherAnimationsKeepTheColours(AnimationType type) {
        List<Letter> letters = shown(animated("<green>Welcome", type), 10);

        assertThat(letters).isNotEmpty().allMatch(letter -> Integer.valueOf(GREEN).equals(colour(letter)));
    }

    @Test
    void theRainbowStillMoves() {
        TextLine line = animated("<bold>Welcome", AnimationType.RAINBOW);

        Integer before = colour(shown(line, 1).getFirst());
        Integer after = colour(shown(line, 1).getFirst());

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void aTypewriterShowsOneLetterMoreEachStep() {
        TextLine line = animated("<bold>Welcome", AnimationType.TYPEWRITER);

        shown(line, 3);

        assertThat(plain(line)).isEqualTo("Wel");
    }

    @Test
    void aScrollSlidesAWindowOverTheText() {
        TextLine line = animated("<bold>Welcome", AnimationType.SCROLL);
        assertThat(plain(line)).isEqualTo("Welc");

        shown(line, 2);

        assertThat(plain(line)).isEqualTo("lcom");
    }

    @Test
    void anEmojiIsOneLetter() {
        List<Letter> letters = shown(animated("a😀b", AnimationType.RAINBOW), 1);

        assertThat(letters).extracting(Letter::text).containsExactly("a", "😀", "b");
    }

    @Test
    void anEmptyLineStaysEmpty() {
        assertThat(plain(animated("", AnimationType.RAINBOW))).isEmpty();
    }
}
