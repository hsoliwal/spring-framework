# Spring collection storage: bounded array packet

Baseline: `hsoliwal/spring-framework` at `49adc91c7e41eaafb52fc11e1210989894bda1f3`.
Canonical owner: `com.synexia` M3 collections contract refinement recipe; dataset `spring-arrays`.
All target bytes are prepared as templates, qualified by the actual OpenRewrite SDK, and admitted by the strict hash-pinned receiver. Production namespace and public/protected declarations are preserved.

## Physical changes

`CompositeIterator` retains its original `LinkedHashSet` only during registration. After traversal starts and all active `add` calls return, `freezeIterators()` copies child iterator identities into an owned `Iterator[]` and drops the set reference. Each `hasNext` and `next` still starts at the first child, repeats child `hasNext`, and preserves child order, null children, unsupported removal, and the lock-before-hash/equals check. Hash/equality callbacks may reenter registration or start traversal while an addition is pending: the addition depth keeps the exact native set authoritative until the outermost finally block. The original contains/add pair, hash collisions/tree bins, duplicate rejection, and silent failed set-add after an intervening duplicate remain intact.

This is **array traversal storage with a temporary construction set**. Assembly still allocates linked entries. The first traversal adds one O(n) array copy, then releases all registration nodes and its set/hash-table descriptors. No zero-node construction, concurrency, allocation-failure equivalence, or speedup claim is made. Existing unsynchronized behavior stays outside a concurrency guarantee.

`JdkProxyHint.Builder` retains appended interface references in an existing JDK `ArrayList` donor instead of `LinkedList`. Its private list never escapes: trusted array/list bulk appends precede the unchanged `List.copyOf` immutable snapshot. Null-element failure still occurs when building the hint; invalid class lists fail before any append. Duplicate/order behavior, caller-array isolation, snapshot isolation, reachable-type flags and Java-serialization hint flags stay unchanged. The flag describes a proxy hint; the builder itself has no serialization contract.

## Original-source qualification

Two immutable `.java.txt` resources are SHA-256 checked and independently compiled under their original binary names with JDK `JavaCompiler`. A child-first loader loads only each exact original owner and its nested classes; framework/JDK APIs remain the actual native classes. The rewritten source is never used as its own oracle.

Eleven new JUnit tests cover 625 small composite group shapes and null payloads; 80 colliding comparable iterators plus duplicates; 30 hash/equality reentry/trigger/failure combinations; insertion of a duplicate between contains and add; null child and abrupt child failure traces; exact array identity/registration release; 216 class-validation shapes; null timing; immutable copy/alias behavior; 1,024 builder values; and references whose methods throw if inspected. These augment the unchanged native owner and integration tests. The packet requires `./gradlew --no-daemon --no-scan :spring-core:check` on JDK25, with main sources targeting Java17 and the repository's NullAway, Checkstyle and multi-release gates unchanged. Recorded authoring proof is separate from the final received-source Gradle result.

## Existing workflow

The existing pull-request workflow now admits exactly `hsoliwal/spring-framework` as well as upstream. Its `pull_request` event, `contents: read` permission, shared build action and default `publish: false` remain unchanged. No secrets are newly passed, no release step is enabled, and no separate feature workflow is added. The existing shared action runs root `check antora`; a local Core check does not claim that whole-fork documentation/build workflow has already run. Human review required by the repository remains a publication/review boundary.

## Remaining owners and real donor refusals

The source census and owner ledger keep every detected storage site visible. The JDK parser records declarations, retained field syntax and explicitly imported JDK collection constructions across all tracked production Java sources. This is a structural census, without semantic attribution: factory-produced, inherited and application-specific ownership is not inferred from syntax alone. The manual ledger identifies the principal util collection owners and all direct LinkedList sites. `INVENTORIED_NOT_QUALIFIED` rows must not be counted as array conversions.

`LinkedMultiValueMap` still owns a serialized LinkedHashMap key index; `LinkedCaseInsensitiveMap` retains serialized concrete LinkedHashMap/HashMap fields and a protected eldest-entry hook. The unchanged canonical M3LinkedHashMap donor is not a transparent replacement. `DONOR_COUNTEREXAMPLES.tsv` records six executed native-versus-donor histories using the sealed donor closure: adapter serialization fails with NotSerializableException; iterator hasNext throws earlier after mutation; forEach stops callbacks after the first key instead of visiting later keys before failure; a removed live entry loses its detached value/setValue behavior; an empty lookup invokes a user hash callback that native lookup omits; and replaceAll checks revision before storing/continuing native callback results. The first five exercise the actual Spring MultiValueMapAdapter API; the sixth directly exercises the backing-map donor. The probe source and donor seals are included for reproduction.

ConcurrentReferenceHashMap has protected Reference/Entry/ReferenceManager boundaries plus weak/soft queues and linked reference chains. ConcurrentLruCache retains CAS node-state transitions, a concurrent key index, an eviction list and a concurrent write queue. No array donor has been qualified here for their GC, atomicity, queued identity and in-flight clear semantics. Composite/filter/multivalue adapters retain caller-owned live delegates. FastByteArrayOutputStream, XML list readers and StopWatch already use arrays or existing array-backed lists; those owners are retained without cosmetic rewrites. HeaderValueHolder, CGLIB bucket lists, requested CollectionFactory runtime types and locale enumerations have exposed list/iterator contracts. The URL parser's private String-only temporary list is an eligible separate bounded task; it is unchanged here.

## Reproduce the refusal probe

Copy `proof/DonorCompatibilityCounterexamples.java.txt` to a temporary `.java` path. Check every hash in `DONOR_SOURCES.tsv`, compile that exact five-file donor closure with Java21+ together with the probe, and place the native Spring Core classes on the classpath. Run `DonorCompatibilityCounterexamples`; it asserts that all six histories differ and prints the included TSV outcomes. This audit does not install donor classes in Spring.

Copyright: upstream Spring Apache-2.0 notices remain intact. New proof/custody work is by Hitesh Soliwal and Contributors to the Synexia Project under Apache-2.0.
