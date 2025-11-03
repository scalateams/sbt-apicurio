# Testing the sbt-apicurio Plugin

This plugin includes comprehensive integration tests that verify functionality against a real Apicurio Registry instance.

## Test Structure

### Test Schemas

The plugin includes test schemas for all supported types in `src/test/resources/test-schemas/`:

- **TestUser.avsc** - Avro schema
- **TestProduct.json** - JSON Schema
- **TestOrder.proto** - Protobuf schema
- **TestAPI.yaml** - OpenAPI schema

### Test Suite

`ApicurioIntegrationSpec` contains tests for:

1. **Schema Discovery**
   - Discovering schemas of all types
   - Correctly identifying artifact types
   - Computing consistent hashes

2. **Publishing Schemas**
   - Creating Avro artifacts
   - Creating JSON Schema artifacts
   - Creating Protobuf artifacts
   - Creating OpenAPI artifacts

3. **Retrieving Schemas**
   - Getting artifact metadata
   - Getting version content
   - Getting latest version
   - Pulling schemas to local files

4. **Version Management**
   - Creating new versions of existing artifacts

5. **End-to-End Workflow**
   - Publishing all schema types
   - Pulling all published schemas
   - Verifying pulled files

## Prerequisites

### Running Apicurio Registry Locally

The easiest way to run Apicurio Registry for testing is with Docker:

```bash
# Run Apicurio Registry 3.x
docker run -p 8080:8080 apicurio/apicurio-registry:3.0.0

# Or with Docker Compose
cat > docker-compose.yml <<EOL
version: '3.8'
services:
  apicurio:
    image: apicurio/apicurio-registry:3.0.0
    ports:
      - "8080:8080"
    environment:
      - QUARKUS_PROFILE=prod
EOL

docker-compose up -d
```

Wait a few seconds for Apicurio to start, then verify:

```bash
curl http://localhost:8080/health/ready
```

Should return status 200 with `{"status":"UP"}`.

## Running Tests

### All Tests (Including Integration)

```bash
# Run all tests
sbt test
```

### Skip Integration Tests

Integration tests are tagged with `IntegrationTest`. To skip them:

```bash
sbt "testOnly * -- -l org.scalateams.sbt.apicurio.IntegrationTest"
```

### Run Only Integration Tests

```bash
sbt "testOnly * -- -n org.scalateams.sbt.apicurio.IntegrationTest"
```

### Run Specific Test Suite

```bash
sbt "testOnly *ApicurioIntegrationSpec"
```

## Configuration

### Environment Variables

Set these environment variables to configure test execution:

```bash
# Registry URL (default: http://localhost:8080)
export APICURIO_TEST_URL="http://localhost:8080"

# Optional: API key if registry requires authentication
export APICURIO_TEST_API_KEY="your-api-key"

# Run tests
sbt test
```

### Test Group ID

Tests use the group ID `com.example` for all test artifacts. This keeps test data separate from production data.

## Test Data Cleanup

Integration tests create artifacts in Apicurio Registry under the `com.example` group. To clean up:

```bash
# List artifacts in test group
curl http://localhost:8080/apis/registry/v3/groups/com.example/artifacts

# Delete specific artifact
curl -X DELETE http://localhost:8080/apis/registry/v3/groups/com.example/artifacts/TestUser

# Or reset the entire registry (if using ephemeral storage)
docker restart apicurio-registry
```

## Expected Test Output

### Success

```
[info] ApicurioIntegrationSpec:
[info] SchemaFileUtils
[info] - should discover Avro schema files
[info] - should discover JSON Schema files
[info] - should discover Protobuf schema files
[info] - should discover OpenAPI schema files
[info] - should correctly identify artifact types
[info] - should compute consistent hashes for schema content
[info] ApicurioClient - Publishing
[info] - should create an Avro artifact in Apicurio
[info] - should create a JSON Schema artifact in Apicurio
[info] - should create a Protobuf artifact in Apicurio
[info] - should create an OpenAPI artifact in Apicurio
[info] ApicurioClient - Retrieving
[info] - should retrieve artifact metadata
[info] - should retrieve version content
[info] - should retrieve latest version
[info] - should pull schema content by version
[info] - should pull latest version when 'latest' is specified
[info] ApicurioClient - Version Management
[info] - should create a new version for existing artifact
[info] End-to-End Workflow
[info] - should publish and pull all schema types
[info] Run completed in 15 seconds.
[info] Total number of tests run: 17
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 17, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

### Apicurio Not Available

If Apicurio is not running, tests are skipped automatically:

```
Apicurio not available: Connection refused
Skipping integration tests. Set APICURIO_TEST_URL to run tests.
[info] ApicurioIntegrationSpec:
[info] SchemaFileUtils
[info] - should discover Avro schema files
[info] - should discover JSON Schema files
[info] - should discover Protobuf schema files
[info] - should discover OpenAPI schema files
[info] - should correctly identify artifact types
[info] - should compute consistent hashes for schema content
[info] ApicurioClient - Publishing
[info] - should create an Avro artifact in Apicurio (canceled)
...
```

Non-integration tests still run, but integration tests are automatically canceled.

## Continuous Integration

### GitHub Actions Example

```yaml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      apicurio:
        image: apicurio/apicurio-registry:3.0.0
        ports:
          - 8080:8080
        options: >-
          --health-cmd "curl -f http://localhost:8080/health/ready || exit 1"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '11'
          cache: 'sbt'
      
      - name: Run tests
        env:
          APICURIO_TEST_URL: http://localhost:8080
        run: sbt test
```

### Travis CI Example

```yaml
services:
  - docker

before_install:
  - docker run -d -p 8080:8080 apicurio/apicurio-registry:3.0.0
  - sleep 10  # Wait for Apicurio to start

env:
  - APICURIO_TEST_URL=http://localhost:8080

script:
  - sbt test
```

## Troubleshooting

### Connection Refused

```
Apicurio not available: Connection refused
```

**Solution:** Ensure Apicurio is running and accessible:
```bash
curl http://localhost:8080/health/ready
```

### Tests Timeout

If tests hang or timeout, check Apicurio logs:
```bash
docker logs apicurio-registry
```

### Authentication Errors

If using an authenticated registry:
```bash
export APICURIO_TEST_API_KEY="your-token"
sbt test
```

### Schema Already Exists

Tests may fail if artifacts already exist. Clean up test data:
```bash
# Delete test group artifacts
curl -X DELETE http://localhost:8080/apis/registry/v3/groups/com.example/artifacts/TestUser
curl -X DELETE http://localhost:8080/apis/registry/v3/groups/com.example/artifacts/TestProduct
curl -X DELETE http://localhost:8080/apis/registry/v3/groups/com.example/artifacts/TestOrder
curl -X DELETE http://localhost:8080/apis/registry/v3/groups/com.example/artifacts/TestAPI
```

### Protobuf Validation Errors

The plugin correctly handles Protobuf schemas (which are not JSON). If you see validation errors:
1. Ensure you're using the latest plugin version
2. Check that the .proto file is valid Protobuf syntax

## Manual Testing

You can also test manually against your tenant service:

```bash
# 1. Start Apicurio Registry
docker run -p 8080:8080 apicurio/apicurio-registry:3.0.0

# 2. In your tenant service, update build.sbt
apicurioRegistryUrl := "http://localhost:8080"
apicurioGroupId := "com.example"

# 3. Publish schemas
sbt apicurioPublish

# 4. View in Apicurio UI
open http://localhost:8080/ui/artifacts
```

## Test Coverage

The test suite verifies:

- ✅ Schema discovery for all types
- ✅ Artifact type detection
- ✅ Hash computation
- ✅ Publishing Avro schemas
- ✅ Publishing JSON Schemas
- ✅ Publishing Protobuf schemas
- ✅ Publishing OpenAPI schemas
- ✅ Retrieving artifact metadata
- ✅ Retrieving version metadata
- ✅ Pulling schema content
- ✅ Creating new versions
- ✅ End-to-end publish + pull workflow
- ✅ Correct request/response structure per Apicurio 3.x API

## Next Steps

After tests pass:
1. Publish plugin locally: `sbt publishLocal`
2. Use in your service
3. Verify against your real Apicurio Registry instance
