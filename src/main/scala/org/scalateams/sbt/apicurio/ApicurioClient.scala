package org.scalateams.sbt.apicurio

import org.scalateams.sbt.apicurio.ApicurioModels.*
import io.circe.parser.*
import sbt.util.Logger
import sttp.client3.*
import sttp.client3.circe.*

import java.security.MessageDigest
import scala.util.Try

/** Functional resource management for ApicurioClient. Automatically manages backend lifecycle.
  */
object ApicurioClient {

  /** Execute an operation with an ApicurioClient, ensuring proper resource cleanup. This is the recommended way to use
    * the client. Uses functional resource management pattern - acquires resource, executes operation, ensures cleanup.
    *
    * This method uses Try for resource lifecycle management to ensure the HTTP backend is properly closed. The
    * operation result is extracted using fold() rather than get() to maintain functional principles. Client close
    * failures are isolated in their own Try to prevent them from masking operation failures. All error handling within
    * operations uses Either[ApicurioError, T].
    *
    * @example
    *   {{{ ApicurioClient.withClient(url, keycloakConfig, logger) { client => client.publishSchema(groupId, artifactId,
    *   artifactType, content, compatLevel) } }}}
    */
  def withClient[A](
    registryUrl: String,
    keycloakConfig: Option[KeycloakConfig],
    logger: Logger
  )(f: ApicurioClient => A
  ): A = {
    val client = new ApicurioClient(registryUrl, keycloakConfig, logger)
    val result = scala.util.Try(f(client))
    scala.util.Try(client.close()) // Always attempt close, isolate failures
    result.fold(throw _, identity) // Extract value or propagate original exception
  }
}

/** Orders version identifiers by Semantic Versioning 2.0.0 precedence.
  *
  * Apicurio version strings are typically semantic versions ("3.0.0", "4.5.1"). A bare or partial core such as "3" or
  * "3.0" is zero-padded so "3" == "3.0.0"; build metadata (after '+') is ignored for precedence (§10); and a normal
  * release outranks any pre-release of the same core (§11). Any string that is not valid semver is ordered below every
  * semantic version (and lexicographically among other non-semver strings) so a custom label never masquerades as the
  * latest version.
  */
private[apicurio] object SemanticVersionOrdering extends Ordering[String] {

  final private case class Parsed(core: List[Long], preRelease: List[String])

  def compare(a: String, b: String): Int =
    (parse(a), parse(b)) match {
      case (Some(x), Some(y)) => compareParsed(x, y)
      case (Some(_), None)    => 1
      case (None, Some(_))    => -1
      case (None, None)       => a.compareTo(b)
    }

  private def parse(version: String): Option[Parsed] = {
    val withoutBuild      = version.takeWhile(_ != '+')
    val (coreStr, preStr) = withoutBuild.indexOf('-') match {
      case -1  => (withoutBuild, "")
      case idx => (withoutBuild.substring(0, idx), withoutBuild.substring(idx + 1))
    }
    val core              = coreStr.split('.').toList.map(parseNonNegativeLong)
    if (coreStr.isEmpty || core.exists(_.isEmpty)) None
    else Some(Parsed(core.flatten, if (preStr.isEmpty) Nil else preStr.split('.').toList))
  }

  // Long (not Int) so that large all-digit components do not overflow and get demoted to non-semver.
  private def parseNonNegativeLong(s: String): Option[Long] =
    if (s.nonEmpty && s.forall(_.isDigit)) Try(s.toLong).toOption else None

  private def compareParsed(x: Parsed, y: Parsed): Int = {
    val coreComparison = compareCore(x.core, y.core)
    if (coreComparison != 0) coreComparison else comparePreRelease(x.preRelease, y.preRelease)
  }

  private def compareCore(x: List[Long], y: List[Long]): Int = {
    val length = math.max(x.length, y.length)
    x.padTo(length, 0L)
      .zip(y.padTo(length, 0L))
      .find { case (l, r) => l != r }
      .map { case (l, r) => l.compare(r) }
      .getOrElse(0)
  }

  private def comparePreRelease(x: List[String], y: List[String]): Int =
    (x, y) match {
      case (Nil, Nil) => 0
      case (Nil, _)   => 1 // a release outranks a pre-release of the same core (§11)
      case (_, Nil)   => -1
      case _          =>
        x.zip(y)
          .map { case (l, r) => comparePreReleaseId(l, r) }
          .find(_ != 0)
          .getOrElse(x.length.compare(y.length)) // more identifiers wins when all preceding are equal
    }

  private def comparePreReleaseId(l: String, r: String): Int =
    (parseNonNegativeLong(l), parseNonNegativeLong(r)) match {
      case (Some(a), Some(b)) => a.compare(b)   // numeric identifiers compared numerically
      case (Some(_), None)    => -1             // numeric identifiers have lower precedence (§11)
      case (None, Some(_))    => 1
      case (None, None)       => l.compareTo(r) // alphanumeric identifiers compared lexically
    }
}

class ApicurioClient(
  registryUrl: String,
  keycloakConfig: Option[KeycloakConfig],
  logger: Logger) {

  private val backend      = HttpURLConnectionBackend()
  private val baseUri      = uri"$registryUrl"
  private val tokenManager = keycloakConfig.map(config => new KeycloakTokenManager(config, backend))

  /** Get authentication headers. Returns Either to propagate token errors functionally.
    */
  private def authHeaders: ApicurioResult[Map[String, String]] =
    tokenManager match {
      case Some(manager) =>
        manager.getValidToken().map(token => Map("Authorization" -> s"Bearer $token"))
      case None          =>
        // No authentication configured
        Right(Map.empty[String, String])
    }

  private def contentTypeForFileExtension(fileExtension: String, artifactType: ArtifactType): String =
    (fileExtension.toLowerCase, artifactType) match {
      case ("proto", _)               => "application/x-protobuf"
      case ("yaml" | "yml", _)        => "application/x-yaml"
      case ("json", _)                => "application/json"
      case ("avsc" | "avro", _)       => "application/json"
      // Fallback for Protobuf artifacts with non-standard file extensions
      case (_, ArtifactType.Protobuf) => "application/x-protobuf"
      case _                          => "application/json"
    }

  private def isYamlExtension(fileExtension: String): Boolean =
    fileExtension.toLowerCase == "yaml" || fileExtension.toLowerCase == "yml"

  /** Close the HTTP backend. Call this when done with the client. Prefer using ApicurioClient.withClient for automatic
    * resource management.
    */
  def close(): Unit = backend.close()

  /** Get artifact metadata
    */
  def getArtifactMetadata(groupId: String, artifactId: String): ApicurioResult[ArtifactMetadata] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId"

    logger.debug(s"Getting artifact metadata: $groupId:$artifactId")

    authHeaders.flatMap { headers =>
      val request = basicRequest
        .get(url)
        .headers(headers)
        .response(asJson[ArtifactMetadata])

      Try {
        val response = request.send(backend)
        response.body match {
          case Right(metadata)                          => Right(metadata)
          case Left(error) if response.code.code == 404 =>
            Left(ApicurioError.ArtifactNotFound(groupId, artifactId))
          case Left(error)                              =>
            Left(ApicurioError.HttpError(response.code.code, error.getMessage))
        }
      }.fold(
        ex => Left(ApicurioError.NetworkError(ex)),
        either => either
      )
    }
  }

  /** Get latest version of an artifact
    */
  def getLatestVersion(groupId: String, artifactId: String): ApicurioResult[VersionMetadata] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/versions"

    logger.debug(s"Getting latest version: $groupId:$artifactId")

    authHeaders.flatMap { headers =>
      val request = basicRequest
        .get(url)
        .headers(headers)
        .response(asString)

      Try {
        val response = request.send(backend)
        response.body match {
          case Right(body)                              =>
            parse(body).flatMap(_.hcursor.downField("versions").as[List[VersionMetadata]]) match {
              case Right(versions) if versions.nonEmpty =>
                Right(versions.maxBy(_.version)(using SemanticVersionOrdering))
              case Right(_)                             =>
                Left(ApicurioError.ArtifactNotFound(groupId, artifactId))
              case Left(error)                          =>
                Left(ApicurioError.ParseError(s"Failed to parse versions: ${error.getMessage}"))
            }
          case Left(error) if response.code.code == 404 =>
            Left(ApicurioError.ArtifactNotFound(groupId, artifactId))
          case Left(error)                              =>
            Left(ApicurioError.HttpError(response.code.code, error))
        }
      }.fold(
        ex => Left(ApicurioError.NetworkError(ex)),
        either => either
      )
    }
  }

  /** Get specific version content with content-type from response headers
    */
  def getVersionContentWithType(
    groupId: String,
    artifactId: String,
    version: String
  ): ApicurioResult[(String, String)] =
    // If "latest" is requested, resolve it to the actual latest version number first
    if (version == "latest") {
      getLatestVersion(groupId, artifactId).flatMap { latestVersionMeta =>
        getVersionContentWithType(groupId, artifactId, latestVersionMeta.version)
      }
    } else {
      val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/versions/$version/content"

      logger.debug(s"Getting version content with type: $groupId:$artifactId:$version")

      authHeaders.flatMap { headers =>
        val request = basicRequest
          .get(url)
          .headers(headers)
          .response(asString)

        Try {
          val response = request.send(backend)
          response.body match {
            case Right(content)                           =>
              // Extract Content-Type from response headers
              val contentType = response
                .header("Content-Type")
                .orElse(response.header("content-type"))
                .getOrElse("application/json")
                .split(";")(0)
                .trim // Only trim the header, not the content
              Right((content, contentType))
            case Left(error) if response.code.code == 404 =>
              Left(ApicurioError.VersionNotFound(groupId, artifactId, version))
            case Left(error)                              =>
              Left(ApicurioError.HttpError(response.code.code, error))
          }
        }.fold(
          ex => Left(ApicurioError.NetworkError(ex)),
          either => either
        )
      }
    }

  /** Get specific version content
    */
  def getVersionContent(
    groupId: String,
    artifactId: String,
    version: String
  ): ApicurioResult[String] =
    getVersionContentWithType(groupId, artifactId, version).map(_._1)

  /** Create a new artifact Returns both artifact and version metadata
    */
  def createArtifact(
    groupId: String,
    artifactId: String,
    artifactType: ArtifactType,
    content: String,
    fileExtension: String,
    references: List[ContentReference] = List.empty
  ): ApicurioResult[CreateArtifactResponse] = {
    val url = uri"$baseUri/groups/$groupId/artifacts"

    logger.info(s"Creating artifact: $groupId:$artifactId (${artifactType.value})")

    // Validate content is valid JSON for JSON-based schema types
    // Protobuf schemas and YAML files are not JSON, so skip validation for those
    val validationResult: Either[ApicurioError, Unit] =
      if (artifactType != ArtifactType.Protobuf && !isYamlExtension(fileExtension)) {
        parse(content).left
          .map(error => ApicurioError.InvalidSchema(s"Failed to parse schema content as JSON: ${error.getMessage}"))
          .map(_ => ())
      } else {
        Right(())
      }

    validationResult.flatMap { _ =>
      // Create the request body according to Apicurio 3.x API spec
      val requestBody = CreateArtifactRequest(
        artifactId = artifactId,
        artifactType = artifactType.value,
        firstVersion = FirstVersionRequest(
          version = None, // Let Apicurio assign version
          content = ContentRequest(
            content = content, // Content as string (JSON for most types, raw proto for Protobuf, YAML for YAML files)
            contentType = contentTypeForFileExtension(fileExtension, artifactType),
            references = references
          )
        )
      )

      if (references.nonEmpty) {
        logger.debug(
          s"Creating artifact with ${references.size} reference(s): ${references.map(_.artifactId).mkString(", ")}"
        )
      }

      authHeaders.flatMap { headers =>
        val request = basicRequest
          .post(url)
          .headers(headers ++ Map("Content-Type" -> "application/json"))
          .body(requestBody)
          .response(asJson[CreateArtifactResponse])

        Try {
          val response = request.send(backend)
          response.body match {
            case Right(createResponse) => Right(createResponse)
            case Left(error)           =>
              Left(ApicurioError.HttpError(response.code.code, error.getMessage))
          }
        }.fold(
          ex => Left(ApicurioError.NetworkError(ex)),
          either => either
        )
      }
    }
  }

  /** Create a new version of an existing artifact
    */
  def createVersion(
    groupId: String,
    artifactId: String,
    content: String,
    fileExtension: String,
    references: List[ContentReference] = List.empty
  ): ApicurioResult[VersionMetadata] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/versions"

    logger.info(s"Creating new version: $groupId:$artifactId")

    // Get artifact metadata to determine type
    val artifactType = getArtifactMetadata(groupId, artifactId) match {
      case Right(metadata) => ArtifactType.fromString(metadata.artifactType)
      case Left(_)         => None
    }
    val isProtobuf   = artifactType.contains(ArtifactType.Protobuf)

    // Validate content is valid JSON for JSON-based schema types
    val validationResult: Either[ApicurioError, Unit] =
      if (!isProtobuf && !isYamlExtension(fileExtension)) {
        parse(content).left
          .map(error => ApicurioError.InvalidSchema(s"Failed to parse schema content as JSON: ${error.getMessage}"))
          .map(_ => ())
      } else {
        Right(())
      }

    validationResult.flatMap { _ =>
      // Create the request body according to Apicurio 3.x API spec
      val requestBody = CreateVersionRequest(
        version = None, // Let Apicurio auto-increment
        content = ContentRequest(
          content = content, // Content as string
          contentType = artifactType
            .map(contentTypeForFileExtension(fileExtension, _))
            .getOrElse("application/json"),
          references = references
        )
      )

      if (references.nonEmpty) {
        logger.debug(
          s"Creating version with ${references.size} reference(s): ${references.map(_.artifactId).mkString(", ")}"
        )
      }

      authHeaders.flatMap { headers =>
        val request = basicRequest
          .post(url)
          .headers(headers ++ Map("Content-Type" -> "application/json"))
          .body(requestBody)
          .response(asJson[VersionMetadata])

        Try {
          val response = request.send(backend)
          response.body match {
            case Right(metadata) => Right(metadata)
            case Left(error)     =>
              Left(ApicurioError.HttpError(response.code.code, error.getMessage))
          }
        }.fold(
          ex => Left(ApicurioError.NetworkError(ex)),
          either => either
        )
      }
    }
  }

  /** Check compatibility of schema content against the registry
    */
  def checkCompatibility(
    groupId: String,
    artifactId: String,
    content: String,
    compatibilityLevel: CompatibilityLevel
  ): ApicurioResult[Boolean] = {
    val url = uri"$baseUri/groups/$groupId/artifacts/$artifactId/rules/COMPATIBILITY"

    logger.debug(s"Checking compatibility: $groupId:$artifactId (${compatibilityLevel.value})")

    authHeaders.flatMap { headers =>
      // First, ensure the compatibility rule is set
      val setRuleRequest = basicRequest
        .put(url)
        .headers(headers ++ Map("Content-Type" -> "application/json"))
        .body(s"""{"config":"${compatibilityLevel.value}"}""")
        .response(asString)

      Try {
        // Set or update the compatibility rule
        val ruleResponse = setRuleRequest.send(backend)
        if (!ruleResponse.isSuccess && ruleResponse.code.code != 404) {
          logger.warn(s"Failed to set compatibility rule: ${ruleResponse.body}")
        }

        // Now test compatibility
        val testUrl     = uri"$baseUri/groups/$groupId/artifacts/$artifactId/rules/COMPATIBILITY/test"
        val testRequest = basicRequest
          .post(testUrl)
          .headers(headers ++ Map("Content-Type" -> "application/json"))
          .body(content)
          .response(asString)

        val testResponse = testRequest.send(backend)
        testResponse.body match {
          case Right(body)                                  =>
            parse(body).flatMap(_.hcursor.get[Boolean]("compatible")) match {
              case Right(compatible) => Right(compatible)
              case Left(_)           =>
                // If we can't parse the response, assume incompatible
                logger.warn(s"Could not parse compatibility response, assuming incompatible")
                Right(false)
            }
          case Left(error) if testResponse.code.code == 404 =>
            // Artifact doesn't exist yet, so it's "compatible"
            logger.debug(s"Artifact doesn't exist, skipping compatibility check")
            Right(true)
          case Left(error)                                  =>
            logger.warn(s"Compatibility check failed: $error")
            Right(false)
        }
      }.fold(
        ex => Left(ApicurioError.NetworkError(ex)),
        either => either
      )
    }
  }

  /** Publish a schema - creates artifact if not exists, or creates new version if changed Returns
    * Left(CreateArtifactResponse) if newly created, Right(VersionMetadata) if updated
    *
    * Note: Changes to content OR references will trigger a new version
    */
  def publishSchema(
    groupId: String,
    artifactId: String,
    artifactType: ArtifactType,
    content: String,
    fileExtension: String,
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
          latestVersion   <- getLatestVersion(groupId, artifactId)
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
              case Right(true)  =>
                createVersion(groupId, artifactId, content, fileExtension, references).map(Right(_))
              case Right(false) =>
                Left(ApicurioError.IncompatibleSchema(groupId, artifactId, "Compatibility check failed"))
              case Left(err)    =>
                logger.warn(s"Compatibility check failed, proceeding anyway: ${err.message}")
                createVersion(groupId, artifactId, content, fileExtension, references).map(Right(_))
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
        createArtifact(groupId, artifactId, artifactType, content, fileExtension, references).map(Left(_))

      case Left(err) =>
        Left(err)
    }
  }

  private def computeHash(content: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash   = digest.digest(content.getBytes("UTF-8"))
    hash.map("%02x".format(_)).mkString
  }
}
