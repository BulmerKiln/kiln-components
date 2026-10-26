/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo;

import com.bulmerkiln.format.Reason;
import com.bulmerkiln.format.Refusal;
import com.bulmerkiln.format.RefusedException;

/** A request the registry refuses, rendered by {@link CargoFormatModule} as Cargo's error body. */
public class CargoException extends RefusedException {

    private static final long serialVersionUID = 1L;

    public CargoException(Reason reason, String message) {
        super(Refusal.of(reason, message));
    }
}
