package com.dogetennant.dholograms.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The translations in the jar, which every server gets on its first start. */
class TranslationFilesTest {

    private static final File FOLDER = new File("src/main/resources/translations");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)}");

    private static YamlConfiguration read(String name) throws Exception {
        YamlConfiguration file = new YamlConfiguration();
        file.load(new File(FOLDER, name)); // a YAML error fails the test instead of reading as empty
        return file;
    }

    private static Set<String> placeholders(Object text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(String.valueOf(text));
        while (matcher.find()) found.add(matcher.group(1));
        return found;
    }

    /** docs/problems/dholograms-settings-file-typos.md */
    @Test
    void everyTranslationIsValidYamlWithEveryEnglishTextAndItsPlaceholders() throws Exception {
        YamlConfiguration english = read("en_us.yml");
        String[] names = FOLDER.list((dir, name) -> name.endsWith(".yml"));
        assertThat(names).contains("en_us.yml", "cs_cz.yml");

        for (String name : names) {
            YamlConfiguration translation = read(name);
            assertThat(translation.getKeys(true)).as(name).containsAll(english.getKeys(true));
            for (String key : english.getKeys(true)) {
                if (english.isConfigurationSection(key)) continue;
                Object text = english.get(key);
                Object translated = translation.get(key);
                if (text instanceof List<?>) text = String.join(" ", english.getStringList(key));
                if (translated instanceof List<?>) translated = String.join(" ", translation.getStringList(key));
                assertThat(placeholders(translated)).as(name + " " + key).isEqualTo(placeholders(text));
            }
        }
    }
}
