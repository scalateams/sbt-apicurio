# Contributing to sbt-apicurio

Thank you for your interest in contributing to sbt-apicurio! This document provides guidelines and instructions for contributing to this project.

## Code of Conduct

By participating in this project, you are expected to uphold our commitment to providing a welcoming and inclusive environment for everyone.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When you create a bug report, include as many details as possible:

- **Use a clear and descriptive title**
- **Describe the exact steps to reproduce the problem**
- **Provide specific examples** - Include code snippets, configuration files, or error messages
- **Describe the behavior you observed** and what you expected to see
- **Include environment details**:
  - SBT version
  - Scala version
  - Operating system
  - Apicurio Registry version
  - Plugin version

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion:

- **Use a clear and descriptive title**
- **Provide a detailed description** of the suggested enhancement
- **Explain why this enhancement would be useful** to most users
- **List any similar features** in other projects if applicable

### Pull Requests

1. **Fork the repository** and create your branch from `main`
2. **Make your changes**:
   - Write clear, concise commit messages
   - Follow the existing code style
   - Add tests for new functionality
   - Update documentation as needed
3. **Test your changes**:
   ```bash
   sbt clean compile test
   ```
4. **Ensure the build passes** on both your local machine and CI
5. **Submit a pull request**

#### Pull Request Guidelines

- Keep pull requests focused on a single feature or bug fix
- Update the README.md with details of changes if applicable
- Include relevant issue numbers in the PR description
- Ensure all tests pass and no warnings are introduced
- Add tests for new functionality
- Follow Scala best practices and conventions

## Development Setup

### Prerequisites

- JDK 11 or 17
- SBT 1.x
- Git

### Building Locally

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/sbt-apicurio.git
cd sbt-apicurio

# Build the project
sbt compile

# Run tests
sbt test

# Publish to your local Ivy repository for testing
sbt publishLocal
```

### Testing Your Changes

To test the plugin in another project:

1. Publish the plugin locally: `sbt publishLocal`
2. In your test project's `project/plugins.sbt`:
   ```scala
   addSbtPlugin("org.scalateams" % "sbt-apicurio" % "0.1.0-SNAPSHOT")
   ```
3. Test the plugin functionality in your test project

## Code Style

- Follow standard Scala conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions focused and concise
- Use ScalaFmt if configured (check for `.scalafmt.conf`)

## Commit Messages

- Use the present tense ("Add feature" not "Added feature")
- Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
- Limit the first line to 72 characters or less
- Reference issues and pull requests after the first line

Example:
```
Add support for custom schema validation rules

- Implement ValidationRule trait
- Add configuration for custom validators
- Update documentation

Closes #123
```

## Documentation

- Update the README.md for user-facing changes
- Add inline documentation for new public APIs
- Include examples for new features
- Update configuration examples if settings change

## Testing

- Write unit tests for new functionality
- Ensure existing tests pass: `sbt test`
- Add integration tests for complex features
- Test against different SBT and Scala versions if possible

## Continuous Integration

This project uses:
- **GitHub Actions** - Automated testing and releases
- **Scala Steward** - Automated dependency updates

All pull requests must pass CI checks before merging.

## Automated Dependency Updates

This project uses [Scala Steward](https://github.com/scala-steward-org/scala-steward) for automated dependency updates. 

- Scala Steward PRs are labeled with `scala-steward` and `dependencies`
- Patch updates are auto-merged if CI passes
- Minor/major updates require manual review
- Check the `.scala-steward.conf` file for configuration

## Release Process

(Maintainers only)

This project uses automated releases via sbt-ci-release. For detailed release instructions, see [RELEASE.md](RELEASE.md).

Quick overview:
1. Ensure all tests pass and CI is green
2. Create and push a git tag: `git tag -a v0.x.x -m "Release version 0.x.x" && git push origin v0.x.x`
3. GitHub Actions will automatically publish to Maven Central

Versions are managed automatically based on git tags using semantic versioning.

## Getting Help

- Check the [README.md](README.md) for usage documentation
- Search [existing issues](https://github.com/scalateams/sbt-apicurio/issues)
- Ask questions by opening a new issue with the `question` label

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

Thank you for contributing to sbt-apicurio! 🎉
