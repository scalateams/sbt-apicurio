# sbt-apicurio

[![CI](https://github.com/scalateams/sbt-apicurio/workflows/CI/badge.svg)](https://github.com/scalateams/sbt-apicurio/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

An SBT AutoPlugin for integrating with [Apicurio Schema Registry 3.x](https://www.apicur.io/registry/) during the build cycle. This plugin allows you to publish schemas to Apicurio Registry and pull external schemas at build time.

## Features

- **Schema Publishing**: Automatically detect and publish changed schemas to Apicurio Registry
- **Schema References**: Automatically detect and handle nested/dependent schemas across all formats
- **Dependency Ordering**: Publish schemas in correct dependency order (dependencies first)
- **Change Detection**: Hash-based comparison to only publish when schemas have changed
- **Compatibility Checking**: Validate schema compatibility before publishing
- **Schema Dependencies**: Pull schemas from registry before compilation
- **Multi-Format Support**: Avro, JSON Schema, Protobuf, OpenAPI, AsyncAPI
- **API Key Authentication**: Secure authentication with Apicurio Registry

## Installation

This plugin is published to Maven Central. Add it to your `project/plugins.sbt`:

```scala
addSbtPlugin("org.scalateams" % "sbt-apicurio" % "<version>")
```

[![Maven Central](https://img.shields.io/maven-central/v/org.scalateams/sbt-apicurio.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.scalateams%22%20AND%20a:%22sbt-apicurio%22)

Check [Maven Central](https://search.maven.org/search?q=g:%22org.scalateams%22%20AND%20a:%22sbt-apicurio%22) for the latest version.

### Snapshot Releases

Snapshot versions are published on every commit to `main` branch:

```scala
resolvers += Resolver.sonatypeRepo("snapshots")
addSbtPlugin("org.scalateams" % "sbt-apicurio" % "0.1.0+<commits>-<hash>-SNAPSHOT")
```

## Quick Start

### Enable the plugin

In your `build.sbt`:

```scala
enablePlugins(ApicurioPlugin)
```

### Getting Help

Get comprehensive usage information anytime:

```bash
sbt apicurioHelp
```

This displays:
- All available tasks and settings
- Configuration examples
- Supported schema types
- Common workflows
- Your current configuration status

See [HELP_FEATURE.md](HELP_FEATURE.md) for details.

### Configure required settings

```scala
// Required settings
apicurioRegistryUrl := "https://your-registry.example.com"
apicurioGroupId := "com.example.myservice"

// Optional: API key for authentication
apicurioApiKey := Some(sys.env.getOrElse("APICURIO_API_KEY", ""))
```

### Place your schemas

By default, the plugin looks for schemas in `src/main/schemas/`:

```
src/main/schemas/
├── UserCreated.avsc
├── OrderPlaced.avsc
└── ProductUpdated.json
```

### Publish schemas

```bash
sbt apicurioPublish
```

## Configuration

### Required Settings

| Setting | Type | Description |
|---------|------|-------------|
| `apicurioRegistryUrl` | `String` | URL of your Apicurio Registry instance |
| `apicurioGroupId` | `String` | Group ID for your schemas (e.g., `com.example.myservice`) |

### Optional Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `apicurioApiKey` | `Option[String]` | `None` | API key for registry authentication |
| `apicurioCompatibilityLevel` | `CompatibilityLevel` | `BACKWARD` | Schema compatibility level |
| `apicurioSchemaPaths` | `Seq[File]` | `src/main/schemas` | Directories containing schema files |
| `apicurioPullOutputDir` | `File` | `target/schemas` | Output directory for pulled schemas |
| `apicurioPullDependencies` | `Seq[ApicurioDependency]` | `Seq.empty` | External schemas to pull |

### Compatibility Levels

```scala
apicurioCompatibilityLevel := CompatibilityLevel.Backward           // Default
apicurioCompatibilityLevel := CompatibilityLevel.BackwardTransitive
apicurioCompatibilityLevel := CompatibilityLevel.Forward
apicurioCompatibilityLevel := CompatibilityLevel.ForwardTransitive
apicurioCompatibilityLevel := CompatibilityLevel.Full
apicurioCompatibilityLevel := CompatibilityLevel.FullTransitive
apicurioCompatibilityLevel := CompatibilityLevel.None
```

## Usage

### Publishing Schemas

The `apicurioPublish` task discovers and publishes schemas to the registry:

```bash
sbt apicurioPublish
```

**How it works:**
1. Discovers all schema files in configured paths
2. Computes hash of each schema
3. Compares with latest version in registry
4. Checks compatibility if schema has changed
5. Creates new artifact or version if needed
6. Skips unchanged schemas

**Integration with CI/CD:**

```scala
// In build.sbt
val publishSchemas = taskKey[Unit]("Publish schemas in CI")

publishSchemas := {
  if (sys.env.contains("CI")) {
    apicurioPublish.value
  } else {
    streams.value.log.info("Skipping schema publish outside CI")
  }
}

// Add to your release/deploy task
publish := {
  publishSchemas.value
  publish.value
}
```

### Pulling Schema Dependencies

Declare dependencies on schemas from other services using the `schema` helper method:

```scala
apicurioPullDependencies := Seq(
  schema("com.example.catalog", "CatalogItemCreated", "latest"),
  schema("com.example.catalog", "CatalogItemUpdated", "latest"),
  schema("com.example.tenant", "TenantCreated", "3"),
  schema("com.example.pricing", "PriceChanged", "2")
)
```

**Note:** The `schema` helper method is used instead of the `%` operator to avoid conflicts with SBT's built-in library dependency operators.

Schemas are automatically pulled before compilation starts:

```bash
sbt compile  # Automatically pulls dependencies first
```

**Manual pull:**

```bash
sbt apicurioPull
```

Downloaded schemas are saved to `target/schemas` (or configured output directory) organized by group:

```
target/schemas/
├── com/example/catalog/
│   ├── CatalogItemCreated.json
│   └── CatalogItemUpdated.json
└── com/example/tenant/
    └── TenantCreated.json
```

### Custom Schema Locations

```scala
apicurioSchemaPaths := Seq(
  sourceDirectory.value / "main" / "schemas",
  sourceDirectory.value / "main" / "events",
  baseDirectory.value / "custom-schemas"
)
```

### Custom Pull Output Directory

```scala
apicurioPullOutputDir := sourceDirectory.value / "main" / "external-schemas"
```

## Schema File Organization

### Artifact Mapping

- **Group ID**: Explicitly configured via `apicurioGroupId`
- **Artifact ID**: Derived from filename (without extension)
  - `UserCreated.avsc` → artifact ID: `UserCreated`
  - `order-placed.json` → artifact ID: `order-placed`
- **Version**: Auto-incremented by Apicurio Registry

### Supported File Types

| Extension | Artifact Type | Description |
|-----------|---------------|-------------|
| `.avsc`, `.avro` | `AVRO` | Avro schema |
| `.proto` | `PROTOBUF` | Protocol Buffers |
| `.json` | `JSON` | JSON Schema |
| `.yaml`, `.yml` | `OPENAPI`/`ASYNCAPI` | OpenAPI or AsyncAPI |

## Tasks

| Task | Description |
|------|-------------|
| `apicurioHelp` | Display comprehensive help and usage information |
| `apicurioPublish` | Publish schemas to Apicurio Registry |
| `apicurioPull` | Pull schema dependencies from registry |
| `apicurioDiscoverSchemas` | Discover and list all schema files |
| `apicurioValidateSettings` | Validate plugin configuration |

## Schema References (Nested Schemas)

The plugin automatically detects and handles schema references for nested/dependent schemas:

```scala
// Schemas with dependencies
src/main/schemas/
├── Address.avsc         // No dependencies
├── Customer.avsc        // References Address
└── Order.avsc           // References Customer
```

**What the plugin does:**

1. **Detects references** in Avro, JSON Schema, and Protobuf schemas
2. **Orders schemas** by dependencies (publishes Address → Customer → Order)
3. **Includes reference metadata** in Apicurio API requests
4. **Validates** for circular dependencies

**Output:**

```bash
$ sbt apicurioPublish

Schema dependencies detected:
  Customer depends on: Address
  Order depends on: Customer
Publishing in dependency order: Address → Customer → Order
✓ Created: Address (AVRO) version 1
✓ Created: Customer (AVRO) version 1
✓ Created: Order (AVRO) version 1
```

**Reference formats:**

- **Avro**: Record type references in fields
- **JSON Schema**: `$ref` with `apicurio://groupId/artifactId` URIs
- **Protobuf**: `import "path/to/schema.proto"` statements

See [SCHEMA_REFERENCES.md](SCHEMA_REFERENCES.md) for complete documentation.

## Complete Example

```scala
// build.sbt
name := "my-service"
organization := "com.example"

enablePlugins(ApicurioPlugin)

// Required Apicurio settings
apicurioRegistryUrl := "https://registry.example.com"
apicurioGroupId := "com.example.myservice"
apicurioApiKey := sys.env.get("APICURIO_API_KEY")

// Optional settings
apicurioCompatibilityLevel := CompatibilityLevel.Backward
apicurioSchemaPaths := Seq(
  sourceDirectory.value / "main" / "schemas"
)

// Pull dependencies from other services using the schema helper
apicurioPullDependencies := Seq(
  schema("com.example.catalog", "CatalogItemCreated", "latest"),
  schema("com.example.order", "OrderPlaced", "latest"),
  schema("com.example.customer", "CustomerUpdated", "5")
)

// Optional: Hook into CI/CD
val publishSchemasInCI = taskKey[Unit]("Publish schemas in CI environment")
publishSchemasInCI := {
  if (sys.env.get("CI").contains("true")) {
    streams.value.log.info("Publishing schemas to Apicurio Registry")
    apicurioPublish.value
  }
}
```

## Environment Variables

You can use environment variables for sensitive configuration:

```scala
apicurioApiKey := sys.env.get("APICURIO_API_KEY")
apicurioRegistryUrl := sys.env.getOrElse("APICURIO_URL", "https://default-registry.example.com")
```

**CI/CD Example:**

```bash
export APICURIO_API_KEY="your-api-key"
export APICURIO_URL="https://registry.prod.example.com"
sbt apicurioPublish
```

## Workflow Examples

### Development Workflow

1. Create or modify schema files in `src/main/schemas/`
2. Test locally: `sbt compile test`
3. Validate schemas: `sbt apicurioValidateSettings`
4. Commit changes to version control
5. CI/CD publishes schemas automatically

### CI/CD Integration

```yaml
# GitHub Actions example
- name: Publish Schemas
  env:
    APICURIO_API_KEY: ${{ secrets.APICURIO_API_KEY }}
    APICURIO_URL: https://registry.prod.example.com
  run: sbt apicurioPublish
```

### Inter-Service Dependencies

**Service A** (produces events):
```scala
// Service A: catalog-svc
apicurioGroupId := "com.example.catalog"
// Schemas: src/main/schemas/CatalogItemCreated.avsc
```

**Service B** (consumes events):
```scala
// Service B: order-svc
apicurioGroupId := "com.example.order"
apicurioPullDependencies := Seq(
  schema("com.example.catalog", "CatalogItemCreated", "latest")
)
// Pulled to: target/schemas/com/example/catalog/CatalogItemCreated.json
```

## Troubleshooting

### "apicurioGroupId is not set"

The plugin requires explicit group ID configuration. Add to your `build.sbt`:

```scala
apicurioGroupId := "com.example.myservice"
```

### "Artifact not found" when pulling

Ensure the artifact exists in the registry and the group/artifact IDs are correct. Check:
- Registry URL is correct
- API key has read permissions
- Artifact was previously published

### Compatibility check failures

If publishing fails due to compatibility:
1. Review your compatibility level setting
2. Check the schema changes against compatibility rules
3. Consider using a less strict compatibility level (e.g., `NONE` for development)

### Schemas not discovered

Check that:
- Schema files have supported extensions (`.avsc`, `.proto`, `.json`, `.yaml`, `.yml`)
- `apicurioSchemaPaths` points to correct directories
- Files are not in `.gitignore` or otherwise excluded

## Development

### Building the plugin

```bash
sbt compile
```

### Testing locally

```bash
sbt publishLocal
```

Then in another project:

```scala
// project/plugins.sbt
addSbtPlugin("org.scalateams" % "sbt-apicurio" % "<version>")
```

## Requirements

- SBT 1.x
- Apicurio Registry 3.x
- Scala 2.12 (for SBT plugin compatibility)

## CI/CD

This project uses modern CI/CD automation:

- **GitHub Actions**: Runs tests on multiple Java versions (11, 17) and handles automated releases
- **Scala Steward**: Automated dependency updates via [@scala-steward](https://github.com/scala-steward-org/scala-steward)
- **Mergify**: Auto-merges dependency updates that pass CI
- **sbt-ci-release**: Automated publishing to Maven Central on git tag push

### Automated Dependency Updates

This project uses [Scala Steward](https://github.com/scala-steward-org/scala-steward) to keep dependencies up to date. Scala Steward will:
- Automatically create pull requests for dependency updates
- Update dependencies weekly
- Group related dependencies together (e.g., all Circe modules)
- Apply custom labels for easy identification

Patch updates that pass CI are automatically merged via Mergify. Minor and major updates require manual review.

## Contributing

Contributions are welcome! Here's how you can help:

### Reporting Issues

- Use the [GitHub issue tracker](https://github.com/scalateams/sbt-apicurio/issues)
- Check if the issue already exists before creating a new one
- Provide detailed reproduction steps and environment information

### Submitting Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests: `sbt test`
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Code Style

- Follow standard Scala conventions
- Use ScalaFmt for formatting (if configured)
- Write tests for new functionality
- Update documentation as needed

### Development Setup

```bash
# Clone the repository
git clone https://github.com/scalateams/sbt-apicurio.git
cd sbt-apicurio

# Build the project
sbt compile

# Run tests
sbt test

# Publish locally for testing
sbt publishLocal
```

## Releases

This project uses automated releases to Maven Central via [sbt-ci-release](https://github.com/sbt/sbt-ci-release).

- **Releases** are triggered by pushing git tags (e.g., `v0.1.0`)
- **Snapshots** are published on every commit to `main`
- Versions follow [Semantic Versioning](https://semver.org/)

For maintainers, see [RELEASE.md](RELEASE.md) for detailed release instructions.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

Copyright 2025 ScalaTeams

## Support

For issues or questions, please open an issue on the [GitHub issue tracker](https://github.com/scalateams/sbt-apicurio/issues).
