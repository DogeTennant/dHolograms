package com.dogetennant.dholograms.hologram.line;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** The letters of a line, each with its own style, so an animation can change the colour and keep the rest. */
final class StyledLetters {

    record Letter(String text, Style style) {

        Component component() {
            return Component.text(text, style);
        }

        Component component(int rgb) {
            return Component.text(text, style.color(TextColor.color(rgb)));
        }
    }

    private StyledLetters() {}

    static List<Letter> of(Component line) {
        List<Letter> letters = new ArrayList<>();
        Deque<Style> styles = new ArrayDeque<>(List.of(Style.empty()));
        ComponentFlattener.basic().flatten(line, new FlattenerListener() {
            @Override
            public void pushStyle(Style style) {
                styles.push(styles.peek().merge(style)); // what a part inherits from the parts around it
            }

            @Override
            public void popStyle(Style style) {
                styles.pop();
            }

            @Override
            public void component(String text) {
                text.codePoints().forEach(letter -> letters.add(new Letter(Character.toString(letter), styles.peek())));
            }
        });
        return letters;
    }
}
