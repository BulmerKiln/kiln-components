/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * Envelope encryption: every secret Kiln stores, such as a token-signing key, is encrypted under its
 * own data key, and the data key under the key-encryption key (KEK) a
 * {@link com.bulmerkiln.crypto.KeyProvider} holds.
 */
@NullMarked
package com.bulmerkiln.crypto;

import org.jspecify.annotations.NullMarked;
