# Help Feature

The sbt-apicurio plugin now includes comprehensive help messaging to guide users.

## apicurioHelp Task

Run `sbt apicurioHelp` to display comprehensive usage information:

```bash
sbt apicurioHelp
```

### What It Shows

1. **Overview** - Brief description of the plugin
2. **Available Tasks** - All plugin tasks with descriptions
3. **Settings** - All configuration options
4. **Supported Schema Types** - File extensions for each schema type
5. **Compatibility Levels** - Available compatibility options
6. **Example Configuration** - Complete build.sbt example
7. **Common Workflows** - Step-by-step usage patterns
8. **Documentation Links** - References to detailed docs
9. **Current Configuration** - Your project's current settings
10. **Configuration Status** - Validation of required settings

### Example Output

```
================================================================================
  sbt-apicurio - Apicurio Schema Registry Plugin
================================================================================

OVERVIEW:
  SBT plugin for managing schemas with Apicurio Registry 3.x

TASKS:
  apicurioHelp             - Display this help message
  apicurioPublish          - Publish schemas to Apicurio Registry
  apicurioPull             - Pull schema dependencies from registry
  apicurioDiscoverSchemas  - Discover all schema files in configured paths
  apicurioValidateSettings - Validate plugin configuration

SETTINGS:
  apicurioRegistryUrl        - Registry URL (required)
  apicurioGroupId            - Artifact group ID (required)
  apicurioApiKey             - API key for authentication (optional)
  apicurioCompatibilityLevel - Schema compatibility level (default: Backward)
  apicurioSchemaPaths        - Schema file directories (default: src/main/schemas)
  apicurioPullOutputDir      - Output directory for pulled schemas (default: target/schemas)
  apicurioPullDependencies   - Schema dependencies to pull

SUPPORTED SCHEMA TYPES:
  • Avro         (.avsc, .avro)
  • JSON Schema  (.json)
  • Protobuf     (.proto)
  • OpenAPI      (.yaml, .yml)
  • AsyncAPI     (.yaml, .yml)

COMPATIBILITY LEVELS:
  • CompatibilityLevel.Backward  - Can read data written with previous schema
  • CompatibilityLevel.Forward   - Previous schema can read data written with new schema
  • CompatibilityLevel.Full      - Both backward and forward compatible
  • CompatibilityLevel.None      - No compatibility checking

EXAMPLE CONFIGURATION:

  // build.sbt
  enablePlugins(ApicurioPlugin)

  apicurioRegistryUrl := "https://your-registry.com"
  apicurioGroupId := "com.example.yourservice"
  apicurioApiKey := sys.env.get("APICURIO_API_KEY")
  apicurioCompatibilityLevel := CompatibilityLevel.Backward

  apicurioSchemaPaths := Seq(
    sourceDirectory.value / "main" / "schemas"
  )

  apicurioPullDependencies := Seq(
    schema("com.example.catalog", "ProductCreated", "latest"),
    schema("com.example.tenant", "TenantCreated", "3")
  )

COMMON WORKFLOWS:

  1. Validate configuration:
     sbt apicurioValidateSettings

  2. Discover schemas:
     sbt apicurioDiscoverSchemas

  3. Publish schemas:
     sbt apicurioPublish

  4. Pull dependencies:
     sbt apicurioPull

  5. Pull dependencies automatically before compile:
     sbt compile

DOCUMENTATION:
  • GitHub:   https://github.com/scalateams/sbt-apicurio
  • README:   Full documentation and examples
  • TESTING:  Testing guide with integration tests
  • RELEASE:  Release process and versioning

CURRENT CONFIGURATION:
  Registry URL:       https://your-registry.com
  Group ID:           com.example.yourservice
  API Key:            [CONFIGURED]
  Compatibility:      BACKWARD
  Schema Paths:       src/main/schemas
  Pull Output Dir:    target/schemas
  Dependencies:       2 configured

  Configured Dependencies:
    • com.example.catalog:ProductCreated:latest
    • com.example.tenant:TenantCreated:3

  Status: ✓ Configuration is valid

================================================================================
```

## Enhanced Task Output

All tasks now provide more helpful output:

### apicurioValidateSettings

Shows configuration status and helpful error messages:

```
Validating Apicurio plugin configuration...
  Registry URL: https://your-registry.com
  Group ID:     com.example.yourservice
  API Key:      [CONFIGURED]
✓ Configuration is valid
  Ready to publish to: https://your-registry.com
  Using group ID: com.example.yourservice
```

If configuration is invalid:

```
✗ Configuration is invalid
  Error: apicurioRegistryUrl is not set

To fix this, add the following to your build.sbt:

  enablePlugins(ApicurioPlugin)
  apicurioRegistryUrl := "https://your-registry.com"
  apicurioGroupId := "com.example.yourservice"

Run 'sbt apicurioHelp' for more information
```

### apicurioDiscoverSchemas

Shows detailed schema discovery results:

```
Discovering schemas in 1 path(s)...
Found 4 schema file(s):
  AVRO: 2 file(s)
    • UserCreated.avsc
    • OrderPlaced.avsc
  JSON: 1 file(s)
    • Product.json
  PROTOBUF: 1 file(s)
    • Message.proto
```

If no schemas found:

```
No schemas found in configured paths: src/main/schemas
Tip: Run 'sbt apicurioHelp' to see configuration options
```

### apicurioPublish

Enhanced publishing feedback with visual indicators:

```
Publishing 4 schemas to Apicurio Registry
Group ID: com.example.yourservice
Compatibility Level: BACKWARD
✓ Created: UserCreated (AVRO) version 1
✓ Updated: OrderPlaced version 2
✓ Created: Product (JSON) version 1
✓ Created: Message (PROTOBUF) version 1

Publishing Summary:
  ✓ Published:  4 schema(s)
  - Unchanged:  0 schema(s)
  ✗ Failed:     0 schema(s)
```

If no schemas found:

```
No schemas found to publish
Tip: Check your apicurioSchemaPaths setting or run 'sbt apicurioDiscoverSchemas'
Default schema path: src/main/schemas
```

### apicurioPull

Shows detailed pull results:

```
Pulling 2 schema dependencies from Apicurio Registry
✓ Pulled: com.example.catalog:ProductCreated:latest
✓ Pulled: com.example.tenant:TenantCreated:3
Successfully pulled 2 schema(s) to target/schemas
```

## Improved Task Descriptions

All tasks and settings now have more descriptive help text visible in `sbt help`:

```bash
# Show help for a specific task
sbt "help apicurioPublish"

# Show help for a setting
sbt "help apicurioRegistryUrl"
```

## Benefits

1. **Self-Documenting** - Users can discover features without reading docs
2. **Configuration Validation** - Shows current settings and validates them
3. **Visual Feedback** - Uses ✓, ✗, - symbols for clear status indicators
4. **Helpful Errors** - Provides actionable solutions when things go wrong
5. **Example-Driven** - Shows real configuration examples
6. **Progressive Disclosure** - Quick help via `apicurioHelp`, detailed help in docs

## Integration with SBT

The plugin integrates with SBT's built-in help system:

```bash
# List all tasks
sbt tasks

# Show plugin tasks only
sbt tasks | grep apicurio

# Get detailed help for a task
sbt "help apicurioHelp"
```

## First-Time User Experience

For new users who haven't configured the plugin yet:

```bash
# Add plugin to project/plugins.sbt
addSbtPlugin("org.scalateams" % "sbt-apicurio" % "0.1.0")

# Enable in build.sbt
enablePlugins(ApicurioPlugin)

# Get help
sbt apicurioHelp
```

The help task will show:

- All available configuration options
- Example configuration
- Common workflows
- Current status (showing what's missing)

## See Also

- [README.md](README.md) - Full documentation
- [LOCAL_USAGE.md](LOCAL_USAGE.md) - Local development guide
- [TESTING.md](TESTING.md) - Testing guide
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
