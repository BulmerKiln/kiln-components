/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm;

import com.bulmerkiln.format.Reason;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Checks a published tarball against the {@code dist.integrity} (Subresource Integrity, sha512)
 * and {@code dist.shasum} (sha1) the client declared, hashing the bytes as they stream in.
 */
public final class DistVerifier {

    private static final Pattern SHASUM = Pattern.compile("[0-9a-f]{40}");

    private final MessageDigest sha512 = digest("SHA-512");
    private final MessageDigest sha1 = digest("SHA-1");

    public void update(byte[] bytes, int offset, int length) {
        sha512.update(bytes, offset, length);
        sha1.update(bytes, offset, length);
    }

    /**
     * Completes the hashes and checks them. {@code integrity} is required; npm has sent it on every
     * publish since npm 5. If several sha512 values are listed, one must match (W3C SRI, section
     * 3.3.5). Other algorithms in the list are ignored, as SRI uses only the strongest.
     */
    public void verify(String integrity, @Nullable String shasum) {
        byte[] actual512 = sha512.digest();
        byte[] actual1 = sha1.digest();
        boolean sawSha512 = false;
        boolean matched = false;
        for (String token : integrity.strip().split("[ \\t\\n]+", -1)) {
            if (!token.startsWith("sha512-")) {
                continue;
            }
            sawSha512 = true;
            String encoded = token.substring("sha512-".length());
            int options = encoded.indexOf('?');
            byte[] expected = decode(options < 0 ? encoded : encoded.substring(0, options));
            matched |= MessageDigest.isEqual(expected, actual512);
        }
        if (!sawSha512) {
            throw new NpmException(Reason.INVALID, "dist.integrity must include a sha512 hash");
        }
        if (!matched) {
            throw new NpmException(Reason.INVALID, "The tarball does not match dist.integrity");
        }
        if (shasum != null
                && (!SHASUM.matcher(shasum).matches()
                        || !MessageDigest.isEqual(HexFormat.of().parseHex(shasum), actual1))) {
            throw new NpmException(Reason.INVALID, "The tarball does not match dist.shasum");
        }
    }

    /** The 64-byte hash, or an empty array, which matches nothing, for anything else. */
    private static byte[] decode(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return bytes.length == 64 ? bytes : new byte[0];
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides " + algorithm, e);
        }
    }
}
