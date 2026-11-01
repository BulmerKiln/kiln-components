/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.domain;

import com.bulmerkiln.core.annotation.UntrustedInput;
import com.bulmerkiln.format.cargo.CargoException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.SemanticVersion;
import lombok.Value;

/** A sparse-registry route below {@code /cargo/} (FMT-CARGO-7). */
public sealed interface CargoRoute {

    /** {@code index/config.json}: where downloads and the API are, and that auth is required. */
    @Value
    static final class Config implements CargoRoute {}

    /** One crate's index file, at the path the sparse index defines for its name. */
    @Value
    static final class Index implements CargoRoute {

        private final CargoCrateName name;
    }

    @Value
    static final class Publish implements CargoRoute {}

    @Value
    static final class Download implements CargoRoute {

        private final CargoCrateName name;

        private final String version;
    }

    /** {@code yank} or {@code unyank}. */
    @Value
    static final class Yank implements CargoRoute {

        private final CargoCrateName name;

        private final String version;

        private final boolean yanked;
    }

    /** A Cargo command Kiln answers with guidance, such as {@code owner} or {@code search}. */
    @Value
    static final class Unsupported implements CargoRoute {

        private final String command;
    }

    int MAX_LENGTH = 512;

    /** Parses the path below {@code /cargo/}, without a leading slash. */
    static CargoRoute parse(String path) {
        if (path.length() > MAX_LENGTH) {
            throw notFound();
        }
        if ("index/config.json".equals(path)) {
            return new Config();
        }
        if (path.startsWith("index/")) {
            String file = path.substring("index/".length());
            CargoCrateName name = new CargoCrateName(file.substring(file.lastIndexOf('/') + 1));
            if (!file.equals(name.indexPath())) {
                throw notFound();
            }
            return new Index(name);
        }
        if ("api/v1/crates/new".equals(path)) {
            return new Publish();
        }
        if ("api/v1/crates".equals(path)) {
            return new Unsupported("search");
        }
        if (path.startsWith("api/v1/crates/")) {
            return CargoRoutePaths.crate(
                    path.substring("api/v1/crates/".length()).split("/", -1));
        }
        throw notFound();
    }

    /** A crate version: strict SemVer (FMT-13). */
    @UntrustedInput
    static String version(String value) {
        if (!SemanticVersion.isValid(value)) {
            throw new CargoException(Reason.INVALID, "Invalid version");
        }
        return value;
    }

    private static CargoException notFound() {
        return new CargoException(Reason.NOT_FOUND, "Not found");
    }
}
