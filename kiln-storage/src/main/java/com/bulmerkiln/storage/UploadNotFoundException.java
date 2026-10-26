/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

/** The upload session doesn't exist, or has expired. */
public class UploadNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UploadNotFoundException() {
        super("Upload session not found");
    }
}
