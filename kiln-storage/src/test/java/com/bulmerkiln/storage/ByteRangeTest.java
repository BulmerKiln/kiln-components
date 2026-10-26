/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ByteRangeTest {

    @ParameterizedTest
    @CsvSource({
        "'bytes=0-499', 0, 499",
        "'bytes=500-999', 500, 999",
        "'bytes=500-', 500, 999",
        "'bytes=-200', 800, 999",
        "'bytes=-5000', 0, 999",
        "'bytes=900-5000', 900, 999",
        "'BYTES= 1-2 ', 1, 2",
        "'bytes=0-99999999999999999999', 0, 999",
    })
    void resolvesSingleRanges(String header, long first, long last) {
        assertThat(ByteRange.parse(header, 1000)).contains(new ByteRange(first, last));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "bytes",
                "bytes=",
                "bytes=-",
                "items=0-1",
                "bytes=0-1,5-6",
                "bytes=5-1",
                "bytes=a-b",
                "bytes=+1-2",
                "bytes=1--2",
                "bytes = 1-2"
            })
    void ignoresHeadersItCantServe(String header) {
        assertThat(ByteRange.parse(header, 1000)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes=1000-", "bytes=1000-2000", "bytes=-0", "bytes=99999999999999999999-"})
    void refusesUnsatisfiableRanges(String header) {
        assertThatThrownBy(() -> ByteRange.parse(header, 1000))
                .isInstanceOfSatisfying(
                        RangeNotSatisfiableException.class,
                        e -> assertThat(e.size()).isEqualTo(1000));
    }

    @Test
    void anEmptyRepresentationHasNoSatisfiableRange() {
        assertThatThrownBy(() -> ByteRange.parse("bytes=-1", 0)).isInstanceOf(RangeNotSatisfiableException.class);
        assertThatThrownBy(() -> ByteRange.parse("bytes=0-", 0)).isInstanceOf(RangeNotSatisfiableException.class);
    }

    @Property
    void resolvedRangesLieWithinTheRepresentation(
            @ForAll @LongRange(min = 0, max = Long.MAX_VALUE / 2) long size,
            @ForAll @LongRange(min = 0, max = Long.MAX_VALUE / 2) long first,
            @ForAll @LongRange(min = 0, max = Long.MAX_VALUE / 2) long last) {
        assertWithin(() -> ByteRange.parse("bytes=" + first + "-" + last, size), size);
        assertWithin(() -> ByteRange.parse("bytes=-" + last, size), size);
    }

    @FuzzTest
    void parseOnlyIgnoresOrRefuses(FuzzedDataProvider data) {
        long size = data.consumeLong(0, Long.MAX_VALUE);
        assertWithin(() -> ByteRange.parse(data.consumeRemainingAsString(), size), size);
    }

    @Test
    void theConstructorRefusesImpossibleRanges() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ByteRange(-1, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> new ByteRange(5, 4));
        assertThat(new ByteRange(5, 5).length()).isEqualTo(1);
        assertThat(new ByteRange(0, Long.MAX_VALUE - 1).length()).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @CsvSource({
        "'bytes=0-0', 1, 0, 0",
        "'bytes=-1', 1, 0, 0",
        "'bytes=0-', 1, 0, 0",
        "'bytes=007-009', 1000, 7, 9",
        "'Bytes=1-2', 1000, 1, 2",
        "'bytes=999-999', 1000, 999, 999",
        "'bytes=-999999999999999999', 1000, 0, 999",
        "'bytes=-9223372036854775808', 1000, 0, 999",
    })
    void handlesBoundaries(String header, long size, long first, long last) {
        assertThat(ByteRange.parse(header, size)).contains(new ByteRange(first, last));
    }

    /** Inputs that a looser parser might read as a different range. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "bytes=\u0661-\u0662", // Arabic-Indic digits
                "bytes=\uFF11-\uFF12", // fullwidth digits
                "bytes=1-2\u0000",
                "bytes=0x10-0x20",
                "bytes=1e3-2e3",
                "bytes=-1-5",
                "bytes=1.0-2.0",
                "bytes=1 2-3",
                "bytes=1-2;foo",
                "bytes:1-2",
                "bytes =1-2"
            })
    void ignoresAmbiguousHeaders(String header) {
        assertThat(ByteRange.parse(header, 1000)).isEmpty();
    }

    @Test
    void ignoresMultipleRangesInsteadOfServingTheirOverlap() {
        // Many overlapping ranges are a known amplification attack (CVE-2011-3192).
        assertThat(ByteRange.parse("bytes=" + "0-,".repeat(1000) + "0-", 1000)).isEmpty();
    }

    @Test
    void handlesHugeHeadersQuickly() {
        String huge = "bytes=" + "1".repeat(5_000_000) + "-";
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertThatThrownBy(() -> ByteRange.parse(huge, 1000)).isInstanceOf(RangeNotSatisfiableException.class);
            assertThat(ByteRange.parse("bytes=" + "-".repeat(5_000_000), 1000)).isEmpty();
        });
    }

    private static void assertWithin(Supplier<Optional<ByteRange>> parse, long size) {
        try {
            parse.get().ifPresent(range -> {
                assertThat(range.first()).isBetween(0L, size - 1);
                assertThat(range.last()).isBetween(range.first(), size - 1);
            });
        } catch (RangeNotSatisfiableException expected) {
            // Refusing is always allowed; serving outside the representation never is.
        }
    }
}
