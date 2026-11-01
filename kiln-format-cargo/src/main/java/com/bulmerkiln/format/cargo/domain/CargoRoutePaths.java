/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.domain;

import com.bulmerkiln.format.cargo.CargoException;
import com.bulmerkiln.format.constant.Reason;

/** The routes about one crate, below {@code api/v1/crates/}, for {@link CargoRoute#parse}. */
final class CargoRoutePaths {

    private CargoRoutePaths() {}

    /** {@code <name>/<version>/download|yank|unyank} or {@code <name>/owners}. */
    static CargoRoute crate(String[] parts) {
        if (parts.length == 2 && "owners".equals(parts[1])) {
            return new CargoRoute.Unsupported("owner");
        }
        if (parts.length != 3) {
            throw new CargoException(Reason.NOT_FOUND, "Not found");
        }
        CargoCrateName name = new CargoCrateName(parts[0]);
        String version = CargoRoute.version(parts[1]);
        return switch (parts[2]) {
            case "download" -> new CargoRoute.Download(name, version);
            case "yank" -> new CargoRoute.Yank(name, version, true);
            case "unyank" -> new CargoRoute.Yank(name, version, false);
            default -> throw new CargoException(Reason.NOT_FOUND, "Not found");
        };
    }
}
