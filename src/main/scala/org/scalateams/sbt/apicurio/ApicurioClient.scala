package org.scalateams.sbt.apicurio

import org.scalateams.sbt.apicurio.ApicurioModels._
import io.circe.parser._
import sbt.util.Logger
import sttp.client3._
import sttp.client3.circe._

import java.security.MessageDigest

class ApicurioClient(
  registryUrl: String,
  apiKey: Option[String],
  logger: Logger
) {

  private val backend = HttpURLConnectionBackend()
  private val baseUri = uri"$registryUrl"

  private def authHeaders: Map[String, String] = {
    apiKey.map(key => Map("Authorization" -> s"Bearer $key")).getOrElse(Map.empty)
  }

  private def contentTypeForArtifactType(artifactType: ArtifactType): String = artifactType match {
    case ArtifactType.Protobuf => "application/x-protobuf"
    case _ => "application/json"
  }

  def close(): Unit = backend.close()

  /**
   * Get artifact metadata
   */
  def getArtifactMetadata(groupId: String, artifactId: String): ApicurioResult[ArtifactMetadata] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId"

    logger.debug(s"Getting artifact metadata: $groupId:$artifactId")

    val request = basicRequest
      .get(url)
      .headers(authHeaders)
      .response(asJson[ArtifactMetadata])

    try {
      val response = request.send(backend)
      response.body match {
        case Right(metadata) => Right(metadata)
        case Left(error) if response.code.code == 404 =>
          Left(ApicurioError.ArtifactNotFound(groupId, artifactId))
        case Left(error) =>
          Left(ApicurioError.HttpError(response.code.code, error.getMessage))
      }
    } catch {
      case ex: Exception => Left(ApicurioError.NetworkError(ex))
    }
  }

  /**
   * Get latest version of an artifact
   */
  def getLatestVersion(groupId: String, artifactId: String): ApicurioResult[VersionMetadata] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/versions"

    logger.debug(s"Getting latest version: $groupId:$artifactId")

    val request = basicRequest
      .get(url)
      .headers(authHeaders)
      .response(asString)

    try {
      val response = request.send(backend)
      response.body match {
        case Right(body) =>
          parse(body).flatMap(_.hcursor.downField("versions").as[List[VersionMetadata]]) match {
            case Right(versions) if versions.nonEmpty =>
              Right(versions.maxBy(_.version))
            case Right(_) =>
              Left(ApicurioError.ArtifactNotFound(groupId, artifactId))
            case Left(error) =>
              Left(ApicurioError.ParseError(s"Failed to parse versions: ${error.getMessage}"))
          }
        case Left(error) if response.code.code == 404 =>
          Left(ApicurioError.ArtifactNotFound(groupId, artifactId))
        case Left(error) =>
          Left(ApicurioError.HttpError(response.code.code, error))
      }
    } catch {
      case ex: Exception => Left(ApicurioError.NetworkError(ex))
    }
  }

  /**
   * Get specific version content
   */
  def getVersionContent(groupId: String, artifactId: String, version: String): ApicurioResult[String] = {
    // If "latest" is requested, resolve it to the actual latest version number first
    if (version == "latest") {
      getLatestVersion(groupId, artifactId).flatMap { latestVersionMeta =>
        getVersionContent(groupId, artifactId, latestVersionMeta.version)
      }
    } else {
      val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/versions/$version/content"

      logger.debug(s"Getting version content: $groupId:$artifactId:$version")

      val request = basicRequest
        .get(url)
        .headers(authHeaders ++ Map("Accept" -> "application/json"))
        .response(asString)

      try {
        val response = request.send(backend)
        response.body match {
          case Right(content) => Right(content)
          case Left(error) if response.code.code == 404 =>
            Left(ApicurioError.VersionNotFound(groupId, artifactId, version))
          case Left(error) =>
            Left(ApicurioError.HttpError(response.code.code, error))
        }
      } catch {
        case ex: Exception => Left(ApicurioError.NetworkError(ex))
      }
    }
  }

  /**
   * Create a new artifact
   * Returns both artifact and version metadata
   */
  def createArtifact(
    groupId: String,
    artifactId: String,
    artifactType: ArtifactType,
    content: String,
    references: List[ContentReference] = List.empty
  ): ApicurioResult[CreateArtifactResponse] = {
    val url = uri"$baseUri/groups/$groupId/artifacts"

    logger.info(s"Creating artifact: $groupId:$artifactId (${artifactType.value})")

    // Validate content is valid JSON for JSON-based schema types
    // Protobuf schemas are not JSON, so skip validation for those
    if (artifactType != ArtifactType.Protobuf) {
      parse(content) match {
        case Left(error) =>
          return Left(ApicurioError.InvalidSchema(s"Failed to parse schema content as JSON: ${error.getMessage}"))
        case Right(_) => // Valid JSON, continue
      }
    }

    // Create the request body according to Apicurio 3.x API spec
    val requestBody = CreateArtifactRequest(
      artifactId = artifactId,
      artifactType = artifactType.value,
      firstVersion = FirstVersionRequest(
        version = None, // Let Apicurio assign version
        content = ContentRequest(
          content = content, // Content as string (JSON for most types, raw proto for Protobuf)
          contentType = contentTypeForArtifactType(artifactType),
          references = references
        )
      )
    )

    if (references.nonEmpty) {
      logger.debug(s"Creating artifact with ${references.size} reference(s): ${references.map(_.artifactId).mkString(", ")}")
    }

    val request = basicRequest
      .post(url)
      .headers(authHeaders ++ Map("Content-Type" -> "application/json"))
      .body(requestBody)
      .response(asJson[CreateArtifactResponse])

    try {
      val response = request.send(backend)
      response.body match {
        case Right(createResponse) => Right(createResponse)
        case Left(error) =>
          Left(ApicurioError.HttpError(response.code.code, error.getMessage))
      }
    } catch {
      case ex: Exception => Left(ApicurioError.NetworkError(ex))
    }
  }

  /**
   * Create a new version of an existing artifact
   */
  def createVersion(
    groupId: String,
    artifactId: String,
    content: String,
    references: List[ContentReference] = List.empty
  ): ApicurioResult[VersionMetadata] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/versions"

    logger.info(s"Creating new version: $groupId:$artifactId")

    // Get artifact metadata to determine type
    val artifactType = getArtifactMetadata(groupId, artifactId) match {
      case Right(metadata) => ArtifactType.fromString(metadata.artifactType)
      case Left(_) => None
    }
    val isProtobuf = artifactType.contains(ArtifactType.Protobuf)

    // Validate content is valid JSON for JSON-based schema types
    if (!isProtobuf) {
      parse(content) match {
        case Left(error) =>
          return Left(ApicurioError.InvalidSchema(s"Failed to parse schema content as JSON: ${error.getMessage}"))
        case Right(_) => // Valid JSON, continue
      }
    }

    // Create the request body according to Apicurio 3.x API spec
    val requestBody = CreateVersionRequest(
      version = None, // Let Apicurio auto-increment
      content = ContentRequest(
        content = content, // Content as string
        contentType = artifactType.map(contentTypeForArtifactType).getOrElse("application/json"),
        references = references
      )
    )

    if (references.nonEmpty) {
      logger.debug(s"Creating version with ${references.size} reference(s): ${references.map(_.artifactId).mkString(", ")}")
    }

    val request = basicRequest
      .post(url)
      .headers(authHeaders ++ Map("Content-Type" -> "application/json"))
      .body(requestBody)
      .response(asJson[VersionMetadata])

    try {
      val response = request.send(backend)
      response.body match {
        case Right(metadata) => Right(metadata)
        case Left(error) =>
          Left(ApicurioError.HttpError(response.code.code, error.getMessage))
      }
    } catch {
      case ex: Exception => Left(ApicurioError.NetworkError(ex))
    }
  }

  /**
   * Check compatibility of schema content against the registry
   */
  def checkCompatibility(
    groupId: String,
    artifactId: String,
    content: String,
    compatibilityLevel: CompatibilityLevel
  ): ApicurioResult[Boolean] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/rules/COMPATIBILITY"

    logger.debug(s"Checking compatibility: $groupId:$artifactId (${compatibilityLevel.value})")

    // First, ensure the compatibility rule is set
    val setRuleRequest = basicRequest
      .put(url)
      .headers(authHeaders ++ Map("Content-Type" -> "application/json"))
      .body(s"""{"config":"${compatibilityLevel.value}"}""")
      .response(asString)

    try {
      // Set or update the compatibility rule
      val ruleResponse = setRuleRequest.send(backend)
      if (!ruleResponse.isSuccess && ruleResponse.code.code != 404) {
        logger.warn(s"Failed to set compatibility rule: ${ruleResponse.body}")
      }

      // Now test compatibility
      val testUrl = uri"$baseUri/groups/$groupId/artifacts/$artifactId/rules/COMPATIBILITY/test"
      val testRequest = basicRequest
        .post(testUrl)
        .headers(authHeaders ++ Map("Content-Type" -> "application/json"))
        .body(content)
        .response(asString)

      val testResponse = testRequest.send(backend)
      testResponse.body match {
        case Right(body) =>
          parse(body).flatMap(_.hcursor.get[Boolean]("compatible")) match {
            case Right(compatible) => Right(compatible)
            case Left(_) =>
              // If we can't parse the response, assume incompatible
              logger.warn(s"Could not parse compatibility response, assuming incompatible")
              Right(false)
          }
        case Left(error) if testResponse.code.code == 404 =>
          // Artifact doesn't exist yet, so it's "compatible"
          logger.debug(s"Artifact doesn't exist, skipping compatibility check")
          Right(true)
        case Left(error) =>
          logger.warn(s"Compatibility check failed: $error")
          Right(false)
      }
    } catch {
      case ex: Exception => Left(ApicurioError.NetworkError(ex))
    }
  }

  /**
   * Publish a schema - creates artifact if not exists, or creates new version if changed
   * Returns Left(CreateArtifactResponse) if newly created, Right(VersionMetadata) if updated
   *
   * Note: Changes to content OR references will trigger a new version
   */
  def publishSchema(
    groupId: String,
    artifactId: String,
    artifactType: ArtifactType,
    content: String,
    compatibilityLevel: CompatibilityLevel,
    references: List[ContentReference] = List.empty
  ): ApicurioResult[Either[CreateArtifactResponse, VersionMetadata]] = {
    val contentHash = computeHash(content)

    // Log what we're publishing
    if (references.nonEmpty) {
      logger.info(s"Publishing $artifactId with ${references.size} reference(s):")
      references.foreach { ref =>
        logger.info(s"  → ${ref.groupId.getOrElse(groupId)}:${ref.artifactId}:${ref.version.getOrElse("latest")}")
      }
    }

    getArtifactMetadata(groupId, artifactId) match {
      case Right(metadata) =>
        // Artifact exists, check if content or references have changed
        val result = for {
          latestVersion <- getLatestVersion(groupId, artifactId)
          existingContent <- getVersionContent(groupId, artifactId, latestVersion.version)
        } yield {
          val existingHash = computeHash(existingContent)

          // Check if content changed
          val contentChanged = contentHash != existingHash

          // Check if references changed
          // Note: We treat having ANY references as a change, since we can't easily
          // retrieve existing references from the API to compare. This ensures
          // references are always included even if content is identical.
          val referencesChanged = references.nonEmpty

          if (!contentChanged && !referencesChanged) {
            logger.info(s"Schema unchanged: $groupId:$artifactId (version ${latestVersion.version})")
            Right(Right(latestVersion))
          } else {
            if (contentChanged) logger.debug(s"Content changed for $artifactId")
            if (referencesChanged) logger.info(s"Adding/updating ${references.size} reference(s) for $artifactId")

            // Check compatibility before creating new version
            checkCompatibility(groupId, artifactId, content, compatibilityLevel) match {
              case Right(true) =>
                createVersion(groupId, artifactId, content, references).map(Right(_))
              case Right(false) =>
                Left(ApicurioError.IncompatibleSchema(groupId, artifactId, "Compatibility check failed"))
              case Left(err) =>
                logger.warn(s"Compatibility check failed, proceeding anyway: ${err.message}")
                createVersion(groupId, artifactId, content, references).map(Right(_))
            }
          }
        }

        result.flatMap(identity) // Flatten the nested Either

      case Left(ApicurioError.ArtifactNotFound(_, _)) =>
        // Artifact doesn't exist, create it
        if (references.nonEmpty) {
          logger.info(s"Creating new artifact: $artifactId with ${references.size} reference(s)")
        } else {
          logger.info(s"Creating new artifact: $artifactId")
        }
        createArtifact(groupId, artifactId, artifactType, content, references).map(Left(_))

      case Left(err) =>
        Left(err)
    }
  }

  private def computeHash(content: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(content.getBytes("UTF-8"))
    hash.map("%02x".format(_)).mkString
  }
}

class ApicurioException(message: String, cause: Throwable = null)
  extends Exception(message, cause)

class ApicurioNotFoundException(message: String)
  extends ApicurioException(message)
