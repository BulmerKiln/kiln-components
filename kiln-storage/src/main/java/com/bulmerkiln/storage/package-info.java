/**
 * Blob storage, as its own subsystem. Everything else reaches blobs through a
 * {@link com.bulmerkiln.storage.StorageRouter}, never a backend directly. Backends are separate
 * modules that each contribute a {@link com.bulmerkiln.storage.StorageBackend}; the installer picks
 * one with {@code kiln.storage.type}.
 */
@NullMarked
package com.bulmerkiln.storage;

import org.jspecify.annotations.NullMarked;
