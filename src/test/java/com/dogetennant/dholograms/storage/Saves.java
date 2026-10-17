package com.dogetennant.dholograms.storage;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Waits for a storage's save thread (tests in other packages: {@link SaveQueue} is package-private). */
public final class Saves {

    private Saves() {
    }

    /** Returns once every save queued so far is written (a marker queued behind them; no sleeps). */
    public static void waitFor(StorageProvider storage) {
        CountDownLatch written = new CountDownLatch(1);
        queueOf(storage).submit(written::countDown);
        try {
            if (!written.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("the save thread is stuck");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static SaveQueue queueOf(StorageProvider storage) {
        for (Field field : storage.getClass().getDeclaredFields()) {
            if (field.getType() == SaveQueue.class) {
                try {
                    field.setAccessible(true);
                    return (SaveQueue) field.get(storage);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException(storage.getClass().getSimpleName() + " has no save queue");
    }
}
