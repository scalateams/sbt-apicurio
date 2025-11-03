package com.upstartcommerce.sbt.apicurio

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

object ApicurioModels {

  sealed trait CompatibilityLevel {
    def value: String
  }

  object CompatibilityLevel {
    case object Backward extends CompatibilityLevel { val value = "BACKWARD" }
    case object BackwardTransitive extends CompatibilityLevel { val value = "BACKWARD_TRANSITIVE" }
    case object Forward extends CompatibilityLevel { val value = "FORWARD" }
    case object ForwardTransitive extends CompatibilityLevel { val value = "FORWARD_TRANSITIVE" }
    case object Full extends CompatibilityLevel { val value = "FULL" }
    case object FullTransitive extends CompatibilityLevel { val value = "FULL_TRANSITIVE" }
    case object None extends CompatibilityLevel { val value = "NONE" }

    def fromString(s: String): Option[CompatibilityLevel] = s.toUpperCase match {
      case "BACKWARD" => Some(Backward)
      case "BACKWARD_TRANSITIVE" => Some(BackwardTransitive)
      case "FORWARD" => Some(Forward)
      case "FORWARD_TRANSITIVE" => Some(ForwardTransitive)
      case "FULL" => Some(Full)
      case "FULL_TRANSITIVE" => Some(FullTransitive)
      case "NONE" => Some(None)
      case _ => scala.None
    }
  }

  sealed trait ArtifactType {
    def value: String
  }

  object ArtifactType {
    case object Avro extends ArtifactType { val value = "AVRO" }
    case object Protobuf extends ArtifactType { val value = "PROTOBUF" }
    case object JsonSchema extends ArtifactType { val value = "JSON" }
    case object OpenApi extends ArtifactType { val value = "OPENAPI" }
    case object AsyncApi extends ArtifactType { val value = "ASYNCAPI" }

    def fromExtension(ext: String): Option[ArtifactType] = ext.toLowerCase match {
      case "avsc" | "avro" => Some(Avro)
      case "proto" => Some(Protobuf)
      case "json" => Some(JsonSchema)
      case "yaml" | "yml" => Some(OpenApi) // Could be AsyncAPI too, will need content inspection
      case _ => scala.None
    }

    def fromString(s: String): Option[ArtifactType] = s.toUpperCase match {
      case "AVRO" => Some(Avro)
      case "PROTOBUF" => Some(Protobuf)
      case "JSON" => Some(JsonSchema)
      case "OPENAPI" => Some(OpenApi)
      case "ASYNCAPI" => Some(AsyncApi)
      case _ => scala.None
    }
  }

  case class ArtifactMetadata(
    groupId: String,
    artifactId: String,
    version: String,
    artifactType: String,
    contentId: Option[Long] = None,
    globalId: Option[Long] = None
  )

  object ArtifactMetadata {
    implicit val decoder: Decoder[ArtifactMetadata] = deriveDecoder
    implicit val encoder: Encoder[ArtifactMetadata] = deriveEncoder
  }

  case class CreateArtifactRequest(
    artifactId: String,
    artifactType: String,
    name: Option[String] = None,
    description: Option[String] = None
  )

  object CreateArtifactRequest {
    implicit val encoder: Encoder[CreateArtifactRequest] = deriveEncoder
  }

  case class CreateVersionRequest(
    version: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None
  )

  object CreateVersionRequest {
    implicit val encoder: Encoder[CreateVersionRequest] = deriveEncoder
  }

  case class VersionMetadata(
    version: String,
    contentId: Long,
    globalId: Long,
    createdOn: String
  )

  object VersionMetadata {
    implicit val decoder: Decoder[VersionMetadata] = deriveDecoder
  }

  case class ApicurioDependency(
    groupId: String,
    artifactId: String,
    version: String
  ) {
    override def toString: String = s"$groupId % $artifactId % $version"
  }

  case class SchemaFile(
    file: java.io.File,
    content: String,
    hash: String,
    artifactType: ArtifactType
  )

  case class CompatibilityCheckResult(
    compatible: Boolean,
    message: Option[String] = None
  )

  object CompatibilityCheckResult {
    implicit val decoder: Decoder[CompatibilityCheckResult] = deriveDecoder
  }
}
