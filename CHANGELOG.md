# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- Dropped the `sbt-scalafmt` plugin (it has no sbt-2 artifact) in favor of
  the standalone `scalafmt` CLI via Coursier (`cs launch scalafmt`), invoked
  directly in CI and for local formatting.

### Caveats (sbt 2)
- **`Compile / compile` opts out of sbt 2's action cache** in projects that
  enable this plugin. The pull-before-compile hook redefines `compile`, which
  sbt 2 cannot action-cache; `Def.uncached` is both required to compile and
  necessary so the pull is never skipped by a cache hit. Zinc incremental
  compilation is unaffected; only the local/remote action cache for `compile`
  is disabled.
- **`apicurioPull` runs before every compile**, so with
  `apicurioPullWarnOnStaleVersions` enabled (default) each *pinned* dependency
  costs one registry lookup per compile. Use `"latest"` pins (no lookup) or set
  `apicurioPullWarnOnStaleVersions := false` to avoid this in tight edit/compile
  loops.

---

## [0.2.0] - 2025-11-17

### Added

#### Keycloak OAuth2 Authentication
- **Complete OAuth2 client credentials flow implementation** for production environments where Apicurio Registry is fronted by Keycloak
- **Automatic token management** with intelligent caching and proactive refresh (30 seconds before expiry)
- **Thread-safe token handling** using `AtomicReference` for concurrent build operations
- **Functional error handling** with `ApicurioResult[T]` for token acquisition and refresh
- New `KeycloakTokenManager` class for OAuth2 token lifecycle management
- New `apicurioKeycloakConfig` setting with `keycloak()` helper function
- Comprehensive Keycloak setup documentation:
  - `KEYCLOAK_SETUP.md` - Complete setup guide for NoChannel and local development
  - `KEYCLOAK_CHECKLIST.md` - Step-by-step configuration verification
  - `VERIFIED_WORKING.md` - Working configuration examples and proof

#### Recursive Schema Pulling
- **New `apicurioPullRecursive` setting** to enable automatic transitive dependency resolution
- When enabled, the plugin automatically follows schema references and pulls all dependencies recursively
- Eliminates manual dependency tracking for complex schema hierarchies
- Documented in `SCHEMA_REFERENCES.md` with examples

#### Documentation & Investigation
- **"About This Project" section** in README documenting human-AI collaborative development approach
- **API version investigation documentation** (`API_VERSION_INVESTIGATION.md`) explaining v2 vs v3 API differences
- Enhanced authentication section in README with Keycloak configuration examples
- Updated all examples to use Keycloak OAuth2 instead of deprecated API key

### Changed

#### Breaking Changes
- **Replaced `apicurioApiKey` setting with `apicurioKeycloakConfig`**
  - Old: `apicurioApiKey := Some("your-api-key")`
  - New: `apicurioKeycloakConfig := Some(keycloak(url, realm, clientId, clientSecret))`
- **Authentication is now optional** - plugin works without authentication for local/development registries
- All API calls now use functional error propagation with `ApicurioResult[T]`

#### Migration Guide
```scala
// Before (v0.1.x)
apicurioApiKey := sys.env.get("APICURIO_API_KEY")

// After (v0.2.0)
apicurioKeycloakConfig := {
  for {
    url          <- sys.env.get("KEYCLOAK_URL")
    realm        <- sys.env.get("KEYCLOAK_REALM")
    clientId     <- sys.env.get("KEYCLOAK_CLIENT_ID")
    clientSecret <- sys.env.get("KEYCLOAK_CLIENT_SECRET")
  } yield keycloak(url, realm, clientId, clientSecret)
}
```

#### Internal Improvements
- Refactored `ApicurioClient` constructor to accept `Option[KeycloakConfig]` instead of `Option[String]`
- Updated `authHeaders` method to return `ApicurioResult[Map[String, String]]` for proper error propagation
- All HTTP operations now use `flatMap` composition for error handling
- Enhanced `SchemaFileUtils.validateSettings` to validate Keycloak configuration
- Updated all 32 integration tests to use Keycloak configuration pattern

### Fixed
- Environment variable examples in README now reference correct variable names
- CI/CD examples updated to use Keycloak environment variables
- Configuration examples throughout README now consistent with v0.2.0 API

### Documentation
- Added comprehensive "About This Project" section explaining development approach
- Updated Quick Start guide with Keycloak configuration
- Enhanced Environment Variables section with Keycloak examples
- Updated all code examples to use new authentication pattern
- Added troubleshooting section for Keycloak authentication
- Documented Apicurio Registry v3 requirement

### Technical Details
- **Test Coverage**: 32 integration tests, all passing
- **Authentication**: OAuth2 client credentials flow with automatic refresh
- **Error Handling**: Functional `Either`-based error propagation throughout
- **Thread Safety**: Concurrent token access via `AtomicReference`
- **Token Lifetime**: Configurable refresh buffer (default: 30 seconds before expiry)
- **API Support**: Apicurio Registry 3.x REST API

### Known Issues
- Plugin requires Apicurio Registry 3.x (v2 API not supported)
- Schema references have limited support in Apicurio Registry v2 environments
- For best results, upgrade Apicurio Registry to version 3.x

---

## [0.1.5] - 2025-11-14

### Fixed
- Fixed schema file extensions for downloaded schemas
- Eliminated functional programming anti-patterns in error handling
- Improved Content-Type extraction from HTTP headers

### Documentation
- Documented `apicurioPullRecursive` flag for recursive schema pulling

---

## [0.1.4] - 2025-11-13

### Added
- Recursive transitive dependency resolution for schema pulling
- Extract Content-Type from HTTP response headers

### Changed
- Updated integration test URL for Apicurio Registry 3.x

### Fixed
- Release workflow to only publish tags and mark as latest
- Correct file extensions for downloaded schemas

---

## [0.1.3] - 2025-11-12

### Fixed
- Set correct content type for YAML OpenAPI/AsyncAPI schemas

### Added
- Integration test for YAML OpenAPI content handling

---

## [0.1.2] - 2025-11-11

### Added
- Initial support for Apicurio Registry 3.x
- Schema publishing with change detection
- Schema pulling with dependencies
- Multi-format support (Avro, JSON Schema, Protobuf, OpenAPI, AsyncAPI)
- Compatibility checking
- Schema reference handling

---

[1.0.0]: https://github.com/scalateams/sbt-apicurio/compare/v0.3.1...v1.0.0
[0.2.0]: https://github.com/scalateams/sbt-apicurio/compare/v0.1.5...v0.2.0
[0.1.5]: https://github.com/scalateams/sbt-apicurio/compare/0.1.4...v0.1.5
[0.1.4]: https://github.com/scalateams/sbt-apicurio/compare/v0.1.3...0.1.4
[0.1.3]: https://github.com/scalateams/sbt-apicurio/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/scalateams/sbt-apicurio/releases/tag/v0.1.2
