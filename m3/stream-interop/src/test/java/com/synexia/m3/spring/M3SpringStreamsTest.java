// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.synexia.m3.spring;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import static org.junit.jupiter.api.Assertions.*;
class M3SpringStreamsTest {
    @Test void orderedMultiValueMapAndAbsentKey() {
        var data = new LinkedMultiValueMap<String,Integer>();
        data.add("x",1); data.add("x",1);
        var frozen = M3SpringStreams.snapshotValues(data,"x");
        var live = M3SpringStreams.liveExistingValues(data,"x");
        data.add("x",3);
        assertEquals(List.of(1,1),frozen.collect(Collectors.toList()));
        assertEquals(List.of(1,1,3),live.collect(Collectors.toList()));
        assertEquals(List.of(2,2,6),live.map(x -> x*2).collect(Collectors.toList()));
        assertEquals(0L,M3SpringStreams.snapshotValues(data,"missing").count());
    }

    @Test void allIterableCollectionFamiliesUseTheSameOperators() {
        var list = new java.util.ArrayList<>(List.of(4,4));
        var snap = M3SpringStreams.snapshotAny(list);
        var live = M3SpringStreams.liveAny(list);
        list.add(5);
        assertEquals(List.of(4,4),snap.collect(Collectors.toList()));
        assertEquals(List.of(4,4,5),live.collect(Collectors.toList()));
        assertEquals(List.of(8,8,10),live.map(v -> v*2).collect(Collectors.toList()));
    }
}
