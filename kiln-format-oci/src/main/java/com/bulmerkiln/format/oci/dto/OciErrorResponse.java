/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import java.util.List;

/**
 * The spec's error envelope, {@code {"errors": [{"code", "message"}]}}; records keep the field order
 * clients expect.
 */
public record OciErrorResponse(List<Item> errors) {

    /** One error: its spec code and a message. */
    public record Item(String code, String message) {}
}
