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

package org.springframework.web.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contracts of the parser's private array-backed token sequence and its consumers.
 *
 * @author Hitesh Soliwal and Contributors to the Synexia Project
 */
class WhatWgUrlParserArrayStorageTests {

	@Test
	void strictSplitPreservesEmptyAndUnicodeTokens() throws Exception {
		assertThat(strictSplit("", '.')).containsExactly("");
		assertThat(strictSplit(".", '.')).containsExactly("", "");
		assertThat(strictSplit("..", '.')).containsExactly("", "", "");
		assertThat(strictSplit(".a..b.", '.')).containsExactly("", "a", "", "b", "");
		assertThat(strictSplit("a,b,", ',')).containsExactly("a", "b", "");
		assertThat(strictSplit("\u03bb.\ud83d\ude00.\ud800", '.'))
				.containsExactly("\u03bb", "\ud83d\ude00", "\ud800");
	}

	@Test
	void strictSplitOwnsArrayStorageAcrossGrowthAndTailRemoval() throws Exception {
		List<String> parts = strictSplit("x.".repeat(4096) + "last", '.');
		assertThat(parts instanceof ArrayList<?>).as("array-backed private token storage").isTrue();
		assertThat(parts instanceof RandomAccess).as("indexed token access").isTrue();
		assertThat(parts).hasSize(4097);
		assertThat(parts.subList(0, 4096)).allMatch("x"::equals);
		assertThat(parts.remove(parts.size() - 1)).isEqualTo("last");
		assertThat(parts.get(parts.size() - 1)).isEqualTo("x");
		parts.add("replacement");
		assertThat(parts.get(4096)).isEqualTo("replacement");
	}

	@Test
	void strictSplitResultsAreIndependentAndNullInputStillFails() throws Exception {
		List<String> first = strictSplit("a.b.", '.');
		List<String> second = strictSplit("a.b.", '.');
		first.set(0, "changed");
		first.remove(first.size() - 1);
		assertThat(second).containsExactly("a", "b", "");
		assertThatNullPointerException().isThrownBy(() -> strictSplit(null, '.'));
	}

	@Test
	void numericHostClassificationKeepsTheSingleTrailingEmptyRule() throws Exception {
		for (String numeric : List.of("1", "1.", "127.1", "0xff", "a.0", "a.09", "a.\u0661")) {
			assertThat(endsInNumber(numeric)).as(numeric).isTrue();
		}
		for (String domain : List.of("", ".", "..", "a", "a.", "1..", "a.0xg")) {
			assertThat(endsInNumber(domain)).as(domain).isFalse();
		}
	}

	@Test
	void ipv4ParsingKeepsOrdinalTokensAndRadixRules() {
		assertHost("http://127.1/", "127.0.0.1");
		assertHost("http://0x7f.1/", "127.0.0.1");
		assertHost("http://0177.1/", "127.0.0.1");
		assertHost("http://1.2.3.4/", "1.2.3.4");
		assertHost("http://2147483647/", "127.255.255.255");
		assertHost("http://1.2.3../", "1.2.3..");
		// Preserve the current signed integer parser boundary; widening it is a separate change.
		assertThatIllegalArgumentException().isThrownBy(() -> parse("http://4294967295/", null));
		assertThatIllegalArgumentException().isThrownBy(() -> parse("http://1.2.3.4.5/", null));
		assertThatIllegalArgumentException().isThrownBy(() -> parse("http://1.2.3.999/", null));
		assertThatIllegalArgumentException().isThrownBy(() -> parse("http://.1/", null));
	}

	@Test
	void trailingDotPreservesValidationMessageAndCallbackFailure() {
		List<String> errors = new ArrayList<>();
		WhatWgUrlParser.UrlRecord result = parse("http://1.2.3.4./", errors::add);
		assertThat(result.host().toString()).isEqualTo("1.2.3.4");
		assertThat(errors).containsExactly(
				"URL validation error for URL [http://1.2.3.4./]@14. IPv4 address ends with \".\"");
		IllegalStateException failure = new IllegalStateException("validation callback failure");
		assertThatThrownBy(() -> parse("http://1.2.3.4./", message -> {
			throw failure;
		})).isSameAs(failure);
	}

	private static void assertHost(String input, String expected) {
		assertThat(parse(input, null).host().toString()).isEqualTo(expected);
	}

	private static WhatWgUrlParser.UrlRecord parse(String input,
			@Nullable Consumer<String> validationErrorHandler) {
		return WhatWgUrlParser.parse(input, null, null, validationErrorHandler);
	}

	@SuppressWarnings("unchecked")
	private static List<String> strictSplit(@Nullable String input, int delimiter) throws Exception {
		Method method = WhatWgUrlParser.class.getDeclaredMethod("strictSplit", String.class, int.class);
		return (List<String>) invoke(method, input, delimiter);
	}

	private static boolean endsInNumber(String input) throws Exception {
		Method method = WhatWgUrlParser.Host.class.getDeclaredMethod("endsInNumber", String.class);
		return (boolean) invoke(method, input);
	}

	private static Object invoke(Method method, @Nullable Object... arguments) throws Exception {
		method.setAccessible(true);
		try {
			return method.invoke(null, arguments);
		}
		catch (InvocationTargetException ex) {
			if (ex.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (ex.getCause() instanceof Error error) {
				throw error;
			}
			throw ex;
		}
	}

}
