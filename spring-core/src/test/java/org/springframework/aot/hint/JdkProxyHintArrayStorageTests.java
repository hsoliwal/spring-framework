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

package org.springframework.aot.hint;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.util.M3ArrayStorageOracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Compare the private builder storage with its independently compiled preimage.
 *
 * @author Hitesh Soliwal and Contributors to the Synexia Project
 */
class JdkProxyHintArrayStorageTests {

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
	void allSmallValidationShapesMatchOriginalAndPreservePriorState() throws Exception {
		Class<?>[] choices = {Runnable.class, Consumer.class, String.class, int.class, null, Sealed.class};
		for (int shape = 0; shape < 216; shape++) {
			Class<?>[] input = new Class<?>[3];
			int selection = shape;
			for (int index = 0; index < input.length; index++) {
				input[index] = choices[selection % choices.length];
				selection /= choices.length;
			}
			assertThat(validationTrace(false, input)).as("class shape %s", shape)
					.isEqualTo(validationTrace(true, input));
		}
	}

	private static List<String> validationTrace(boolean original, Class<?>[] input) throws Exception {
		Access access = new Access(original);
		List<String> trace = new ArrayList<>();
		access.appendClasses(Runnable.class);
		record(trace, "append", () -> access.appendClasses(input));
		record(trace, "build", access::snapshot);
		return trace;
	}

	@Test
	void nullArrayAndNullElementTimingMatchOriginal() throws Exception {
		assertThat(nullTrace(false)).isEqualTo(nullTrace(true));
	}

	private static List<String> nullTrace(boolean original) throws Exception {
		Access access = new Access(original);
		List<String> trace = new ArrayList<>();
		access.appendClasses(Runnable.class);
		record(trace, "nullClassArray", () -> access.appendClasses((Class<?>[]) null));
		record(trace, "nullTypeArray", () -> access.appendTypes((TypeReference[]) null));
		record(trace, "buildBeforeNullElement", access::snapshot);
		record(trace, "appendNullElement", () -> access.appendTypes(new TypeReference[] {null}));
		record(trace, "buildAfterNullElement", access::snapshot);
		return trace;
	}

	@Test
	void callerArrayChangesAndLaterAppendsDoNotChangeBuiltHints() throws Exception {
		assertThat(snapshotTrace(false)).isEqualTo(snapshotTrace(true));
	}

	private static List<String> snapshotTrace(boolean original) throws Exception {
		Access access = new Access(original);
		List<String> trace = new ArrayList<>();
		TypeReference[] input = {TypeReference.of(Runnable.class), TypeReference.of(Runnable.class)};
		access.appendTypes(input);
		Object first = access.build();
		input[0] = TypeReference.of(Consumer.class);
		access.appendTypes(input);
		access.flags(TypeReference.of(String.class), true);
		trace.add("first:" + access.snapshot(first));
		trace.add("second:" + access.snapshot());
		access.flags(null, false);
		access.appendClasses();
		access.appendTypes();
		trace.add("third:" + access.snapshot());
		return trace;
	}

	@Test
	void arrayStorageRetainsOrderAndOnlyBuiltListsEscape() throws Exception {
		JdkProxyHint.Builder builder = new JdkProxyHint.Builder();
		TypeReference value = TypeReference.of(Runnable.class);
		for (int index = 0; index < 1024; index++) {
			builder.proxiedInterfaces(value);
		}
		Field field = JdkProxyHint.Builder.class.getDeclaredField("proxiedInterfaces");
		field.setAccessible(true);
		assertThat(field.get(builder)).isInstanceOf(ArrayList.class);
		JdkProxyHint hint = builder.build();
		assertThat(hint.getProxiedInterfaces()).hasSize(1024).allMatch(item -> item == value);
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> hint.getProxiedInterfaces().clear());
		builder.proxiedInterfaces(TypeReference.of(Consumer.class));
		assertThat(hint.getProxiedInterfaces()).hasSize(1024);
		assertThat(builder.build().getProxiedInterfaces()).hasSize(1025);
	}

	@Test
	void referencesAreNotInspectedDuringAppendOrImmutableCopy() {
		TypeReference reference = new TypeReference() {
			@Override
			public String getName() {
				throw new AssertionError("getName callback");
			}
			@Override
			public String getCanonicalName() {
				throw new AssertionError("getCanonicalName callback");
			}
			@Override
			public String getPackageName() {
				throw new AssertionError("getPackageName callback");
			}
			@Override
			public String getSimpleName() {
				throw new AssertionError("getSimpleName callback");
			}
			@Override
			public TypeReference getEnclosingType() {
				throw new AssertionError("getEnclosingType callback");
			}
			@Override
			public int compareTo(TypeReference other) {
				throw new AssertionError("compareTo callback");
			}
		};
		JdkProxyHint.Builder builder = JdkProxyHint.of(reference, reference);
		assertThat(builder.build().getProxiedInterfaces().get(0)).isSameAs(reference);
		builder.proxiedInterfaces(new TypeReference[] {null});
		assertThatNullPointerException().isThrownBy(builder::build);
	}

	private static void record(List<String> trace, String label, Supplier<?> action) {
		try {
			trace.add(label + ":" + action.get());
		}
		catch (RuntimeException ex) {
			trace.add(label + ":" + ex.getClass().getName() + ":" + ex.getMessage());
		}
	}


	private static final class Access {

		private final Object builder;

		private final Class<?> builderType;

		Access(boolean original) throws Exception {
			Class<?> owner = (original ? oracle.originalClass(JdkProxyHint.class.getName()) : JdkProxyHint.class);
			this.builder = M3ArrayStorageOracle.invoke(owner.getMethod("of", TypeReference[].class), null,
					(Object) new TypeReference[0]);
			this.builderType = this.builder.getClass();
		}

		boolean appendClasses(Class<?>... values) {
			return invoke("proxiedInterfaces", Class[].class, values) == this.builder;
		}

		boolean appendTypes(TypeReference... values) {
			return invoke("proxiedInterfaces", TypeReference[].class, values) == this.builder;
		}

		void flags(TypeReference reachable, boolean serializable) {
			invoke("onReachableType", TypeReference.class, reachable);
			invoke("withJavaSerialization", boolean.class, serializable);
		}

		Object invoke(String method, Class<?> parameter, Object value) {
			try {
				return M3ArrayStorageOracle.invoke(this.builderType.getMethod(method, parameter), this.builder, value);
			}
			catch (NoSuchMethodException ex) {
				throw new IllegalStateException(ex);
			}
		}

		Object build() {
			try {
				return M3ArrayStorageOracle.invoke(this.builderType.getDeclaredMethod("build"), this.builder);
			}
			catch (NoSuchMethodException ex) {
				throw new IllegalStateException(ex);
			}
		}

		String snapshot() {
			return snapshot(build());
		}

		String snapshot(Object hint) {
			try {
				Class<?> owner = hint.getClass();
				return Arrays.asList(
						M3ArrayStorageOracle.invoke(owner.getMethod("getProxiedInterfaces"), hint),
						M3ArrayStorageOracle.invoke(owner.getMethod("getReachableType"), hint),
						M3ArrayStorageOracle.invoke(owner.getMethod("hasJavaSerialization"), hint)).toString();
			}
			catch (NoSuchMethodException ex) {
				throw new IllegalStateException(ex);
			}
		}
	}


	private sealed interface Sealed permits SealedImplementation {
	}


	private static final class SealedImplementation implements Sealed {
	}

}
