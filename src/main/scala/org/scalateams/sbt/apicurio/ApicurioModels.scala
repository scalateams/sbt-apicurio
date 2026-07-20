package org.scalateams.sbt.apicurio

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

object ApicurioModels {

  enum CompatibilityLevel(val value: String) {
    case Backward           extends CompatibilityLevel("BACKWARD")
    case BackwardTransitive extends CompatibilityLevel("BACKWARD_TRANSITIVE")
    case Forward            extends CompatibilityLevel("FORWARD")
    case ForwardTransitive  extends CompatibilityLevel("FORWARD_TRANSITIVE")
    case Full               extends CompatibilityLevel("FULL")
    case FullTransitive     extends CompatibilityLevel("FULL_TRANSITIVE")
    case None               extends CompatibilityLevel("NONE")
  }

  object CompatibilityLevel {
    def fromString(s: String): Option[CompatibilityLevel] = s.toUpperCase match {
      case "BACKWARD"            => Some(Backward)
      case "BACKWARD_TRANSITIVE" => Some(BackwardTransitive)
      case "FORWARD"             => Some(Forward)
      case "FORWARD_TRANSITIVE"  => Some(ForwardTransitive)
      case "FULL"                => Some(Full)
      case "FULL_TRANSITIVE"     => Some(FullTransitive)
      case "NONE"                => Some(None)
      case _                     => scala.None
    }
  }

  enum ArtifactType(val value: String) {
    case Avro       extends ArtifactType("AVRO")
    case Protobuf   extends ArtifactType("PROTOBUF")
    case JsonSchema extends ArtifactType("JSON")
    case OpenApi    extends ArtifactType("OPENAPI")
    case AsyncApi   extends ArtifactType("ASYNCAPI")
  }

  object ArtifactType {
    def fromExtension(ext: String): Option[ArtifactType] = ext.toLowerCase match {
      case "avsc" | "avro" => Some(Avro)
      case "proto"         => Some(Protobuf)
      case "json"          => Some(JsonSchema)
      case "yaml" | "yml"  => Some(OpenApi) // Could be AsyncAPI too, will need content inspection
      case _               => scala.None
    }

    def fromString(s: String): Option[ArtifactType] = s.toUpperCase match {
      case "AVRO"     => Some(Avro)
      case "PROTOBUF" => Some(Protobuf)
      case "JSON"     => Some(JsonSchema)
      case "OPENAPI"  => Some(OpenApi)
      case "ASYNCAPI" => Some(AsyncApi)
      case _          => scala.None
    }
  }

  // Artifact-level metadata (from GET /groups/{groupId}/artifacts/{artifactId})
  case class ArtifactMetadata(
    groupId: String,
    artifactId: String,
    artifactType: String,
    owner: Option[String] = None,
    createdOn: Option[String] = None,
    modifiedBy: Option[String] = None,
    modifiedOn: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None,
    labels: Option[Map[String, String]] = None)

  object ArtifactMetadata {
    given Decoder[ArtifactMetadata] = deriveDecoder[ArtifactMetadata]
    given Encoder[ArtifactMetadata] = deriveEncoder[ArtifactMetadata]
  }

  // Response from POST /groups/{groupId}/artifacts (contains both artifact and version)
  case class CreateArtifactResponse(
    artifact: ArtifactMetadata,
    version: VersionMetadata)

  object CreateArtifactResponse {
    given Decoder[CreateArtifactResponse] = deriveDecoder[CreateArtifactResponse]
  }

  case class CreateArtifactRequest(
    artifactId: String,
    artifactType: String,
    firstVersion: FirstVersionRequest,
    name: Option[String] = None,
    description: Option[String] = None)

  object CreateArtifactRequest {
    given Encoder[CreateArtifactRequest] = deriveEncoder[CreateArtifactRequest]
  }

  case class FirstVersionRequest(
    version: Option[String] = None,
    content: ContentRequest,
    name: Option[String] = None,
    description: Option[String] = None)

  object FirstVersionRequest {
    given Encoder[FirstVersionRequest] = deriveEncoder[FirstVersionRequest]
  }

  case class ContentRequest(
    content: String,
    contentType: String = "application/json",
    references: List[ContentReference] = List.empty)

  object ContentRequest {
    given Encoder[ContentRequest] = deriveEncoder[ContentRequest]
  }

  case class ContentReference(
    groupId: Option[String] = None,
    artifactId: String,
    version: Option[String] = None,
    name: String)

  object ContentReference {
    given Encoder[ContentReference] = deriveEncoder[ContentReference]
  }

  case class CreateVersionRequest(
    version: Option[String] = None,
    content: ContentRequest,
    name: Option[String] = None,
    description: Option[String] = None)

  object CreateVersionRequest {
    given Encoder[CreateVersionRequest] = deriveEncoder[CreateVersionRequest]
  }

  // Version-level metadata
  case class VersionMetadata(
    version: String,
    groupId: String,
    artifactId: String,
    artifactType: String,
    contentId: Long,
    globalId: Long,
    state: String,
    createdOn: String,
    owner: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None,
    labels: Option[Map[String, String]] = None)

  object VersionMetadata {
    given Decoder[VersionMetadata] = deriveDecoder[VersionMetadata]
  }

  case class ApicurioDependency(
    groupId: String,
    artifactId: String,
    version: String) {
    override def toString: String = s"$groupId % $artifactId % $version"
  }

  /** Schema content with metadata for determining file format */
  case class SchemaContentWithMetadata(
    content: String,
    contentType: String,
    artifactType: ArtifactType)

  case class SchemaFile(
    file: java.io.File,
    content: String,
    hash: String,
    artifactType: ArtifactType,
    fileExtension: String)

  case class CompatibilityCheckResult(
    compatible: Boolean,
    message: Option[String] = None)

  object CompatibilityCheckResult {
    given Decoder[CompatibilityCheckResult] = deriveDecoder[CompatibilityCheckResult]
  }

  /** Keycloak OAuth2 configuration for client credentials flow */
  case class KeycloakConfig(
    url: String,
    realm: String,
    clientId: String,
    clientSecret: String) {

    /** Constructs the token endpoint URL from the Keycloak base URL and realm */
    def tokenEndpoint: String = s"$url/realms/$realm/protocol/openid-connect/token"
  }

  /** OAuth2 token response from Keycloak token endpoint */
  case class TokenResponse(
    access_token: String,
    expires_in: Long,
    refresh_expires_in: Option[Long] = None,
    token_type: String,
    scope: Option[String] = None)

  object TokenResponse {
    given Decoder[TokenResponse] = deriveDecoder[TokenResponse]
  }

  /** Functional error types for Apicurio operations. Using Either[ApicurioError, T] instead of Try[T] provides type
    * safety, composability, explicit error handling, and structured messages.
    */
  enum ApicurioError {
    case ArtifactNotFound(groupId: String, artifactId: String)
    case VersionNotFound(
      groupId: String,
      artifactId: String,
      version: String)
    case IncompatibleSchema(
      groupId: String,
      artifactId: String,
      reason: String)
    case CircularDependency(schemas: Set[String])
    case InvalidSchema(reason: String)
    case HttpError(statusCode: Int, body: String)
    case NetworkError(cause: Throwable)
    case ParseError(reason: String)
    case ConfigurationError(reason: String)
    case AuthenticationError(reason: String, cause: Option[Throwable] = None)
    case TokenRefreshError(reason: String, cause: Option[Throwable] = None)

    def message: String = this match {
      case ArtifactNotFound(groupId, artifactId)           =>
        s"Artifact not found: $groupId:$artifactId"
      case VersionNotFound(groupId, artifactId, version)   =>
        s"Version not found: $groupId:$artifactId:$version"
      case IncompatibleSchema(groupId, artifactId, reason) =>
        s"Schema is not compatible with existing versions: $groupId:$artifactId - $reason"
      case CircularDependency(schemas)                     =>
        val schemaList = schemas.toList.sorted.mkString(", ")
        s"""Circular dependency detected among schemas: $schemaList
           |
           |These schemas form a dependency cycle where each depends on another in the group.
           |Schemas must be organized in a directed acyclic graph (DAG) for publication.
           |
           |To resolve:
           |1. Review the schema references to identify the circular dependency chain
           |2. Refactor schemas to break the cycle (e.g., extract common types into a separate schema)
           |3. Ensure dependencies flow in one direction only""".stripMargin
      case InvalidSchema(reason)                           => s"Invalid schema: $reason"
      case HttpError(statusCode, body)                     => s"HTTP error $statusCode: $body"
      case NetworkError(cause)                             => s"Network error: ${cause.getMessage}"
      case ParseError(reason)                              => s"Parse error: $reason"
      case ConfigurationError(reason)                      => s"Configuration error: $reason"
      case AuthenticationError(reason, cause)              =>
        s"Authentication error: $reason${cause.map(c => s" (${c.getMessage})").getOrElse("")}"
      case TokenRefreshError(reason, cause)                =>
        s"Token refresh error: $reason${cause.map(c => s" (${c.getMessage})").getOrElse("")}"
    }
  }

  /** Type alias for Either-based results */
  type ApicurioResult[T] = Either[ApicurioError, T]
}
