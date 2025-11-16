# Keycloak Service Account Setup Checklist

Use this checklist to verify the `github-action-apicurio` service account is properly configured in Keycloak.

## ✅ Pre-requisites

- [ ] Access to Keycloak Admin Console: https://keycloak.nochannel-dev.upstart.team
- [ ] Admin permissions for the `registry` realm
- [ ] Apicurio Registry running at: https://apicurio.nochannel-dev.upstart.team

## ✅ Client Configuration

Navigate to: **Clients** → **github-action-apicurio**

### Settings Tab

- [ ] **Client ID**: `github-action-apicurio`
- [ ] **Enabled**: `ON`
- [ ] **Client Protocol**: `openid-connect`
- [ ] **Access Type**: `confidential`
- [ ] **Standard Flow Enabled**: `OFF` (not needed for service accounts)
- [ ] **Implicit Flow Enabled**: `OFF` (not needed)
- [ ] **Direct Access Grants Enabled**: `OFF` (not needed)
- [ ] **Service Accounts Enabled**: `ON` ✅ **(REQUIRED)**
- [ ] **Authorization Enabled**: `OFF` (optional)
- [ ] **Valid Redirect URIs**: Can be empty or `*` (not used for service accounts)
- [ ] **Web Origins**: Can be empty or `*` (not used for service accounts)

Click **Save** after making changes.

### Credentials Tab

- [ ] **Client Authenticator**: `Client Id and Secret`
- [ ] **Secret** field shows a value
- [ ] Copy the secret or click **Regenerate Secret** to create a new one
- [ ] Store the secret securely (it won't be shown again)

**Current Secret** (as of setup): `Om7qT2iBD3QB3JeQPcyiWfSCMh2QCRdz`
✅ **Verified working** - Token generation successful!

### Service Account Roles Tab

This tab only appears if "Service Accounts Enabled" is ON.

- [ ] Service account user exists: `service-account-github-action-apicurio`
- [ ] In **Client Roles** dropdown, find the Apicurio Registry client
  - Common names: `apicurio-registry`, `registry-api`, `registry-rest-api`
- [ ] From **Available Roles**, select `sr-admin` or `sr-developer`
- [ ] Click **Add selected** →
- [ ] Verify role appears in **Assigned Roles**

**Role Permissions**:
- `sr-admin`: Full access (create, read, update, delete schemas)
- `sr-developer`: Developer access (typically sufficient for publishing schemas)

If you don't see these roles, they need to be created in the Apicurio Registry client first.

## ✅ Realm Roles (Optional but Recommended)

Navigate to: **Roles** → **Realm Roles**

- [ ] Check if `registry-admin` or similar realm role exists
- [ ] If it exists and has the Apicurio roles as composite roles, assign it to the service account

This is optional - assigning client roles directly is sufficient.

## ✅ Token Settings

Navigate to: **Realm Settings** → **Tokens** tab

Verify reasonable token lifetimes:

- [ ] **Access Token Lifespan**: 5-15 minutes (default: 5 min)
  - Too short: Frequent token refreshes
  - Too long: Security risk
- [ ] **Client Login Timeout**: 1 minute (for initial token request)

The plugin automatically refreshes tokens 30 seconds before expiry, so 5-10 minutes is ideal.

## ✅ Testing the Configuration

### 1. Test Token Request

```bash
curl -X POST 'https://keycloak.nochannel-dev.upstart.team/realms/registry/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials' \
  -d 'client_id=github-action-apicurio' \
  -d 'client_secret=YOUR_SECRET_HERE'
```

Expected response:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 300,
  "refresh_expires_in": 0,
  "token_type": "Bearer",
  "not-before-policy": 0,
  "scope": "profile email"
}
```

**If you get an error:**
- `unauthorized_client`: Client secret is incorrect → Regenerate in Keycloak
- `invalid_client`: Client ID is wrong or client doesn't exist
- `access_denied`: Service Accounts not enabled for this client

### 2. Test Apicurio API Access

Extract the token from step 1 and test Apicurio access:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# Test listing groups
curl -H "Authorization: Bearer $TOKEN" \
  https://apicurio.nochannel-dev.upstart.team/apis/registry/v3/groups

# Test creating a test artifact
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  https://apicurio.nochannel-dev.upstart.team/apis/registry/v3/groups/test/artifacts \
  -d '{
    "artifactId": "test-schema",
    "artifactType": "JSON",
    "firstVersion": {
      "content": {
        "content": "{\"type\": \"string\"}",
        "contentType": "application/json"
      }
    }
  }'
```

Expected response: 200 OK with artifact metadata

**If you get an error:**
- `401 Unauthorized`: Token is invalid or expired
- `403 Forbidden`: Service account lacks proper roles → Check Service Account Roles tab
- `404 Not Found`: Apicurio URL is incorrect

### 3. Test with sbt-apicurio Plugin

```bash
# Set environment variable
export KEYCLOAK_CLIENT_SECRET="YOUR_SECRET_HERE"

# Configure build.sbt (see KEYCLOAK_SETUP.md)

# Validate settings
sbt apicurioValidateSettings

# Try publishing (requires schemas in src/main/schemas/)
sbt apicurioPublish
```

## ✅ Security Verification

- [ ] Client secret is stored securely (environment variables, secrets manager)
- [ ] Client secret is NOT committed to git
- [ ] CI/CD systems use masked/protected secrets
- [ ] Only necessary roles are assigned (principle of least privilege)
- [ ] Token lifetime is appropriate (5-10 minutes recommended)
- [ ] Keycloak access logs are monitored for suspicious activity

## ✅ Documentation Updated

- [ ] Client secret documented in team password manager or vault
- [ ] CI/CD pipelines configured with the secret
- [ ] Team members informed of the configuration
- [ ] Troubleshooting contact identified (platform/DevOps team)

## 🆘 Troubleshooting Common Issues

### "Invalid client or Invalid client credentials"
- ✅ Regenerate client secret in Keycloak Credentials tab
- ✅ Verify "Service Accounts Enabled" is ON
- ✅ Check client ID is exactly `github-action-apicurio`

### "Access denied" when calling Apicurio API
- ✅ Check Service Account Roles tab
- ✅ Verify `sr-admin` or `sr-developer` role is assigned
- ✅ Check the Apicurio client name in the dropdown

### Token expires too quickly
- ✅ Increase Access Token Lifespan in Realm Settings → Tokens
- ✅ Plugin auto-refreshes 30s before expiry, so 5-10 min is sufficient

### Service Account Roles tab is missing
- ✅ "Service Accounts Enabled" must be ON in Settings tab
- ✅ Save the client and refresh the page

### Can't find sr-admin role
- ✅ The role must be created in the Apicurio Registry client
- ✅ Contact platform team to set up Apicurio-Keycloak integration
- ✅ Verify Apicurio is configured to use Keycloak for authorization

## 📞 Support

If you encounter issues after completing this checklist:

1. **Check Keycloak logs**: Look for authentication errors
2. **Check Apicurio logs**: Look for authorization errors
3. **Contact platform team**: They can verify Keycloak-Apicurio integration
4. **Consult KEYCLOAK_SETUP.md**: Detailed setup and troubleshooting guide

## ✅ Setup Complete!

Once all checkboxes are complete and tests pass:

- [ ] Document the working client secret securely
- [ ] Update CI/CD pipelines with the secret
- [ ] Notify team that Keycloak authentication is ready
- [ ] Archive this checklist for future reference
