package com.dogetennant.dholograms.server;

import com.dogetennant.dholograms.DHolograms;
import com.dogetennant.dholograms.hologram.Hologram;
import com.dogetennant.dholograms.storage.Saves;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
        MockBukkitGaps.fill();
        ServerMock server = MockBukkit.mock();
        server.addSimpleWorld("world");
        TestServer mc = new TestServer(server, MockBukkit.load(DHolograms.class));
        server.getScheduler().performOneTick(); // the holograms are read on the first tick
        return mc;
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
        restart(() -> { });
    }

    /**
     * A restart with {@code whileStarting} done after dHolograms started and before the first tick
     * - where other plugins start (Multiverse loading its worlds).
     */
    void restart(Runnable whileStarting) {
        server.getPluginManager().disablePlugin(plugin);
        plugin.reloadConfig();
        server.getPluginManager().enablePlugin(plugin);
        whileStarting.run();
        server.getScheduler().performOneTick();
    }

    PlayerMock admin() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        admin.setLocation(new Location(world(), 0.5, 64, 0.5));
        return admin;
    }

    /** A player without op standing a few blocks from the admin (well within a hologram's range). */
    PlayerMock player(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setLocation(new Location(world(), 3.5, 64, 3.5));
        return player;
    }

    World world() {
        return server.getWorld("world");
    }

    Hologram hologram(String name) {
        return plugin.getHologramManager().getHologram(name);
    }

    /** Returns once every hologram save made so far is written to disk. */
    void saved() {
        Saves.waitFor(plugin.getStorage());
    }

    /** The plugin's delayed work (a joining player's holograms are shown 5 ticks later). */
    void ticks(int ticks) {
        server.getScheduler().performTicks(ticks);
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
