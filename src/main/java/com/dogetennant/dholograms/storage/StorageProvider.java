package com.dogetennant.dholograms.storage;

import com.dogetennant.dholograms.hologram.Hologram;

import java.util.List;

public interface StorageProvider {

    /** Initialise the storage backend (create files / connect pool / create tables). */
    void init() throws Exception;

    /** Close connections and release resources. */
    void close();

    /** Load all persisted holograms. */
    List<Hologram> loadAll();

    /** Persist or update a hologram (upsert semantics). */
    void save(Hologram hologram);

    /** Remove a hologram by name. */
    void delete(String name);
}
