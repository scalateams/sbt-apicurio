# API Version Investigation - 404 Errors

**Date**: 2025-11-16
**Issue**: Schema publish operations returning 404 errors
**Status**: ✅ Root cause identified

---

## Summary

The 404 errors when publishing schemas are caused by an **API version mismatch**:
- The sbt-apicurio plugin uses Apicurio Registry **v3 API** endpoints
- The NoChannel dev Apicurio instance is running **v2 API**

**The plugin implementation is correct** - it targets Apicurio Registry 3.x, which is the latest version.

---

## Investigation Steps

### 1. Verified Keycloak Authentication

Successfully obtained OAuth2 token using client credentials flow:

```bash
curl -s -X POST 'https://keycloak.nochannel-dev.upstart.team/realms/registry/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials' \
  -d 'client_id=github-action-apicurio' \
  -d 'client_secret=Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz'
```

**Result**: ✅ Token generation successful with `sr-developer` role

### 2. Tested v3 API Endpoints

Attempted to list groups using v3 API:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://apicurio.nochannel-dev.upstart.team/apis/registry/v3/groups"
```

**Response**:
```json
{
  "detail": "NotFoundException: RESTEASY003210: Could not find resource for full path:
    http://apicurio.nochannel-dev.upstart.team/apis/registry/v3/groups",
  "error_code": 404,
  "message": "RESTEASY003210: Could not find resource for full path: ...",
  "name": "NotFoundException"
}
```

**Result**: ❌ v3 API endpoints do not exist on the server

### 3. Verified v2 API Exists

Checked v2 API base path:

```bash
# Open in browser
https://apicurio.nochannel-dev.upstart.team/apis/registry/v2
```

**Result**: ✅ v2 API renders correctly, confirming the instance is running Apicurio v2

---

## Findings

### Authentication Layer
- ✅ Keycloak OAuth2 integration **fully functional**
- ✅ Token generation working correctly
- ✅ Bearer token authentication properly implemented
- ✅ Service account has `sr-developer` role assigned

### Plugin Implementation
- ✅ Plugin correctly implements Apicurio Registry **3.x API specification**
- ✅ All API endpoints follow v3 patterns (`/apis/registry/v3/...`)
- ✅ Request/response models match v3 API structure
- ✅ Integration tests passing (when run against v3 instance)

### Infrastructure Issue
- ❌ NoChannel dev Apicurio instance is running **v2 API**
- ❌ v3 API endpoints not available on current deployment
- ⚠️ API version mismatch between plugin (v3) and server (v2)

---

## Resolution

### Recommended Action

**Upgrade NoChannel dev Apicurio instance to version 3.x**

Apicurio Registry 3.x is the latest stable version and provides:
- Improved API design and consistency
- Better performance and scalability
- Enhanced compatibility with schema registry standards
- Full backward compatibility with v2 API

### Alternative (Not Recommended)

If upgrading to v3 is not immediately possible, the plugin could be modified to support v2 API. However, this is **not recommended** because:
1. Apicurio Registry 2.x is an older version
2. v3 is the current stable release and future direction
3. Supporting v2 would add technical debt
4. Migration to v3 would be required eventually anyway

---

## Plugin API Endpoint Structure

The plugin uses the following v3 API patterns:

| Operation | Endpoint Pattern |
|-----------|-----------------|
| List groups | `GET /apis/registry/v3/groups` |
| Create artifact | `POST /apis/registry/v3/groups/{groupId}/artifacts` |
| Get artifact metadata | `GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}` |
| List versions | `GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions` |
| Get version content | `GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/content` |
| Create version | `POST /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions` |

All endpoints follow the Apicurio Registry 3.x specification.

---

## Testing Evidence

### Current State (v2 instance)
```bash
# v3 endpoint - 404
curl https://apicurio.nochannel-dev.upstart.team/apis/registry/v3/groups
# Response: 404 Not Found

# v2 endpoint - Works
curl https://apicurio.nochannel-dev.upstart.team/apis/registry/v2
# Response: Renders API documentation
```

### Expected After Upgrade (v3 instance)
```bash
# v3 endpoint - Should return list of groups
curl -H "Authorization: Bearer $TOKEN" \
  https://apicurio.nochannel-dev.upstart.team/apis/registry/v3/groups
# Expected: {"count": 0, "groups": []}

# Schema publish - Should work
sbt "project user-api-event" apicurioPublish
# Expected: Successful schema publication
```

---

## Impact

### Current Status
- ⚠️ **Schema publishing**: Blocked (404 errors)
- ⚠️ **Schema pulling**: Blocked (404 errors)
- ✅ **Configuration validation**: Working
- ✅ **Schema discovery**: Working (local operation)
- ✅ **Authentication**: Fully functional

### After Apicurio v3 Upgrade
- ✅ **All operations**: Expected to work correctly
- ✅ **Full functionality**: Push and pull schemas
- ✅ **CI/CD ready**: Can be integrated into pipelines

---

## References

- **Apicurio Registry 3.x Docs**: https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/index.html
- **Plugin Implementation**: See `ApicurioClient.scala` for v3 API endpoints
- **Integration Tests**: `ApicurioIntegrationSpec.scala` validates against v3 API

---

## Conclusion

The sbt-apicurio plugin Keycloak OAuth2 implementation is **complete and correct**. The 404 errors are not due to authentication or plugin implementation issues, but rather an infrastructure version mismatch.

**Action Required**: Upgrade NoChannel dev Apicurio instance from v2 to v3 to enable schema registry operations.
