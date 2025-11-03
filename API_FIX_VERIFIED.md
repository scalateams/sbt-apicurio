# Apicurio 3.x API Fix - Verified Against Official Documentation

## Problem

The plugin was not sending the correct JSON structure expected by Apicurio Registry 3.x API.

**Error:** `MissingRequiredParameterException: Request is missing a required parameter: body.firstVersion.content.content`

## Root Cause

According to the official Apicurio Registry 3.x documentation, the create artifact API expects a nested structure where the schema content is provided as a **JSON string**, not a parsed JSON object.

## Correct API Structure (from Apicurio 3.x Docs)

### Creating an Artifact

**Endpoint:** `POST /groups/{groupId}/artifacts`

**Request Body:**
```json
{
  "artifactId": "share-price",
  "artifactType": "AVRO",
  "firstVersion": {
    "version": "1",
    "content": {
      "content": "<schema content as JSON string>",
      "contentType": "application/json",
      "references": []
    }
  }
}
```

### Creating a Version

**Endpoint:** `POST /groups/{groupId}/artifacts/{artifactId}/versions`

**Request Body:**
```json
{
  "version": "2",
  "content": {
    "content": "<schema content as JSON string>",
    "contentType": "application/json",
    "references": []
  }
}
```

## Key Points

1. **content is a string**: The actual schema must be sent as a JSON string, not parsed JSON
2. **Nested structure**: `firstVersion.content.content` - three levels of nesting
3. **contentType**: Must be "application/json"
4. **references**: Optional array for artifact references (empty by default)

## Changes Made

### 1. Updated Models (`ApicurioModels.scala`)

Added proper case classes matching the API spec:

- `CreateArtifactRequest` - Top level artifact creation request
- `FirstVersionRequest` - First version details for new artifacts
- `ContentRequest` - Content wrapper with contentType and references
- `ContentReference` - For schema references (future use)
- `CreateVersionRequest` - Version creation for existing artifacts

### 2. Updated Client (`ApicurioClient.scala`)

**createArtifact:**
```scala
val requestBody = CreateArtifactRequest(
  artifactId = artifactId,
  artifactType = artifactType.value,
  firstVersion = FirstVersionRequest(
    version = None,
    content = ContentRequest(
      content = content,  // Content as string
      contentType = "application/json"
    )
  )
)
```

**createVersion:**
```scala
val requestBody = CreateVersionRequest(
  version = None,
  content = ContentRequest(
    content = content,  // Content as string
    contentType = "application/json"
  )
)
```

## Updated Plugin Version

**New Version:** `0.0.0+1-dc3b6996+20251103-0603-SNAPSHOT`

## How to Update Your Service

### 1. Update `project/plugins.sbt`:

```scala
addSbtPlugin("org.scalateams" % "sbt-apicurio" % "0.0.0+1-dc3b6996+20251103-0603-SNAPSHOT")
```

### 2. Reload and Publish:

```bash
sbt reload
sbt apicurioPublish
```

## Expected Output

```
[info] Publishing 3 schemas to Apicurio Registry
[info] Group ID: com.upstartcommerce.tenant
[info] Compatibility Level: BACKWARD
[info] Creating artifact: TenantState (AVRO)
[info] Created artifact: TenantState version 1
[info] Creating artifact: Audits (AVRO)
[info] Created artifact: Audits version 1
[info] Creating artifact: TenantEvent (AVRO)
[info] Created artifact: TenantEvent version 1
[info] Publishing complete: 3 published, 0 unchanged, 0 failed
```

## Verification

The implementation now matches the official Apicurio Registry 3.x REST API specification as documented at:
- https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/

## Authentication Note

If using API keys, ensure your token is set:
```scala
apicurioApiKey := sys.env.get("APICURIO_API_KEY")
```

The plugin will send: `Authorization: Bearer $TOKEN`

## Troubleshooting

### Still Getting Errors?

1. **Verify Apicurio version**: Ensure you're running Apicurio Registry 3.x
   ```bash
   curl -s http://your-registry/system/info | jq .version
   ```

2. **Check endpoint**: Verify the URL is correct
   ```scala
   apicurioRegistryUrl := "https://your-registry.com"  // No trailing slash, no /apis/registry/v3
   ```

3. **Validate schemas**: Ensure your .avsc files are valid JSON
   ```bash
   cat src/main/schemas/YourSchema.avsc | jq .
   ```

4. **Enable debug logging**: Add to your service's build.sbt
   ```scala
   logLevel := Level.Debug
   ```

5. **Test manually**: Try creating an artifact via curl
   ```bash
   curl -X POST "http://your-registry/apis/registry/v3/groups/test/artifacts" \
     -H "Content-Type: application/json" \
     -d '{
       "artifactId": "test-schema",
       "artifactType": "AVRO",
       "firstVersion": {
         "content": {
           "content": "{\"type\":\"record\",\"name\":\"Test\"}",
           "contentType": "application/json"
         }
       }
     }'
   ```

If you still encounter issues, please share:
- Full error message
- SBT output with debug enabled
- Sample schema file
- Apicurio version (`/system/info`)
