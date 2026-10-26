/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import com.bulmerkiln.format.Reason;
import com.bulmerkiln.format.Refusal;
import com.bulmerkiln.format.RefusedException;

/** A path that isn't a valid Maven repository path; answered with 400, the path never echoed. */
public class InvalidMavenPathException extends RefusedException {

    private static final long serialVersionUID = 1L;

    public InvalidMavenPathException(String message) {
        super(Refusal.of(Reason.INVALID, message));
    }
}
