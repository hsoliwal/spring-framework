// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.synexia.m3.spring;

import com.m3.collections.M3ObjectStream;
import java.util.List;
import java.util.Objects;
import org.springframework.util.MultiValueMap;
/** Explicit per-key value-list semantics; existing Spring map contracts stay owned by Spring. */
public final class M3SpringStreams {
    private M3SpringStreams() {}
    public static <K,V> M3ObjectStream<V> snapshotValues(MultiValueMap<K,V> map,K key) {
        List<V> values = Objects.requireNonNull(map, "map").get(key);
        return M3ObjectStream.snapshot(values == null ? List.<V>of() : values);
    }
    /** Live with respect to mutation of the original list, not replacement of the mapping. */
    public static <K,V> M3ObjectStream<V> liveExistingValues(MultiValueMap<K,V> map,K key) {
        List<V> values = Objects.requireNonNull(map, "map").get(key);
        return M3ObjectStream.live(values == null ? List.<V>of() : values);
    }

    /** Universal Iterable adapter for List, Set, Queue, Bag and other provider occurrences. */
    public static <T> M3ObjectStream<T> snapshotAny(Iterable<? extends T> source) {
        return M3ObjectStream.snapshot(Objects.requireNonNull(source, "source"));
    }

    /** Universal live adapter; encounter and fail-fast semantics remain provider-owned. */
    public static <T> M3ObjectStream<T> liveAny(java.util.Collection<T> source) {
        return M3ObjectStream.live(Objects.requireNonNull(source, "source"));
    }
}
