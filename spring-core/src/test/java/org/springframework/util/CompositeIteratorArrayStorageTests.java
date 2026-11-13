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

// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project

package org.springframework.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Compare retained arrays and callback histories with the frozen native source.
 *
 * @author Hitesh Soliwal and Contributors to the Synexia Project
 */
class CompositeIteratorArrayStorageTests {

	private static M3ArrayStorageOracle oracle;


	@BeforeAll
	static void compileOriginals(@TempDir Path work) throws Exception {
		oracle = new M3ArrayStorageOracle(work);
	}

	@AfterAll
	static void closeOriginals() throws Exception {
		oracle.close();
	}


	@Test
	void allSmallGroupShapesAndNullPayloadsMatchOriginal() throws Exception {
		for (int shape = 0; shape < 625; shape++) {
			assertThat(groupTrace(false, shape)).as("group shape %s", shape)
					.isEqualTo(groupTrace(true, shape));
		}
	}

	private static List<String> groupTrace(boolean original, int shape) throws Exception {
		Access access = new Access(original);
		List<String> events = new ArrayList<>();
		int count = 0;
		for (int group = 0; group < 4; group++) {
			int size = shape % 5;
			shape /= 5;
			List<String> values = new ArrayList<>();
			for (int index = 0; index < size; index++) {
				values.add(index % 2 == 0 ? null : group + ":" + index);
			}
			count += size;
			access.add(values.iterator());
		}
		for (int index = 0; index <= count; index++) {
			record(events, "hasNext", access.iterator::hasNext);
			record(events, "hasNextAgain", access.iterator::hasNext);
			record(events, "next", access.iterator::next);
		}
		record(events, "remove", () -> {
			access.iterator.remove();
			return null;
		});
		record(events, "addAfterUse", () -> {
			access.add(null);
			return null;
		});
		return events;
	}

	@Test
	void collisionTreeAndDuplicateCallbacksMatchOriginal() throws Exception {
		assertThat(collisionTrace(false)).isEqualTo(collisionTrace(true));
	}

	private static List<String> collisionTrace(boolean original) throws Exception {
		Access access = new Access(original);
		List<String> events = new ArrayList<>();
		for (int index = 0; index < 80; index++) {
			access.add(new Probe("item" + index, events, 1));
		}
		for (int index = 0; index < 80; index++) {
			Probe duplicate = new Probe("item" + index, events, 1);
			record(events, "duplicate", () -> {
				access.add(duplicate);
				return null;
			});
		}
		for (int index = 0; index < 81; index++) {
			record(events, "next", access.iterator::next);
		}
		return events;
	}

	@Test
	void hashAndEqualityReentryPreservePendingAddAndFailureHistories() throws Exception {
		for (boolean equality : List.of(false, true)) {
			for (int trigger = 1; trigger <= 3; trigger++) {
				for (int action = 0; action < 5; action++) {
					assertThat(reentryTrace(false, equality, trigger, action))
							.as("equality=%s trigger=%s action=%s", equality, trigger, action)
							.isEqualTo(reentryTrace(true, equality, trigger, action));
				}
			}
		}
	}

	private static List<String> reentryTrace(boolean original, boolean equality, int trigger, int action)
			throws Exception {

		Access access = new Access(original);
		List<String> events = new ArrayList<>();
		access.add(new Probe("seed", events, 1));
		Probe outer = new Probe("outer", events, 1);
		outer.equalityAction = equality;
		outer.trigger = trigger;
		outer.action = () -> {
			events.add("callback-start");
			if (action == 0) {
				record(events, "nestedHasNext", access.iterator::hasNext);
			}
			else if (action == 1) {
				record(events, "nestedNext", access.iterator::next);
			}
			else if (action == 2 || action == 3) {
				Probe inner = new Probe("inner", events, 1);
				inner.action = () -> {
					record(events, "innerHasNext", access.iterator::hasNext);
					if (action == 3) {
						throw new IllegalArgumentException("inner callback failure");
					}
				};
				record(events, "nestedAdd", () -> {
					access.add(inner);
					return null;
				});
			}
			else {
				record(events, "nestedHasNext", access.iterator::hasNext);
				throw new IllegalArgumentException("outer callback failure");
			}
			events.add("callback-end");
		};
		record(events, "outerAdd", () -> {
			access.add(outer);
			return null;
		});
		for (int index = 0; index < 4; index++) {
			record(events, "hasNext", access.iterator::hasNext);
			record(events, "next", access.iterator::next);
		}
		record(events, "lockedAdd", () -> {
			access.add(new Probe("late", events, 1));
			return null;
		});
		return events;
	}

	@Test
	void duplicateInsertedBetweenContainsAndAddKeepsOriginalSilentSetResult() throws Exception {
		assertThat(interveningDuplicateTrace(false)).isEqualTo(interveningDuplicateTrace(true));
	}

	private static List<String> interveningDuplicateTrace(boolean original) throws Exception {
		Access access = new Access(original);
		List<String> events = new ArrayList<>();
		access.add(new Probe("seed", events, 0));
		Probe outer = new Probe("outer", events, 1);
		outer.trigger = 2;
		outer.action = () -> access.add(new Probe("outer", events, 2));
		record(events, "outerAdd", () -> {
			access.add(outer);
			return null;
		});
		for (int index = 0; index < 4; index++) {
			record(events, "next", access.iterator::next);
		}
		return events;
	}

	@Test
	void nullChildrenLockOrderingAndAbruptChildFailuresMatchOriginal() throws Exception {
		assertThat(nullAndFailureTrace(false)).isEqualTo(nullAndFailureTrace(true));
	}

	private static List<String> nullAndFailureTrace(boolean original) throws Exception {
		List<String> events = new ArrayList<>();
		Access nullChild = new Access(original);
		nullChild.add(null);
		record(events, "duplicateNull", () -> {
			nullChild.add(null);
			return null;
		});
		record(events, "hasNull", nullChild.iterator::hasNext);
		record(events, "nextNull", nullChild.iterator::next);
		record(events, "lockedNull", () -> {
			nullChild.add(null);
			return null;
		});
		Access abrupt = new Access(original);
		abrupt.add(new Iterator<>() {
			@Override
			public boolean hasNext() {
				events.add("childHasNext");
				throw new IllegalArgumentException("child failure");
			}
			@Override
			public String next() {
				throw new AssertionError("must not reach child next");
			}
		});
		record(events, "abruptHasNext", abrupt.iterator::hasNext);
		record(events, "abruptNext", abrupt.iterator::next);
		record(events, "abruptLocked", () -> {
			abrupt.add(null);
			return null;
		});
		return events;
	}

	@Test
	void firstTraversalReleasesEveryRegistrationNode() throws Exception {
		CompositeIterator<String> composite = new CompositeIterator<>();
		List<Iterator<String>> children = new ArrayList<>();
		for (int index = 0; index < 256; index++) {
			Iterator<String> child = Arrays.asList("value", null).iterator();
			children.add(child);
			composite.add(child);
		}
		Field registry = CompositeIterator.class.getDeclaredField("iterators");
		Field traversal = CompositeIterator.class.getDeclaredField("traversal");
		registry.setAccessible(true);
		traversal.setAccessible(true);
		assertThat(registry.get(composite)).isInstanceOf(LinkedHashSet.class);
		assertThat(traversal.get(composite)).isNull();
		assertThat(composite.hasNext()).isTrue();
		assertThat(registry.get(composite)).isNull();
		assertThat((Object[]) traversal.get(composite)).containsExactlyElementsOf(children);
		assertThatIllegalStateException().isThrownBy(() -> composite.add(children.get(0)));
		assertThat(registry.get(composite)).isNull();
	}

	private static void record(List<String> events, String label, Supplier<?> action) {
		try {
			events.add(label + ":" + action.get());
		}
		catch (RuntimeException ex) {
			events.add(label + ":" + ex.getClass().getName() + ":" + ex.getMessage());
		}
	}


	private static final class Access {

		private final Iterator<String> iterator;

		private final Method add;

		@SuppressWarnings("unchecked")
		Access(boolean original) throws Exception {
			Class<?> type = (original ? oracle.originalClass(CompositeIterator.class.getName()) : CompositeIterator.class);
			this.iterator = (Iterator<String>) type.getConstructor().newInstance();
			this.add = type.getMethod("add", Iterator.class);
		}

		void add(Iterator<String> child) {
			M3ArrayStorageOracle.invoke(this.add, this.iterator, child);
		}
	}


	private static final class Probe implements Iterator<String>, Comparable<Probe> {

		private final String label;

		private final List<String> events;

		private int remaining;

		private int calls;

		private int trigger = 1;

		private boolean equalityAction;

		private Runnable action = () -> {};

		Probe(String label, List<String> events, int remaining) {
			this.label = label;
			this.events = events;
			this.remaining = remaining;
		}

		@Override
		public int hashCode() {
			this.events.add("hash:" + this.label);
			if (!this.equalityAction && ++this.calls == this.trigger) {
				this.action.run();
			}
			return 7;
		}

		@Override
		public boolean equals(Object other) {
			this.events.add("equals:" + this.label + ":" + ((Probe) other).label);
			if (this.equalityAction && ++this.calls == this.trigger) {
				this.action.run();
			}
			return other instanceof Probe probe && this.label.equals(probe.label);
		}

		@Override
		public int compareTo(Probe other) {
			this.events.add("compare:" + this.label + ":" + other.label);
			return this.label.compareTo(other.label);
		}

		@Override
		public boolean hasNext() {
			this.events.add("childHasNext:" + this.label + ":" + this.remaining);
			return this.remaining > 0;
		}

		@Override
		public String next() {
			this.events.add("childNext:" + this.label + ":" + this.remaining);
			if (this.remaining == 0) {
				throw new NoSuchElementException();
			}
			return this.label + ":" + this.remaining--;
		}
	}

}
