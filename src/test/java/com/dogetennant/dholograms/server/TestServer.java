package com.dogetennant.dholograms.server;

import com.dogetennant.dholograms.DHolograms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole plugin on a MockBukkit server: its own config, translations and holograms.yml in a
 * temporary folder, every listener and command registered. Stop it with {@code MockBukkit.unmock()}.
 */
final class TestServer {

    final ServerMock server;
    final DHolograms plugin;

    private TestServer(ServerMock server, DHolograms plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    static TestServer start() {
        removeMockitoServer();
        ServerMock server = MockBukkit.mock();
        server.addSimpleWorld("world");
        return new TestServer(server, MockBukkit.load(DHolograms.class));
    }

    /** The logic tests leave a Mockito server in {@code Bukkit}; MockBukkit refuses to start next to it. */
    private static void removeMockitoServer() {
        if (Bukkit.getServer() == null || Bukkit.getServer() instanceof ServerMock) return;
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot remove the other tests' server", e);
        }
    }

    /**
     * What a server start does with the plugin: stop it, then start it again. A real start reads
     * config.yml from disk, so the copy Bukkit kept from the run before is thrown away first.
     */
    void restart() {
        server.getPluginManager().disablePlugin(plugin);
        plugin.reloadConfig();
        server.getPluginManager().enablePlugin(plugin);
    }

    PlayerMock admin() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        return admin;
    }

    Path file(String name) {
        return plugin.getDataFolder().toPath().resolve(name);
    }

    /** Replaces {@code from} with {@code to} in a file in the plugin folder (it must be there). */
    void replace(String name, String from, String to) throws IOException {
        String text = Files.readString(file(name));
        if (!text.contains(from)) throw new IllegalArgumentException(name + " has no '" + from + "'");
        Files.writeString(file(name), text.replace(from, to));
    }

    /** What the player was told since the last call, as plain text. */
    static List<String> messages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return messages;
    }
}
