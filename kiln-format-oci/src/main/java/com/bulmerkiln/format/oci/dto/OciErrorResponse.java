/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import java.util.List;
import lombok.Value;

/**
 * The spec's error envelope, {@code {"errors": [{"code", "message"}]}}; records keep the field order
 * clients expect.
 */
@Value
public final class OciErrorResponse {

    private final List<Item> errors;

    /** One error: its spec code and a message. */
    @Value
    public static final class Item {

        private final String code;

        private final String message;
    }
}
