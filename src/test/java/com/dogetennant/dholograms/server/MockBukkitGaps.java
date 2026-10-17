package com.dogetennant.dholograms.server;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.This;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.mockbukkit.mockbukkit.entity.DisplayMock;
import org.mockbukkit.mockbukkit.entity.EntityMock;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Methods MockBukkit 4.116.3 leaves unimplemented (they throw {@code UnimplementedOperationException})
 * that every hologram entity uses: whether an entity is shown by default, and a display's billboard.
 * The server builds the entities itself, so no subclass can stand in; the methods are redefined once
 * per JVM and keep what was set. Who sees an entity is still MockBukkit's {@code hideEntity} /
 * {@code showEntity}.
 */
final class MockBukkitGaps {

    private static boolean filled;

    private MockBukkitGaps() {
    }

    static synchronized void fill() {
        if (filled) return;
        ByteBuddyAgent.install();
        new ByteBuddy()
                .redefine(EntityMock.class)
                .method(named("setVisibleByDefault").or(named("isVisibleByDefault")))
                .intercept(MethodDelegation.to(VisibleByDefault.class))
                .make()
                .load(EntityMock.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
        new ByteBuddy()
                .redefine(DisplayMock.class)
                .method(named("setBillboard").or(named("getBillboard")))
                .intercept(MethodDelegation.to(Billboards.class))
                .make()
                .load(DisplayMock.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
        filled = true;
    }

    /** {@code Entity#setVisibleByDefault} and its getter. Public: the redefined class calls it. */
    public static final class VisibleByDefault {

        private static final Map<UUID, Boolean> VALUES = new ConcurrentHashMap<>();

        private VisibleByDefault() {
        }

        public static void setVisibleByDefault(@This Entity entity, @Argument(0) boolean visible) {
            VALUES.put(entity.getUniqueId(), visible);
        }

        public static boolean isVisibleByDefault(@This Entity entity) {
            return VALUES.getOrDefault(entity.getUniqueId(), true);
        }
    }

    /** {@code Display#setBillboard} and its getter. Public: the redefined class calls it. */
    public static final class Billboards {

        private static final Map<UUID, Display.Billboard> VALUES = new ConcurrentHashMap<>();

        private Billboards() {
        }

        public static void setBillboard(@This Entity display, @Argument(0) Display.Billboard billboard) {
            VALUES.put(display.getUniqueId(), billboard);
        }

        public static Display.Billboard getBillboard(@This Entity display) {
            return VALUES.getOrDefault(display.getUniqueId(), Display.Billboard.FIXED);
        }
    }
}
