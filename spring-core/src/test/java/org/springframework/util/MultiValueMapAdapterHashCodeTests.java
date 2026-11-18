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

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MultiValueMapAdapterHashCodeTests {


	@Test
	void singleToMultiHashMatchesEqualProjectionAndEntrySet() {
		Map<String, String> target = new LinkedHashMap<>();
		target.put("first", "alpha");
		target.put("second", "beta");
		MultiValueMap<String, String> adapter = new SingleToMultiValueMapAdapter<>(target);
		Map<String, List<String>> expected = new LinkedHashMap<>();
		expected.put("first", List.of("alpha"));
		expected.put("second", List.of("beta"));

		assertThat(adapter).isEqualTo(expected);
		assertThat(expected).isEqualTo(adapter);
		assertThat(adapter.hashCode()).isEqualTo(expected.hashCode());
		assertThat(adapter.entrySet().hashCode()).isEqualTo(expected.hashCode());
		Map<Map<String, List<String>>, String> keyed = new HashMap<>();
		keyed.put(adapter, "found");
		assertThat(keyed.get(expected)).isEqualTo("found");
	}

	@Test
	void nullableSingleProjectionHashMatchesItsVisibleEntries() {
		Map<String, String> target = new LinkedHashMap<>();
		target.put(null, null);
		MultiValueMap<String, String> adapter = new SingleToMultiValueMapAdapter<>(target);
		Map<String, List<String>> expected = new LinkedHashMap<>();
		expected.put(null, Collections.singletonList(null));

		assertThat(adapter.hashCode()).isEqualTo(expected.hashCode());
		assertThat(adapter.entrySet().hashCode()).isEqualTo(expected.hashCode());
	}

	@Test
	void multiToSingleHashUsesOnlyTheProjectedFirstValue() {
		LinkedMultiValueMap<String, String> target = new LinkedMultiValueMap<>();
		target.add("first", "alpha");
		target.add("first", "ignored");
		target.add("second", null);
		Map<String, String> adapter = new MultiToSingleValueMapAdapter<>(target);
		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("first", "alpha");
		expected.put("second", null);

		assertThat(adapter).isEqualTo(expected);
		assertThat(expected).isEqualTo(adapter);
		assertThat(adapter.hashCode()).isEqualTo(expected.hashCode());
		assertThat(adapter.entrySet().hashCode()).isEqualTo(expected.hashCode());
		Map<Map<String, String>, String> keyed = new HashMap<>();
		keyed.put(adapter, "found");
		assertThat(keyed.get(expected)).isEqualTo("found");
	}

	@Test
	void liveHashChangesWhilePreviouslyExposedEntriesRemainSnapshots() {
		Map<String, String> target = new LinkedHashMap<>();
		target.put("key", "before");
		MultiValueMap<String, String> adapter = new SingleToMultiValueMapAdapter<>(target);
		Set<Map.Entry<String, List<String>>> entries = adapter.entrySet();
		Map.Entry<String, List<String>> snapshot = entries.iterator().next();
		int previousHash = snapshot.hashCode();
		target.put("key", "after");

		assertThat(snapshot.getValue()).containsExactly("before");
		assertThat(snapshot.hashCode()).isEqualTo(previousHash);
		assertThat(adapter.entrySet()).isSameAs(entries);
		assertThat(adapter.hashCode()).isEqualTo(Map.of("key", List.of("after")).hashCode());
		assertThat(entries.hashCode()).isEqualTo(adapter.hashCode());
	}

	@Test
	void singleProjectionPreservesGetterBeforeHashCallbackOrder() {
		List<String> calls = new ArrayList<>();
		HashedValue key = new HashedValue("key", Integer.MAX_VALUE, calls);
		HashedValue value = new HashedValue("value", Integer.MAX_VALUE, calls);
		Map<HashedValue, HashedValue> target = observedMap(key, value, calls);
		MultiValueMap<HashedValue, HashedValue> adapter = new SingleToMultiValueMapAdapter<>(target);

		int expected = Integer.MAX_VALUE ^ (31 + Integer.MAX_VALUE);
		assertThat(adapter.hashCode()).isEqualTo(expected);
		assertThat(calls).containsExactly("key", "value", "hash:key", "hash:value");
		calls.clear();
		assertThat(adapter.entrySet().hashCode()).isEqualTo(expected);
		assertThat(calls).containsExactly("key", "value", "hash:key", "hash:value");
	}

	@Test
	void firstValueProjectionPreservesGetterBeforeHashCallbackOrder() {
		List<String> calls = new ArrayList<>();
		HashedValue key = new HashedValue("key", -31, calls);
		HashedValue value = new HashedValue("value", Integer.MIN_VALUE, calls);
		List<HashedValue> values = new AbstractList<>() {
			@Override
			public HashedValue get(int index) {
				assertThat(index).isZero();
				calls.add("first");
				return value;
			}

			@Override
			public int size() {
				return 1;
			}
		};
		MultiValueMap<HashedValue, HashedValue> target =
				new MultiValueMapAdapter<>(observedMap(key, values, calls));
		Map<HashedValue, HashedValue> adapter = new MultiToSingleValueMapAdapter<>(target);

		int expected = -31 ^ Integer.MIN_VALUE;
		assertThat(adapter.hashCode()).isEqualTo(expected);
		assertThat(calls).containsExactly("key", "value", "first", "hash:key", "hash:value");
		calls.clear();
		assertThat(adapter.entrySet().hashCode()).isEqualTo(expected);
		assertThat(calls).containsExactly("key", "value", "first", "hash:key", "hash:value");
	}

	@Test
	void absentFirstValuesKeepMapHashNonThrowingAndEntrySetFailures() {
		LinkedMultiValueMap<String, String> target = new LinkedMultiValueMap<>();
		target.put("empty", Collections.emptyList());
		target.put("null", null);
		Map<String, String> adapter = new MultiToSingleValueMapAdapter<>(target);
		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("empty", null);
		expected.put("null", null);

		assertThat(adapter.hashCode()).isEqualTo(expected.hashCode());
		assertThatExceptionOfType(IndexOutOfBoundsException.class)
				.isThrownBy(() -> adapter.entrySet().hashCode());
		target.remove("empty");
		expected.remove("empty");
		assertThat(adapter.hashCode()).isEqualTo(expected.hashCode());
		assertThatExceptionOfType(NullPointerException.class)
				.isThrownBy(() -> adapter.entrySet().hashCode());
	}

	@Test
	void emptyProjectionsHashToZero() {
		assertThat(new SingleToMultiValueMapAdapter<>(Map.of()).hashCode()).isZero();
		assertThat(new MultiToSingleValueMapAdapter<>(new LinkedMultiValueMap<>()).hashCode()).isZero();
	}

	private static <K, V> Map<K, V> observedMap(K key, V value, List<String> calls) {
		Map.Entry<K, V> entry = new AbstractMap.SimpleImmutableEntry<>(key, value) {
			@Override
			public K getKey() {
				calls.add("key");
				return super.getKey();
			}

			@Override
			public V getValue() {
				calls.add("value");
				return super.getValue();
			}
		};
		return new AbstractMap<>() {
			@Override
			public Set<Entry<K, V>> entrySet() {
				return Collections.singleton(entry);
			}

			@Override
			public int hashCode() {
				throw new AssertionError("The backing map has a different value projection");
			}
		};
	}

	private static final class HashedValue {

		private final String name;

		private final int hash;

		private final List<String> calls;


		HashedValue(String name, int hash, List<String> calls) {
			this.name = name;
			this.hash = hash;
			this.calls = calls;
		}


		@Override
		public int hashCode() {
			this.calls.add("hash:" + this.name);
			return this.hash;
		}
	}

}
