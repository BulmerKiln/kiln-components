/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import com.bulmerkiln.format.Refusal;
import com.bulmerkiln.format.RefusedException;

/**
 * A request the registry refuses, with its spec error code. Rendered by {@link OciFormatModule}.
 * {@code UNAUTHORIZED} needs a challenge, so it comes from the auth subsystem as a
 * {@link Refusal#unauthorized}, never from here.
 */
public class OciException extends RefusedException {

    private static final long serialVersionUID = 1L;

    private final OciErrorCode code;

    public OciException(OciErrorCode code, String message) {
        super(new Refusal(code.reason(), message, code.name(), null));
        this.code = code;
    }

    public OciErrorCode code() {
        return code;
    }
}
