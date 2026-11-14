/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.util;

import java.util.AbstractMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the single-normalization branch of LinkedCaseInsensitiveMap.EntrySet.contains. */
class LinkedCaseInsensitiveMapEntryLookupTests {

    @Test
    void exactMapPreservesOriginalCasingAndNullValueMembership() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>(Locale.ROOT);
        map.put("Content-Type", "text/plain");
        map.put("X-NULL", null);
        assertTrue(map.entrySet().contains(new AbstractMap.SimpleImmutableEntry<>(
                "content-type", "text/plain")));
        assertTrue(map.entrySet().contains(new AbstractMap.SimpleImmutableEntry<>(
                "CONTENT-TYPE", "text/plain")));
        assertTrue(map.entrySet().contains(new AbstractMap.SimpleImmutableEntry<>(
                "x-null", null)));
        assertFalse(map.entrySet().contains(new AbstractMap.SimpleImmutableEntry<>(
                "content-type", "different")));
        assertFalse(map.entrySet().contains(new AbstractMap.SimpleImmutableEntry<>(
                "unknown", null)));
        assertFalse(map.entrySet().contains(new AbstractMap.SimpleImmutableEntry<>(
                (Object) 42, "text/plain")));
        assertEquals("Content-Type", map.keySet().iterator().next());
    }

    @Test
    void lowercasingRemainsLocaleSensitive() {
        LinkedCaseInsensitiveMap<Integer> turkish =
                new LinkedCaseInsensitiveMap<>(Locale.forLanguageTag("tr-TR"));
        turkish.put("I", 1);
        assertTrue(turkish.entrySet().contains(new AbstractMap.SimpleImmutableEntry<>(
                "\u0131", 1)));
        assertFalse(turkish.entrySet().contains(new AbstractMap.SimpleImmutableEntry<>(
                "i", 1)));

        LinkedCaseInsensitiveMap<Integer> german =
                new LinkedCaseInsensitiveMap<>(Locale.forLanguageTag("de-DE"));
        german.put("AB", 2);
        assertTrue(german.entrySet().contains(new AbstractMap.SimpleImmutableEntry<>(
                "ab", 2)));
        assertEquals(1, german.size());
    }

    private static final class ObservableEntry extends AbstractMap.SimpleEntry<String, String> {
        private int valueReads;

        ObservableEntry(String key, String value) {
            super(key, value);
        }

        @Override
        public String getValue() {
            this.valueReads++;
            return super.getValue();
        }
    }

    @Test
    void entryValueReadStillOccursOnlyAfterMatchingKey() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>(Locale.ROOT);
        map.put("Key", "value");
        ObservableEntry present = new ObservableEntry("KEY", "value");
        ObservableEntry missing = new ObservableEntry("unavailable", "value");
        assertTrue(map.entrySet().contains(present));
        assertFalse(map.entrySet().contains(missing));
        assertEquals(1, present.valueReads);
        assertEquals(0, missing.valueReads);
    }

    private static final class InstrumentedMap extends LinkedCaseInsensitiveMap<String> {
        private int keyCalls;
        private int getCalls;

        @Override
        public boolean containsKey(Object key) {
            this.keyCalls++;
            return super.containsKey(key);
        }

        @Override
        public @Nullable String get(Object key) {
            this.getCalls++;
            return super.get(key);
        }
    }

    @Test
    void subclassContainsKeyAndGetHooksRemainObservable() {
        InstrumentedMap map = new InstrumentedMap();
        map.put("Example", "stored");
        assertTrue(map.entrySet().contains(Map.entry("example", "stored")));
        assertEquals(1, map.keyCalls);
        assertEquals(1, map.getCalls);
        assertFalse(map.entrySet().contains(Map.entry("unavailable", "stored")));
        assertEquals(2, map.keyCalls);
        assertEquals(1, map.getCalls);
    }
}
