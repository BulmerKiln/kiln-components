/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm;

import com.bulmerkiln.format.Reason;
import com.bulmerkiln.format.Refusal;
import com.bulmerkiln.format.RefusedException;

/** A request the registry refuses, rendered by {@link NpmFormatModule} as npm's error body. */
public class NpmException extends RefusedException {

    private static final long serialVersionUID = 1L;

    public NpmException(Reason reason, String message) {
        super(Refusal.of(reason, message));
    }
}
