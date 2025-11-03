# Fix for Apicurio 3.x API Compatibility

## Problem

The plugin was sending artifact creation requests using HTTP headers (`X-Registry-ArtifactId`, `X-Registry-ArtifactType`) with the schema content as the request body. This worked in earlier versions of Apicurio but Apicurio 3.x expects a JSON request body with the artifact metadata and content structured properly.

**Error message:**
```
Failed to create artifact: statusCode: 400, response: {
  "detail":"MissingRequiredParameterException: Request is missing a required parameter: body.artifactType",
  "title":"Request is missing a required parameter: body.artifactType",
  "status":400,
  "name":"MissingRequiredParameterException"
}
```

## Changes Made

### 1. Updated `ApicurioModels.scala`

**CreateArtifactRequest:**
- Added `firstVersion` field containing the initial schema content
- Now includes: `artifactId`, `artifactType`, `firstVersion`, optional `name` and `description`

**CreateVersionRequest:**
- Added `content` field as `io.circe.Json` to hold the parsed schema
- Now includes: `content`, optional `version`, `name`, and `description`

### 2. Updated `ApicurioClient.scala`

**createArtifact method:**
- Now parses the schema content as JSON
- Creates a `CreateArtifactRequest` object with the parsed content
- Sends the request as a proper JSON body instead of using headers
- Request structure:
  ```json
  {
    "artifactId": "TenantState",
    "artifactType": "AVRO",
    "firstVersion": {
      "content": { /* parsed schema JSON */ }
    }
  }
  ```

**createVersion method:**
- Now parses the schema content as JSON
- Creates a `CreateVersionRequest` object with the parsed content
- Sends the request as a proper JSON body
- Request structure:
  ```json
  {
    "content": { /* parsed schema JSON */ }
  }
  ```

## Updated Plugin Version

**New Version:** `0.0.0+1-dc3b6996+20251103-0554-SNAPSHOT`

## What You Need to Do

### 1. Update Plugin Version in Your Service

In your service's `project/plugins.sbt`, update to the new version:

```scala
addSbtPlugin("org.scalateams" % "sbt-apicurio" % "0.0.0+1-dc3b6996+20251103-0554-SNAPSHOT")
```

### 2. Reload SBT

```bash
# In your service directory
sbt reload
```

### 3. Try Publishing Again

```bash
sbt apicurioPublish
```

## Expected Behavior

After the fix, the plugin should:
- Parse your Avro schemas as JSON
- Send properly formatted requests to Apicurio 3.x
- Successfully create artifacts and versions
- Log success messages like:
  ```
  [info] Created artifact: TenantState (AVRO)
  [info] Publishing complete: X published, Y unchanged, 0 failed
  ```

## Troubleshooting

### Schema Not Valid JSON

If you get an error like "Failed to parse schema content as JSON", ensure your schema files are valid JSON. Avro schemas should be valid JSON documents.

### Still Getting 400 Errors

Check:
1. You're using the correct version of the plugin (check `sbt plugins` output)
2. Your Apicurio Registry is version 3.x
3. Your `apicurioRegistryUrl` points to the correct endpoint
4. You have proper authentication if required (`apicurioApiKey`)

### Verify Plugin Loaded

```bash
sbt "show apicurioGroupId"
```

Should display your configured group ID. If not, the plugin may not be loaded correctly.

## Testing the Fix

You can test incrementally:

```bash
# 1. Verify schemas are discovered
sbt apicurioDiscoverSchemas

# 2. Validate configuration
sbt apicurioValidateSettings

# 3. Try publishing
sbt apicurioPublish
```

If you see any new errors, please share the full error output!
