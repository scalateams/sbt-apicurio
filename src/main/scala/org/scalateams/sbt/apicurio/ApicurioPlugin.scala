package org.scalateams.sbt.apicurio

import org.scalateams.sbt.apicurio.ApicurioModels._
import sbt.Keys._
import sbt._

import scala.util.{Failure, Success}

object ApicurioPlugin extends AutoPlugin {

  override def trigger = noTrigger

  object autoImport {
    // Settings
    val apicurioRegistryUrl = settingKey[String]("Apicurio Registry base URL (e.g., https://registry.example.com)")
    val apicurioApiKey = settingKey[Option[String]]("Optional API key for Apicurio Registry authentication")
    val apicurioGroupId = settingKey[String]("Group ID for artifacts (e.g., com.example.yourservice)")
    val apicurioCompatibilityLevel = settingKey[CompatibilityLevel]("Schema compatibility level: Backward, Forward, Full, or None (default: Backward)")
    val apicurioSchemaPaths = settingKey[Seq[File]]("Paths to directories containing schema files (default: src/main/schemas)")
    val apicurioPullOutputDir = settingKey[File]("Directory to save pulled schemas (default: target/schemas)")
    val apicurioPullDependencies = settingKey[Seq[ApicurioDependency]]("List of schema dependencies to pull using schema(groupId, artifactId, version)")

    // Tasks
    val apicurioHelp = taskKey[Unit]("Display help information about the Apicurio plugin")
    val apicurioPublish = taskKey[Unit]("Publish schemas to Apicurio Registry")
    val apicurioPull = taskKey[Seq[File]]("Pull schema dependencies from Apicurio Registry")
    val apicurioDiscoverSchemas = taskKey[Seq[SchemaFile]]("Discover all schema files")
    val apicurioValidateSettings = taskKey[Unit]("Validate Apicurio plugin settings")

    // Re-export models for easy access
    val CompatibilityLevel = ApicurioModels.CompatibilityLevel
    type CompatibilityLevel = ApicurioModels.CompatibilityLevel

    /**
     * Helper method to create schema dependency declarations without operator conflicts.
     *
     * Use this method instead of the % operator to avoid conflicts with SBT's built-in operators:
     *
     * {{{
     * apicurioPullDependencies := Seq(
     *   schema("com.example.catalog", "CatalogItemCreated", "latest"),
     *   schema("com.example.tenant", "TenantCreated", "3")
     * )
     * }}}
     *
     * @param groupId Group ID of the schema (e.g., "com.example.catalog")
     * @param artifactId Artifact ID / schema name (e.g., "CatalogItemCreated")
     * @param version Version string or "latest"
     * @return ApicurioDependency for use in apicurioPullDependencies
     */
    def schema(groupId: String, artifactId: String, version: String): ApicurioDependency =
      ApicurioDependency(groupId, artifactId, version)
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    // Default settings
    apicurioApiKey := None,
    apicurioCompatibilityLevel := CompatibilityLevel.Backward,
    apicurioSchemaPaths := Seq(sourceDirectory.value / "main" / "schemas"),
    apicurioPullOutputDir := target.value / "schemas",
    apicurioPullDependencies := Seq.empty,

    // Help task
    apicurioHelp := {
      val log = streams.value.log

      log.info("")
      log.info("=" * 80)
      log.info("  sbt-apicurio - Apicurio Schema Registry Plugin")
      log.info("=" * 80)
      log.info("")
      log.info("OVERVIEW:")
      log.info("  SBT plugin for managing schemas with Apicurio Registry 3.x")
      log.info("")
      log.info("TASKS:")
      log.info("  apicurioHelp             - Display this help message")
      log.info("  apicurioPublish          - Publish schemas to Apicurio Registry")
      log.info("  apicurioPull             - Pull schema dependencies from registry")
      log.info("  apicurioDiscoverSchemas  - Discover all schema files in configured paths")
      log.info("  apicurioValidateSettings - Validate plugin configuration")
      log.info("")
      log.info("SETTINGS:")
      log.info("  apicurioRegistryUrl        - Registry URL (required)")
      log.info("  apicurioGroupId            - Artifact group ID (required)")
      log.info("  apicurioApiKey             - API key for authentication (optional)")
      log.info("  apicurioCompatibilityLevel - Schema compatibility level (default: Backward)")
      log.info("  apicurioSchemaPaths        - Schema file directories (default: src/main/schemas)")
      log.info("  apicurioPullOutputDir      - Output directory for pulled schemas (default: target/schemas)")
      log.info("  apicurioPullDependencies   - Schema dependencies to pull")
      log.info("")
      log.info("SUPPORTED SCHEMA TYPES:")
      log.info("  • Avro         (.avsc, .avro)")
      log.info("  • JSON Schema  (.json)")
      log.info("  • Protobuf     (.proto)")
      log.info("  • OpenAPI      (.yaml, .yml)")
      log.info("  • AsyncAPI     (.yaml, .yml)")
      log.info("")
      log.info("COMPATIBILITY LEVELS:")
      log.info("  • CompatibilityLevel.Backward  - Can read data written with previous schema")
      log.info("  • CompatibilityLevel.Forward   - Previous schema can read data written with new schema")
      log.info("  • CompatibilityLevel.Full      - Both backward and forward compatible")
      log.info("  • CompatibilityLevel.None      - No compatibility checking")
      log.info("")
      log.info("EXAMPLE CONFIGURATION:")
      log.info("")
      log.info("  // build.sbt")
      log.info("  enablePlugins(ApicurioPlugin)")
      log.info("")
      log.info("  apicurioRegistryUrl := \"https://your-registry.com\"")
      log.info("  apicurioGroupId := \"com.example.yourservice\"")
      log.info("  apicurioApiKey := sys.env.get(\"APICURIO_API_KEY\")")
      log.info("  apicurioCompatibilityLevel := CompatibilityLevel.Backward")
      log.info("")
      log.info("  apicurioSchemaPaths := Seq(")
      log.info("    sourceDirectory.value / \"main\" / \"schemas\"")
      log.info("  )")
      log.info("")
      log.info("  apicurioPullDependencies := Seq(")
      log.info("    schema(\"com.example.catalog\", \"ProductCreated\", \"latest\"),")
      log.info("    schema(\"com.example.tenant\", \"TenantCreated\", \"3\")")
      log.info("  )")
      log.info("")
      log.info("COMMON WORKFLOWS:")
      log.info("")
      log.info("  1. Validate configuration:")
      log.info("     sbt apicurioValidateSettings")
      log.info("")
      log.info("  2. Discover schemas:")
      log.info("     sbt apicurioDiscoverSchemas")
      log.info("")
      log.info("  3. Publish schemas:")
      log.info("     sbt apicurioPublish")
      log.info("")
      log.info("  4. Pull dependencies:")
      log.info("     sbt apicurioPull")
      log.info("")
      log.info("  5. Pull dependencies automatically before compile:")
      log.info("     sbt compile")
      log.info("")
      log.info("DOCUMENTATION:")
      log.info("  • GitHub:   https://github.com/scalateams/sbt-apicurio")
      log.info("  • README:   Full documentation and examples")
      log.info("  • TESTING:  Testing guide with integration tests")
      log.info("  • RELEASE:  Release process and versioning")
      log.info("")
      log.info("CURRENT CONFIGURATION:")

      val url = apicurioRegistryUrl.?.value
      val groupId = apicurioGroupId.?.value
      val apiKey = apicurioApiKey.value
      val compatLevel = apicurioCompatibilityLevel.value
      val schemaPaths = apicurioSchemaPaths.value
      val pullOutputDir = apicurioPullOutputDir.value
      val dependencies = apicurioPullDependencies.value

      log.info(s"  Registry URL:       ${url.getOrElse("[NOT SET - REQUIRED]")}")
      log.info(s"  Group ID:           ${groupId.getOrElse("[NOT SET - REQUIRED]")}")
      log.info(s"  API Key:            ${if (apiKey.isDefined) "[CONFIGURED]" else "[NOT SET]"}")
      log.info(s"  Compatibility:      ${compatLevel.value}")
      log.info(s"  Schema Paths:       ${schemaPaths.mkString(", ")}")
      log.info(s"  Pull Output Dir:    $pullOutputDir")
      log.info(s"  Dependencies:       ${dependencies.size} configured")

      if (dependencies.nonEmpty) {
        log.info("")
        log.info("  Configured Dependencies:")
        dependencies.foreach { dep =>
          log.info(s"    • ${dep.groupId}:${dep.artifactId}:${dep.version}")
        }
      }

      log.info("")

      SchemaFileUtils.validateSettings(url, apiKey, groupId, log) match {
        case Right(_) =>
          log.info("  Status: ✓ Configuration is valid")
        case Left(error) =>
          log.warn(s"  Status: ✗ Configuration incomplete - $error")
      }

      log.info("")
      log.info("=" * 80)
      log.info("")
    },

    // Discovery task
    apicurioDiscoverSchemas := {
      val paths = apicurioSchemaPaths.value
      val log = streams.value.log

      log.info(s"Discovering schemas in ${paths.size} path(s)...")
      val schemas = SchemaFileUtils.discoverSchemas(paths, log)

      if (schemas.isEmpty) {
        log.warn(s"No schemas found in configured paths: ${paths.mkString(", ")}")
        log.info("Tip: Run 'sbt apicurioHelp' to see configuration options")
      } else {
        log.info(s"Found ${schemas.size} schema file(s):")
        schemas.groupBy(_.artifactType).foreach { case (artifactType, files) =>
          log.info(s"  ${artifactType.value}: ${files.size} file(s)")
          files.foreach { schema =>
            log.info(s"    • ${schema.file.getName}")
          }
        }
      }

      schemas
    },

    // Validation task
    apicurioValidateSettings := {
      val log = streams.value.log
      val url = apicurioRegistryUrl.?.value
      val apiKey = apicurioApiKey.value
      val groupId = apicurioGroupId.?.value

      log.info("Validating Apicurio plugin configuration...")
      log.info(s"  Registry URL: ${url.getOrElse("[NOT SET]")}")
      log.info(s"  Group ID:     ${groupId.getOrElse("[NOT SET]")}")
      log.info(s"  API Key:      ${if (apiKey.isDefined) "[CONFIGURED]" else "[NOT SET]"}")

      SchemaFileUtils.validateSettings(url, apiKey, groupId, log) match {
        case Right((validUrl, _, validGroupId)) =>
          log.info("✓ Configuration is valid")
          log.info(s"  Ready to publish to: $validUrl")
          log.info(s"  Using group ID: $validGroupId")
        case Left(error) =>
          log.error("✗ Configuration is invalid")
          log.error(s"  Error: $error")
          log.info("")
          log.info("To fix this, add the following to your build.sbt:")
          log.info("")
          log.info("  enablePlugins(ApicurioPlugin)")
          log.info("  apicurioRegistryUrl := \"https://your-registry.com\"")
          log.info("  apicurioGroupId := \"com.example.yourservice\"")
          log.info("")
          log.info("Run 'sbt apicurioHelp' for more information")
          sys.error(s"Invalid Apicurio configuration: $error")
      }
    },

    // Pull task - runs before compile
    apicurioPull := {
      val log = streams.value.log
      val url = apicurioRegistryUrl.?.value
      val apiKey = apicurioApiKey.value
      val groupId = apicurioGroupId.?.value
      val outputDir = apicurioPullOutputDir.value
      val dependencies = apicurioPullDependencies.value

      if (dependencies.isEmpty) {
        log.debug("No schema dependencies configured")
        log.debug("Tip: Add dependencies in build.sbt with: apicurioPullDependencies := Seq(schema(\"groupId\", \"artifactId\", \"version\"))")
        Seq.empty
      } else {
        SchemaFileUtils.validateSettings(url, apiKey, groupId, log) match {
          case Right((validUrl, validApiKey, _)) =>
            val client = new ApicurioClient(validUrl, validApiKey, log)
            try {
              log.info(s"Pulling ${dependencies.size} schema dependencies from Apicurio Registry")

              val pulledFiles = dependencies.flatMap { dep =>
                val version = if (dep.version == "latest") "latest" else dep.version

                client.getVersionContent(dep.groupId, dep.artifactId, version) match {
                  case Success(content) =>
                    SchemaFileUtils.saveSchema(outputDir, dep, content, log) match {
                      case Success(file) =>
                        log.info(s"✓ Pulled: ${dep.groupId}:${dep.artifactId}:${dep.version}")
                        Some(file)
                      case Failure(ex) =>
                        log.error(s"✗ Failed to save schema ${dep.groupId}:${dep.artifactId}: ${ex.getMessage}")
                        None
                    }
                  case Failure(ex) =>
                    log.error(s"✗ Failed to pull schema ${dep.groupId}:${dep.artifactId}:${dep.version}: ${ex.getMessage}")
                    None
                }
              }

              val successCount = pulledFiles.size
              val failCount = dependencies.size - successCount

              if (successCount > 0) {
                log.info(s"Successfully pulled $successCount schema(s) to $outputDir")
              }
              if (failCount > 0) {
                log.warn(s"Failed to pull $failCount schema(s)")
              }

              pulledFiles
            } finally {
              client.close()
            }
          case Left(error) =>
            sys.error(s"Cannot pull schemas: $error")
        }
      }
    },

    // Publish task
    apicurioPublish := {
      val log = streams.value.log
      val url = apicurioRegistryUrl.?.value
      val apiKey = apicurioApiKey.value
      val groupId = apicurioGroupId.?.value
      val compatLevel = apicurioCompatibilityLevel.value
      val schemas = apicurioDiscoverSchemas.value

      SchemaFileUtils.validateSettings(url, apiKey, groupId, log) match {
        case Right((validUrl, validApiKey, validGroupId)) =>
          if (schemas.isEmpty) {
            log.warn("No schemas found to publish")
            log.info("Tip: Check your apicurioSchemaPaths setting or run 'sbt apicurioDiscoverSchemas'")
            log.info("Default schema path: src/main/schemas")
          } else {
            val client = new ApicurioClient(validUrl, validApiKey, log)
            try {
              log.info(s"Publishing ${schemas.size} schemas to Apicurio Registry")
              log.info(s"Group ID: $validGroupId")
              log.info(s"Compatibility Level: ${compatLevel.value}")

              // Detect references in all schemas
              log.debug("Detecting schema references...")
              val schemasWithRefs = schemas.map { schema =>
                val artifactId = SchemaFileUtils.extractArtifactId(schema)
                SchemaReferenceUtils.detectReferences(schema, artifactId, log)
              }.toList

              // Check for references and log
              val hasReferences = schemasWithRefs.exists(_.references.nonEmpty)
              if (hasReferences) {
                log.info("Schema dependencies detected:")
                schemasWithRefs.filter(_.references.nonEmpty).foreach { s =>
                  log.info(s"  ${s.artifactId} depends on: ${s.references.flatMap(_.artifactId).mkString(", ")}")
                }
              }

              // Order schemas by dependencies
              val orderedSchemas: List[SchemaReferenceUtils.SchemaWithReferences] = SchemaReferenceUtils.orderSchemasByDependencies(schemasWithRefs, log) match {
                case Success(ordered) =>
                  if (hasReferences) {
                    log.info(s"Publishing in dependency order: ${ordered.map(_.artifactId).mkString(" → ")}")
                  }
                  ordered
                case Failure(ex) =>
                  log.warn(s"Could not order schemas by dependencies: ${ex.getMessage}")
                  log.warn("Publishing in discovery order (may fail if dependencies not published)")
                  schemasWithRefs
              }

              // Create schema map for reference resolution
              val schemaMap = orderedSchemas.map(s => s.artifactId -> s).toMap

              var published = 0
              var unchanged = 0
              var failed = 0

              orderedSchemas.foreach { schemaWithRefs =>
                val artifactId = schemaWithRefs.artifactId
                val schema = schemaWithRefs.schema

                // Resolve references to ContentReference objects
                val refs = SchemaReferenceUtils.resolveReferences(
                  schemaWithRefs.references,
                  validGroupId,
                  schemaMap,
                  log
                )

                client.publishSchema(
                  validGroupId,
                  artifactId,
                  schema.artifactType,
                  schema.content,
                  compatLevel,
                  refs
                ) match {
                  case Success(Left(createResponse)) =>
                    // New artifact created - extract version info from response
                    log.info(s"✓ Created: $artifactId (${schema.artifactType.value}) version ${createResponse.version.version}")
                    published += 1
                  case Success(Right(version)) =>
                    if (version.version == "1") {
                      log.info(s"✓ Created: $artifactId version ${version.version}")
                      published += 1
                    } else {
                      log.info(s"✓ Updated: $artifactId version ${version.version}")
                      published += 1
                    }
                  case Failure(_: ApicurioException) if unchanged == 0 =>
                    // First time seeing unchanged, check if it's actually unchanged
                    log.debug(s"- Unchanged: $artifactId")
                    unchanged += 1
                  case Failure(ex) =>
                    log.error(s"✗ Failed: $artifactId - ${ex.getMessage}")
                    failed += 1
                }
              }

              log.info("")
              log.info("Publishing Summary:")
              if (published > 0) log.info(s"  ✓ Published:  $published schema(s)")
              if (unchanged > 0) log.info(s"  - Unchanged:  $unchanged schema(s)")
              if (failed > 0) log.error(s"  ✗ Failed:     $failed schema(s)")
              log.info("")

              if (failed > 0) {
                sys.error(s"Failed to publish $failed schemas. Check error messages above.")
              }
            } finally {
              client.close()
            }
          }
        case Left(error) =>
          sys.error(s"Cannot publish schemas: $error")
      }
    },

    // Hook pull into compile
    Compile / compile := {
      apicurioPull.value
      (Compile / compile).value
    }
  )
}
