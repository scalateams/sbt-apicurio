# Fix: Correct Artifact vs Version Metadata Separation

## Problem

The plugin was incorrectly mixing artifact-level and version-level metadata in the `ArtifactMetadata` model, causing JSON decoding failures when interacting with Apicurio Registry 3.x.

### What Was Wrong

**Incorrect Model (Before):**
```scala
case class ArtifactMetadata(
  groupId: String,
  artifactId: String,
  version: String,      // ❌ WRONG - This is version-level
  artifactType: String,
  contentId: Option[Long] = None,  // ❌ WRONG - version-level
  globalId: Option[Long] = None    // ❌ WRONG - version-level
)
```

### Root Cause

In Apicurio Registry 3.x, there's a clear separation:

**Artifact Metadata** (from `GET /groups/{groupId}/artifacts/{artifactId}`):
- groupId
- artifactId
- artifactType
- owner, createdOn, modifiedBy, modifiedOn
- name, description, labels (optional)
- **NO** version, contentId, or globalId (these are version-specific)

**Version Metadata** (from version-specific endpoints):
- version
- contentId
- globalId
- state
- groupId, artifactId, artifactType
- owner, createdOn
- name, description, labels (optional)

**Create Artifact Response** (`POST /groups/{groupId}/artifacts`):
Returns BOTH artifact and version metadata as separate objects:
```json
{
  "artifact": {
    "groupId": "my-group",
    "artifactId": "share-price",
    "artifactType": "AVRO",
    "owner": "...",
    "createdOn": "2024-09-26T17:24:21Z",
    "modifiedBy": "...",
    "modifiedOn": "2024-09-26T17:24:21Z"
  },
  "version": {
    "version": "1",
    "groupId": "my-group",
    "artifactId": "share-price",
    "artifactType": "AVRO",
    "contentId": 456,
    "globalId": 123,
    "state": "ENABLED",
    "owner": "...",
    "createdOn": "2024-09-26T17:24:21Z"
  }
}
```

## Solution

### 1. Fixed ArtifactMetadata (Artifact-Level Only)

```scala
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
  labels: Option[Map[String, String]] = None
)
```

### 2. Fixed VersionMetadata (Version-Level Only)

```scala
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
  labels: Option[Map[String, String]] = None
)
```

### 3. New CreateArtifactResponse

```scala
case class CreateArtifactResponse(
  artifact: ArtifactMetadata,
  version: VersionMetadata
)
```

### 4. Updated Client Methods

**createArtifact** now returns `CreateArtifactResponse`:
```scala
def createArtifact(
  groupId: String,
  artifactId: String,
  artifactType: ArtifactType,
  content: String
): Try[CreateArtifactResponse]
```

**publishSchema** now returns:
```scala
Try[Either[CreateArtifactResponse, VersionMetadata]]
```
- Left = newly created artifact (with both artifact and version info)
- Right = updated version (version info only)

### 5. Updated Plugin

The plugin now properly extracts version information from the response:
```scala
case Success(Left(createResponse)) =>
  log.info(s"Created artifact: $artifactId version ${createResponse.version.version}")
```

## Updated Plugin Version

**New Version:** `0.0.0+1-dc3b6996+20251103-0611-SNAPSHOT`

## How to Update Your Service

### 1. Update `project/plugins.sbt`:

```scala
addSbtPlugin("org.scalateams" % "sbt-apicurio" % "0.0.0+1-dc3b6996+20251103-0611-SNAPSHOT")
```

### 2. Reload SBT:

```bash
sbt reload
```

### 3. Try Publishing:

```bash
sbt apicurioPublish
```

## Expected Success Output

```
[info] Publishing 3 schemas to Apicurio Registry
[info] Group ID: com.upstartcommerce.tenant
[info] Compatibility Level: BACKWARD
[info] Creating artifact: TenantState (AVRO)
[info] Created artifact: TenantState (AVRO) version 1
[info] Creating artifact: Audits (AVRO)
[info] Created artifact: Audits (AVRO) version 1
[info] Creating artifact: TenantEvent (AVRO)
[info] Created artifact: TenantEvent (AVRO) version 1
[info] Publishing complete: 3 published, 0 unchanged, 0 failed
```

## What This Fixes

1. **JSON Decoding Errors** - The plugin can now correctly parse responses from Apicurio 3.x
2. **Artifact Creation** - Properly handles the dual artifact/version response
3. **Artifact Queries** - Can successfully query artifact metadata without version info
4. **API Compliance** - Models now match the official Apicurio 3.x API specification

## API Endpoints Now Correctly Supported

### GET /groups/{groupId}/artifacts/{artifactId}
Returns artifact metadata only (no version info)

### POST /groups/{groupId}/artifacts
Returns both artifact and version metadata in separate objects

### POST /groups/{groupId}/artifacts/{artifactId}/versions
Returns version metadata

### GET /groups/{groupId}/artifacts/{artifactId}/versions
Returns list of version metadata

### GET /groups/{groupId}/artifacts/{artifactId}/versions/{version}
Returns specific version metadata

### GET /groups/{groupId}/artifacts/{artifactId}/versions/{version}/content
Returns the actual schema content

## Verification

This implementation is verified against:
- Apicurio Registry 3.x official documentation
- Actual API responses from Apicurio Registry 3.0.x

## Benefits

1. **Proper separation of concerns** - Artifact vs version metadata
2. **Type safety** - Can't mix artifact and version fields
3. **API compatibility** - Matches Apicurio 3.x exactly
4. **Better error handling** - Clear error messages when responses don't match
5. **Future-proof** - Can handle additional optional fields without breaking

## Testing

You can verify the fix by:

1. **Create a new artifact**:
   ```bash
   sbt apicurioPublish
   ```
   Should succeed and show version "1"

2. **Update an existing artifact**:
   Modify a schema and run again
   ```bash
   sbt apicurioPublish
   ```
   Should show incremented version

3. **Query artifact metadata**:
   The plugin will no longer fail when querying existing artifacts

## Troubleshooting

### "Failed to parse response" Errors

If you still see parsing errors, ensure:
1. You're using the latest plugin version
2. Your Apicurio Registry is version 3.x (check `/system/info`)
3. You've reloaded SBT (`sbt reload`)

### Version Number Not Shown

The plugin now correctly extracts and displays version numbers from both:
- New artifact creation (from `CreateArtifactResponse.version.version`)
- Version updates (from `VersionMetadata.version`)

## References

- [Apicurio Registry 3.x Documentation](https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/)
- [Apicurio Registry GitHub](https://github.com/Apicurio/apicurio-registry)
