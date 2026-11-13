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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests the primitive hash-index and array-owned iterator registry.
 *
 * @author Hitesh Soliwal
 */
class CompositeIteratorArrayRegistryTests {

	@Test
	void registrationsRetainOnlyArrayLanes() throws Exception {
		CompositeIterator<Integer> composite = new CompositeIterator<>();
		List<Integer> values = new ArrayList<>();
		for (int index = 0; index < 4_096; index++) {
			values.add(index);
			composite.add(List.of(index).iterator());
		}

		Field iterators = CompositeIterator.class.getDeclaredField("iterators");
		iterators.setAccessible(true);
		assertThat(iterators.get(composite)).isInstanceOf(Iterator[].class);
		Field hashes = CompositeIterator.class.getDeclaredField("hashes");
		hashes.setAccessible(true);
		assertThat(hashes.get(composite)).isInstanceOf(int[].class);
		Field hashIndex = CompositeIterator.class.getDeclaredField("hashIndex");
		hashIndex.setAccessible(true);
		assertThat(hashIndex.get(composite)).isInstanceOf(int[].class);
		for (Field field : CompositeIterator.class.getDeclaredFields()) {
			assertThat(Set.class.isAssignableFrom(field.getType())).isFalse();
		}
		assertThat(composite).toIterable().containsExactlyElementsOf(values);
		assertThatIllegalStateException().isThrownBy(() ->
				composite.add(List.of(5).iterator()));
	}

	@Test
	void duplicateIteratorsAndCollidingHashesRetainSetContract() {
		CompositeIterator<Integer> composite = new CompositeIterator<>();
		CollidingIterator first = new CollidingIterator(1);
		CollidingIterator second = new CollidingIterator(2);
		composite.add(first);
		composite.add(second);
		assertThatIllegalArgumentException().isThrownBy(() ->
				composite.add(new CollidingIterator(1)))
				.withMessage("You cannot add the same iterator twice");
		assertThat(composite).toIterable().containsExactly(1, 2);
	}

	@Test
	void hashCallbacksMatchHistoricalLinkedHashSetProbes() {
		CompositeIterator<Integer> composite = new CompositeIterator<>();
		AtomicInteger firstHashCalls = new AtomicInteger();
		AtomicInteger secondHashCalls = new AtomicInteger();
		CallbackIterator first = new CallbackIterator(1, firstHashCalls);
		CallbackIterator second = new CallbackIterator(2, secondHashCalls);

		// The first contains() on an uninitialized HashMap has no hash callback;
		// add() hashes once. Subsequent contains()+add() hashes twice.
		composite.add(first);
		assertThat(firstHashCalls).hasValue(1);
		composite.add(second);
		assertThat(secondHashCalls).hasValue(2);
		assertThatIllegalArgumentException().isThrownBy(() -> composite.add(first));
		assertThat(firstHashCalls).hasValue(2);
		assertThat(composite).toIterable().containsExactly(1, 2);
	}

	@Test
	void reentrantHashCallbackRetainsNestedBeforeOuterInsertionOrder() {
		CompositeIterator<Integer> composite = new CompositeIterator<>();
		AtomicInteger hashCallbacks = new AtomicInteger();
		Iterator<Integer> outer = new Iterator<>() {
			@Override
			public int hashCode() {
				if (hashCallbacks.getAndIncrement() == 0) {
					composite.add(List.of(-1).iterator());
				}
				return 123;
			}

			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public Integer next() {
				throw new java.util.NoSuchElementException();
			}
		};
		composite.add(outer);
		assertThat(hashCallbacks).hasValue(1);
		assertThat(composite).toIterable().containsExactly(-1);
	}

	private static final class CollidingIterator implements Iterator<Integer> {
		private final int value;
		private boolean pending = true;

		CollidingIterator(int value) {
			this.value = value;
		}

		@Override
		public int hashCode() {
			return 7;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof CollidingIterator iterator && iterator.value == this.value;
		}

		@Override
		public boolean hasNext() {
			return this.pending;
		}

		@Override
		public Integer next() {
			if (!this.pending) {
				throw new java.util.NoSuchElementException();
			}
			this.pending = false;
			return this.value;
		}
	}

	private static final class CallbackIterator implements Iterator<Integer> {
		private final int value;
		private final AtomicInteger hashCalls;
		private boolean pending = true;

		CallbackIterator(int value, AtomicInteger hashCalls) {
			this.value = value;
			this.hashCalls = hashCalls;
		}

		@Override
		public int hashCode() {
			this.hashCalls.incrementAndGet();
			return this.value;
		}

		@Override
		public boolean hasNext() {
			return this.pending;
		}

		@Override
		public Integer next() {
			this.pending = false;
			return this.value;
		}
	}
}
