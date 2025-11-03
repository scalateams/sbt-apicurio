# Schema References

The sbt-apicurio plugin automatically detects and handles schema references (nested/dependent schemas) across all supported schema types.

## Overview

When schemas reference other schemas, the plugin:

1. **Detects** references automatically by parsing schema content
2. **Orders** schemas by dependencies (publishes dependencies first)
3. **Includes** reference metadata when publishing to Apicurio
4. **Validates** circular dependencies
5. **Logs** dependency relationships for visibility

This ensures schemas are published in the correct order and that Apicurio Registry can properly resolve references.

## Supported Reference Types

### Avro Schema References

Avro schemas can reference other record types:

```json
// ProductCategory.avsc
{
  "type": "record",
  "name": "ProductCategory",
  "namespace": "com.example.catalog",
  "fields": [
    {"name": "id", "type": "string"},
    {"name": "name", "type": "string"}
  ]
}
```

```json
// Product.avsc - References ProductCategory
{
  "type": "record",
  "name": "Product",
  "namespace": "com.example.catalog",
  "fields": [
    {"name": "id", "type": "string"},
    {"name": "name", "type": "string"},
    {"name": "category", "type": "ProductCategory"}  // Reference
  ]
}
```

**Detected references:**
- Record type fields
- Array item types
- Union types
- Map value types

### JSON Schema References

JSON Schemas use `$ref` to reference other schemas:

```json
// Address.json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "street": {"type": "string"},
    "city": {"type": "string"},
    "zipCode": {"type": "string"}
  }
}
```

```json
// Customer.json - References Address
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "id": {"type": "string"},
    "name": {"type": "string"},
    "address": {
      "$ref": "apicurio://com.example.customer/Address"  // Reference
    }
  }
}
```

**Detected references:**
- `$ref` with `apicurio://` URIs
- `$ref` with HTTP/HTTPS URLs
- `$ref` with schema paths

**Reference URI formats:**
- `apicurio://groupId/artifactId` - Latest version
- `apicurio://groupId/artifactId/versions/3` - Specific version
- `https://registry.example.com/schemas/Address` - External URL

### Protobuf References

Protobuf files use `import` statements:

```protobuf
// timestamp.proto
syntax = "proto3";
package com.example.common;

message Timestamp {
  int64 seconds = 1;
  int32 nanos = 2;
}
```

```protobuf
// event.proto - Imports timestamp.proto
syntax = "proto3";
package com.example.events;

import "timestamp.proto";  // Reference

message Event {
  string id = 1;
  string name = 2;
  Timestamp created_at = 3;  // Uses imported type
}
```

**Detected references:**
- `import "path/to/schema.proto";` statements

## How It Works

### 1. Automatic Detection

When you run `sbt apicurioPublish`, the plugin:

```scala
// Example schemas in src/main/schemas/
src/main/schemas/
├── ProductCategory.avsc   // No dependencies
├── Product.avsc           // Depends on ProductCategory
└── Order.avsc             // Depends on Product
```

**Plugin detects:**
```
ProductCategory.avsc → (no references)
Product.avsc → references ProductCategory
Order.avsc → references Product
```

### 2. Dependency Ordering

The plugin uses topological sort to determine publish order:

```
Publishing in dependency order: ProductCategory → Product → Order
```

This ensures dependencies are published before schemas that depend on them.

### 3. Reference Metadata

When publishing, the plugin includes reference information in the Apicurio API request:

```json
{
  "artifactId": "Product",
  "artifactType": "AVRO",
  "firstVersion": {
    "content": {
      "content": "{ ... schema content ... }",
      "references": [
        {
          "groupId": "com.example.catalog",
          "artifactId": "ProductCategory",
          "name": "ProductCategory"
        }
      ]
    }
  }
}
```

Apicurio Registry uses this metadata to:
- Validate references exist
- Provide schema resolution
- Enable compatibility checking across referenced schemas

## Example Output

### Without References

```bash
$ sbt apicurioPublish

Publishing 3 schemas to Apicurio Registry
Group ID: com.example.catalog
Compatibility Level: BACKWARD
✓ Created: ProductCategory (AVRO) version 1
✓ Created: Product (AVRO) version 1
✓ Created: Order (AVRO) version 1

Publishing Summary:
  ✓ Published:  3 schema(s)
```

### With References

```bash
$ sbt apicurioPublish

Publishing 3 schemas to Apicurio Registry
Group ID: com.example.catalog
Compatibility Level: BACKWARD
Schema dependencies detected:
  Product depends on: ProductCategory
  Order depends on: Product
Publishing in dependency order: ProductCategory → Product → Order
✓ Created: ProductCategory (AVRO) version 1
✓ Created: Product (AVRO) version 1
✓ Created: Order (AVRO) version 1

Publishing Summary:
  ✓ Published:  3 schema(s)
```

## Error Handling

### Circular Dependencies

The plugin detects circular dependencies and reports an error:

```bash
[error] Circular dependency detected in schemas: Product, Order
[error] Cannot publish schemas with circular dependencies
```

**Fix:** Refactor schemas to remove circular references.

### Missing Dependencies

If a schema references an artifact that doesn't exist in the batch or registry:

```bash
[warn] External reference: ProductCategory -> ProductCategory
[warn] Ensure ProductCategory exists in registry before publishing dependent schemas
```

**Solutions:**
1. Add the missing schema to your `src/main/schemas/` directory
2. Pull it as a dependency: `apicurioPullDependencies += schema("com.example", "ProductCategory", "latest")`
3. Publish it separately first

## Best Practices

### 1. Organize Schemas by Dependencies

```
src/main/schemas/
├── common/              # Base schemas with no dependencies
│   ├── Address.avsc
│   └── Timestamp.avsc
├── entities/            # Entity schemas that may reference common
│   ├── Customer.avsc    # References Address
│   └── Product.avsc
└── events/              # Event schemas that reference entities
    ├── CustomerCreated.avsc  # References Customer
    └── OrderPlaced.avsc       # References Product
```

The plugin will discover and order them correctly regardless of directory structure.

### 2. Use Fully Qualified Names

**Avro:**
```json
{
  "type": "record",
  "name": "Product",
  "namespace": "com.example.catalog",  // Always include namespace
  "fields": [
    {"name": "category", "type": "com.example.catalog.ProductCategory"}  // Fully qualified
  ]
}
```

**JSON Schema:**
```json
{
  "address": {
    "$ref": "apicurio://com.example.customer/Address"  // Include groupId
  }
}
```

**Protobuf:**
```protobuf
import "com/example/common/timestamp.proto";  // Full path
```

### 3. Version Dependencies Explicitly

When pulling external dependencies, specify versions:

```scala
apicurioPullDependencies := Seq(
  schema("com.example.common", "Address", "2"),  // Pin to version 2
  schema("com.example.common", "Timestamp", "1")
)
```

This ensures consistent builds and prevents breaking changes.

### 4. Test Dependency Changes

When updating a schema that other schemas depend on:

1. Check compatibility: `sbt apicurioPublish` will validate
2. Update dependent schemas if needed
3. Publish all together in a single batch

The plugin ensures they're published in the correct order.

## Troubleshooting

### "Could not resolve artifact ID for reference"

```
[warn] Could not resolve artifact ID for reference: com.example.Unknown
```

**Cause:** The plugin couldn't extract an artifact ID from the reference.

**Fix:** Ensure references use clear, parseable formats:
- Avro: Use the type name directly
- JSON Schema: Use `apicurio://groupId/artifactId` URIs
- Protobuf: Use clear import paths

### "Publishing in discovery order (may fail if dependencies not published)"

```
[warn] Could not order schemas by dependencies: Circular dependency detected
[warn] Publishing in discovery order (may fail if dependencies not published)
```

**Cause:** Circular dependencies or complex dependency graph.

**Fix:**
1. Check for circular references between schemas
2. Ensure all referenced schemas are present
3. Consider breaking circular dependencies

### References Not Resolved in Apicurio UI

If you see "unresolved reference" warnings in Apicurio's UI:

1. Verify the referenced schema exists: `curl https://registry.example.com/apis/registry/v3/groups/com.example/artifacts/ProductCategory`
2. Check the reference format in your schema matches Apicurio's expectations
3. Ensure groupId matches between referencing and referenced schemas

## Advanced: External References

If you need to reference schemas from other groups or services:

```scala
// build.sbt
apicurioPullDependencies := Seq(
  // Pull shared schemas from common service
  schema("com.example.common", "Address", "latest"),
  schema("com.example.common", "Timestamp", "latest")
)
```

Then reference them in your schemas:

```json
{
  "type": "record",
  "name": "Customer",
  "namespace": "com.example.customer",
  "fields": [
    {
      "name": "address",
      "type": "com.example.common.Address"  // From pulled dependency
    }
  ]
}
```

The plugin will:
1. Pull the dependencies before compilation
2. Detect the reference when publishing
3. Include the reference metadata with correct groupId

## API Details

### ContentReference Structure

When publishing, references are sent to Apicurio as:

```json
{
  "groupId": "com.example.catalog",      // Optional, defaults to current group
  "artifactId": "ProductCategory",       // Required
  "version": "2",                        // Optional, defaults to latest
  "name": "ProductCategory"              // Required, reference identifier
}
```

### Apicurio Registry 3.x API

The plugin uses Apicurio's reference API:
- `POST /groups/{groupId}/artifacts` - Create with references
- `POST /groups/{groupId}/artifacts/{artifactId}/versions` - Create version with references

References are included in the `content.references` field of the request body.

## See Also

- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Avro Schema Resolution](https://avro.apache.org/docs/current/spec.html#Schema+Resolution)
- [JSON Schema $ref](https://json-schema.org/understanding-json-schema/structuring.html#ref)
- [Protobuf Imports](https://developers.google.com/protocol-buffers/docs/proto3#importing-definitions)
