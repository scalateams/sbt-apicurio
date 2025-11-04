package org.scalateams.sbt.apicurio

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

object ApicurioModels {

  sealed trait CompatibilityLevel {
    def value: String
  }

  object CompatibilityLevel {
    case object Backward           extends CompatibilityLevel { val value = "BACKWARD"            }
    case object BackwardTransitive extends CompatibilityLevel { val value = "BACKWARD_TRANSITIVE" }
    case object Forward            extends CompatibilityLevel { val value = "FORWARD"             }
    case object ForwardTransitive  extends CompatibilityLevel { val value = "FORWARD_TRANSITIVE"  }
    case object Full               extends CompatibilityLevel { val value = "FULL"                }
    case object FullTransitive     extends CompatibilityLevel { val value = "FULL_TRANSITIVE"     }
    case object None               extends CompatibilityLevel { val value = "NONE"                }

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

  sealed trait ArtifactType {
    def value: String
  }

  object ArtifactType {
    case object Avro       extends ArtifactType { val value = "AVRO"     }
    case object Protobuf   extends ArtifactType { val value = "PROTOBUF" }
    case object JsonSchema extends ArtifactType { val value = "JSON"     }
    case object OpenApi    extends ArtifactType { val value = "OPENAPI"  }
    case object AsyncApi   extends ArtifactType { val value = "ASYNCAPI" }

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
    implicit val decoder: Decoder[ArtifactMetadata] = deriveDecoder
    implicit val encoder: Encoder[ArtifactMetadata] = deriveEncoder
  }

  // Response from POST /groups/{groupId}/artifacts (contains both artifact and version)
  case class CreateArtifactResponse(
    artifact: ArtifactMetadata,
    version: VersionMetadata)

  object CreateArtifactResponse {
    implicit val decoder: Decoder[CreateArtifactResponse] = deriveDecoder
  }

  case class CreateArtifactRequest(
    artifactId: String,
    artifactType: String,
    firstVersion: FirstVersionRequest,
    name: Option[String] = None,
    description: Option[String] = None)

  object CreateArtifactRequest {
    implicit val encoder: Encoder[CreateArtifactRequest] = deriveEncoder
  }

  case class FirstVersionRequest(
    version: Option[String] = None,
    content: ContentRequest,
    name: Option[String] = None,
    description: Option[String] = None)

  object FirstVersionRequest {
    implicit val encoder: Encoder[FirstVersionRequest] = deriveEncoder
  }

  case class ContentRequest(
    content: String,
    contentType: String = "application/json",
    references: List[ContentReference] = List.empty)

  object ContentRequest {
    implicit val encoder: Encoder[ContentRequest] = deriveEncoder
  }

  case class ContentReference(
    groupId: Option[String] = None,
    artifactId: String,
    version: Option[String] = None,
    name: String)

  object ContentReference {
    implicit val encoder: Encoder[ContentReference] = deriveEncoder
  }

  case class CreateVersionRequest(
    version: Option[String] = None,
    content: ContentRequest,
    name: Option[String] = None,
    description: Option[String] = None)

  object CreateVersionRequest {
    implicit val encoder: Encoder[CreateVersionRequest] = deriveEncoder
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
    implicit val decoder: Decoder[VersionMetadata] = deriveDecoder
  }

  case class ApicurioDependency(
    groupId: String,
    artifactId: String,
    version: String) {
    override def toString: String = s"$groupId % $artifactId % $version"
  }

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
    implicit val decoder: Decoder[CompatibilityCheckResult] = deriveDecoder
  }

  /** Functional error types for Apicurio operations. Using Either[ApicurioError, T] instead of Try[T] provides:
    *   - Type safety: All errors are known at compile time
    *   - Composability: Easy to chain operations with flatMap/map
    *   - Explicit error handling: No hidden exceptions
    *   - Better error messages: Structured error information
    */
  sealed trait ApicurioError {
    def message: String
  }

  object ApicurioError {
    final case class ArtifactNotFound(groupId: String, artifactId: String) extends ApicurioError {
      def message: String = s"Artifact not found: $groupId:$artifactId"
    }

    final case class VersionNotFound(
      groupId: String,
      artifactId: String,
      version: String)
        extends ApicurioError {
      def message: String = s"Version not found: $groupId:$artifactId:$version"
    }

    final case class IncompatibleSchema(
      groupId: String,
      artifactId: String,
      reason: String)
        extends ApicurioError {
      def message: String = s"Schema is not compatible with existing versions: $groupId:$artifactId - $reason"
    }

    final case class CircularDependency(schemas: Set[String]) extends ApicurioError {
      def message: String = {
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
      }
    }

    final case class InvalidSchema(reason: String) extends ApicurioError {
      def message: String = s"Invalid schema: $reason"
    }

    final case class HttpError(statusCode: Int, body: String) extends ApicurioError {
      def message: String = s"HTTP error $statusCode: $body"
    }

    final case class NetworkError(cause: Throwable) extends ApicurioError {
      def message: String = s"Network error: ${cause.getMessage}"
    }

    final case class ParseError(reason: String) extends ApicurioError {
      def message: String = s"Parse error: $reason"
    }

    final case class ConfigurationError(reason: String) extends ApicurioError {
      def message: String = s"Configuration error: $reason"
    }
  }

  /** Type alias for Either-based results
    */
  type ApicurioResult[T] = Either[ApicurioError, T]
}
