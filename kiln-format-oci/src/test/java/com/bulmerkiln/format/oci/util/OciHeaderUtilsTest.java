/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.format.oci.OciException;
import org.junit.jupiter.api.Test;

class OciHeaderUtilsTest {

    @Test
    void readsChunkRangesAndPageSizes() {
        assertThat(OciHeaderUtils.contentRangeStart(null)).isNull();
        assertThat(OciHeaderUtils.contentRangeStart("10-19")).isEqualTo(10L);
        assertThatThrownBy(() -> OciHeaderUtils.contentRangeStart("bytes 0-1")).isInstanceOf(OciException.class);
        assertThatThrownBy(() -> OciHeaderUtils.contentRangeStart("5-4")).isInstanceOf(OciException.class);
        assertThat(OciHeaderUtils.pageSize(null)).isNull();
        assertThat(OciHeaderUtils.pageSize("50")).isEqualTo(50);
        assertThatThrownBy(() -> OciHeaderUtils.pageSize("-1")).isInstanceOf(OciException.class);
    }
}
