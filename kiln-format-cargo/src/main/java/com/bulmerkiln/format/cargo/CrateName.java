/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo;

import com.bulmerkiln.format.Reason;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A crate name, held to crates.io's rules: ASCII letters, digits, {@code -} and {@code _}, starting
 * with a letter, at most 64 characters. Names are unique under {@link #canonical()}: case and the
 * difference between {@code -} and {@code _} are ignored, so {@code Serde_JSON} can't impersonate
 * {@code serde-json}.
 */
public record CrateName(String value) {

    public static final int MAX_LENGTH = 64;

    private static final Pattern GRAMMAR = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    /**
     * Names Cargo would write as Windows device files, plus the Rust standard library crates, which
     * crates.io also reserves.
     */
    private static final Set<String> RESERVED = Set.of(
            "con",
            "prn",
            "aux",
            "nul",
            "com1",
            "com2",
            "com3",
            "com4",
            "com5",
            "com6",
            "com7",
            "com8",
            "com9",
            "lpt1",
            "lpt2",
            "lpt3",
            "lpt4",
            "lpt5",
            "lpt6",
            "lpt7",
            "lpt8",
            "lpt9",
            "std",
            "core",
            "alloc",
            "proc-macro",
            "test");

    public CrateName {
        if (!GRAMMAR.matcher(value).matches() || RESERVED.contains(canonical(value))) {
            throw new CargoException(Reason.INVALID, "Invalid crate name");
        }
    }

    /** The form uniqueness is checked in: lowercase, with {@code _} as {@code -}. */
    public String canonical() {
        return canonical(value);
    }

    /**
     * The crate's file in the sparse index (Cargo "Registry index", "Index files"): {@code 1/a},
     * {@code 2/ab}, {@code 3/a/abc}, or {@code ab/cd/abcd} for longer names, all lowercase.
     */
    public String indexPath() {
        String name = value.toLowerCase(Locale.ROOT);
        return switch (name.length()) {
            case 1 -> "1/" + name;
            case 2 -> "2/" + name;
            case 3 -> "3/" + name.charAt(0) + "/" + name;
            default -> name.substring(0, 2) + "/" + name.substring(2, 4) + "/" + name;
        };
    }

    @Override
    public String toString() {
        return value;
    }

    private static String canonical(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
