/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;

/** A request the registry refuses, rendered by {@link NpmFormatModule} as npm's error body. */
public class NpmException extends RefusedException {

    private static final long serialVersionUID = 1L;

    public NpmException(Reason reason, String message) {
        super(Refusal.of(reason, message));
    }

    public static NpmException invalid(String message) {
        return new NpmException(Reason.INVALID, message);
    }

    public static NpmException notFound() {
        return new NpmException(Reason.NOT_FOUND, "Not found");
    }
}
