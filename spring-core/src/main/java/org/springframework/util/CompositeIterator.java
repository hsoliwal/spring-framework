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

// Modified 2026 by Hitesh Soliwal and Synexia contributors:
// array-own the iterator registry and its hash index; no per-registration Set nodes.

package org.springframework.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jspecify.annotations.Nullable;

/**
 * Composite iterator that combines multiple other iterators,
 * as registered via {@link #add(Iterator)}.
 *
 * <p>This implementation registers iterators in insertion order and invokes
 * them in sequence until all iterators are exhausted.
 *
 * <p>Registration uses parent-owned arrays for iterator references, hashes and
 * an open-addressed index. No linked hash-set nodes or copied traversal array
 * are retained. Registration and duplicate detection are expected O(1), while
 * traversal follows the original insertion order.
 *
 * @author Erwin Vervaet
 * @author Juergen Hoeller
 * @author Yanming Zhou
 * @author Hitesh Soliwal and Synexia contributors
 * @since 3.0
 * @param <E> the element type
 */
public class CompositeIterator<E extends @Nullable Object> implements Iterator<E> {

	private static final Iterator<?>[] EMPTY_ITERATORS = new Iterator<?>[0];

	private static final int[] EMPTY_INTS = new int[0];

	private Iterator<?>[] iterators = EMPTY_ITERATORS;

	private int[] hashes = EMPTY_INTS;

	/** One-based registration coordinates, zero for an empty bucket. */
	private int[] hashIndex = EMPTY_INTS;

	private int size;

	private boolean inUse;

	/**
	 * Register an iterator. Duplicate checking obeys the incoming iterator's
	 * hashCode/equals methods, as with the historical LinkedHashSet registry.
	 */
	public void add(Iterator<E> iterator) {
		Assert.state(!this.inUse,
				"You can no longer add iterators to a composite iterator that's already in use");
		// The original empty LinkedHashSet has no HashMap table yet;
		// contains() does not call the incoming iterator's hashCode then.
		if (this.size != 0 && containsIterator(iterator, hash(iterator))) {
			throw new IllegalArgumentException("You cannot add the same iterator twice");
		}
		// A subsequent add() always invokes hashCode once, even on a
		// brand-new set. Re-entrant registration may have changed the table.
		int hash = hash(iterator);
		if (!containsIterator(iterator, hash)) {
			append(iterator, hash);
		}
	}

	private static int hash(@Nullable Object iterator) {
		return iterator == null ? 0 : iterator.hashCode();
	}

	/** Locate a stored iterator without allocating a Map.Entry or node. */
	private boolean containsIterator(@Nullable Iterator<E> iterator, int rawHash) {
		int[] table = this.hashIndex;
		if (table.length == 0) {
			return false;
		}
		int hash = rawHash ^ (rawHash >>> 16);
		int position = hash & (table.length - 1);
		for (;;) {
			int stored = table[position];
			if (stored == 0) {
				return false;
			}
			int index = stored - 1;
			if (this.hashes[index] == rawHash) {
				Iterator<?> candidate = this.iterators[index];
				if (candidate == iterator || (iterator != null && iterator.equals(candidate))) {
					return true;
				}
				// equals/hashCode callbacks may reenter registration. Resume from the
				// current index if an intervening append replaced the bucket array.
				if (table != this.hashIndex) {
					table = this.hashIndex;
					position = hash & (table.length - 1);
					continue;
				}
			}
			position = (position + 1) & (table.length - 1);
		}
	}

	private void append(@Nullable Iterator<E> iterator, int rawHash) {
		int current = this.size;
		if (current == this.iterators.length) {
			int capacity = current < 4 ? 4 : current + (current >>> 1) + 1;
			if (capacity <= current) {
				throw new OutOfMemoryError("Iterator registry exhausted");
			}
			Iterator<?>[] grown = Arrays.copyOf(this.iterators, capacity);
			int[] grownHashes = Arrays.copyOf(this.hashes, capacity);
			this.iterators = grown;
			this.hashes = grownHashes;
		}
		if (this.hashIndex.length <= (current + 1L) * 2) {
			int capacity = this.hashIndex.length == 0 ? 16 : this.hashIndex.length << 1;
			if (capacity <= 0) {
				throw new OutOfMemoryError("Iterator hash index exhausted");
			}
			rehash(capacity);
		}
		int hash = rawHash ^ (rawHash >>> 16);
		int position = hash & (this.hashIndex.length - 1);
		while (this.hashIndex[position] != 0) {
			position = (position + 1) & (this.hashIndex.length - 1);
		}
		this.iterators[current] = iterator;
		this.hashes[current] = rawHash;
		this.hashIndex[position] = current + 1;
		this.size = current + 1;
	}

	private void rehash(int capacity) {
		int[] replacement = new int[capacity];
		int mask = capacity - 1;
		for (int i = 0; i < this.size; i++) {
			int hash = this.hashes[i] ^ (this.hashes[i] >>> 16);
			int position = hash & mask;
			while (replacement[position] != 0) {
				position = (position + 1) & mask;
			}
			replacement[position] = i + 1;
		}
		this.hashIndex = replacement;
	}

	@Override
	public boolean hasNext() {
		this.inUse = true;
		for (int i = 0; i < this.size; i++) {
			if (iteratorAt(i).hasNext()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public E next() {
		this.inUse = true;
		for (int i = 0; i < this.size; i++) {
			Iterator<E> iterator = iteratorAt(i);
			if (iterator.hasNext()) {
				return iterator.next();
			}
		}
		throw new NoSuchElementException("All iterators exhausted");
	}

	@SuppressWarnings("unchecked")
	private Iterator<E> iteratorAt(int index) {
		return (Iterator<E>) this.iterators[index];
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("CompositeIterator does not support remove()");
	}
}
