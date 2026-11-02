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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeIteratorLazyStorageTests {

	@Test
	void emptyCompositesShareTraversalWithoutRegistrationStorage() throws Exception {
		CompositeIterator<String> first = new CompositeIterator<>();
		CompositeIterator<String> second = new CompositeIterator<>();
		assertThat(field(first, "iterators")).isNull();
		assertThat(field(second, "iterators")).isNull();
		assertThat(first.hasNext()).isFalse();
		assertThat(second.hasNext()).isFalse();
		assertThat(field(first, "traversal")).isSameAs(field(second, "traversal"));
		assertThat(field(first, "iterators")).isNull();
		assertThatThrownBy(first::next).isInstanceOf(NoSuchElementException.class)
				.hasMessage("All iterators exhausted");
	}

	@Test
	void registrationRetainsSetHashEqualityAndDuplicateProtocol() {
		List<String> actual = new ArrayList<>();
		List<String> expected = new ArrayList<>();
		CompositeIterator<String> composite = new CompositeIterator<>();
		Set<Iterator<String>> reference = new LinkedHashSet<>();
		for (String name : List.of("first", "second", "first", "third", "second")) {
			try {
				composite.add(new TrackedIterator(name, actual));
			}
			catch (IllegalArgumentException failure) {
				assertThat(failure).hasMessage("You cannot add the same iterator twice");
				actual.add("You cannot add the same iterator twice");
			}
			TrackedIterator iterator = new TrackedIterator(name, expected);
			if (reference.contains(iterator)) {
				expected.add("You cannot add the same iterator twice");
			}
			else {
				reference.add(iterator);
			}
		}
		assertThat(actual).containsExactlyElementsOf(expected);
		assertThat(composite).toIterable().containsExactly("first", "second", "third");
	}

	@Test
	void hashCallbackCanCloseRegistrationDuringFirstAddition() {
		CompositeIterator<String> composite = new CompositeIterator<>();
		TrackedIterator first = new TrackedIterator("first", new ArrayList<>());
		first.onHash = () -> assertThat(composite.hasNext()).isFalse();
		composite.add(first);
		assertThat(composite.next()).isEqualTo("first");
		assertThat(composite.hasNext()).isFalse();
		assertThatIllegalStateException().isThrownBy(() -> composite.add(List.of("late").iterator()));
	}

	@Test
	void failedHashLeavesRegistrationOpenAndRetainsFailureIdentity() {
		CompositeIterator<String> composite = new CompositeIterator<>();
		TrackedIterator first = new TrackedIterator("failed", new ArrayList<>());
		RuntimeException failure = new IllegalStateException("hash failure");
		first.onHash = () -> {
			throw failure;
		};
		assertThatThrownBy(() -> composite.add(first)).isSameAs(failure);
		composite.add(List.of("accepted").iterator());
		assertThat(composite).toIterable().containsExactly("accepted");
	}

	@Test
	void equalityCallbackCanTraverseWhileAdditionIsPending() {
		CompositeIterator<String> composite = new CompositeIterator<>();
		TrackedIterator first = new TrackedIterator("first", new ArrayList<>());
		TrackedIterator second = new TrackedIterator("second", new ArrayList<>());
		composite.add(first);
		second.onEquals = () -> assertThat(composite.hasNext()).isTrue();
		composite.add(second);
		assertThat(composite).toIterable().containsExactly("first", "second");
		assertThatIllegalStateException().isThrownBy(() -> composite.add(List.of("late").iterator()));
	}

	@Test
	void nestedRegistrationPreservesInsertionOrder() {
		CompositeIterator<String> composite = new CompositeIterator<>();
		TrackedIterator first = new TrackedIterator("first", new ArrayList<>());
		TrackedIterator second = new TrackedIterator("second", new ArrayList<>());
		TrackedIterator nested = new TrackedIterator("nested", new ArrayList<>());
		composite.add(first);
		second.onEquals = () -> {
			second.onEquals = null;
			composite.add(nested);
		};
		composite.add(second);
		assertThat(composite).toIterable().containsExactly("first", "nested", "second");
	}

	@Test
	void exhaustedChildIsRevisitedIfItBecomesAvailableAgain() {
		List<String> trace = new ArrayList<>();
		CompositeIterator<String> composite = new CompositeIterator<>();
		TrackedIterator first = new TrackedIterator("first", trace);
		TrackedIterator second = new TrackedIterator("second", trace);
		first.remaining = 0;
		composite.add(first);
		composite.add(second);
		trace.clear();
		assertThat(composite.hasNext()).isTrue();
		assertThat(trace).containsExactly("first.hasNext", "second.hasNext");
		first.remaining = 1;
		trace.clear();
		assertThat(composite.next()).isEqualTo("first");
		assertThat(trace).containsExactly("first.hasNext", "first.next");
		assertThat(composite.next()).isEqualTo("second");
	}

	@Test
	void childFailureDoesNotFallThroughToAnotherChild() {
		List<String> trace = new ArrayList<>();
		CompositeIterator<String> composite = new CompositeIterator<>();
		TrackedIterator first = new TrackedIterator("first", trace);
		TrackedIterator second = new TrackedIterator("second", trace);
		RuntimeException failure = new IllegalStateException("child failure");
		first.hasNextFailure = failure;
		composite.add(first);
		composite.add(second);
		trace.clear();
		assertThatThrownBy(composite::next).isSameAs(failure);
		assertThat(trace).containsExactly("first.hasNext");
		assertThatIllegalStateException().isThrownBy(() -> composite.add(List.of("late").iterator()));
	}

	@Test
	void unsupportedRemoveDoesNotCloseRegistration() {
		CompositeIterator<String> composite = new CompositeIterator<>();
		assertThatThrownBy(composite::remove).isInstanceOf(UnsupportedOperationException.class)
				.hasMessage("CompositeIterator does not support remove()");
		composite.add(List.of("accepted").iterator());
		assertThat(composite).toIterable().containsExactly("accepted");
	}

	@Test
	void emptyNextClosesRegistrationBeforeAnyLaterHashCallback() {
		CompositeIterator<String> composite = new CompositeIterator<>();
		List<String> trace = new ArrayList<>();
		assertThatThrownBy(composite::next).isInstanceOf(NoSuchElementException.class);
		assertThatIllegalStateException().isThrownBy(
				() -> composite.add(new TrackedIterator("late", trace)));
		assertThat(trace).isEmpty();
	}

	private static @Nullable Object field(CompositeIterator<?> composite, String name) throws Exception {
		Field field = CompositeIterator.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(composite);
	}


	private static final class TrackedIterator implements Iterator<String> {

		private final String name;

		private final List<String> trace;

		private int remaining = 1;

		private @Nullable Runnable onHash;

		private @Nullable Runnable onEquals;

		private @Nullable RuntimeException hasNextFailure;


		TrackedIterator(String name, List<String> trace) {
			this.name = name;
			this.trace = trace;
		}

		@Override
		public boolean hasNext() {
			this.trace.add(this.name + ".hasNext");
			if (this.hasNextFailure != null) {
				throw this.hasNextFailure;
			}
			return this.remaining > 0;
		}

		@Override
		public String next() {
			this.trace.add(this.name + ".next");
			if (this.remaining == 0) {
				throw new NoSuchElementException();
			}
			this.remaining--;
			return this.name;
		}

		@Override
		public int hashCode() {
			this.trace.add(this.name + ".hashCode");
			if (this.onHash != null) {
				this.onHash.run();
			}
			return 1;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			this.trace.add(this.name + ".equals");
			if (this.onEquals != null) {
				this.onEquals.run();
			}
			return other instanceof TrackedIterator iterator && this.name.equals(iterator.name);
		}
	}

}
