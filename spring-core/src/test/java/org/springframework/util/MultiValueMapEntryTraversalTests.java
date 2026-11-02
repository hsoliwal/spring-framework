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
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultiValueMapEntryTraversalTests {

	@Test
	void singleToMultiContainsValuePreservesEntryGetterOrderAndShortCircuit() {
		List<String> trace = new ArrayList<>();
		Map<String, String> source = tracedMap(List.of(entry("a", "one", trace), entry("b", "two", trace)), trace);
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		assertTrue(adapter.containsValue(List.of("one")));
		assertEquals(List.of("entries", "iterator", "hasNext", "next", "key:a", "value:a"), trace);
		trace.clear();
		assertTrue(adapter.containsValue(List.of("two")));
		assertEquals(List.of("iterator", "hasNext", "next", "key:a", "value:a", "hasNext", "next", "key:b", "value:b"), trace);
	}

	@Test
	void multiToSingleContainsValuePreservesEntryGetterOrderAndShortCircuit() {
		List<String> trace = new ArrayList<>();
		Map<String, List<String>> source = tracedMap(List.of(entry("a", List.of("one"), trace), entry("b", List.of("two"), trace)), trace);
		Map<String, String> adapter = new MultiValueMapAdapter<>(source).asSingleValueMap();
		assertTrue(adapter.containsValue("one"));
		assertEquals(List.of("entries", "iterator", "hasNext", "next", "key:a", "value:a"), trace);
		trace.clear();
		assertTrue(adapter.containsValue("two"));
		assertEquals(List.of("iterator", "hasNext", "next", "key:a", "value:a", "hasNext", "next", "key:b", "value:b"), trace);
	}

	@Test
	void equalityKeepsCachedEntrySetAndStopsAtFirstMismatch() {
		List<String> trace = new ArrayList<>();
		Map<String, String> source = tracedMap(List.of(entry("a", "one", trace), entry("b", "two", trace)), trace);
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		adapter.entrySet();
		trace.clear();
		assertFalse(adapter.equals(Map.of("a", List.of("different"), "b", List.of("two"))));
		assertEquals(List.of("size", "iterator", "hasNext", "next", "key:a", "value:a"), trace);
		trace.clear();
		assertTrue(adapter.equals(Map.of("a", List.of("one"), "b", List.of("two"))));
		assertEquals(List.of("size", "iterator", "hasNext", "next", "key:a", "value:a", "hasNext", "next", "key:b", "value:b", "hasNext"), trace);
	}

	@Test
	void inverseEqualityKeepsCachedEntrySetAndStopsAtFirstMismatch() {
		List<String> trace = new ArrayList<>();
		Map<String, List<String>> source = tracedMap(List.of(entry("a", List.of("one"), trace), entry("b", List.of("two"), trace)), trace);
		Map<String, String> adapter = new MultiValueMapAdapter<>(source).asSingleValueMap();
		adapter.entrySet();
		trace.clear();
		assertFalse(adapter.equals(Map.of("a", "different", "b", "two")));
		assertEquals(List.of("size", "iterator", "hasNext", "next", "key:a", "value:a"), trace);
		trace.clear();
		assertTrue(adapter.equals(Map.of("a", "one", "b", "two")));
		assertEquals(List.of("size", "iterator", "hasNext", "next", "key:a", "value:a", "hasNext", "next", "key:b", "value:b", "hasNext"), trace);
	}

	@Test
	void nullAndEmptyListObservationsKeepTheirOriginalBehavior() {
		Map<String, String> single = new LinkedHashMap<>();
		single.put("key", null);
		MultiValueMap<String, String> multi = MultiValueMap.fromSingleValue(single);
		assertFalse(multi.containsValue(null));
		assertTrue(multi.containsValue(Collections.singletonList(null)));
		assertTrue(multi.equals(Collections.singletonMap("key", Collections.singletonList(null))));
		LinkedMultiValueMap<String, String> underlying = new LinkedMultiValueMap<>();
		underlying.put("key", Collections.singletonList(null));
		Map<String, String> adapted = underlying.asSingleValueMap();
		assertTrue(adapted.containsValue(null));
		assertTrue(adapted.equals(Collections.singletonMap("key", null)));
		underlying.put("key", Collections.emptyList());
		assertThrows(IndexOutOfBoundsException.class, () -> adapted.containsValue(null));
		assertThrows(IndexOutOfBoundsException.class, () -> adapted.equals(Collections.singletonMap("key", null)));
		underlying.put("key", null);
		assertThrows(NullPointerException.class, () -> adapted.containsValue(null));
		assertFalse(adapted.equals(Collections.singletonMap("key", null)));
	}

	@Test
	void userEqualityReceivesFreshSingletonListsAndExceptionsStillPropagate() {
		Map<String, Object> backing = new LinkedHashMap<>();
		backing.put("a", new Object());
		backing.put("b", new Object());
		MultiValueMap<String, Object> adapter = MultiValueMap.fromSingleValue(backing);
		List<Object> observed = new ArrayList<>();
		Object probe = new Object() {
			@Override public boolean equals(Object other) { observed.add(other); return false; }
			@Override public int hashCode() { return 1; }
		};
		assertFalse(adapter.containsValue(probe));
		assertEquals(2, observed.size());
		assertNotSame(observed.get(0), observed.get(1));
		assertSame(backing.get("a"), ((List<?>) observed.get(0)).get(0));
		Object failing = new Object() {
			@Override public boolean equals(Object other) { throw new IllegalStateException("callback"); }
			@Override public int hashCode() { return 1; }
		};
		assertThrows(IllegalStateException.class, () -> adapter.containsValue(failing));
	}

	@Test
	void publicEntriesRemainFreshDetachedAndImmutable() {
		LinkedHashMap<String, String> source = new LinkedHashMap<>();
		source.put("key", "old");
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		Map.Entry<String, List<String>> first = adapter.entrySet().iterator().next();
		Map.Entry<String, List<String>> second = adapter.entrySet().iterator().next();
		assertNotSame(first, second);
		assertNotSame(first.getValue(), second.getValue());
		source.put("key", "new");
		assertEquals(List.of("old"), first.getValue());
		assertThrows(UnsupportedOperationException.class, () -> first.setValue(List.of("bad")));
		assertThrows(UnsupportedOperationException.class, () -> adapter.entrySet().iterator().remove());
		LinkedMultiValueMap<String, String> multi = new LinkedMultiValueMap<>();
		multi.add("key", "old");
		Map<String, String> inverse = multi.asSingleValueMap();
		Map.Entry<String, String> projection = inverse.entrySet().iterator().next();
		assertNotSame(projection, inverse.entrySet().iterator().next());
		multi.set("key", "new");
		assertEquals("old", projection.getValue());
		assertThrows(UnsupportedOperationException.class, () -> projection.setValue("bad"));
	}

	@Test
	void reentrantSourceMutationPreservesIteratorFailFast() {
		Map<String, String> source = new LinkedHashMap<>();
		source.put("a", "one"); source.put("b", "two");
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		Object probe = new Object() {
			@Override public boolean equals(Object other) { source.remove("b"); return false; }
			@Override public int hashCode() { return 1; }
		};
		assertThrows(java.util.ConcurrentModificationException.class, () -> adapter.containsValue(probe));
	}

	private static <V> Map.Entry<String, V> entry(String key, V value, List<String> trace) {
		return new Map.Entry<>() {
			@Override public String getKey() { trace.add("key:" + key); return key; }
			@Override public V getValue() { trace.add("value:" + key); return value; }
			@Override public V setValue(V ignored) { throw new UnsupportedOperationException(); }
		};
	}

	private static <V> Map<String, V> tracedMap(List<Map.Entry<String, V>> entries, List<String> trace) {
		return new AbstractMap<>() {
			@Override public int size() { trace.add("size"); return entries.size(); }
			@Override public Set<Map.Entry<String, V>> entrySet() {
				trace.add("entries");
				return new AbstractSet<>() {
					@Override public int size() { trace.add("entrySize"); return entries.size(); }
					@Override public Iterator<Map.Entry<String, V>> iterator() {
						trace.add("iterator");
						Iterator<Map.Entry<String, V>> target = entries.iterator();
						return new Iterator<>() {
							@Override public boolean hasNext() { trace.add("hasNext"); return target.hasNext(); }
							@Override public Map.Entry<String, V> next() { trace.add("next"); return target.next(); }
						};
					}
				};
			}
		};
	}
}
