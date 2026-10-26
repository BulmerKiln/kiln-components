/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.InvalidDigestException;
import java.util.regex.Pattern;

/** The {@code <reference>} of a manifest URL: a tag or a digest. */
public sealed interface Reference {

    /** A tag (OCI distribution-spec, spec.md, "Pulling manifests"). */
    record Tag(String value) implements Reference {

        private static final Pattern GRAMMAR = Pattern.compile("[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}");

        public Tag {
            if (value.length() > 128 || !GRAMMAR.matcher(value).matches()) {
                throw new OciException(OciErrorCode.MANIFEST_INVALID, "Invalid tag");
            }
        }

        @Override
        public String toString() {
            return value;
        }
    }

    record ByDigest(Digest digest) implements Reference {

        @Override
        public String toString() {
            return digest.toString();
        }
    }

    /** Tags can't contain {@code :}, and every digest does. */
    static Reference parse(String value) {
        if (value.indexOf(':') < 0) {
            return new Tag(value);
        }
        try {
            return new ByDigest(Digest.parse(value));
        } catch (InvalidDigestException e) {
            throw new OciException(OciErrorCode.DIGEST_INVALID, "Invalid digest");
        }
    }
}
