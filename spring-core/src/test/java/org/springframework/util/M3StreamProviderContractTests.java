/*
 * Copyright 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.springframework.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Provider contract oracle for adapters over Spring's mutable multi-value views. */
class M3StreamProviderContractTests {
    @Test
    void liveMultiValueViewsAndCaseInsensitiveKeysRemainProviderOwned() {
        LinkedMultiValueMap<String, String> multi = new LinkedMultiValueMap<>();
        multi.add("X-Trace", "one");
        multi.add("X-Trace", "one");
        List<String> live = multi.get("X-Trace");
        List<String> detached = new ArrayList<>(live);

        multi.add("X-Trace", "two");

        assertEquals(List.of("one", "one"), detached);
        assertEquals(List.of("one", "one", "two"), live);
        assertNotSame(live, detached);

        LinkedCaseInsensitiveMap<String> headers = new LinkedCaseInsensitiveMap<>(Locale.ROOT);
        headers.put("X-Trace", "present");
        assertEquals("present", headers.get("x-trace"));
    }
}
