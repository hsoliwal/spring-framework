/*
 * Copyright 2026-present the original author or authors.
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

// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and contributors
// SPDX-License-Identifier: Apache-2.0

package org.springframework.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Provider contract oracle for adapters over Spring's mutable multi-value views. */
class M3StreamProviderContractTests {

	@Test
	void liveMultiValueViewsAndCaseInsensitiveKeysRemainProviderOwned() {
		LinkedMultiValueMap<String, String> multi = new LinkedMultiValueMap<>();
		multi.add("X-Trace", "one");
		multi.add("X-Trace", "one");
		List<String> live = multi.get("X-Trace");
		List<String> detached = new ArrayList<>(live);

		multi.add("X-Trace", "two");

		assertThat(detached).containsExactly("one", "one");
		assertThat(live).containsExactly("one", "one", "two").isNotSameAs(detached);

		LinkedCaseInsensitiveMap<String> headers = new LinkedCaseInsensitiveMap<>(Locale.ROOT);
		headers.put("X-Trace", "present");
		assertThat(headers.get("x-trace")).isEqualTo("present");
	}

}
