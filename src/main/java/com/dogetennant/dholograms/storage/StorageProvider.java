package com.dogetennant.dholograms.storage;

import com.dogetennant.dholograms.hologram.Hologram;

import java.io.IOException;
import java.util.List;

public interface StorageProvider {

    /** Initialise the storage backend (create files / connect pool / create tables). */
    void init() throws Exception;

    /** Close connections and release resources. */
    void close();

    /** Load all persisted holograms. */
    List<Hologram> loadAll();

    /**
     * Reads every hologram again from the file or database, once the saves queued before are
     * written (/dh reload). Server thread.
     *
     * @throws IOException when holograms.yml has a YAML error (the file is left as it is)
     */
    List<Hologram> reloadAll() throws IOException;

    /** Persist or update a hologram (upsert semantics). */
    void save(Hologram hologram);

    /** Remove a hologram by name. */
    void delete(String name);
}
