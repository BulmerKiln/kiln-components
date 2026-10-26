/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.fs;

import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.StorageBackend;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Offers the filesystem store as the {@code fs} backend; {@code kiln.storage.type=fs} selects it. */
@AutoConfiguration
@EnableConfigurationProperties(FsStorageProperties.class)
public class FsStorageAutoConfiguration {

    @Bean
    StorageBackend fsStorageBackend(FsStorageProperties properties) {
        return new StorageBackend() {
            @Override
            public String type() {
                return "fs";
            }

            @Override
            public BlobStore create() throws IOException {
                Path root = properties.root();
                if (root == null) {
                    throw new IllegalStateException("kiln.storage.fs.root must be set to the blob storage directory");
                }
                return new FsBlobStore(root);
            }
        };
    }
}
