/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An inclusive byte range within a representation of known size (RFC 9110, section 14.1.2).
 *
 * @param first the first byte, from 0
 * @param last the last byte, inclusive
 */
public record ByteRange(long first, long last) {

    /** Enough digits for any {@code long}; longer values can't be satisfiable anyway. */
    private static final int MAX_DIGITS = 18;

    public ByteRange {
        if (first < 0 || last < first) {
            throw new IllegalArgumentException("Invalid range " + first + "-" + last);
        }
    }

    public long length() {
        return last - first + 1;
    }

    /**
     * Resolves a {@code Range} header against a representation of {@code size} bytes.
     *
     * <p>Following RFC 9110 (section 14.2), a header that can't be parsed, uses another unit or
     * asks for several ranges is ignored, and the whole representation is served: the result is
     * empty. Kiln serves single ranges only, which is all registry clients send.
     *
     * @throws RangeNotSatisfiableException if the range starts beyond the end, or the representation
     *     is empty (section 14.1.1)
     */
    public static Optional<ByteRange> parse(String header, long size) {
        String spec = singleRange(header);
        if (spec == null) {
            return Optional.empty();
        }
        int dash = spec.indexOf('-');
        String start = spec.substring(0, dash).strip();
        String end = spec.substring(dash + 1).strip();
        return start.isEmpty() ? suffix(end, size) : span(start, end, size);
    }

    /** The range after {@code bytes=}, or null for another unit, a malformed value or several ranges. */
    private static @Nullable String singleRange(String header) {
        String value = header.strip();
        if (!value.toLowerCase(Locale.ROOT).startsWith("bytes=")) {
            return null;
        }
        String spec = value.substring("bytes=".length()).strip();
        return spec.indexOf('-') < 0 || spec.indexOf(',') >= 0 ? null : spec;
    }

    /** {@code bytes=-N}: the last N bytes. */
    private static Optional<ByteRange> suffix(String digits, long size) {
        Long suffix = parseDigits(digits);
        if (suffix == null) {
            return Optional.empty();
        }
        if (suffix == 0 || size == 0) {
            throw new RangeNotSatisfiableException(size);
        }
        return Optional.of(new ByteRange(Math.max(0, size - suffix), size - 1));
    }

    /** {@code bytes=A-B}, or {@code bytes=A-} for everything from A. */
    private static Optional<ByteRange> span(String start, String end, long size) {
        Long first = parseDigits(start);
        Long last = end.isEmpty() ? Long.valueOf(Long.MAX_VALUE) : parseDigits(end);
        if (first == null || last == null || last < first) {
            return Optional.empty();
        }
        if (first >= size) {
            throw new RangeNotSatisfiableException(size);
        }
        return Optional.of(new ByteRange(first, Math.min(last, size - 1)));
    }

    /** Returns null unless {@code s} is 1 to 18 ASCII digits. Larger values are clamped by callers. */
    private static @Nullable Long parseDigits(String s) {
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() > MAX_DIGITS) {
            return s.chars().allMatch(c -> c >= '0' && c <= '9') ? Long.MAX_VALUE : null;
        }
        long result = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
            result = result * 10 + (c - '0');
        }
        return result;
    }
}
