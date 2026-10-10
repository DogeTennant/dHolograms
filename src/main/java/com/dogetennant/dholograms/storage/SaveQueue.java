package com.dogetennant.dholograms.storage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One background thread for every write, in the order they were made.
 *
 * <p>Each save used to start its own async task, so two saves close together ran at the same
 * time: holograms.yml written by two threads at once, MySQL lines deleted and inserted twice.
 * Saves hand this a snapshot taken on the server thread, never the live hologram.
 */
final class SaveQueue {

    private final Logger logger;
    private final ExecutorService executor;

    SaveQueue(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "dHolograms-Save");
            thread.setDaemon(true);
            return thread;
        });
    }

    void submit(Runnable write) {
        Runnable logged = () -> {
            try {
                write.run();
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "A hologram save failed", e);
            }
        };
        try {
            executor.execute(logged);
        } catch (RejectedExecutionException e) {
            logged.run();                       // closed already (plugin disabling): write here
        }
    }

    /** Takes no new work and waits for what is queued (plugin disable). */
    void close(long timeout, TimeUnit unit) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                logger.severe("Hologram saves did not finish in time - some changes may be lost.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
