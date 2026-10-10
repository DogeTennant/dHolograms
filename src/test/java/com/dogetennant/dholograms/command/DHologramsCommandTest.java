package com.dogetennant.dholograms.command;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.HologramManager;
import com.dogetennant.dholograms.util.LangManager;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Without dholograms.admin, /dh shows a player nothing: no command, no suggestions, no help. */
class DHologramsCommandTest {

    private final DHolograms plugin = mock(DHolograms.class);
    private final LangManager lang = mock(LangManager.class);
    private final HologramManager manager = mock(HologramManager.class);
    private final Command command = mock(Command.class);
    private final DHologramsCommand dh = new DHologramsCommand(plugin);

    DHologramsCommandTest() {
        when(plugin.getLang()).thenReturn(lang);
        when(plugin.getHologramManager()).thenReturn(manager);
    }

    private static Player player(boolean admin) {
        Player player = mock(Player.class);
        when(player.hasPermission("dholograms.admin")).thenReturn(admin);
        return player;
    }

    @Test
    void aPlayerWithoutThePermissionGetsNoSuggestions() {
        Player player = player(false);
        String[][] typed = {
                {""}, {"h"}, {"help"}, {"help", ""}, {"import", ""}, {"delete", ""}, {"display", "x", ""},
                {"display", "x", "background", ""}, {"click", "x", ""}, {"language", ""}, {"show", "x", ""},
        };

        for (String[] args : typed) {
            assertThat(dh.onTabComplete(player, command, "dh", args)).as(String.join(" ", args)).isEmpty();
        }
        verifyNoInteractions(manager);           // not even hologram names are looked up
    }

    @Test
    void aPlayerWithoutThePermissionGetsNoHelp() {
        Player player = player(false);

        dh.onCommand(player, command, "dh", new String[0]);
        dh.onCommand(player, command, "dh", new String[] {"help"});
        dh.onCommand(player, command, "dh", new String[] {"import", "decentholograms"});
        dh.onCommand(player, command, "dh", new String[] {"list"});

        verify(lang, org.mockito.Mockito.times(4)).send(player, "no-permission");
        verify(lang, never()).getConfig();        // the help's list of commands is never read
        verify(player, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
        verifyNoInteractions(manager);
    }

    @Test
    void anAdminGetsTheSuggestions() {
        Player admin = player(true);

        assertThat(dh.onTabComplete(admin, command, "dh", new String[] {""})).contains("create", "import", "help");
        assertThat(dh.onTabComplete(admin, command, "dh", new String[] {"IM"})).containsExactly("import");
        assertThat(dh.onTabComplete(admin, command, "dh", new String[] {"import", ""})).containsExactly("decentholograms");
        verify(lang, never()).send(any(), anyString());
    }

    @Test
    void theCommandItselfNeedsThePermission() throws Exception {
        // Paper leaves a command out of a player's suggestions (and Bukkit refuses to run it) when
        // the player lacks the permission plugin.yml gives it - /dh and /dholograms alike
        YamlConfiguration pluginYml;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertThat(in).isNotNull();
            pluginYml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        assertThat(pluginYml.getConfigurationSection("commands").getKeys(false)).containsExactly("dholograms");
        assertThat(pluginYml.getString("commands.dholograms.permission")).isEqualTo("dholograms.admin");
        assertThat(pluginYml.getStringList("commands.dholograms.aliases")).containsExactly("dh");
        assertThat(pluginYml.getString("permissions.dholograms.admin.default")).isEqualTo("op");
    }
}
