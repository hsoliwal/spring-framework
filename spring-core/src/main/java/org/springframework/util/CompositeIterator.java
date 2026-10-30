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

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Composite iterator that combines multiple other iterators,
 * as registered via {@link #add(Iterator)}.
 *
 * <p>This implementation registers iterators in insertion order and invokes
 * them in sequence until all iterators are exhausted.
 *
 * @author Erwin Vervaet
 * @author Juergen Hoeller
 * @author Yanming Zhou
 * @author Hitesh Soliwal and Contributors to the Synexia Project
 * @since 3.0
 * @param <E> the element type
 */
public class CompositeIterator<E extends @Nullable Object> implements Iterator<E> {

	private @Nullable Set<Iterator<E>> iterators = new LinkedHashSet<>();

	private Iterator<E> @Nullable [] traversal;

	private int additions;

	private boolean inUse = false;


	/**
	 * Add given iterator to this composite.
	 */
	public void add(Iterator<E> iterator) {
		Assert.state(!this.inUse, "You can no longer add iterators to a composite iterator that's already in use");
		Set<Iterator<E>> pending = this.iterators;
		Assert.state(pending != null, "Iterator registration is closed");
		this.additions++;
		try {
			if (pending.contains(iterator)) {
				throw new IllegalArgumentException("You cannot add the same iterator twice");
			}
			pending.add(iterator);
		}
		finally {
			this.additions--;
			freezeIterators();
		}
	}

	@Override
	public boolean hasNext() {
		this.inUse = true;
		freezeIterators();
		Iterator<E>[] children = this.traversal;
		if (children != null) {
			for (Iterator<E> iterator : children) {
				if (iterator.hasNext()) {
					return true;
				}
			}
		}
		else {
			Set<Iterator<E>> pending = this.iterators;
			Assert.state(pending != null, "Iterator registration is closed");
			for (Iterator<E> iterator : pending) {
				if (iterator.hasNext()) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public E next() {
		this.inUse = true;
		freezeIterators();
		Iterator<E>[] children = this.traversal;
		if (children != null) {
			for (Iterator<E> iterator : children) {
				if (iterator.hasNext()) {
					return iterator.next();
				}
			}
		}
		else {
			Set<Iterator<E>> pending = this.iterators;
			Assert.state(pending != null, "Iterator registration is closed");
			for (Iterator<E> iterator : pending) {
				if (iterator.hasNext()) {
					return iterator.next();
				}
			}
		}
		throw new NoSuchElementException("All iterators exhausted");
	}

	/**
	 * Retain the original set while a hash or equality callback can reenter add.
	 * Release its nodes once registration is closed and all additions have returned.
	 */
	@SuppressWarnings("unchecked")
	private void freezeIterators() {
		Set<Iterator<E>> pending = this.iterators;
		if (this.inUse && this.additions == 0 && pending != null) {
			this.traversal = pending.toArray(Iterator[]::new);
			this.iterators = null;
		}
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("CompositeIterator does not support remove()");
	}

}
