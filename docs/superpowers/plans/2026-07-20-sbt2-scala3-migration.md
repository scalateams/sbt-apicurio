# sbt 2.x / Scala 3 Migration (1.0.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Republish `sbt-apicurio` for sbt 2.x only, rewritten in idiomatic Scala 3, as the stable 1.0.0 release, preserving all existing behavior.

**Architecture:** Native sbt 2.x meta-build (no cross-axis). The plugin is an `AutoPlugin` whose ADTs become Scala 3 `enum`s, circe codecs become `given`s in companion objects (auto implicit scope — no wildcard-`given` import needed), re-exports become `export`, and effectful tasks are wrapped in `Def.uncached` for sbt 2's default task caching. The four non-ADT source files (`ApicurioClient`, `KeycloakTokenManager`, `SchemaFileUtils`, `SchemaReferenceUtils`) already use Scala-2/3-common constructs and port with import fixes only, verified by compiling.

**Tech Stack:** sbt 2.0.3, Scala 3.8.4, JDK 17, sttp-client3 3.9.7, circe 0.14.7, ScalaTest 3.2.18, sbt-ci-release 1.12.0.

## Global Constraints

- sbt **2.0.3** in `project/build.properties`; Scala **3.8.4**; JDK **17** minimum.
- sbt 2.x **only** — no sbt 1.x cross-build, no `pluginCrossBuild` override.
- **Full Scala 3 rewrite**: `enum`, `given`/`using`, `export`, `.*` and `?` wildcards. No `implicit` keyword.
- **Behavior-preserving**: no feature/API changes beyond what the port requires. The stale-pin warning (`apicurioPullWarnOnStaleVersions`, `SemanticVersionOrdering`) is ported unchanged.
- Keep circe **`deriveDecoder`/`deriveEncoder`** (not `derives`) to preserve decode semantics (absent `Option` → `None`; case-class defaults not consulted).
- Keep the `object ApicurioModels` container so qualified references (`ApicurioModels.KeycloakConfig`, etc.) still resolve.
- Dependency coordinates unchanged, declared with `%%`.
- **Run scalafmt before every commit** (project rule). Work on branch `claude/sbt-apicurio-sbt2-scala3-1.0.0`. Do not tag/push `v1.0.0` without explicit user approval.
- Package for all source: `org.scalateams.sbt.apicurio`.

---

## File Map

| File | Change |
|------|--------|
| `project/build.properties` | sbt 1.11.0 → 2.0.3 |
| `project/plugins.sbt` | sbt-ci-release → 1.12.0; resolve sbt-scalafmt for sbt 2 (or CLI fallback) |
| `build.sbt` | `SbtPlugin`, Scala 3.8.4, Scala-3 scalacOptions |
| `.scalafmt.conf` | dialect `scala212` → `scala3` |
| `src/main/scala/.../ApicurioModels.scala` | enums + `given` (full rewrite) |
| `src/main/scala/.../ApicurioPlugin.scala` | `export`, `Setting[?]`, `.*` imports, `Def.uncached` |
| `src/main/scala/.../ApicurioClient.scala` | `.*` imports; verify sttp/circe given resolution |
| `src/main/scala/.../KeycloakTokenManager.scala` | `.*` imports; verify sttp/circe given resolution |
| `src/main/scala/.../SchemaFileUtils.scala` | `.*` imports |
| `src/main/scala/.../SchemaReferenceUtils.scala` | `.*` imports |
| `src/test/scala/.../ApicurioIntegrationSpec.scala` | `.*` imports; verify `Logger` surface |
| `src/test/scala/.../SemanticVersionOrderingSpec.scala` | none expected |
| `.github/workflows/ci.yml` | matrix Scala 3.8.4, JDK 17 |
| `.github/workflows/release.yml` | JDK 11 → 17 |
| `.scala-steward.conf` | drop 2.13/2.12 pins |
| `example/build.sbt` | rewrite to current API |
| `example/project/build.properties` | create, sbt 2.0.3 |
| `README.md`, `CHANGELOG.md` | install/prereqs/version, 1.0.0 entry |

---

## Task 1: sbt 2.x / Scala 3 build scaffolding + scalafmt decision

**Files:**
- Modify: `project/build.properties`
- Modify: `project/plugins.sbt`
- Modify: `build.sbt`
- Modify: `.scalafmt.conf`

**Interfaces:**
- Produces: a build that loads on sbt 2.0.3 with `scalaVersion` = 3.8.4. (Compilation will fail until Task 2 — that is expected here.)

- [ ] **Step 1: Set the sbt version**

Replace the entire contents of `project/build.properties` with:

```properties
sbt.version=2.0.3
```

- [ ] **Step 2: Update meta-build plugins**

Replace the entire contents of `project/plugins.sbt` with:

```scala
// Publishing and versioning
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.0")

// Code quality
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")
```

- [ ] **Step 3: Rewrite build.sbt for Scala 3 / SbtPlugin**

Replace the entire contents of `build.sbt` with:

```scala
// Use inThisBuild for settings that should apply to the entire build
inThisBuild(
  List(
    organization := "com.scalateams",
    homepage     := Some(url("https://github.com/scalateams/sbt-apicurio")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        id = "scalateams",
        name = "ScalaTeams",
        email = "team@scalateams.org",
        url = url("https://github.com/scalateams")
      )
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/scalateams/sbt-apicurio"),
        "scm:git:git@github.com:scalateams/sbt-apicurio.git"
      )
    )
  )
)

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin) // sets sbtPlugin := true and wires scripted
  .settings(
    name         := "sbt-apicurio",
    scalaVersion := "3.8.4",
    description  := "SBT plugin for Apicurio Schema Registry integration",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "core"    % "3.9.7",
      "com.softwaremill.sttp.client3" %% "circe"   % "3.9.7",
      "io.circe"                      %% "circe-core"    % "0.14.7",
      "io.circe"                      %% "circe-generic" % "0.14.7",
      "io.circe"                      %% "circe-parser"  % "0.14.7",
      "org.scalatest"                 %% "scalatest"     % "3.2.18" % Test
    ),
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-deprecation",
      "-feature",
      "-unchecked"
    ),
    // Configure POM metadata for Maven Central display
    pomPostProcess := { node =>
      import scala.xml.*
      import scala.xml.transform.*
      new RuleTransformer(new RewriteRule {
        override def transform(n: Node): Seq[Node] = n match {
          case e: Elem if e.label == "name" =>
            <name>SBT Apicurio Plugin</name>
          case _ => n
        }
      }).transform(node).head
    }
  )
```

Notes: `-Xlint`, `-Ywarn-dead-code`, `-Ywarn-numeric-widen` are Scala 2 flags and are removed (they do not exist in Scala 3.8.4 and would fail the build). `SbtPlugin` sets `sbtPlugin := true`, so it is no longer set by hand.

- [ ] **Step 4: Set the scalafmt dialect to Scala 3**

In `.scalafmt.conf`, change the dialect line:

```
runner.dialect = scala3
```

(from `runner.dialect = scala212`). Leave all other scalafmt settings unchanged.

- [ ] **Step 5: Verify the build loads on sbt 2.0.3 / Scala 3.8.4**

Run: `sbt "show scalaVersion" "show sbtVersion"`
Expected: prints `3.8.4` and `2.0.3`. The build **loads** (a subsequent `compile` will still fail — sources not yet ported; that is fine here).

- [ ] **Step 6: Confirm the scalafmt toolchain**

Run: `sbt scalafmtCheck` (any target; it only needs to resolve the plugin, not pass).
Expected: the `sbt-scalafmt` plugin resolves and the `scalafmtCheck` task exists (formatting failures are OK at this point).

If `sbt-scalafmt` 2.5.4 does **not** resolve under sbt 2.0.3, apply the CLI fallback instead:
- Remove the `sbt-scalafmt` line from `project/plugins.sbt`.
- Record in `CHANGELOG.md` (dev note) and use the standalone formatter for the "run scalafmt" steps in later tasks:
  `cs launch scalafmt:3.7.17 -- --config .scalafmt.conf $(git ls-files '*.scala' '*.sbt')`
  and for CI checks: `... -- --test --config .scalafmt.conf ...`.

- [ ] **Step 7: Commit**

```bash
sbt scalafmtAll >/dev/null 2>&1 || cs launch scalafmt:3.7.17 -- --config .scalafmt.conf $(git ls-files '*.sbt') || true
git add project/build.properties project/plugins.sbt build.sbt .scalafmt.conf
git commit -m "build: target sbt 2.0.3 / Scala 3.8.4 for the plugin"
```

---

## Task 2: Rewrite `ApicurioModels.scala` and get `sbt compile` green

**Files:**
- Modify (full rewrite): `src/main/scala/org/scalateams/sbt/apicurio/ApicurioModels.scala`
- Modify: `src/main/scala/org/scalateams/sbt/apicurio/ApicurioPlugin.scala`
- Modify: `src/main/scala/org/scalateams/sbt/apicurio/ApicurioClient.scala`
- Modify: `src/main/scala/org/scalateams/sbt/apicurio/KeycloakTokenManager.scala`
- Modify: `src/main/scala/org/scalateams/sbt/apicurio/SchemaFileUtils.scala`
- Modify: `src/main/scala/org/scalateams/sbt/apicurio/SchemaReferenceUtils.scala`

**Interfaces:**
- Consumes: nothing external.
- Produces (relied on by all callers and tests — signatures unchanged from today):
  - `enum CompatibilityLevel(val value: String)` with cases `Backward, BackwardTransitive, Forward, ForwardTransitive, Full, FullTransitive, None`; companion `CompatibilityLevel.fromString(s: String): Option[CompatibilityLevel]`.
  - `enum ArtifactType(val value: String)` with cases `Avro, Protobuf, JsonSchema, OpenApi, AsyncApi`; companion `ArtifactType.fromExtension(ext: String): Option[ArtifactType]`, `ArtifactType.fromString(s: String): Option[ArtifactType]`.
  - `enum ApicurioError` with cases `ArtifactNotFound(groupId, artifactId)`, `VersionNotFound(groupId, artifactId, version)`, `IncompatibleSchema(groupId, artifactId, reason)`, `CircularDependency(schemas: Set[String])`, `InvalidSchema(reason)`, `HttpError(statusCode: Int, body: String)`, `NetworkError(cause: Throwable)`, `ParseError(reason)`, `ConfigurationError(reason)`, `AuthenticationError(reason, cause: Option[Throwable] = None)`, `TokenRefreshError(reason, cause: Option[Throwable] = None)`; method `def message: String`.
  - case classes `ArtifactMetadata`, `CreateArtifactResponse`, `CreateArtifactRequest`, `FirstVersionRequest`, `ContentRequest`, `ContentReference`, `CreateVersionRequest`, `VersionMetadata`, `ApicurioDependency`, `SchemaContentWithMetadata`, `SchemaFile`, `CompatibilityCheckResult`, `KeycloakConfig`, `TokenResponse` — same fields as today; `given` circe codecs in the same companions that had them.
  - `type ApicurioResult[T] = Either[ApicurioError, T]`.

- [ ] **Step 1: Rewrite ApicurioModels.scala**

Replace the entire contents of `src/main/scala/org/scalateams/sbt/apicurio/ApicurioModels.scala` with:

```scala
package org.scalateams.sbt.apicurio

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

object ApicurioModels {

  enum CompatibilityLevel(val value: String) {
    case Backward           extends CompatibilityLevel("BACKWARD")
    case BackwardTransitive extends CompatibilityLevel("BACKWARD_TRANSITIVE")
    case Forward            extends CompatibilityLevel("FORWARD")
    case ForwardTransitive  extends CompatibilityLevel("FORWARD_TRANSITIVE")
    case Full               extends CompatibilityLevel("FULL")
    case FullTransitive     extends CompatibilityLevel("FULL_TRANSITIVE")
    case None               extends CompatibilityLevel("NONE")
  }

  object CompatibilityLevel {
    def fromString(s: String): Option[CompatibilityLevel] = s.toUpperCase match {
      case "BACKWARD"            => Some(Backward)
      case "BACKWARD_TRANSITIVE" => Some(BackwardTransitive)
      case "FORWARD"             => Some(Forward)
      case "FORWARD_TRANSITIVE"  => Some(ForwardTransitive)
      case "FULL"                => Some(Full)
      case "FULL_TRANSITIVE"     => Some(FullTransitive)
      case "NONE"                => Some(None)
      case _                     => scala.None
    }
  }

  enum ArtifactType(val value: String) {
    case Avro       extends ArtifactType("AVRO")
    case Protobuf   extends ArtifactType("PROTOBUF")
    case JsonSchema extends ArtifactType("JSON")
    case OpenApi    extends ArtifactType("OPENAPI")
    case AsyncApi   extends ArtifactType("ASYNCAPI")
  }

  object ArtifactType {
    def fromExtension(ext: String): Option[ArtifactType] = ext.toLowerCase match {
      case "avsc" | "avro" => Some(Avro)
      case "proto"         => Some(Protobuf)
      case "json"          => Some(JsonSchema)
      case "yaml" | "yml"  => Some(OpenApi) // Could be AsyncAPI too, will need content inspection
      case _               => scala.None
    }

    def fromString(s: String): Option[ArtifactType] = s.toUpperCase match {
      case "AVRO"     => Some(Avro)
      case "PROTOBUF" => Some(Protobuf)
      case "JSON"     => Some(JsonSchema)
      case "OPENAPI"  => Some(OpenApi)
      case "ASYNCAPI" => Some(AsyncApi)
      case _          => scala.None
    }
  }

  // Artifact-level metadata (from GET /groups/{groupId}/artifacts/{artifactId})
  case class ArtifactMetadata(
    groupId: String,
    artifactId: String,
    artifactType: String,
    owner: Option[String] = None,
    createdOn: Option[String] = None,
    modifiedBy: Option[String] = None,
    modifiedOn: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None,
    labels: Option[Map[String, String]] = None)

  object ArtifactMetadata {
    given Decoder[ArtifactMetadata] = deriveDecoder[ArtifactMetadata]
    given Encoder[ArtifactMetadata] = deriveEncoder[ArtifactMetadata]
  }

  // Response from POST /groups/{groupId}/artifacts (contains both artifact and version)
  case class CreateArtifactResponse(
    artifact: ArtifactMetadata,
    version: VersionMetadata)

  object CreateArtifactResponse {
    given Decoder[CreateArtifactResponse] = deriveDecoder[CreateArtifactResponse]
  }

  case class CreateArtifactRequest(
    artifactId: String,
    artifactType: String,
    firstVersion: FirstVersionRequest,
    name: Option[String] = None,
    description: Option[String] = None)

  object CreateArtifactRequest {
    given Encoder[CreateArtifactRequest] = deriveEncoder[CreateArtifactRequest]
  }

  case class FirstVersionRequest(
    version: Option[String] = None,
    content: ContentRequest,
    name: Option[String] = None,
    description: Option[String] = None)

  object FirstVersionRequest {
    given Encoder[FirstVersionRequest] = deriveEncoder[FirstVersionRequest]
  }

  case class ContentRequest(
    content: String,
    contentType: String = "application/json",
    references: List[ContentReference] = List.empty)

  object ContentRequest {
    given Encoder[ContentRequest] = deriveEncoder[ContentRequest]
  }

  case class ContentReference(
    groupId: Option[String] = None,
    artifactId: String,
    version: Option[String] = None,
    name: String)

  object ContentReference {
    given Encoder[ContentReference] = deriveEncoder[ContentReference]
  }

  case class CreateVersionRequest(
    version: Option[String] = None,
    content: ContentRequest,
    name: Option[String] = None,
    description: Option[String] = None)

  object CreateVersionRequest {
    given Encoder[CreateVersionRequest] = deriveEncoder[CreateVersionRequest]
  }

  // Version-level metadata
  case class VersionMetadata(
    version: String,
    groupId: String,
    artifactId: String,
    artifactType: String,
    contentId: Long,
    globalId: Long,
    state: String,
    createdOn: String,
    owner: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None,
    labels: Option[Map[String, String]] = None)

  object VersionMetadata {
    given Decoder[VersionMetadata] = deriveDecoder[VersionMetadata]
  }

  case class ApicurioDependency(
    groupId: String,
    artifactId: String,
    version: String) {
    override def toString: String = s"$groupId % $artifactId % $version"
  }

  /** Schema content with metadata for determining file format */
  case class SchemaContentWithMetadata(
    content: String,
    contentType: String,
    artifactType: ArtifactType)

  case class SchemaFile(
    file: java.io.File,
    content: String,
    hash: String,
    artifactType: ArtifactType,
    fileExtension: String)

  case class CompatibilityCheckResult(
    compatible: Boolean,
    message: Option[String] = None)

  object CompatibilityCheckResult {
    given Decoder[CompatibilityCheckResult] = deriveDecoder[CompatibilityCheckResult]
  }

  /** Keycloak OAuth2 configuration for client credentials flow */
  case class KeycloakConfig(
    url: String,
    realm: String,
    clientId: String,
    clientSecret: String) {

    /** Constructs the token endpoint URL from the Keycloak base URL and realm */
    def tokenEndpoint: String = s"$url/realms/$realm/protocol/openid-connect/token"
  }

  /** OAuth2 token response from Keycloak token endpoint */
  case class TokenResponse(
    access_token: String,
    expires_in: Long,
    refresh_expires_in: Option[Long] = None,
    token_type: String,
    scope: Option[String] = None)

  object TokenResponse {
    given Decoder[TokenResponse] = deriveDecoder[TokenResponse]
  }

  /** Functional error types for Apicurio operations. Using Either[ApicurioError, T] instead of
    * Try[T] provides type safety, composability, explicit error handling, and structured messages.
    */
  enum ApicurioError {
    case ArtifactNotFound(groupId: String, artifactId: String)
    case VersionNotFound(groupId: String, artifactId: String, version: String)
    case IncompatibleSchema(groupId: String, artifactId: String, reason: String)
    case CircularDependency(schemas: Set[String])
    case InvalidSchema(reason: String)
    case HttpError(statusCode: Int, body: String)
    case NetworkError(cause: Throwable)
    case ParseError(reason: String)
    case ConfigurationError(reason: String)
    case AuthenticationError(reason: String, cause: Option[Throwable] = None)
    case TokenRefreshError(reason: String, cause: Option[Throwable] = None)

    def message: String = this match {
      case ArtifactNotFound(groupId, artifactId)          =>
        s"Artifact not found: $groupId:$artifactId"
      case VersionNotFound(groupId, artifactId, version)  =>
        s"Version not found: $groupId:$artifactId:$version"
      case IncompatibleSchema(groupId, artifactId, reason) =>
        s"Schema is not compatible with existing versions: $groupId:$artifactId - $reason"
      case CircularDependency(schemas)                    =>
        val schemaList = schemas.toList.sorted.mkString(", ")
        s"""Circular dependency detected among schemas: $schemaList
           |
           |These schemas form a dependency cycle where each depends on another in the group.
           |Schemas must be organized in a directed acyclic graph (DAG) for publication.
           |
           |To resolve:
           |1. Review the schema references to identify the circular dependency chain
           |2. Refactor schemas to break the cycle (e.g., extract common types into a separate schema)
           |3. Ensure dependencies flow in one direction only""".stripMargin
      case InvalidSchema(reason)          => s"Invalid schema: $reason"
      case HttpError(statusCode, body)    => s"HTTP error $statusCode: $body"
      case NetworkError(cause)            => s"Network error: ${cause.getMessage}"
      case ParseError(reason)             => s"Parse error: $reason"
      case ConfigurationError(reason)     => s"Configuration error: $reason"
      case AuthenticationError(reason, cause) =>
        s"Authentication error: $reason${cause.map(c => s" (${c.getMessage})").getOrElse("")}"
      case TokenRefreshError(reason, cause)   =>
        s"Token refresh error: $reason${cause.map(c => s" (${c.getMessage})").getOrElse("")}"
    }
  }

  /** Type alias for Either-based results */
  type ApicurioResult[T] = Either[ApicurioError, T]
}
```

- [ ] **Step 2: Update ApicurioPlugin.scala for Scala 3 (compile-critical parts only)**

In `src/main/scala/org/scalateams/sbt/apicurio/ApicurioPlugin.scala`:

(a) Change the top imports from:
```scala
import org.scalateams.sbt.apicurio.ApicurioModels._
import sbt.Keys._
import sbt._
```
to:
```scala
import org.scalateams.sbt.apicurio.ApicurioModels.*
import sbt.Keys.*
import sbt.*
```

(b) Replace the four re-export lines in `object autoImport`:
```scala
    // Re-export models for easy access
    val CompatibilityLevel = ApicurioModels.CompatibilityLevel
    type CompatibilityLevel = ApicurioModels.CompatibilityLevel
    val KeycloakConfig = ApicurioModels.KeycloakConfig
    type KeycloakConfig = ApicurioModels.KeycloakConfig
```
with:
```scala
    // Re-export models for easy access in build.sbt
    export ApicurioModels.{CompatibilityLevel, KeycloakConfig}
```
(The inner `import autoImport.*` shadows the outer `import ApicurioModels.*`, so this does not create an ambiguity — mirrors the existing structure.)

(c) Change `import autoImport._` (line ~112) to `import autoImport.*`.

(d) Change the `projectSettings` signature from:
```scala
  override lazy val projectSettings: Seq[Setting[_]] = Seq(
```
to:
```scala
  override lazy val projectSettings: Seq[Setting[?]] = Seq(
```

Leave the task bodies unchanged in this task (Def.uncached comes in Task 3).

- [ ] **Step 3: Update wildcard imports in the remaining source files**

Change every `import x.y._` to `import x.y.*` in these files (leave all other code unchanged):
- `ApicurioClient.scala`: `import org.scalateams.sbt.apicurio.ApicurioModels.*`, `import io.circe.parser.*`, `import sttp.client3.*`, `import sttp.client3.circe.*`.
- `KeycloakTokenManager.scala`: `import org.scalateams.sbt.apicurio.ApicurioModels.*`, `import sttp.client3.*`, `import sttp.client3.circe.*`.
- `SchemaFileUtils.scala`: `import org.scalateams.sbt.apicurio.ApicurioModels.*`, `import sbt.*`.
- `SchemaReferenceUtils.scala`: `import org.scalateams.sbt.apicurio.ApicurioModels.*`, `import io.circe.parser.*`.

- [ ] **Step 4: Compile and drive to green**

Run: `sbt compile`
Expected: `[success]`.

If errors appear, fix in place — the anticipated ones and their fixes:
- **sttp circe body/response instance not found** (in `ApicurioClient.scala` or `KeycloakTokenManager.scala`): add `import sttp.client3.circe.given` alongside `import sttp.client3.circe.*`.
- **`import ... _` deprecation**: already handled in Step 3 by switching to `.*`.
- **enum case value inference**: the circe `deriveDecoder[T]`/`deriveEncoder[T]` are written with explicit type args, so no inference gaps are expected.
- Do **not** change any logic, method signature, or ADT shape — only imports/syntax needed to compile.

- [ ] **Step 5: Commit**

```bash
sbt scalafmtAll >/dev/null 2>&1 || cs launch scalafmt:3.7.17 -- --config .scalafmt.conf $(git ls-files '*.scala') || true
git add src/main
git commit -m "refactor: port plugin sources to Scala 3 (enums, given, exports)"
```

---

## Task 3: Wrap effectful tasks in `Def.uncached`

> **EXECUTION NOTE (2026-07-20): FOLDED INTO TASK 2.** During execution we found that sbt 2.x's action-caching macro fails at *compile time* (not just runtime) when a task result type lacks a `HashWriter`/`JsonFormat` (e.g. `Seq[File]`, `Seq[SchemaFile]`). `Def.uncached` is therefore a prerequisite for `sbt compile` to succeed, so this task was merged into Task 2 and executed there. The steps below are retained for reference.

**Files:**
- Modify: `src/main/scala/org/scalateams/sbt/apicurio/ApicurioPlugin.scala`

**Interfaces:**
- Consumes: the task keys and bodies from Task 2 (`apicurioHelp`, `apicurioDiscoverSchemas`, `apicurioValidateSettings`, `apicurioPull`, `apicurioPublish`).
- Produces: the same tasks, now opted out of sbt 2's default task cache.

- [ ] **Step 1: Wrap each effectful task body**

In `projectSettings`, wrap the right-hand side of each of these task definitions in `Def.uncached { ... }` (they perform network I/O, file writes, and/or return non-serializable values):
- `apicurioHelp := Def.uncached { <existing body> }`
- `apicurioDiscoverSchemas := Def.uncached { <existing body> }`
- `apicurioValidateSettings := Def.uncached { <existing body> }`
- `apicurioPull := Def.uncached { <existing body> }`
- `apicurioPublish := Def.uncached { <existing body> }`

Example (apicurioDiscoverSchemas), before:
```scala
    apicurioDiscoverSchemas := {
      val paths = apicurioSchemaPaths.value
      // ...
      schemas
    },
```
after:
```scala
    apicurioDiscoverSchemas := Def.uncached {
      val paths = apicurioSchemaPaths.value
      // ...
      schemas
    },
```

Leave the `Compile / compile := { apicurioPull.value; (Compile / compile).value }` hook as-is (it is not a cached-value producer).

- [ ] **Step 2: Verify it compiles and the exact API**

Run: `sbt compile`
Expected: `[success]`.

If `Def.uncached` is not resolved or has a different arity under 2.0.3, consult the sbt 2.x migration guide's task-caching section (https://www.scala-sbt.org/2.x/docs/en/changes/migrating-from-sbt-1.x.html) and use the documented opt-out form; the intent is that these five tasks are **not** cached. Do not proceed until `sbt compile` is green with the tasks marked uncached.

- [ ] **Step 3: Smoke-test task invocation**

Run: `sbt apicurioHelp`
Expected: prints the help banner and a "Configuration incomplete" status (no host configured) without a cache error. Run it a second time and confirm it re-executes (prints again) rather than returning a cached no-op.

- [ ] **Step 4: Commit**

```bash
sbt scalafmtAll >/dev/null 2>&1 || cs launch scalafmt:3.7.17 -- --config .scalafmt.conf $(git ls-files '*.scala') || true
git add src/main/scala/org/scalateams/sbt/apicurio/ApicurioPlugin.scala
git commit -m "fix: opt effectful tasks out of sbt 2 default task caching"
```

---

## Task 4: Port tests and get unit tests green

**Files:**
- Modify: `src/test/scala/org/scalateams/sbt/apicurio/ApicurioIntegrationSpec.scala`
- Modify: `src/test/scala/org/scalateams/sbt/apicurio/SemanticVersionOrderingSpec.scala`

**Interfaces:**
- Consumes: the Task 2 model/enum API and `ApicurioClient`, `SchemaFileUtils`, `SchemaReferenceUtils` public methods (unchanged signatures).
- Produces: passing unit tests; integration tests remain gated behind `IntegrationTest` tag + env vars.

- [ ] **Step 1: Update wildcard imports in the specs**

In `ApicurioIntegrationSpec.scala`, change `import org.scalatest.flatspec.AnyFlatSpec` etc. that use `_` wildcards to `.*` (specifically any `import ..._`). The named imports (`AnyFlatSpec`, `Matchers`, `BeforeAndAfterAll`, `Tag`, `Logger`, `IO`, `File`) stay as-is. In `SemanticVersionOrderingSpec.scala` no import changes are expected.

- [ ] **Step 2: Compile the tests and drive to green**

Run: `sbt Test/compile`
Expected: `[success]`.

Anticipated fixes:
- **`TestLogger extends sbt.util.Logger`**: if the compiler reports unimplemented members, implement exactly the abstract methods sbt 2.0.3's `sbt.util.Logger` declares, keeping the current no-op/buffer behavior (`trace`, `success`, `log(level, message)`). Do not change the buffering logic.
- **`Either#left.getOrElse`**: retained; if a deprecation warning appears it is non-fatal.
- Do not alter test assertions or behavior.

- [ ] **Step 3: Run unit tests (skip integration)**

Run: `sbt "testOnly * -- -l org.scalateams.sbt.apicurio.IntegrationTest"`
Expected: PASS. This runs the registry-free tests (URL assembly, settings validation, semver ordering, reference detection, dependency ordering, deduplication, error messages) and excludes the `IntegrationTest`-tagged ones.

- [ ] **Step 4: Commit**

```bash
sbt scalafmtAll >/dev/null 2>&1 || cs launch scalafmt:3.7.17 -- --config .scalafmt.conf $(git ls-files '*.scala') || true
git add src/test
git commit -m "test: port specs to Scala 3; unit tests green"
```

---

## Task 5: Formatting clean (`scalafmt`)

**Files:**
- Modify: any Scala/sbt file needing reformatting under the `scala3` dialect.

**Interfaces:** none.

- [ ] **Step 1: Format the whole project**

Run: `sbt scalafmtAll scalafmtSbt` (or CLI fallback: `cs launch scalafmt:3.7.17 -- --config .scalafmt.conf $(git ls-files '*.scala' '*.sbt')`)
Expected: files reformatted in place.

- [ ] **Step 2: Verify the check passes**

Run: `sbt scalafmtCheckAll scalafmtSbtCheck` (or CLI: `... -- --test --config .scalafmt.conf ...`)
Expected: `[success]` / no diff.

- [ ] **Step 3: Commit (only if files changed)**

```bash
git add -A
git commit -m "style: scalafmt (scala3 dialect)" || echo "nothing to format"
```

---

## Task 6: Fix the `example/` project

**Files:**
- Create: `example/project/build.properties`
- Modify (full rewrite): `example/build.sbt`

**Interfaces:**
- Consumes: the plugin's current `autoImport` API (`apicurioRegistryHost`, `apicurioGroupId`, `apicurioKeycloakConfig`, `keycloak(...)`, `schema(...)`, `apicurioCompatibilityLevel`, `apicurioSchemaPaths`, `apicurioPullDependencies`, `apicurioPullRecursive`) via the local source dependency in `example/project/plugins.sbt` (unchanged).

- [ ] **Step 1: Pin the example to sbt 2.0.3**

Create `example/project/build.properties`:

```properties
sbt.version=2.0.3
```

- [ ] **Step 2: Rewrite example/build.sbt to the current API**

Replace the entire contents of `example/build.sbt` with:

```scala
name         := "sbt-apicurio-example"
organization := "org.scalateams.example"
version      := "0.1.0-SNAPSHOT"

scalaVersion := "3.8.4"

// Enable the Apicurio plugin
enablePlugins(ApicurioPlugin)

// Required settings (component-based URL)
apicurioRegistryHost := sys.env.getOrElse("APICURIO_HOST", "localhost")
apicurioRegistryScheme := "http"
apicurioRegistryPort := Some(8080)
apicurioGroupId := "com.example.myservice"

// Optional Keycloak OAuth2 authentication (unauthenticated by default)
apicurioKeycloakConfig := None
// apicurioKeycloakConfig := Some(keycloak(
//   url = sys.env.getOrElse("KEYCLOAK_URL", ""),
//   realm = sys.env.getOrElse("KEYCLOAK_REALM", ""),
//   clientId = sys.env.getOrElse("KEYCLOAK_CLIENT_ID", ""),
//   clientSecret = sys.env.getOrElse("KEYCLOAK_CLIENT_SECRET", "")
// ))

apicurioCompatibilityLevel := CompatibilityLevel.Backward

// Schema paths (default is fine; shown for illustration)
apicurioSchemaPaths := Seq(
  sourceDirectory.value / "main" / "schemas"
)

// Pull dependencies from other services
apicurioPullDependencies := Seq(
  // schema("com.example.catalog", "CatalogItemCreated", "latest"),
  // schema("com.example.order", "OrderPlaced", "latest")
)

// Optional: recursively pull transitive dependencies
// apicurioPullRecursive := true
```

- [ ] **Step 3: Verify the example loads and validates against the local plugin**

Run: `sbt -Dsbt.rootdir=example` is not used; instead run from the example dir:
`cd example && sbt "apicurioValidateSettings" ; cd -`
Expected: the example build loads on sbt 2.0.3, resolves the plugin from the local source dependency, and `apicurioValidateSettings` prints a valid configuration (host `localhost`, group `com.example.myservice`).

- [ ] **Step 4: Commit**

```bash
git add example/build.sbt example/project/build.properties
git commit -m "docs: update example project for sbt 2.x and current plugin API"
```

---

## Task 7: CI, release, and scala-steward config

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `.scala-steward.conf`

**Interfaces:** none (CI/config only).

- [ ] **Step 1: Update ci.yml to Scala 3.8.4 / JDK 17**

In `.github/workflows/ci.yml`, in the `test` job's `strategy.matrix`, replace:
```yaml
        scala: [2.12.19]
        java: [11, 17]
```
with:
```yaml
        scala: [3.8.4]
        java: [17]
```
Leave the `++${{ matrix.scala }}` compile/test/scalafmt steps as they are (they parameterize on the matrix). If the scalafmt CLI fallback was chosen in Task 1, replace the `sbt ++${{ matrix.scala }} scalafmtCheckAll || true` step with the CLI check:
```yaml
      - name: Check formatting
        run: cs launch scalafmt:3.7.17 -- --test --config .scalafmt.conf $(git ls-files '*.scala' '*.sbt')
```

- [ ] **Step 2: Update release.yml JDK**

In `.github/workflows/release.yml`, change `java-version: 11` to `java-version: 17`. Leave `sbt ci-release` and the Central Portal / PGP env unchanged.

- [ ] **Step 3: Drop stale scala-steward pins**

In `.scala-steward.conf`, remove the `updates.ignore` entry that pins `scala-library` to 2.13 and the associated "Keep SBT plugin on 2.12.x" comment. Leave the circe grouping pin and other settings intact.

- [ ] **Step 4: Validate YAML locally**

Run: `python3 -c "import yaml,sys; [yaml.safe_load(open(f)) for f in ['.github/workflows/ci.yml','.github/workflows/release.yml']]; print('yaml ok')"`
Expected: `yaml ok`.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci.yml .github/workflows/release.yml .scala-steward.conf
git commit -m "ci: build on Scala 3.8.4 / JDK 17; drop sbt 1.x pins"
```

---

## Task 8: README, CHANGELOG, and publishLocal smoke test

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`

**Interfaces:** none.

- [ ] **Step 1: Update README installation/prerequisites**

In `README.md`:
- Under Installation, keep `addSbtPlugin("org.scalateams" % "sbt-apicurio" % "<version>")` but add a prerequisites line directly above it:
  `> Requires **sbt 2.x** and **JDK 17+**. (sbt 1.x users: use the 0.3.x line, the final sbt 1.x release.)`
- Update the snapshot-resolver example version placeholder to the 1.0.0 line (`1.0.0+<commits>-<hash>-SNAPSHOT`).

- [ ] **Step 2: Add the 1.0.0 CHANGELOG entry**

At the top of `CHANGELOG.md` (below the header, above `## [0.2.0]`), add:

```markdown
## [1.0.0] - 2026-07-20

### Changed
- **Migrated to sbt 2.x (sbt 2.0.3) and Scala 3 (3.8.4).** The plugin now
  publishes for sbt 2.x only; sbt 1.x is no longer supported (use the 0.3.x
  line for sbt 1.x). Minimum JDK is now 17.
- Source rewritten in idiomatic Scala 3: ADTs are `enum`s, circe codecs are
  `given`s, model re-exports use `export`.
- Effectful tasks are opted out of sbt 2's default task caching via
  `Def.uncached`.
- Behavior is otherwise unchanged (Apicurio 3.x REST integration, semantic
  version ordering, schema reference detection and ordering, Keycloak OAuth2,
  stale-pin warning).
```

- [ ] **Step 3: publishLocal smoke test**

Run: `sbt publishLocal`
Expected: `[success]`. Confirm the artifact publishes under the sbt 2.0 / Scala 3 cross path:
Run: `ls ~/.ivy2/local/com.scalateams/sbt-apicurio/ 2>/dev/null || find ~/.ivy2 -path '*sbt-apicurio*' -name '*.pom' 2>/dev/null | head`
Expected: a path containing `scala_3.*/sbt_2.0/` (confirming the 2.x/Scala-3 artifact), and a `.pom` whose `<name>` is `SBT Apicurio Plugin`.

- [ ] **Step 4: Consume the local 1.0.0 from the example**

Run: `cd example && sbt "apicurioHelp" ; cd -`
Expected: the example (on sbt 2.0.3) loads the plugin and prints the help banner. (The example uses a source dependency, so this also confirms the plugin builds cleanly as a dependency.)

- [ ] **Step 5: Commit**

```bash
git add README.md CHANGELOG.md
git commit -m "docs: document sbt 2.x / 1.0.0 release"
```

---

## Task 9: Clear Scala 3 deprecation warnings in test sources (added during execution)

> Added during execution: a clean rebuild surfaced ~71 Scala 3 deprecation warnings in `ApicurioIntegrationSpec.scala`, all from `_` existential type wildcards in ScalaTest matchers (`a[Left[_, _]]` / `a[Right[_, _]]`, 35 occurrences). Scala 3 deprecates `_` for type wildcards in favor of `?`. This clears them so `sbt test` / CI output is pristine. Behavior-preserving.

**Files:**
- Modify: `src/test/scala/org/scalateams/sbt/apicurio/ApicurioIntegrationSpec.scala`

- [ ] **Step 1:** Replace every `Left[_, _]` with `Left[?, ?]` and every `Right[_, _]` with `Right[?, ?]` in the file (all 35 are inside `shouldBe a[...]` matchers). Change nothing else — no assertion, matcher kind, or test data.
- [ ] **Step 2:** `sbt Test/compile` — confirm the deprecation warnings from those sites are gone and no new warnings appear.
- [ ] **Step 3:** Run the unit tests: `sbt "testOnly * -- -l org.scalateams.sbt.apicurio.IntegrationTest"` — same pass/skip result as before.
- [ ] **Step 4:** Format via CLI: `cs launch scalafmt:3.7.17 -- --config .scalafmt.conf src/test/scala/org/scalateams/sbt/apicurio/ApicurioIntegrationSpec.scala`, then commit: `style: use Scala 3 ? wildcards in test matchers (clear deprecation warnings)`.

---

## Post-plan (requires explicit user approval — not part of task execution)

Tagging `v1.0.0` and pushing triggers `release.yml` → Maven Central. Per the repo branch-safety rule, do **not** tag or push without the user explicitly saying so.

## Self-review notes

- Spec coverage: build approach (Task 1), Scala 3 rewrite incl. enums/given/export (Task 2), `Def.uncached` (Task 3), tests (Task 4), scalafmt dialect (Tasks 1+5), example fix (Task 6), CI/release/scala-steward (Task 7), README/CHANGELOG/publishLocal/example consumption (Task 8), 1.0.0 version + JDK 17 + sbt 2.0.3 (Global Constraints + Tasks 1/7/8). Stale-pin behavior preserved (Task 2, unchanged logic).
- Genuinely-uncertain APIs are flagged with a verify-then-branch step, not left as placeholders: `sbt-scalafmt` sbt-2 availability (Task 1 Step 6 fallback), `Def.uncached` exact form (Task 3 Step 2), `sbt.util.Logger` abstract surface (Task 4 Step 2).
- Type consistency: enum/case-class names and companion method signatures in Task 2's Interfaces are referenced consistently by Tasks 3, 4, 6, 8.
