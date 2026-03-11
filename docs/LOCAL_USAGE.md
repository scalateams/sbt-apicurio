# Using the Plugin Locally

The plugin has been published to your local Ivy repository. Here's how to use it in another service:

## Published Version

**Version:** `0.0.0+1-dc3b6996+20251103-0629-SNAPSHOT` *(fully tested with all schema types)*

**Location:** `~/.ivy2/local/org.scalateams/sbt-apicurio/scala_2.12/sbt_1.0/0.0.0+1-dc3b6996+20251103-0629-SNAPSHOT/`

## What's New in This Version

✅ Correct artifact/version metadata separation per Apicurio 3.x spec
✅ Proper JSON request body structure
✅ Support for Protobuf schemas (non-JSON content)
✅ Comprehensive integration tests for all schema types
✅ Verified against Apicurio Registry 3.0.x

## Setup in Your Service

### 1. Add Plugin to `project/plugins.sbt`

```scala
addSbtPlugin("org.scalateams" % "sbt-apicurio" % "0.0.0+1-dc3b6996+20251103-0629-SNAPSHOT")
```

**IMPORTANT:**
- If you were using a previous version, update the version number in your `project/plugins.sbt`
- This version includes comprehensive test coverage
- Run `sbt reload` after updating
- See `METADATA_FIX.md` for details about metadata fixes
- See `TESTING.md` for information about the test suite

### 2. Enable Plugin in `build.sbt`

```scala
enablePlugins(ApicurioPlugin)

// Required settings
apicurioRegistryUrl := "https://your-apicurio-registry.com"
apicurioGroupId := "com.example.yourservice"

// Optional: API key for authentication
apicurioApiKey := sys.env.get("APICURIO_API_KEY")

// Optional: Schema paths (default is src/main/schemas)
apicurioSchemaPaths := Seq(
  sourceDirectory.value / "main" / "schemas"
)

// Optional: Pull external schema dependencies
apicurioPullDependencies := Seq(
  schema("com.example.catalog", "CatalogItemCreated", "latest"),
  schema("com.example.tenant", "TenantCreated", "3")
)
```

### 3. Create Schema Files

Place your Avro/Protobuf/JSON schemas in `src/main/schemas/`:

```
src/main/schemas/
├── UserCreated.avsc
├── OrderPlaced.avsc
└── ProductUpdated.json
```

## Available Tasks

```bash
# Publish schemas to Apicurio Registry
sbt apicurioPublish

# Pull schema dependencies from registry
sbt apicurioPull

# Discover all schema files
sbt apicurioDiscoverSchemas

# Validate plugin settings
sbt apicurioValidateSettings

# Build (automatically pulls dependencies)
sbt compile
```

## Example Configuration

```scala
// build.sbt
name := "my-service"
organization := "com.example"

enablePlugins(ApicurioPlugin)

// Apicurio configuration
apicurioRegistryUrl := sys.env.getOrElse("APICURIO_URL", "http://localhost:8080")
apicurioGroupId := "com.example.myservice"
apicurioApiKey := sys.env.get("APICURIO_API_KEY")
apicurioCompatibilityLevel := CompatibilityLevel.Backward

// Pull schemas from other services
apicurioPullDependencies := Seq(
  schema("com.example.catalog", "CatalogItemCreated", "latest"),
  schema("com.example.pricing", "PriceChanged", "latest")
)
```

## Environment Variables

```bash
# Optional: Set Apicurio URL
export APICURIO_URL="https://registry.example.com"

# Optional: Set API key
export APICURIO_API_KEY="your-api-key"

# Run commands
sbt compile
sbt apicurioPublish
```

## Troubleshooting

### Plugin Not Found

If SBT can't find the plugin, ensure it's in your local Ivy repository:

```bash
ls ~/.ivy2/local/org.scalateams/sbt-apicurio/scala_2.12/sbt_1.0/
```

### Republish if Needed

From the plugin directory:

```bash
cd /Users/jwgcooke/upstart/nochannel/sbt-apicurio
sbt publishLocal
```

### Clear SBT Cache

If you're having issues after updates:

```bash
# From your service directory
rm -rf project/target
rm -rf target
sbt clean reload
```

## Testing the Plugin

1. Create a test service or use an existing one
2. Add the plugin configuration
3. Create a simple schema file
4. Run `sbt apicurioDiscoverSchemas` to verify detection
5. Configure your Apicurio Registry URL
6. Run `sbt apicurioValidateSettings` to verify configuration
7. Run `sbt apicurioPublish` to publish schemas (if registry is available)

## Notes

- The plugin automatically pulls schema dependencies before compilation
- Schemas are cached in `target/schemas`
- The plugin uses hash-based change detection to avoid unnecessary publishes
- Compatibility checking is performed before publishing new versions

## Testing

This plugin includes comprehensive integration tests. See `TESTING.md` for details.

### Quick Test

To verify the plugin works with your Apicurio Registry:

```bash
# 1. Start Apicurio (if not already running)
docker run -p 8080:8080 apicurio/apicurio-registry:3.0.0

# 2. In the plugin directory, run tests
export APICURIO_TEST_URL="http://localhost:8080"
sbt test

# Or run only integration tests
sbt "testOnly *ApicurioIntegrationSpec"
```

Tests publish and pull schemas for:
- Avro (.avsc)
- JSON Schema (.json)
- Protobuf (.proto)
- OpenAPI (.yaml)

All tests use group `com.example` to keep test data separate.

## Supported Schema Types

| Type | Extension | Example |
|------|-----------|---------|
| Avro | `.avsc`, `.avro` | Event schemas, data records |
| JSON Schema | `.json` | API request/response schemas |
| Protobuf | `.proto` | gRPC message definitions |
| OpenAPI | `.yaml`, `.yml` | REST API specifications |
| AsyncAPI | `.yaml`, `.yml` | Event-driven API specs |

## Documentation

- `README.md` - General plugin documentation
- `TESTING.md` - Comprehensive testing guide
- `METADATA_FIX.md` - Details about metadata model fixes
- `API_FIX_VERIFIED.md` - API structure verification
- `RELEASE.md` - Release process for maintainers
- `CONTRIBUTING.md` - Contribution guidelines
