<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# DOOP key/value source receipt

Both multivalue adapters remove their additional temporary entry projections from containsValue and equals while preserving backing-view and callback behavior.

Canonical recipe and qualification: [Synexia commit cfa05713fe53](https://github.com/hsoliwal/com.synexia/commit/cfa05713fe5395694af36df74373dcfe1e7e9031).

Receiving parent: `9c3b2c286e42c258e977494f7a6c49f0d452986f`. Canonical datasets: `doop-key-value-spring-20261102`. All Java files below are actual verified SDK Result outputs.

## Executed verification

49/49 tests under JIT and interpreter; 22 original source owners compiled. Public/protected declarations and bidirectional serialization matched. Inverse scans allocated 32 rather than 24,048 bytes per 1,000 mappings in the measured interpreter run.

## Exact output blobs

| Path | Git blob |
|---|---|
| `spring-core/src/main/java/org/springframework/util/MultiToSingleValueMapAdapter.java` | `d80320f44534c54080a1117e54a13f66f07203d7` |
| `spring-core/src/main/java/org/springframework/util/SingleToMultiValueMapAdapter.java` | `c012f4c664785405915b96fdfaae68c63d1ca448` |
| `spring-core/src/test/java/org/springframework/util/MultiValueMapEntryTraversalTests.java` | `2650e7f8fb1ce9a72db228089a3b9aa05fb2459b` |

## Acceptance scope

Full Gradle acceptance remains unexecuted; this receipt covers the selected original-source owners. The backing maps and required singleton value-list projections retain their existing semantics.

The existing broader branch acceptance gates remain in force. This receipt does not claim whole-repository or fleet-wide conversion, universal throughput gains, or default-branch integration.
