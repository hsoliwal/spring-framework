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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MultiValueMapEntryTraversalTests {

	@Test
	void singleToMultiContainsValuePreservesEntryGetterOrderAndShortCircuit() {
		List<String> trace = new ArrayList<>();
		Map<String, String> source = tracedMap(List.of(entry("a", "one", trace), entry("b", "two", trace)), trace);
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		assertThat(adapter.containsValue(List.of("one"))).isTrue();
		assertThat(trace).isEqualTo(List.of("entries", "iterator", "hasNext", "next", "key:a", "value:a"));
		trace.clear();
		assertThat(adapter.containsValue(List.of("two"))).isTrue();
		assertThat(trace).isEqualTo(List.of("iterator", "hasNext", "next", "key:a", "value:a", "hasNext", "next", "key:b", "value:b"));
	}

	@Test
	void multiToSingleContainsValuePreservesEntryGetterOrderAndShortCircuit() {
		List<String> trace = new ArrayList<>();
		Map<String, List<String>> source = tracedMap(List.of(entry("a", List.of("one"), trace), entry("b", List.of("two"), trace)), trace);
		Map<String, String> adapter = new MultiValueMapAdapter<>(source).asSingleValueMap();
		assertThat(adapter.containsValue("one")).isTrue();
		assertThat(trace).isEqualTo(List.of("entries", "iterator", "hasNext", "next", "key:a", "value:a"));
		trace.clear();
		assertThat(adapter.containsValue("two")).isTrue();
		assertThat(trace).isEqualTo(List.of("iterator", "hasNext", "next", "key:a", "value:a", "hasNext", "next", "key:b", "value:b"));
	}

	@Test
	void equalityKeepsCachedEntrySetAndStopsAtFirstMismatch() {
		List<String> trace = new ArrayList<>();
		Map<String, String> source = tracedMap(List.of(entry("a", "one", trace), entry("b", "two", trace)), trace);
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		adapter.entrySet();
		trace.clear();
		assertThat(adapter.equals(Map.of("a", List.of("different"), "b", List.of("two")))).isFalse();
		assertThat(trace).isEqualTo(List.of("size", "iterator", "hasNext", "next", "key:a", "value:a"));
		trace.clear();
		assertThat(adapter.equals(Map.of("a", List.of("one"), "b", List.of("two")))).isTrue();
		assertThat(trace).isEqualTo(List.of("size", "iterator", "hasNext", "next", "key:a", "value:a", "hasNext", "next", "key:b", "value:b", "hasNext"));
	}

	@Test
	void inverseEqualityKeepsCachedEntrySetAndStopsAtFirstMismatch() {
		List<String> trace = new ArrayList<>();
		Map<String, List<String>> source = tracedMap(List.of(entry("a", List.of("one"), trace), entry("b", List.of("two"), trace)), trace);
		Map<String, String> adapter = new MultiValueMapAdapter<>(source).asSingleValueMap();
		adapter.entrySet();
		trace.clear();
		assertThat(adapter.equals(Map.of("a", "different", "b", "two"))).isFalse();
		assertThat(trace).isEqualTo(List.of("size", "iterator", "hasNext", "next", "key:a", "value:a"));
		trace.clear();
		assertThat(adapter.equals(Map.of("a", "one", "b", "two"))).isTrue();
		assertThat(trace).isEqualTo(List.of("size", "iterator", "hasNext", "next", "key:a", "value:a", "hasNext", "next", "key:b", "value:b", "hasNext"));
	}

	@Test
	void nullAndEmptyListObservationsKeepTheirOriginalBehavior() {
		Map<String, String> single = new LinkedHashMap<>();
		single.put("key", null);
		MultiValueMap<String, String> multi = MultiValueMap.fromSingleValue(single);
		assertThat(multi.containsValue(null)).isFalse();
		assertThat(multi.containsValue(Collections.singletonList(null))).isTrue();
		assertThat(multi.equals(Collections.singletonMap("key", Collections.singletonList(null)))).isTrue();
		LinkedMultiValueMap<String, String> underlying = new LinkedMultiValueMap<>();
		underlying.put("key", Collections.singletonList(null));
		Map<String, String> adapted = underlying.asSingleValueMap();
		assertThat(adapted.containsValue(null)).isTrue();
		assertThat(adapted.equals(Collections.singletonMap("key", null))).isTrue();
		underlying.put("key", Collections.emptyList());
		assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> adapted.containsValue(null));
		assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> adapted.equals(Collections.singletonMap("key", null)));
		underlying.put("key", null);
		assertThatNullPointerException().isThrownBy(() -> adapted.containsValue(null));
		assertThat(adapted.equals(Collections.singletonMap("key", null))).isFalse();
	}

	@Test
	void userEqualityReceivesFreshSingletonListsAndExceptionsStillPropagate() {
		Map<String, Object> backing = new LinkedHashMap<>();
		backing.put("a", new Object());
		backing.put("b", new Object());
		MultiValueMap<String, Object> adapter = MultiValueMap.fromSingleValue(backing);
		List<Object> observed = new ArrayList<>();
		Object probe = new Object() {
			@Override
			public boolean equals(Object other) {
				observed.add(other);
				return false;
			}
			@Override
			public int hashCode() {
				return 1;
			}
		};
		assertThat(adapter.containsValue(probe)).isFalse();
		assertThat(observed.size()).isEqualTo(2);
		assertThat(observed.get(1)).isNotSameAs(observed.get(0));
		assertThat(((List<?>) observed.get(0)).get(0)).isSameAs(backing.get("a"));
		Object failing = new Object() {
			@Override
			public boolean equals(Object other) {
				throw new IllegalStateException("callback");
			}
			@Override
			public int hashCode() {
				return 1;
			}
		};
		assertThatIllegalStateException().isThrownBy(() -> adapter.containsValue(failing));
	}

	@Test
	void publicEntriesRemainFreshDetachedAndImmutable() {
		LinkedHashMap<String, String> source = new LinkedHashMap<>();
		source.put("key", "old");
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		Map.Entry<String, List<String>> first = adapter.entrySet().iterator().next();
		Map.Entry<String, List<String>> second = adapter.entrySet().iterator().next();
		assertThat(second).isNotSameAs(first);
		assertThat(second.getValue()).isNotSameAs(first.getValue());
		source.put("key", "new");
		assertThat(first.getValue()).isEqualTo(List.of("old"));
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> first.setValue(List.of("bad")));
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> adapter.entrySet().iterator().remove());
		LinkedMultiValueMap<String, String> multi = new LinkedMultiValueMap<>();
		multi.add("key", "old");
		Map<String, String> inverse = multi.asSingleValueMap();
		Map.Entry<String, String> projection = inverse.entrySet().iterator().next();
		assertThat(inverse.entrySet().iterator().next()).isNotSameAs(projection);
		multi.set("key", "new");
		assertThat(projection.getValue()).isEqualTo("old");
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> projection.setValue("bad"));
	}

	@Test
	void reentrantSourceMutationPreservesIteratorFailFast() {
		Map<String, String> source = new LinkedHashMap<>();
		source.put("a", "one");
		source.put("b", "two");
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		Object probe = new Object() {
			@Override
			public boolean equals(Object other) {
				source.remove("b");
				return false;
			}
			@Override
			public int hashCode() {
				return 1;
			}
		};
		assertThatExceptionOfType(java.util.ConcurrentModificationException.class).isThrownBy(() -> adapter.containsValue(probe));
	}

	private static <V> Map.Entry<String, V> entry(String key, V value, List<String> trace) {
		return new Map.Entry<>() {
			@Override
			public String getKey() {
				trace.add("key:" + key);
				return key;
			}
			@Override
			public V getValue() {
				trace.add("value:" + key);
				return value;
			}
			@Override
			public V setValue(V ignored) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static <V> Map<String, V> tracedMap(List<Map.Entry<String, V>> entries, List<String> trace) {
		return new AbstractMap<>() {
			@Override
			public int size() {
				trace.add("size");
				return entries.size();
			}
			@Override
			public Set<Map.Entry<String, V>> entrySet() {
				trace.add("entries");
				return new AbstractSet<>() {
					@Override
					public int size() {
						trace.add("entrySize");
						return entries.size();
					}
					@Override
					public Iterator<Map.Entry<String, V>> iterator() {
						trace.add("iterator");
						Iterator<Map.Entry<String, V>> target = entries.iterator();
						return new Iterator<>() {
							@Override
							public boolean hasNext() {
								trace.add("hasNext");
								return target.hasNext();
							}
							@Override
							public Map.Entry<String, V> next() {
								trace.add("next");
								return target.next();
							}
						};
					}
				};
			}
		};
	}
}
