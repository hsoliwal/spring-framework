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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Equality regression tests for the single-value adapter's private projections.
 */
class SingleToMultiValueMapEqualityTests {

	@Test
	void matchesSingletonProjectionReferenceAcrossSeededSnapshots() {
		Random random = new Random(625133L);
		for (int sample = 0; sample < 256; sample++) {
			Map<Integer, Object> source = new LinkedHashMap<>();
			Map<Integer, Object> other = new LinkedHashMap<>();
			int size = random.nextInt(64);
			for (int index = 0; index < size; index++) {
				Integer key = (index == 0 ? null : index);
				Object value = (random.nextBoolean() ? null : "value" + random.nextInt(16));
				source.put(key, value);
				other.put(key, Collections.singletonList(value));
			}
			MultiValueMap<Integer, Object> adapter = MultiValueMap.fromSingleValue(source);
			assertThat(adapter.equals(other)).isEqualTo(referenceEquals(source, other));
			for (Integer key : source.keySet()) {
				Object value = source.get(key);
				for (Object replacement : Arrays.asList(null, "not a list", List.of(),
						Collections.singletonList(value), Arrays.asList(value, value))) {
					Object previous = other.put(key, replacement);
					assertThat(adapter.equals(other)).isEqualTo(referenceEquals(source, other));
					other.put(key, previous);
				}
			}
			assertThat(adapter.equals(adapter)).isTrue();
			assertThat(adapter.equals(null)).isFalse();
			assertThat(adapter.equals("not a map")).isFalse();
		}
	}

	@Test
	void preservesListIteratorCallbacksAndFailureIdentity() {
		for (int exceptionKind = 0; exceptionKind < 3; exceptionKind++) {
			for (int failAt = 0; failAt <= 12; failAt++) {
				for (int length = 0; length < 4; length++) {
					for (boolean nullValue : new boolean[] {false, true}) {
						for (boolean matches : new boolean[] {false, true}) {
							assertThat(scenario(false, exceptionKind, failAt, length, nullValue, matches))
									.isEqualTo(scenario(true, exceptionKind, failAt, length, nullValue, matches));
						}
					}
				}
			}
		}
	}

	@Test
	void invokesValueEqualsEvenForTheSameReference() {
		List<String> calls = new ArrayList<>();
		Object value = new Object() {
			@Override
			public boolean equals(@Nullable Object other) {
				calls.add("equals");
				return false;
			}

			@Override
			public int hashCode() {
				return 0;
			}
		};
		MultiValueMap<String, Object> adapter = MultiValueMap.fromSingleValue(Map.of("key", value));
		assertThat(adapter.equals(Map.of("key", Collections.singletonList(value)))).isFalse();
		assertThat(calls).containsExactly("equals");
	}

	@Test
	void capturesValueBeforeOtherMapCallback() {
		Map<String, String> source = new LinkedHashMap<>();
		source.put("key", "original");
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		Map<String, List<String>> other = new AbstractMap<>() {
			@Override
			public int size() {
				return 1;
			}

			@Override
			public @Nullable List<String> get(Object key) {
				source.put("key", "changed");
				return List.of("original");
			}

			@Override
			public Set<Entry<String, List<String>>> entrySet() {
				throw new AssertionError("The other map's entry set must not be traversed");
			}
		};
		assertThat(adapter.equals(other)).isTrue();
		assertThat(adapter.getFirst("key")).isEqualTo("changed");
	}

	@Test
	void retainsEscapingEntryAndValueProjections() {
		Map<String, String> source = new LinkedHashMap<>();
		source.put("key", "original");
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		Map.Entry<String, List<String>> entry = adapter.entrySet().iterator().next();
		List<String> values = adapter.values().iterator().next();
		assertThat(adapter.equals(Map.of("key", List.of("original")))).isTrue();
		assertThat(adapter.entrySet()).isSameAs(adapter.entrySet());
		assertThat(adapter.values()).isSameAs(adapter.values());
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> values.add("extra"));
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> entry.setValue(List.of("extra")));
		source.put("key", "changed");
		assertThat(values).containsExactly("original");
		assertThat(entry.getValue()).containsExactly("original");
		assertThat(adapter.values().iterator().next()).containsExactly("changed");
	}

	@Test
	void preservesExceptionBoundaryBeforeTraversal() {
		Map<String, String> source = new LinkedHashMap<>();
		source.put("key", "value");
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		NullPointerException failure = new NullPointerException("size failure");
		Map<String, List<String>> other = new AbstractMap<>() {
			@Override
			public int size() {
				throw failure;
			}

			@Override
			public Set<Entry<String, List<String>>> entrySet() {
				throw new AssertionError("Unexpected entry traversal");
			}
		};
		assertThatNullPointerException().isThrownBy(() -> adapter.equals(other)).isSameAs(failure);
	}

	@Test
	void leavesSerializedStateAndRoundTripUnchanged() throws Exception {
		Map<String, String> source = new LinkedHashMap<>();
		source.put("first", "value");
		source.put("second", null);
		MultiValueMap<String, String> adapter = MultiValueMap.fromSingleValue(source);
		byte[] before = serialize(adapter);
		adapter.entrySet();
		adapter.values();
		assertThat(adapter.equals(Map.of("first", List.of("value"),
				"second", Collections.singletonList(null)))).isTrue();
		assertThat(serialize(adapter)).isEqualTo(before);
		try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(before))) {
			Object restored = input.readObject();
			// A null stored value is exposed as null by get(), not as singleton(null).
			assertThat(restored.equals(adapter)).isFalse();
			assertThat(serialize(restored)).isEqualTo(before);
		}
		assertThat(ObjectStreamClass.lookup(adapter.getClass()).getFields())
				.extracting(field -> field.getName()).containsExactly("targetMap");
	}

	private static byte[] serialize(Object value) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			output.writeObject(value);
		}
		return bytes.toByteArray();
	}

	private static boolean referenceEquals(Map<?, ?> source, @Nullable Object other) {
		if (other instanceof Map<?, ?> otherMap && source.size() == otherMap.size()) {
			try {
				for (Map.Entry<?, ?> entry : source.entrySet()) {
					Object key = entry.getKey();
					List<?> value = Collections.singletonList(entry.getValue());
					if (!value.equals(otherMap.get(key))) {
						return false;
					}
				}
				return true;
			}
			catch (ClassCastException | NullPointerException ignored) {
				// Match the adapter's established exception boundary.
			}
		}
		return false;
	}

	private static List<String> scenario(boolean reference, int exceptionKind, int failAt,
			int length, boolean nullValue, boolean matches) {

		Trace trace = new Trace(exceptionKind, failAt);
		Object value = new Object() {
			@Override
			public boolean equals(@Nullable Object other) {
				trace.event("value.equals");
				return other == this;
			}

			@Override
			public int hashCode() {
				return 0;
			}
		};
		Object storedValue = (nullValue ? null : value);
		List<Object> candidates = new ArrayList<>();
		for (int index = 0; index < length; index++) {
			candidates.add(matches ? storedValue : new Object());
		}
		Map<String, Object> source = new AbstractMap<>() {
			@Override
			public int size() {
				trace.event("source.size");
				return 1;
			}

			@Override
			public Set<Entry<String, Object>> entrySet() {
				trace.event("source.entries");
				return Collections.singleton(new SimpleImmutableEntry<>("key", storedValue) {
					@Override
					public String getKey() {
						trace.event("entry.key");
						return super.getKey();
					}

					@Override
					public Object getValue() {
						trace.event("entry.value");
						return super.getValue();
					}
				});
			}
		};
		Map<String, Object> other = new AbstractMap<>() {
			@Override
			public int size() {
				trace.event("other.size");
				return 1;
			}

			@Override
			public @Nullable Object get(Object key) {
				trace.event("other.get");
				return new TracedList(candidates, trace);
			}

			@Override
			public Set<Entry<String, Object>> entrySet() {
				throw new AssertionError("The other map's entry set must not be traversed");
			}
		};
		try {
			boolean result = (reference ? referenceEquals(source, other) :
					MultiValueMap.fromSingleValue(source).equals(other));
			trace.events.add("result:" + result);
		}
		catch (RuntimeException failure) {
			assertThat(failure).isSameAs(trace.failure);
			trace.events.add("failure");
		}
		return trace.events;
	}


	private static final class Trace {

		private final List<String> events = new ArrayList<>();

		private final RuntimeException failure;

		private final int failAt;


		Trace(int exceptionKind, int failAt) {
			this.failure = switch (exceptionKind) {
				case 0 -> new IllegalStateException("callback failure");
				case 1 -> new NullPointerException("callback failure");
				default -> new ClassCastException("callback failure");
			};
			this.failAt = failAt;
		}


		void event(String event) {
			this.events.add(event);
			if (this.events.size() == this.failAt) {
				throw this.failure;
			}
		}
	}


	private static final class TracedList extends AbstractList<Object> {

		private final List<Object> values;

		private final Trace trace;


		TracedList(List<Object> values, Trace trace) {
			this.values = values;
			this.trace = trace;
		}


		@Override
		public ListIterator<Object> listIterator() {
			this.trace.event("list.listIterator");
			ListIterator<Object> delegate = this.values.listIterator();
			return new ListIterator<>() {
				@Override
				public boolean hasNext() {
					TracedList.this.trace.event("list.hasNext");
					return delegate.hasNext();
				}

				@Override
				public Object next() {
					TracedList.this.trace.event("list.next");
					return delegate.next();
				}

				@Override
				public boolean hasPrevious() {
					throw new AssertionError("Unexpected reverse traversal");
				}

				@Override
				public Object previous() {
					throw new AssertionError("Unexpected reverse traversal");
				}

				@Override
				public int nextIndex() {
					throw new AssertionError("Unexpected indexed traversal");
				}

				@Override
				public int previousIndex() {
					throw new AssertionError("Unexpected indexed traversal");
				}

				@Override
				public void remove() {
					throw new AssertionError("Unexpected mutation");
				}

				@Override
				public void set(Object value) {
					throw new AssertionError("Unexpected mutation");
				}

				@Override
				public void add(Object value) {
					throw new AssertionError("Unexpected mutation");
				}
			};
		}

		@Override
		public Object get(int index) {
			throw new AssertionError("Compare through the list iterator");
		}

		@Override
		public int size() {
			throw new AssertionError("Do not preflight the list size");
		}

		@Override
		public boolean equals(@Nullable Object other) {
			throw new AssertionError("Preserve singleton-to-other comparison direction");
		}

		@Override
		public int hashCode() {
			throw new AssertionError("Equality must not hash the other list");
		}
	}

}
