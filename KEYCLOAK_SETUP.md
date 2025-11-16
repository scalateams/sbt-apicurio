# Keycloak Authentication Setup

This document describes the Keycloak OAuth2 authentication setup for the sbt-apicurio plugin in the NoChannel environment.

## Overview

The sbt-apicurio plugin now supports Keycloak OAuth2 authentication using the **client credentials flow**. This enables secure access to the Apicurio Registry in production and development environments.

## NoChannel Development Environment

### Configuration

```scala
// build.sbt
enablePlugins(ApicurioPlugin)

apicurioRegistryUrl := "https://apicurio.nochannel-dev.upstart.team/apis/registry/v3"
apicurioGroupId := "com.upstartcommerce.yourservice"  // Replace with your service name

apicurioKeycloakConfig := Some(keycloak(
  url = "https://keycloak.nochannel-dev.upstart.team",
  realm = "registry",
  clientId = "github-action-apicurio",
  clientSecret = sys.env.getOrElse("KEYCLOAK_CLIENT_SECRET", "")
))
```

### Credentials

| Property | Value |
|----------|-------|
| **Keycloak URL** | `https://keycloak.nochannel-dev.upstart.team` |
| **Apicurio URL** | `https://apicurio.nochannel-dev.upstart.team/apis/registry/v3` |
| **Realm** | `registry` |
| **Client ID** | `github-action-apicurio` |
| **Client Secret** | `Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz` |

> ✅ **Verified**: These credentials have been tested and are working correctly!
> - Service account exists with `sr-developer` role assigned
> - Client is enabled with "Service Accounts Enabled" = ON
> - Token generation successful (5-minute expiry)

> ⚠️ **Security Note**: The client secret should be stored securely as an environment variable or in a secrets management system. Never commit secrets to version control.

## Local Development Setup

### Option 1: Environment Variable

Set the client secret as an environment variable:

```bash
export KEYCLOAK_CLIENT_SECRET="Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz"

# Then run sbt tasks normally
sbt apicurioPublish
sbt apicurioPull
```

### Option 2: Shell Profile

Add to your `~/.zshrc` or `~/.bashrc`:

```bash
# Apicurio Keycloak credentials (NoChannel Dev)
export KEYCLOAK_CLIENT_SECRET="Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz"
```

Then reload:
```bash
source ~/.zshrc  # or ~/.bashrc
```

### Option 3: SBT Environment File

Create `.sbtopts` in your project root:

```
-DKEYCLOAK_CLIENT_SECRET=Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz
```

Then access in build.sbt:
```scala
apicurioKeycloakConfig := Some(keycloak(
  url = "https://keycloak.nochannel-dev.upstart.team",
  realm = "registry",
  clientId = "github-action-apicurio",
  clientSecret = sys.props.getOrElse("KEYCLOAK_CLIENT_SECRET", "")
))
```

> ⚠️ Add `.sbtopts` to `.gitignore` to avoid committing secrets!

## CI/CD Setup

### GitHub Actions

Add the secret to your repository:
1. Go to repository Settings → Secrets and variables → Actions
2. Add new secret: `APICURIO_CLIENT_SECRET` = `Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz`

Use in workflow:
```yaml
name: Publish Schemas

on:
  push:
    branches: [main]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Publish Schemas to Apicurio
        env:
          KEYCLOAK_CLIENT_SECRET: ${{ secrets.APICURIO_CLIENT_SECRET }}
        run: sbt apicurioPublish
```

### GitLab CI

Add variable in Settings → CI/CD → Variables:
- **Key**: `KEYCLOAK_CLIENT_SECRET`
- **Value**: `Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz`
- **Type**: Variable
- **Protected**: Yes
- **Masked**: Yes

Use in `.gitlab-ci.yml`:
```yaml
publish-schemas:
  stage: deploy
  script:
    - sbt apicurioPublish
  only:
    - main
```

## How It Works

### OAuth2 Client Credentials Flow

1. **Token Request**: Plugin requests access token from Keycloak token endpoint
   ```
   POST https://keycloak.nochannel-dev.upstart.team/realms/registry/protocol/openid-connect/token
   grant_type=client_credentials
   client_id=github-action-apicurio
   client_secret=Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz
   ```

2. **Token Response**: Keycloak returns access token (typically 5-10 minute lifetime)
   ```json
   {
     "access_token": "eyJhbGc...",
     "expires_in": 300,
     "token_type": "Bearer"
   }
   ```

3. **API Calls**: Plugin includes token in Authorization header
   ```
   GET https://apicurio.nochannel-dev.upstart.team/apis/registry/v3/groups/{groupId}/artifacts
   Authorization: Bearer eyJhbGc...
   ```

4. **Token Refresh**: Plugin automatically refreshes token 30 seconds before expiry

### Token Lifecycle Management

- ✅ **Automatic caching**: Token cached after first request
- ✅ **Proactive refresh**: Refreshes 30s before expiry (never hits expiry mid-operation)
- ✅ **Thread-safe**: Safe for concurrent SBT tasks
- ✅ **Transparent**: No user intervention needed

## Service Account Details

The `github-action-apicurio` service account exists in Keycloak (verified from realm export):
- **Service Account User**: `service-account-github-action-apicurio`
- **User ID**: `3ef99ea7-b49f-4bef-b485-bbcb282cb3a6`
- **Created**: 2025-11-16
- **Type**: Service Account (client credentials flow)
- **Realm**: `registry`

### Required Configuration

The client must be configured with:
- **Client ID**: `github-action-apicurio`
- **Access Type**: `confidential`
- **Service Accounts Enabled**: `ON` ✅
- **Roles**: `sr-admin` or `sr-developer` (allows full read/write access to schemas)
- **Token Lifetime**: ~5-10 minutes (configured in Keycloak realm settings)

### Verifying/Fixing the Client Secret

If authentication fails with "Invalid client or Invalid client credentials", the client secret needs to be regenerated:

1. Log into Keycloak Admin Console: https://keycloak.nochannel-dev.upstart.team
2. Navigate to: **Clients** → **github-action-apicurio**
3. Go to the **Credentials** tab
4. Click **Regenerate Secret** button
5. Copy the new secret and update:
   - Your local environment variable
   - CI/CD secrets
   - This documentation

**To verify the secret works:**
```bash
# Test with your credentials
curl -X POST 'https://keycloak.nochannel-dev.upstart.team/realms/registry/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials' \
  -d 'client_id=github-action-apicurio' \
  -d 'client_secret=YOUR_SECRET_HERE'

# Should return JSON with "access_token" field
# Error "unauthorized_client" means the secret is incorrect
```

### Assigning Roles to Service Account

After the client is configured, assign the necessary role:

1. In Keycloak Admin Console, go to **Clients** → **github-action-apicurio**
2. Click the **Service Account Roles** tab
3. In the **Client Roles** dropdown, select the Apicurio client (usually `apicurio-registry` or `registry-api`)
4. Select `sr-admin` from Available Roles and click **Add selected**
5. Verify `sr-admin` appears in Assigned Roles

Without the proper role, the service account won't have permission to push/pull schemas.

## Troubleshooting

### Authentication Errors

If you see authentication errors:

1. **Verify credentials**:
   ```bash
   curl -X POST \
     "https://keycloak.nochannel-dev.upstart.team/realms/registry/protocol/openid-connect/token" \
     -d "grant_type=client_credentials" \
     -d "client_id=github-action-apicurio" \
     -d "client_secret=Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz"
   ```

   Should return JSON with `access_token`. ✅ **Verified working!**

2. **Check environment variable**:
   ```bash
   echo $KEYCLOAK_CLIENT_SECRET
   ```

3. **Enable debug logging**:
   ```bash
   sbt -Dapicurio.debug=true apicurioPublish
   ```

### Token Expiry Issues

If tokens are expiring mid-operation:
- Check Keycloak realm settings for token lifetime
- Verify network connectivity (token refresh requires network access)
- The plugin refreshes 30s before expiry - if operations take longer than token lifetime minus 30s, you may see issues

### Network Issues

If Keycloak/Apicurio are unreachable:
- Verify VPN connection (if required)
- Check network policies/firewalls
- Verify URLs are correct and services are running

## Testing Authentication

Test the authentication setup:

```bash
# Set credentials
export KEYCLOAK_CLIENT_SECRET="Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz"

# Validate settings
sbt apicurioValidateSettings

# Try a simple pull (if you have dependencies configured)
sbt apicurioPull

# Publish schemas
sbt apicurioPublish
```

✅ **Token generation verified working** - Authentication is fully functional!

## Migration from API Key

If you were previously using `apicurioApiKey`, migration is simple:

**Before:**
```scala
apicurioApiKey := Some(sys.env.getOrElse("APICURIO_API_KEY", ""))
```

**After:**
```scala
apicurioKeycloakConfig := Some(keycloak(
  url = "https://keycloak.nochannel-dev.upstart.team",
  realm = "registry",
  clientId = "github-action-apicurio",
  clientSecret = sys.env.getOrElse("KEYCLOAK_CLIENT_SECRET", "")
))
```

## Security Best Practices

1. ✅ **Never commit secrets** to version control
2. ✅ **Use environment variables** for local development
3. ✅ **Use CI/CD secrets** for automated builds
4. ✅ **Rotate secrets periodically** (update in Keycloak and CI/CD)
5. ✅ **Limit service account permissions** (only grant necessary roles)
6. ✅ **Monitor access logs** in Keycloak for unauthorized access

## Support

For issues or questions:
1. Check the [README.md](README.md) for general plugin documentation
2. Review Keycloak admin console: https://keycloak.nochannel-dev.upstart.team
3. Review Apicurio admin UI: https://apicurio.nochannel-dev.upstart.team
4. Contact the platform team for infrastructure issues
