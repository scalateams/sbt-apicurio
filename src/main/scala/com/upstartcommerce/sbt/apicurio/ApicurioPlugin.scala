package com.upstartcommerce.sbt.apicurio

import com.upstartcommerce.sbt.apicurio.ApicurioModels._
import sbt.Keys._
import sbt._

import scala.util.{Failure, Success}

object ApicurioPlugin extends AutoPlugin {

  override def trigger = noTrigger

  object autoImport {
    // Settings
    val apicurioRegistryUrl = settingKey[String]("Apicurio Registry URL")
    val apicurioApiKey = settingKey[Option[String]]("Apicurio Registry API Key")
    val apicurioGroupId = settingKey[String]("Apicurio artifact group ID (required)")
    val apicurioCompatibilityLevel = settingKey[CompatibilityLevel]("Compatibility level for schema validation")
    val apicurioSchemaPaths = settingKey[Seq[File]]("Paths to schema files or directories")
    val apicurioPullOutputDir = settingKey[File]("Output directory for pulled schemas")
    val apicurioPullDependencies = settingKey[Seq[ApicurioDependency]]("Schema dependencies to pull from registry")

    // Tasks
    val apicurioPublish = taskKey[Unit]("Publish schemas to Apicurio Registry")
    val apicurioPull = taskKey[Seq[File]]("Pull schema dependencies from Apicurio Registry")
    val apicurioDiscoverSchemas = taskKey[Seq[SchemaFile]]("Discover all schema files")
    val apicurioValidateSettings = taskKey[Unit]("Validate Apicurio plugin settings")

    // Implicits for dependency DSL
    implicit class ApicurioDependencyOps(val groupId: String) extends AnyVal {
      def %(artifactId: String): ApicurioDependencyBuilder =
        ApicurioDependencyBuilder(groupId, artifactId)
    }

    case class ApicurioDependencyBuilder(groupId: String, artifactId: String) {
      def %(version: String): ApicurioDependency =
        ApicurioDependency(groupId, artifactId, version)
    }

    // Re-export models for easy access
    val CompatibilityLevel = ApicurioModels.CompatibilityLevel
    type CompatibilityLevel = ApicurioModels.CompatibilityLevel
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    // Default settings
    apicurioApiKey := None,
    apicurioCompatibilityLevel := CompatibilityLevel.Backward,
    apicurioSchemaPaths := Seq(sourceDirectory.value / "main" / "schemas"),
    apicurioPullOutputDir := target.value / "schemas",
    apicurioPullDependencies := Seq.empty,

    // Discovery task
    apicurioDiscoverSchemas := {
      val paths = apicurioSchemaPaths.value
      val log = streams.value.log
      SchemaFileUtils.discoverSchemas(paths, log)
    },

    // Validation task
    apicurioValidateSettings := {
      val log = streams.value.log
      val url = apicurioRegistryUrl.?.value
      val apiKey = apicurioApiKey.value
      val groupId = apicurioGroupId.?.value

      SchemaFileUtils.validateSettings(url, apiKey, groupId, log) match {
        case Right(_) =>
          log.info("Apicurio settings validated successfully")
        case Left(error) =>
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
        log.debug("No schema dependencies to pull")
        Seq.empty
      } else {
        SchemaFileUtils.validateSettings(url, apiKey, groupId, log) match {
          case Right((validUrl, validApiKey, _)) =>
            val client = new ApicurioClient(validUrl, validApiKey, log)
            try {
              log.info(s"Pulling ${dependencies.size} schema dependencies from Apicurio Registry")

              dependencies.flatMap { dep =>
                val version = if (dep.version == "latest") "latest" else dep.version

                client.getVersionContent(dep.groupId, dep.artifactId, version) match {
                  case Success(content) =>
                    SchemaFileUtils.saveSchema(outputDir, dep, content, log) match {
                      case Success(file) => Some(file)
                      case Failure(ex) =>
                        log.error(s"Failed to save schema ${dep.groupId}:${dep.artifactId}: ${ex.getMessage}")
                        None
                    }
                  case Failure(ex) =>
                    log.error(s"Failed to pull schema ${dep.groupId}:${dep.artifactId}:${dep.version}: ${ex.getMessage}")
                    None
                }
              }
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
          } else {
            val client = new ApicurioClient(validUrl, validApiKey, log)
            try {
              log.info(s"Publishing ${schemas.size} schemas to Apicurio Registry")
              log.info(s"Group ID: $validGroupId")
              log.info(s"Compatibility Level: ${compatLevel.value}")

              var published = 0
              var unchanged = 0
              var failed = 0

              schemas.foreach { schema =>
                val artifactId = SchemaFileUtils.extractArtifactId(schema)

                client.publishSchema(
                  validGroupId,
                  artifactId,
                  schema.artifactType,
                  schema.content,
                  compatLevel
                ) match {
                  case Success(Left(metadata)) =>
                    log.info(s"Created artifact: $artifactId (${schema.artifactType.value})")
                    published += 1
                  case Success(Right(version)) =>
                    if (version.version == "1") {
                      log.info(s"Created artifact: $artifactId version ${version.version}")
                      published += 1
                    } else {
                      log.info(s"Updated artifact: $artifactId version ${version.version}")
                      published += 1
                    }
                  case Failure(_: ApicurioException) if unchanged == 0 =>
                    // First time seeing unchanged, check if it's actually unchanged
                    unchanged += 1
                  case Failure(ex) =>
                    log.error(s"Failed to publish $artifactId: ${ex.getMessage}")
                    failed += 1
                }
              }

              log.info(s"Publishing complete: $published published, $unchanged unchanged, $failed failed")

              if (failed > 0) {
                sys.error(s"Failed to publish $failed schemas")
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
      val pullResult = apicurioPull.value
      (Compile / compile).value
    }
  )
}
