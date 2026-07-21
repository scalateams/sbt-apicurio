# Design: Publish sbt-apicurio for sbt 2.x (Scala 3), release 1.0.0

Date: 2026-07-20
Status: Approved (pending spec review)

## Goal

Republish `sbt-apicurio` targeting **sbt 2.x only**, rewritten in idiomatic
**Scala 3**, and cut it as the stable **1.0.0** release. This is a platform and
language port, not a feature change: every existing behavior — the Apicurio 3.x
REST interactions, semantic-version ordering, schema reference detection,
dependency ordering, Keycloak OAuth2 auth, and the stale-pin warning — must be
preserved.

## Decisions

- **sbt 2.x only.** sbt 1.x support is dropped. The last 1.x artifact published
  from `main` (v0.3.1 line) remains the final 1.x release; sbt 1.x users cannot
  resolve 1.0.0 because it publishes under the Scala 3 / sbt 2.0 cross-version
  path.
- **Full Scala 3 rewrite.** Convert to `enum`, `given`/`using`, `export`, and
  Scala 3 syntax throughout. No `implicit` keyword.
- **Version 1.0.0.** The plugin is vetted and working with a stable public API;
  the sbt-2 platform switch is the moment to declare 1.0.0 under SemVer. Version
  is set by the git tag (`v1.0.0`) via sbt-ci-release.
- **Stale-pin warning is ported preserving current behavior** (the
  `apicurioPullWarnOnStaleVersions` setting and `SemanticVersionOrdering`). Its
  design is *open to reconsideration* as a separate, explicitly-agreed change
  within the 1.0.0 milestone — it is not silently altered during the port.

## Target platform

| Component      | Version                                             |
|----------------|-----------------------------------------------------|
| sbt            | 2.0.3 (`project/build.properties`)                  |
| Scala          | 3.8.4 (matches sbt 2.x)                             |
| JDK (minimum)  | 17                                                  |
| sbt-ci-release | 1.12.0 (cross-published to sbt 2.0.0)               |

Dependencies keep their current coordinates (all publish for Scala 3, declared
with `%%`):
- `com.softwaremill.sttp.client3` core + circe `3.9.7`
- `io.circe` circe-core/generic/parser `0.14.7`
- `org.scalatest` scalatest `3.2.18` (Test)

## Build approach — native sbt 2.x meta-build

Run the build itself on sbt 2.x and publish directly. Because the plugin is
2.x-only there is no cross-axis and no `pluginCrossBuild` override — the plugin
cross-version defaults to the running sbt (2.0.3).

```scala
// project/build.properties
sbt.version=2.0.3

// build.sbt (shape)
lazy val plugin = (project in file("."))
  .enablePlugins(SbtPlugin)      // replaces manual `sbtPlugin := true`; also wires scripted
  .settings(
    scalaVersion := "3.8.4",
    // pluginCrossBuild / sbtVersion defaults to 2.0.3 — no override needed
    libraryDependencies ++= Seq(/* sttp, circe, scalatest — unchanged */)
  )
```

`inThisBuild(...)` publishing metadata (organization, homepage, licenses,
developers, scmInfo) and the `pomPostProcess` name rewrite are preserved. The
sbt-ci-release publishing model is unchanged (Central Portal, tag-driven).

### Toolchain risk (single item to verify first)

`sbt-scalafmt` must have an sbt-2.x-compatible release, because the project
requires `scalafmt` before every commit. If no sbt-2 build exists yet, the
fallback is to run the standalone `scalafmt` native CLI (via coursier) in CI and
locally instead of the sbt plugin. This is verified at the start of
implementation, before committing to the plugin path.

## Source rewrite (per file)

All source lives under `src/main/scala/org/scalateams/sbt/apicurio/`.

### `ApicurioModels.scala`
- `CompatibilityLevel` (sealed trait + 7 case objects, each with `value: String`)
  → `enum CompatibilityLevel(val value: String)`; `fromString` in the companion.
  The `None` case coexists with `scala.None`, disambiguated as today.
- `ArtifactType` (sealed trait + 5 case objects) → `enum ArtifactType(val value:
  String)`; `fromExtension` / `fromString` in the companion.
- `ApicurioError` (sealed trait + ~12 final case classes, each with `def
  message`) → `enum ApicurioError` with parameterized cases (including
  `cause: Option[Throwable] = None` defaults on the auth errors) and a single
  `message` method implemented by a `match`.
- circe codecs: `implicit val decoder/encoder = deriveDecoder/deriveEncoder`
  → `given Decoder[T] = deriveDecoder` / `given Encoder[T] = deriveEncoder`.
  **Keep `deriveDecoder`/`deriveEncoder`; do not switch to `derives`** — this
  preserves current decoding semantics (absent `Option` fields decode to `None`;
  case-class default values are not consulted by circe semi-auto). Switching to
  `derives` risks changing default-handling and is out of scope.
- `type ApicurioResult[T] = Either[ApicurioError, T]` is unchanged.
- The `object ApicurioModels` container is kept so existing qualified references
  (`ApicurioModels.KeycloakConfig`, `ApicurioModels.ArtifactType`, …) still
  resolve without churn in callers and tests.

### `ApicurioPlugin.scala`
- `Seq[Setting[_]]` → `Seq[Setting[?]]`.
- Re-exports in `autoImport`: replace the `val`/`type` alias pairs with Scala 3
  `export ApicurioModels.{CompatibilityLevel, KeycloakConfig}`.
- Keep `object autoImport`, `projectSettings`, `trigger = noTrigger`, the
  `keycloak(...)` and `schema(...)` helpers, and all setting/task keys.
- `.?.value` optional-setting reads and `sys.error(...)` are retained.

### `ApicurioClient.scala`
- `SemanticVersionOrdering extends Ordering[String]`, `final private case class
  Parsed`, and the SemVer logic are ported unchanged in behavior.
- sttp client3 usage (`HttpURLConnectionBackend()`, `uri"..."`, `basicRequest`,
  `asJson`, `asString`, `.send(backend)`) remains valid on Scala 3.
- `scala.util.Try { ... }.fold(...)` monadic usage stays (this is the `Try`
  monad, not a `try/catch` block, and is consistent with the codebase's
  functional error handling).

### `KeycloakTokenManager.scala`
- `SttpBackend[Identity, Any]`, `AtomicReference`, `synchronized`, the default
  `refreshBufferSeconds` parameter, and the companion `apply` overloads port
  directly to Scala 3.

### `SchemaFileUtils.scala`
- sbt IO usage (`IO.createDirectory`, `IO.write`) and the `file / "sub"` path
  DSL remain valid in sbt 2. Schema discovery uses `dir.listFiles()` (plain
  `java.io.File`), not PathFinder `**`/`*` globbing, so the sbt-2 file-model and
  glob changes do not affect this file.
- `Using(Source.fromFile(...))` and `MessageDigest` are stdlib and unchanged.

### `SchemaReferenceUtils.scala`
- circe `Json.fold`, `Regex`, `@tailrec`, and `sbt.util.Logger` port directly.

## sbt-2-specific behavior

- **Default task caching.** In sbt 2 all tasks are cached by default; tasks with
  side effects or non-serializable return values must be wrapped in
  `Def.uncached(...)`. Apply this to the effectful tasks: `apicurioPull`
  (network + file writes, returns `Seq[File]`), `apicurioPublish` (network),
  `apicurioDiscoverSchemas` (returns non-serializable `Seq[SchemaFile]`),
  `apicurioValidateSettings`, and `apicurioHelp`. This is the most important
  sbt-2 change beyond syntax.
- **`Compile / compile` hook.** The pull-before-compile override is preserved and
  verified to still fire correctly under caching.
- **File model.** The virtual-file/classpath overhaul in sbt 2 targets sbt's
  managed classpath and compile products; this plugin manages its own plain
  `java.io.File` values in settings and tasks, so `settingKey[File]` /
  `settingKey[Seq[File]]` and `File`-returning tasks are unaffected.
- **`target.value` layout** changes to `target/out/...` in sbt 2; the
  `apicurioPullOutputDir` default (`target.value / "schemas"`) still resolves
  correctly, just to the new physical location.

## Tests

Both specs compile on Scala 3 (ScalaTest 3.2.18 has `_3` artifacts):
- `SemanticVersionOrderingSpec` — pure unit tests, port directly.
- `ApicurioIntegrationSpec` — unit portions (URL assembly, settings validation,
  reference detection, dependency ordering, deduplication, error messages) run
  without a registry; integration portions stay gated behind
  `APICURIO_TEST_URL` / `KEYCLOAK_*` env vars and the `IntegrationTest` tag.
  `TestLogger extends sbt.util.Logger` (overriding `trace`/`success`/`log`),
  `Either#left.getOrElse`, and the integration `try/finally` cleanup are
  retained.

## Tooling & CI

- `.scalafmt.conf`: `runner.dialect = scala212` → `scala3`.
- `.scala-steward.conf`: drop the Scala 2.13 / "keep SBT plugin on 2.12.x" pins.
- `.github/workflows/ci.yml`: replace matrix `scala: [2.12.19]` with a single
  Scala 3.8.4 build; JDK matrix `[11, 17]` → `[17]` (optionally add `21`); keep
  compile, test, and `scalafmtCheckAll` (or the CLI fallback).
- `.github/workflows/release.yml`: bump `java-version: 11` → `17`; `sbt
  ci-release` and the Central Portal secrets are unchanged.
- `example/`: currently broken (its `build.sbt` references the removed
  `apicurioRegistryUrl` and `apicurioApiKey` settings). Rewrite to the current
  component-based host + optional Keycloak API, add
  `example/project/build.properties` (`sbt.version=2.0.3`), and fix the plugin
  reference so the example builds under sbt 2.x.

## Docs & versioning

- README: update the install snippet, the Scala/sbt/JDK prerequisites (sbt 2.x,
  JDK 17), and the Maven Central version reference.
- CHANGELOG: add a `1.0.0` entry describing the sbt 2.x / Scala 3 migration and
  the drop of sbt 1.x support.
- Release is cut by tagging `v1.0.0`; sbt-ci-release derives the version from the
  tag. Tagging/pushing is gated on explicit user approval per the repo's branch
  safety rule; work happens on
  `claude/sbt-apicurio-sbt2-scala3-1.0.0`.

## Verification plan

1. `sbt compile` (Scala 3.8.4 / sbt 2.0.3) — clean.
2. `sbt scalafmtCheckAll` (or CLI fallback) — clean.
3. `sbt test` — unit tests green; integration tests skipped without a registry.
4. `sbt publishLocal` — smoke test the artifact and its POM.
5. Consume the locally published 1.0.0 from `example/` under sbt 2.0.3 and run
   `apicurioValidateSettings` / `apicurioHelp`.
6. Where feasible, diff observable behavior against the v0.3.1 line.

## Open items (verified during implementation, not blockers)

- `sbt-scalafmt` sbt-2.x availability (fallback: standalone scalafmt CLI).
- Exact `Def.uncached` signature/placement under 2.0.3.
- `.?` optional-setting read and `sbt.util.Logger` abstract-method surface under
  2.0.3 (a compile check confirms both).

## Out of scope

- Cross-building for sbt 1.x.
- Feature additions or API changes beyond what the Scala 3 port requires.
- Migrating away from sttp-client3 / circe to newer major versions.
- Refactoring integration-test `try/finally` cleanup.
