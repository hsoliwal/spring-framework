# Spring Framework agent guidelines

## Clean output rule (owner directive, 2026-11-14) - read first

This rule overrides any older text in this file.

- This fork is clean output: one coherent Spring Framework repository. M3 work lives
  inside the project's own classes and packages, written the way the project
  writes its code. It is not stamped across folders.
- Do not add `m3/`, `.m3/`, `docs/m3/` or `tools/m3*` folders.
- Do not add any of the following. They belong in `hsoliwal/com.synexia`, the
  convergence workspace:
  - qualification bundles;
  - evidence, receipts and benchmark tables;
  - work orders;
  - donor copies;
  - recipe sources or recipe resources.
- Do not add parallel `M3`-prefixed types or `*.m3` packages where an existing
  class owns the behaviour. Change that class in place, keep it internal unless
  the owner admits new API, and follow the project's naming.
- A pull request here contains only the product change, plus its tests in the
  project's own test layout. Put the proof material in the paired `com.synexia`
  pull request and link to it.
- This fork's merged pull-request history shows the intended direction.
- Collection forks: arrayify and leetcodify the implementations here, then consolidate them into M3JDK (hsoliwal/M3jdk21), which owns the final implementations.


Concise extract of [CONTRIBUTING.md](CONTRIBUTING.md), which remains the reference for humans.


## Priority 0 — additive API superset, historical loss checks and last-checked anchors (2026-11-18)

Before source promotion and merges, preserve all applicable accepted upstream plus fork capabilities as one optimized **backward-compatible superset** (public API/ABI, expected behavior, fixes, tests, serializations, ordering, concurrency, native/resource contracts and prior measured optimizations). Compare and select the best *proven* algorithm from the incumbent, donors, old branches and PRs using correctness tests and performance/memory measurements; add APIs without silently discarding older features. Review Git history **from earliest reachable ancestors to all relevant heads**, old/merged/closed/reverted PRs, tags, merge bases, both parents and changes to deleted/renamed code to find bad commits, source loss and abandoned optimizations. Never rebase `develop`. Record last-checked date, ref/head SHA, scope, root coverage, PR/branch coverage, last full-verified anchor, loss findings, and PENDING/PARTIAL/VERIFIED/HOLD in canonical Synexia history ledger. HEAD observation does not certify an audit. Use existing Synexia Maven/OpenRewrite source-sealed recipes and target proof gates, respecting each repo's role. Default NEW separable Synexia-authored code to Apache-2.0 where permitted; retain all original upstream/donor licenses, NOTICE and attribution. Abstract algorithms are generally reusable ideas, but LLM translation/patternization does not itself relicense code. See [canonical Synexia PR #18764](https://github.com/hsoliwal/com.synexia/pull/18764) and its explicitly PENDING 236-repo registry.

## Build

- Gradle build, JDK from `.sdkmanrc` (currently JDK 25, `sdk env install` / `sdk env`); artifacts target Java 17+.
- Scope builds to the modules you touched: `./gradlew :spring-webmvc:test`, a single test with `--tests <fully.qualified.ClassTests>`.
- Run `./gradlew :<module>:check` before finishing: it runs tests and Checkstyle (`src/checkstyle/checkstyle.xml`), which enforces most of the code style below.
- NullAway null-safety checks run during compilation; fix reported errors instead of suppressing them.
- Do not run `clean` unless needed; the build is incremental and cached.
- Reference docs: `./gradlew antora`, output in `framework-docs/build/site/index.html`.

## Code style

- Match surrounding code; do not reformat unrelated code; a file should look like it was written by a single author.
- Tabs, LF, UTF-8, no trailing whitespace.
- Aim for 90 characters per line for code (105 acceptable, 120 max) and ~80 for Javadoc.
- Wrap lines after separators (`,` `+` `?` `:` `&&` `||`), never before.
- K&R braces, with `else`, `catch`, and `finally` on a new line.
- Two blank lines before fields, constructors, `static {}` blocks, and inner classes; one blank line after a multiline method signature.
- Import order, groups separated by a blank line: `java.*`, then `javax.*` + `jakarta.*`, then others, then `org.springframework.*`, then static imports.
- No wildcard imports. No static imports in production code except constants/enum constants and third-party DSL factory methods; use them in tests (e.g. `assertThat`).
- Every source file: Apache 2.0 license header (`Copyright 2002-present the original author or authors.`, copy from an existing file), package, imports, exactly one top-level class.
- Always reference fields with `this.`, never methods. Always add `@Override`.
- No `var` in production code. No single-character variable names. Wrap ternaries in parentheses with the non-null condition first: `(foo != null ? foo : "default")`.
- Argument checks: `Assert.notNull(event, "Event must not be null")`; state checks: `Assert.state(...)`.
- Null-safety with JSpecify: packages are `@NullMarked` in `package-info.java`, use `org.jspecify.annotations.Nullable` explicitly (e.g. `private @Nullable String name;`), repeat super method nullness on overrides, `@Contract` where useful.
- Static utility classes: `abstract`, `Utils` suffix, private constructor.
- No `System.out`/`System.err` or `printStackTrace()`.

## Javadoc

- First sentence in imperative style ("Return", not "Returns"); `<p>` to start extra paragraphs; `{@code}` for code and `null`.
- No blank line between method description and tags; do not indent wrapped tag descriptions.
- Add `@since` to new classes and new public/protected methods; omit a `.0` patch version (`@since 7.1`, not `7.1.0`).
- Tag order for types: `@author`, `@since`, `@param`, `@see`, `@deprecated`; for members: `@param`, `@return`, `@throws`, `@since`, `@see`, `@deprecated`.

## Tests

- Add or update tests for any code change.
- JUnit Jupiter, AssertJ (including `assertThatIllegalArgumentException()` and similar), Mockito. No JUnit 4/Jupiter/TestNG assertions, no Hamcrest.
- Test class names end with `Tests`.
- Use the standard Mockito API (`when(...).thenReturn(...)`), not `BDDMockito` (`given(...)`), in new test code; do not migrate existing `BDDMockito` usage.

## Docs

- Reference docs are AsciiDoc in `framework-docs/modules/ROOT`; code snippets for `include-code::` live in `framework-docs/src/main/{java,kotlin}`.

## Commits and pull requests

- Pull requests target `main`.
- Subject: imperative, capitalized verb, at most 55 characters, no `fix:`/`docs:` prefixes, no issue/PR number, no trailing period.
- Body wrapped at 72 characters explaining the motivation, followed by `Closes gh-123` (or `See gh-123`), then a `Signed-off-by: Name <email>` trailer (DCO, use `git commit -s`).
- Never write annotations verbatim in commit messages or PR titles (it mentions GitHub users): enclose them in backticks, as in `` `@Override` ``.
- Changes must be reviewed by a human who remains accountable for them before being submitted.


## Superset merge and history-audit rule (2026-11-18)

Read [.github/MERGE_RULE.md](.github/MERGE_RULE.md) before changing an existing implementation or integrating any branch or PR.
Preserve the supported additive API/behavior superset and select implementations by correctness, predictability, measured performance, then elegance.
Audit all reachable history back to every root, all branches/tags and all PR states for lost code or regressions; preserve append-only history.
Record actual UTC coverage and remaining work in [.github/HISTORY_AUDIT.tsv](.github/HISTORY_AUDIT.tsv); policy installation is not a completed source-history audit.
Prefer verified Apache-2.0 sources and independent implementations while preserving all existing third-party rights; LLM rewriting does not remove license obligations.
Detailed private recipe/proof records remain in canonical Synexia; the compact local policy and checkpoint make the rule discoverable here.
<!-- MERGE-SUPERSET-HISTORY-20261118-2 -->
