<!-- SPDX-License-Identifier: Apache-2.0 -->
# Spring Collections EntrySet contains: single casefold and index lookup

Direct original-fork target `hsoliwal/spring-framework@d13f909ee01419d698cdeed7ce5f719aa6d97237`, `spring-core/src/main/java/org/springframework/util/LinkedCaseInsensitiveMap.java`; preimage Git blob `93c584fd6d1b41dffba2f0e2576ec7c08cf1218a`, full SHA-256 `0503a2189c99b8f7b96720d3799b70549d631ed8304bb3c4f9977b82f7a8c8fe`; proposed full SHA-256 `6a360f1f21cf8d8c0633f51d2da1cfd08fbc14e042193fbc81c6a4beec5b23d6`. Only private inner `EntrySet.contains(Object)` changes. This is not M3JDK21 work.

## Locked public/external behavior

Spring LinkedCaseInsensitiveMap preserves original casing and insertion order, locale-dependent key matching, null values, subclass hooks, serialization shape, and Map.Entry equality. The old EntrySet.contains probes `containsKey(key)` and then `get(key)`, converting the same String to its locale-lowercase form twice and probing the case-insensitive index twice. For **exact** LinkedCaseInsensitiveMap instances and String keys only, resolve `caseInsensitiveKeys.get(convertKey(string))` once, then compare `targetMap.get(originalKey)` using the unchanged `ObjectUtils.nullSafeEquals`. The index maps only canonical Strings to non-null original keys. If the runtime map is a subclass or key is non-String, run the **unchanged original containsKey/get path** so subclass method overrides and observed callbacks remain intact.

No new retained payload, wrapper, duplicate index, native path, public/protected method or API. This is a private operation-local algorithm improvement, not a replacement for the existing LinkedHashMap/HashMap-backed storage, which has separate customization contracts.

## Executed isolated JVM proof, not full Spring compilation

JDK21 `javac --release 21 -Xlint:all -Werror` passed for a direct index/normalization atom witness. Deterministic equal-output oracle: **139,268** comparisons across ROOT/ENGLISH/TURKISH/GERMAN locales, null values, arbitrary keys, subclass fallback and miss/hit paths. Repeated warm measurement on 2048 long ASCII keys: original median 3,185,389 ns vs candidate median 1,901,365 ns per paired batch, ratio **0.5969**. One process, nine alternating samples, standalone model only; cross-platform variance, output API/serialization/GC and full Spring Gradle tests remain **NOT_RUN**. Do not merge production code without independent tests. No default-branch writes.
