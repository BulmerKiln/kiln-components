/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.InvalidDigestException;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import java.util.regex.Pattern;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/** The {@code <reference>} of a manifest URL: a tag or a digest. */
public sealed interface OciReference {

    /** A tag (OCI distribution-spec, spec.md, "Pulling manifests"). */
    @Value
    static final class Tag implements OciReference {

        private final String value;

        private static final Pattern GRAMMAR = Pattern.compile("[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}");

        public Tag(String value) {
            if (value.length() > 128 || !GRAMMAR.matcher(value).matches()) {
                throw new OciException(OciErrorCode.MANIFEST_INVALID, "Invalid tag");
            }
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    @Value
    static final class ByDigest implements OciReference {

        private final Digest digest;

        @Override
        public String toString() {
            return digest.toString();
        }
    }

    /** Whether this names a digest other than {@code digest}: a push whose content doesn't match its URL. */
    default boolean contradicts(Digest digest) {
        return this instanceof ByDigest byDigest && !byDigest.getDigest().equals(digest);
    }

    /** The digest this names, if it's a digest. */
    default @Nullable Digest digest() {
        return this instanceof ByDigest byDigest ? byDigest.getDigest() : null;
    }

    /** The tag this names, if it's a tag. */
    default @Nullable String tag() {
        return this instanceof Tag tag ? tag.getValue() : null;
    }

    /** Tags can't contain {@code :}, and every digest does. */
    static OciReference parse(String value) {
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
