# Publishing v0.1.0 - Complete Guide

This document details the complete process for publishing version 0.1.0 of sbt-apicurio to Maven Central.

## One-Time Setup (Prerequisites)

These steps only need to be done once by a project maintainer:

### 1. Sonatype Account Setup

```bash
# 1. Create a Sonatype JIRA account
# Visit: https://issues.sonatype.org/secure/Signup!default.jspa

# 2. Create a ticket to claim the org.scalateams groupId
# Visit: https://issues.sonatype.org/secure/CreateIssue.jspa
# - Project: Community Support - Open Source Project Repository Hosting (OSSRH)
# - Issue Type: New Project
# - Group Id: org.scalateams
# - Project URL: https://github.com/scalateams/sbt-apicurio
# - SCM URL: https://github.com/scalateams/sbt-apicurio.git
```

You'll need to verify domain ownership (or they'll accept GitHub org ownership). This typically takes 1-2 business days.

### 2. Generate GPG Key for Signing

```bash
# Generate a GPG key (use RSA 4096-bit)
gpg --gen-key
# Follow prompts:
# - Real name: Your Name (or ScalaTeams)
# - Email: your@email.com
# - Set a strong passphrase (you'll need this later)

# List your keys to get the key ID
gpg --list-keys
# Output will show something like:
# pub   rsa4096 2025-11-03 [SC]
#       ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234  <- This is your KEYID
# uid   [ultimate] Your Name <your@email.com>

# Export the secret key as base64
gpg --armor --export-secret-keys ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234 > private-key.asc
cat private-key.asc | base64 > private-key-base64.txt

# Publish your public key to key servers
gpg --keyserver hkps://keys.openpgp.org --send-keys ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234
```

**Important:** Save your passphrase securely - you'll need it for GitHub secrets.

### 3. Add GitHub Secrets

Go to: https://github.com/scalateams/sbt-apicurio/settings/secrets/actions

Add these four secrets:

- **`SONATYPE_USERNAME`** - Your Sonatype JIRA username
- **`SONATYPE_PASSWORD`** - Your Sonatype JIRA password
- **`PGP_SECRET`** - Contents of `private-key-base64.txt` (the base64 encoded GPG key)
- **`PGP_PASSPHRASE`** - The passphrase you set when creating the GPG key

### 4. Wait for Sonatype Approval

After creating the JIRA ticket, Sonatype will:
1. Review your request
2. May ask you to verify GitHub org ownership
3. Approve access to `org.scalateams` groupId

**Timeline:** 1-2 business days

You'll receive an email notification when approved.

## Release v0.1.0 - Step-by-Step

Once the prerequisites are complete, follow these steps:

### Step 1: Merge the Feature Branch

**Option A: Create a Pull Request and merge via GitHub**

1. Visit: https://github.com/scalateams/sbt-apicurio/pull/new/feature/repackage-org-scalateams-with-apicurio3-support
2. Review the changes
3. Approve and merge to main

**Option B: Merge locally (if you have write access)**

```bash
git checkout main
git pull origin main
git merge feature/repackage-org-scalateams-with-apicurio3-support
git push origin main
```

### Step 2: Verify Everything is Ready

```bash
# Switch to main branch (if not already)
git checkout main
git pull origin main

# Verify tests pass
sbt test

# Check current state
git log --oneline -5
git status

# Verify you're on a clean main branch
git diff origin/main
```

### Step 3: Create and Push the v0.1.0 Tag

```bash
# Create an annotated tag for v0.1.0
git tag -a v0.1.0 -m "Release version 0.1.0

Initial public release of sbt-apicurio with org.scalateams packaging.

Features:
- Full Apicurio Registry 3.x API support
- Support for Avro, JSON Schema, Protobuf, OpenAPI, and AsyncAPI
- Automated schema publishing and pulling
- Compatibility checking
- Version management
- Comprehensive integration test suite
- Apache 2.0 license
"

# Verify the tag was created
git tag -l -n9 v0.1.0

# Push the tag to trigger the release
git push origin v0.1.0
```

**Important:** Pushing the tag will automatically trigger the GitHub Actions release workflow.

### Step 4: Monitor the Release

The GitHub Actions workflow will automatically:

1. Build the project
2. Run tests
3. Sign artifacts with GPG
4. Publish to Sonatype staging repository
5. Release to Maven Central

**Monitor progress:**
- https://github.com/scalateams/sbt-apicurio/actions
- Look for the "Release" workflow run triggered by the v0.1.0 tag

**Typical duration:** 5-10 minutes

### Step 5: Verify the Release

After the GitHub Actions workflow completes successfully:

**1. Check Sonatype (available within ~10 minutes)**

```bash
# Sonatype Releases Repository
curl -I https://s01.oss.sonatype.org/content/repositories/releases/org/scalateams/sbt-apicurio_2.12_1.0/0.1.0/
```

Or visit:
- https://s01.oss.sonatype.org/content/repositories/releases/org/scalateams/sbt-apicurio_2.12_1.0/0.1.0/

**2. Check Maven Central (may take 2-4 hours to sync)**

```bash
# Maven Central Repository
curl -I https://repo1.maven.org/maven2/org/scalateams/sbt-apicurio_2.12_1.0/0.1.0/
```

Or visit:
- https://repo1.maven.org/maven2/org/scalateams/sbt-apicurio_2.12_1.0/0.1.0/

**3. Search Maven Central**

Visit: https://search.maven.org/search?q=g:org.scalateams%20AND%20a:sbt-apicurio

The plugin should appear in search results after Maven Central sync completes.

### Step 6: Create GitHub Release

Once verified on Maven Central, create a GitHub Release:

1. Visit: https://github.com/scalateams/sbt-apicurio/releases/new
2. Select tag: `v0.1.0`
3. Title: `v0.1.0 - Initial Release`
4. Description (suggested):

```markdown
## sbt-apicurio v0.1.0 - Initial Release

Initial public release of sbt-apicurio, an SBT plugin for Apicurio Schema Registry 3.x.

### Features

- ✅ Full Apicurio Registry 3.x API support
- ✅ Support for multiple schema types:
  - Avro (.avsc, .avro)
  - JSON Schema (.json)
  - Protobuf (.proto)
  - OpenAPI (.yaml, .yml)
  - AsyncAPI (.yaml, .yml)
- ✅ Automated schema publishing with hash-based change detection
- ✅ Schema dependency pulling from registry
- ✅ Compatibility level checking (Backward, Forward, Full, None)
- ✅ Version management
- ✅ Comprehensive integration test suite (17 tests)
- ✅ Apache 2.0 license

### Installation

Add to your `project/plugins.sbt`:

```scala
addSbtPlugin("org.scalateams" % "sbt-apicurio" % "0.1.0")
```

### Quick Start

```scala
// build.sbt
enablePlugins(ApicurioPlugin)

apicurioRegistryUrl := "https://your-registry.com"
apicurioGroupId := "com.example.yourservice"
apicurioApiKey := sys.env.get("APICURIO_API_KEY")

// Publish schemas
sbt apicurioPublish

// Pull dependencies
apicurioPullDependencies := Seq(
  schema("com.example.catalog", "ProductCreated", "latest")
)
sbt apicurioPull
```

### Documentation

- [README.md](README.md) - Full documentation
- [TESTING.md](TESTING.md) - Testing guide
- [LOCAL_USAGE.md](LOCAL_USAGE.md) - Local development
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines

### Package Migration

This is a repackaged version of the internal `com.upstartcommerce.sbt.apicurio` plugin, now open-sourced under `org.scalateams` with Apache 2.0 license.

### Verified Against

- Apicurio Registry 3.0.x
- SBT 1.x
- Scala 2.12
```

5. Publish the release

### Step 7: Announce and Update

**Update the README:**

```bash
# Update the README.md installation section to reference v0.1.0
git checkout main
# Edit README.md to update version numbers from snapshot to 0.1.0
git commit -m "docs: update README with v0.1.0 installation instructions"
git push origin main
```

**Optional: Announce the release**
- Blog post
- Social media (Twitter, LinkedIn)
- Scala community forums
- ScalaTeams website

## Using the Released Plugin

Once published to Maven Central, users can use it:

```scala
// project/plugins.sbt
addSbtPlugin("org.scalateams" % "sbt-apicurio" % "0.1.0")
```

```scala
// build.sbt
enablePlugins(ApicurioPlugin)

apicurioRegistryUrl := "https://your-apicurio-registry.com"
apicurioGroupId := "com.example.yourservice"
apicurioApiKey := sys.env.get("APICURIO_API_KEY")
```

## Troubleshooting

### Release Failed

**Check GitHub Actions logs:** https://github.com/scalateams/sbt-apicurio/actions

**Common issues:**

#### GPG Signing Failed
```
Error: gpg: signing failed: No secret key
```

**Solution:** Verify `PGP_SECRET` and `PGP_PASSPHRASE` are correct in GitHub secrets.

#### Sonatype Authentication Failed
```
Error: Unauthorized (401)
```

**Solution:**
- Verify `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` are correct
- Ensure your Sonatype account is activated
- Check that you have access to `org.scalateams` groupId

#### GroupId Not Approved
```
Error: 403 Forbidden - Not authorized for groupId: org.scalateams
```

**Solution:** Wait for Sonatype JIRA ticket approval (step 1 of prerequisites).

#### Tests Failed
```
Error: Tests failed
```

**Solution:**
- Fix the failing tests
- Create a new tag with a patch version: v0.1.1

### Delete a Tag (If Needed)

If you need to delete a tag and re-release:

```bash
# Delete the tag locally
git tag -d v0.1.0

# Delete the tag remotely
git push --delete origin v0.1.0

# Fix the issues, then create a new tag
git tag -a v0.1.1 -m "Release version 0.1.1"
git push origin v0.1.1
```

**Note:** You cannot re-publish the same version to Maven Central. Always use a new version number.

### Manual Release (Emergency)

If automated release fails completely, you can release manually:

```bash
# Set environment variables
export PGP_PASSPHRASE="your-passphrase"
export PGP_SECRET="your-base64-encoded-key"
export SONATYPE_USERNAME="your-username"
export SONATYPE_PASSWORD="your-password"

# Checkout the tag
git checkout v0.1.0

# Run release
sbt ci-release
```

## Timeline Summary

**One-Time Setup:**
- Sonatype account creation: 5 minutes
- Sonatype groupId approval: 1-2 business days
- GPG key generation: 10 minutes
- GitHub secrets setup: 5 minutes

**Release Process:**
- Merge and tag: 5 minutes
- GitHub Actions build: 5-10 minutes
- Sonatype availability: ~10 minutes after build
- Maven Central sync: 2-4 hours

**Total time for first release:** 2-3 days (mostly waiting for Sonatype)

**Subsequent releases:** ~5 minutes (just push a tag!)

## Release Checklist

Before creating the v0.1.0 tag:

- [ ] All tests pass (`sbt test`)
- [ ] Feature branch merged to main
- [ ] Main branch is up to date
- [ ] CI is green on main branch
- [ ] Sonatype account approved (one-time)
- [ ] GPG keys configured (one-time)
- [ ] GitHub secrets added (one-time)
- [ ] Documentation is up to date
- [ ] README reflects v0.1.0 features

After creating the v0.1.0 tag:

- [ ] Monitor GitHub Actions workflow
- [ ] Verify release on Sonatype
- [ ] Verify sync to Maven Central
- [ ] Create GitHub Release with notes
- [ ] Update README with v0.1.0 examples
- [ ] Announce release (optional)

## Post-Release

After a successful v0.1.0 release:

1. **Update documentation** - Ensure all docs reference v0.1.0 instead of snapshots
2. **Test in a real project** - Verify the published plugin works correctly
3. **Monitor issues** - Watch GitHub issues for any problems
4. **Plan v0.2.0** - Start planning next features

## Next Releases

For future releases (v0.2.0, v0.3.0, etc.), the process is much simpler:

```bash
# 1. Ensure main is up to date with your changes
git checkout main
git pull origin main

# 2. Run tests
sbt test

# 3. Create and push tag
git tag -a v0.2.0 -m "Release version 0.2.0 - [describe changes]"
git push origin v0.2.0

# 4. Wait ~10 minutes for automated release
# 5. Create GitHub Release with notes
```

## Resources

- [sbt-ci-release documentation](https://github.com/sbt/sbt-ci-release)
- [Sonatype OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [Semantic Versioning](https://semver.org/)
- [GPG Documentation](https://www.gnupg.org/documentation/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
