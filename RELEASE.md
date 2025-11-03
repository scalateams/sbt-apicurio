# Release Process

This project uses automated releases via [sbt-ci-release](https://github.com/sbt/sbt-ci-release) and GitHub Actions. Versions are managed automatically based on git tags.

## Automated Releases

The project is configured to automatically publish to Maven Central (via Sonatype) when tags are pushed to GitHub.

## Prerequisites (Maintainers)

Before you can make releases, the following secrets must be configured in GitHub repository settings:

### 1. Sonatype Account

Create an account at https://issues.sonatype.org and request access to the `org.scalateams` groupId.

Add these secrets to GitHub:
- `SONATYPE_USERNAME` - Your Sonatype JIRA username
- `SONATYPE_PASSWORD` - Your Sonatype JIRA password

### 2. GPG Key for Signing

Generate a GPG key pair:

```bash
# Generate key (use RSA 4096-bit)
gpg --gen-key

# List keys to get the key ID
gpg --list-keys

# Export the secret key (replace KEYID with your key ID)
gpg --armor --export-secret-keys KEYID | base64 | pbcopy
```

Add these secrets to GitHub:
- `PGP_SECRET` - Base64 encoded GPG private key (output from command above)
- `PGP_PASSPHRASE` - The passphrase for your GPG key

### 3. Publish GPG Public Key

```bash
# Replace KEYID with your key ID
gpg --keyserver hkps://keys.openpgp.org --send-keys KEYID
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys KEYID
```

## Making a Release

### 1. Verify Current State

```bash
# Ensure you're on main branch
git checkout main
git pull origin main

# Verify tests pass
sbt test

# Check what version will be assigned (based on git describe)
git describe --tags
```

### 2. Create and Push a Tag

The version number is determined by git tags. Use semantic versioning:

```bash
# For a new feature (minor version bump)
git tag -a v0.2.0 -m "Release version 0.2.0"

# For a bug fix (patch version bump)
git tag -a v0.1.1 -m "Release version 0.1.1"

# For a major breaking change
git tag -a v1.0.0 -m "Release version 1.0.0"

# Push the tag
git push origin v0.2.0
```

### 3. Automated Release Process

Once the tag is pushed:

1. GitHub Actions workflow is triggered
2. The project is built and tested
3. Artifacts are signed with GPG
4. Published to Sonatype OSS repository
5. Automatically released to Maven Central (if all checks pass)

Monitor the release at: https://github.com/scalateams/sbt-apicurio/actions

### 4. Verify Release

After ~10 minutes, verify the release:

- Check Sonatype: https://s01.oss.sonatype.org/content/repositories/releases/org/scalateams/sbt-apicurio_2.12_1.0/
- Check Maven Central (may take 2+ hours): https://repo1.maven.org/maven2/org/scalateams/sbt-apicurio_2.12_1.0/
- Search Maven Central: https://search.maven.org/search?q=g:org.scalateams%20AND%20a:sbt-apicurio

## Semantic Versioning

This project uses [Semantic Versioning](https://semver.org/):

- **MAJOR** version (X.0.0): Incompatible API changes
- **MINOR** version (0.X.0): New functionality in a backward compatible manner
- **PATCH** version (0.0.X): Backward compatible bug fixes

### Pre-release Versions

For pre-release versions, use tags like:

- `v1.0.0-M1` - Milestone 1
- `v1.0.0-RC1` - Release Candidate 1
- `v1.0.0-alpha.1` - Alpha version

## Snapshot Releases

Snapshot versions are automatically published on every push to the `main` branch:

- Version format: `0.1.0+3-1234abcd-SNAPSHOT` (based on git describe)
- Published to Sonatype snapshots repository
- Not synced to Maven Central

Users can access snapshots by adding the resolver:

```scala
resolvers += Resolver.sonatypeRepo("snapshots")
```

## Version Strategy

sbt-ci-release uses git tags and git describe to compute versions:

- If the current commit is tagged: use that version (e.g., `0.2.0`)
- If the current commit is not tagged: create a snapshot version (e.g., `0.1.0+3-1234abcd-SNAPSHOT`)

This means:
- **Every git tag triggers a release**
- **Every commit to main creates a snapshot**

## Troubleshooting

### Release Failed

Check the GitHub Actions logs for errors:
- GPG key issues: Verify `PGP_SECRET` and `PGP_PASSPHRASE` are correct
- Sonatype issues: Verify credentials and that you have access to `org.scalateams`
- Build errors: Fix and create a new tag

### Version Already Exists

If you accidentally pushed a tag for a version that already exists:

```bash
# Delete the tag locally
git tag -d v0.2.0

# Delete the tag remotely
git push --delete origin v0.2.0

# Create a new tag with the next version
git tag -a v0.2.1 -m "Release version 0.2.1"
git push origin v0.2.1
```

### Manual Release (Emergency)

If automated release fails, you can release manually:

```bash
# Set environment variables
export PGP_PASSPHRASE="your-passphrase"
export PGP_SECRET="your-base64-encoded-key"
export SONATYPE_USERNAME="your-username"
export SONATYPE_PASSWORD="your-password"

# Run release
sbt ci-release
```

## Release Checklist

Before creating a release tag:

- [ ] All tests pass (`sbt test`)
- [ ] Documentation is updated
- [ ] CHANGELOG is updated (if exists)
- [ ] Version bump is appropriate (major/minor/patch)
- [ ] Commit message is clear
- [ ] CI is green on main branch

## Post-Release

After a successful release:

1. Create a GitHub Release with release notes
2. Announce the release (if applicable)
3. Update any dependent projects
4. Monitor for issues

## Resources

- [sbt-ci-release documentation](https://github.com/sbt/sbt-ci-release)
- [Sonatype OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [Semantic Versioning](https://semver.org/)
