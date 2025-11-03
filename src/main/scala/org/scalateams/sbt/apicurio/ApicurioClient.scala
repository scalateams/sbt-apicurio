package org.scalateams.sbt.apicurio

import org.scalateams.sbt.apicurio.ApicurioModels._
import io.circe.parser._
import sbt.util.Logger
import sttp.client3._
import sttp.client3.circe._

import java.security.MessageDigest
import scala.util.{Failure, Success, Try}

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

  def close(): Unit = backend.close()

  /**
   * Get artifact metadata
   */
  def getArtifactMetadata(groupId: String, artifactId: String): Try[ArtifactMetadata] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId"

    logger.debug(s"Getting artifact metadata: $groupId:$artifactId")

    val request = basicRequest
      .get(url)
      .headers(authHeaders)
      .response(asJson[ArtifactMetadata])

    Try {
      val response = request.send(backend)
      response.body match {
        case Right(metadata) => metadata
        case Left(error) if response.code.code == 404 =>
          throw new ArtifurioNotFoundException(s"Artifact not found: $groupId:$artifactId")
        case Left(error) =>
          throw new ApicurioException(s"Failed to get artifact metadata: ${error.getMessage}")
      }
    }
  }

  /**
   * Get latest version of an artifact
   */
  def getLatestVersion(groupId: String, artifactId: String): Try[VersionMetadata] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/versions"

    logger.debug(s"Getting latest version: $groupId:$artifactId")

    val request = basicRequest
      .get(url)
      .headers(authHeaders)
      .response(asString)

    Try {
      val response = request.send(backend)
      response.body match {
        case Right(body) =>
          parse(body).flatMap(_.hcursor.downField("versions").as[List[VersionMetadata]]) match {
            case Right(versions) if versions.nonEmpty =>
              versions.maxBy(_.version)
            case Right(_) =>
              throw new ApicurioException(s"No versions found for artifact: $groupId:$artifactId")
            case Left(error) =>
              throw new ApicurioException(s"Failed to parse versions: ${error.getMessage}")
          }
        case Left(error) if response.code.code == 404 =>
          throw new ArtifurioNotFoundException(s"Artifact not found: $groupId:$artifactId")
        case Left(error) =>
          throw new ApicurioException(s"Failed to get versions: $error")
      }
    }
  }

  /**
   * Get specific version content
   */
  def getVersionContent(groupId: String, artifactId: String, version: String): Try[String] = {
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

      Try {
        val response = request.send(backend)
        response.body match {
          case Right(content) => content
          case Left(error) if response.code.code == 404 =>
            throw new ArtifurioNotFoundException(s"Version not found: $groupId:$artifactId:$version")
          case Left(error) =>
            throw new ApicurioException(s"Failed to get version content: $error")
        }
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
  ): Try[CreateArtifactResponse] = {
    val url = uri"$baseUri/groups/$groupId/artifacts"

    logger.info(s"Creating artifact: $groupId:$artifactId (${artifactType.value})")

    // Validate content is valid JSON for JSON-based schema types
    // Protobuf schemas are not JSON, so skip validation for those
    if (artifactType != ArtifactType.Protobuf) {
      parse(content) match {
        case Left(error) =>
          throw new ApicurioException(s"Failed to parse schema content as JSON: ${error.getMessage}")
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
          contentType = "application/json",
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

    Try {
      val response = request.send(backend)
      response.body match {
        case Right(createResponse) => createResponse
        case Left(error) =>
          throw new ApicurioException(s"Failed to create artifact: ${error.getMessage}")
      }
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
  ): Try[VersionMetadata] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/versions"

    logger.info(s"Creating new version: $groupId:$artifactId")

    // Get artifact metadata to determine type
    val artifactMetadata = getArtifactMetadata(groupId, artifactId)
    val isProtobuf = artifactMetadata.map(_.artifactType == "PROTOBUF").getOrElse(false)

    // Validate content is valid JSON for JSON-based schema types
    if (!isProtobuf) {
      parse(content) match {
        case Left(error) =>
          throw new ApicurioException(s"Failed to parse schema content as JSON: ${error.getMessage}")
        case Right(_) => // Valid JSON, continue
      }
    }

    // Create the request body according to Apicurio 3.x API spec
    val requestBody = CreateVersionRequest(
      version = None, // Let Apicurio auto-increment
      content = ContentRequest(
        content = content, // Content as string
        contentType = "application/json",
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

    Try {
      val response = request.send(backend)
      response.body match {
        case Right(metadata) => metadata
        case Left(error) =>
          throw new ApicurioException(s"Failed to create version: ${error.getMessage}")
      }
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
  ): Try[Boolean] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/rules/COMPATIBILITY"

    logger.debug(s"Checking compatibility: $groupId:$artifactId (${compatibilityLevel.value})")

    // First, ensure the compatibility rule is set
    val setRuleRequest = basicRequest
      .put(url)
      .headers(authHeaders ++ Map("Content-Type" -> "application/json"))
      .body(s"""{"config":"${compatibilityLevel.value}"}""")
      .response(asString)

    Try {
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
            case Right(compatible) => compatible
            case Left(_) =>
              // If we can't parse the response, assume incompatible
              logger.warn(s"Could not parse compatibility response, assuming incompatible")
              false
          }
        case Left(error) if testResponse.code.code == 404 =>
          // Artifact doesn't exist yet, so it's "compatible"
          logger.debug(s"Artifact doesn't exist, skipping compatibility check")
          true
        case Left(error) =>
          logger.warn(s"Compatibility check failed: $error")
          false
      }
    }
  }

  /**
   * Publish a schema - creates artifact if not exists, or creates new version if changed
   * Returns Left(CreateArtifactResponse) if newly created, Right(VersionMetadata) if updated
   */
  def publishSchema(
    groupId: String,
    artifactId: String,
    artifactType: ArtifactType,
    content: String,
    compatibilityLevel: CompatibilityLevel,
    references: List[ContentReference] = List.empty
  ): Try[Either[CreateArtifactResponse, VersionMetadata]] = {
    val contentHash = computeHash(content)

    getArtifactMetadata(groupId, artifactId) match {
      case Success(metadata) =>
        // Artifact exists, check if content has changed
        getLatestVersion(groupId, artifactId).flatMap { latestVersion =>
          getVersionContent(groupId, artifactId, latestVersion.version).flatMap { existingContent =>
            val existingHash = computeHash(existingContent)

            if (contentHash == existingHash) {
              logger.info(s"Schema unchanged: $groupId:$artifactId (version ${latestVersion.version})")
              Success(Right(latestVersion))
            } else {
              // Check compatibility before creating new version
              checkCompatibility(groupId, artifactId, content, compatibilityLevel) match {
                case Success(true) =>
                  createVersion(groupId, artifactId, content, references).map(Right(_))
                case Success(false) =>
                  Failure(new ApicurioException(
                    s"Schema is not compatible with existing versions: $groupId:$artifactId"
                  ))
                case Failure(ex) =>
                  logger.warn(s"Compatibility check failed, proceeding anyway: ${ex.getMessage}")
                  createVersion(groupId, artifactId, content, references).map(Right(_))
              }
            }
          }
        }

      case Failure(_: ArtifurioNotFoundException) =>
        // Artifact doesn't exist, create it
        createArtifact(groupId, artifactId, artifactType, content, references).map(Left(_))

      case Failure(ex) =>
        Failure(ex)
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

class ArtifurioNotFoundException(message: String)
  extends ApicurioException(message)
