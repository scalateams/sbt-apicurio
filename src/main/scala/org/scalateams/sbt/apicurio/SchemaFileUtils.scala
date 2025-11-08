package org.scalateams.sbt.apicurio

import org.scalateams.sbt.apicurio.ApicurioModels._
import sbt._
import sbt.util.Logger

import java.security.MessageDigest
import scala.io.Source
import scala.util.{Try, Using}

object SchemaFileUtils {

  private val supportedExtensions = Set("avsc", "avro", "proto", "json", "yaml", "yml")

  /** Discover all schema files in the given directories
    */
  def discoverSchemas(schemaPaths: Seq[File], logger: Logger): Seq[SchemaFile] = {
    logger.debug(s"Discovering schemas in: ${schemaPaths.mkString(", ")}")

    val schemas = schemaPaths.flatMap { path =>
      if (path.exists() && path.isDirectory) {
        discoverSchemasRecursive(path, logger)
      } else if (path.exists() && path.isFile && isSchemaFile(path)) {
        loadSchemaFile(path, logger).toSeq
      } else {
        logger.warn(s"Schema path does not exist or is not a directory/file: $path")
        Seq.empty
      }
    }

    logger.info(s"Discovered ${schemas.size} schema files")
    schemas
  }

  /** Recursively discover schema files in a directory
    */
  private def discoverSchemasRecursive(dir: File, logger: Logger): Seq[SchemaFile] = {
    val (files, dirs) = dir.listFiles().partition(_.isFile)

    val schemaFiles = files
      .filter(isSchemaFile)
      .flatMap(loadSchemaFile(_, logger))

    val subDirSchemas = dirs.flatMap(discoverSchemasRecursive(_, logger))

    schemaFiles ++ subDirSchemas
  }

  /** Check if a file is a schema file based on extension
    */
  private def isSchemaFile(file: File): Boolean = {
    val ext = file.getName.split("\\.").lastOption.getOrElse("")
    supportedExtensions.contains(ext.toLowerCase)
  }

  /** Load a schema file and compute its hash
    */
  private def loadSchemaFile(file: File, logger: Logger): Option[SchemaFile] = {
    val result = for {
      content <- Using(Source.fromFile(file, "UTF-8"))(_.mkString).toEither
      ext = file.getName.split("\\.").lastOption.getOrElse("")
      artifactType <- ArtifactType.fromExtension(ext).toRight(s"Unknown artifact type for file: ${file.getName}")
    } yield {
      val hash = computeHash(content)
      SchemaFile(file, content, hash, artifactType, ext.toLowerCase)
    }

    result match {
      case Right(schemaFile) => Some(schemaFile)
      case Left(error)       =>
        logger.error(s"Failed to load schema file ${file.getAbsolutePath}: $error")
        None
    }
  }

  /** Extract artifact ID from schema file (filename without extension)
    */
  def extractArtifactId(schemaFile: SchemaFile): String = {
    val fileName = schemaFile.file.getName
    val lastDot  = fileName.lastIndexOf('.')
    if (lastDot > 0) fileName.substring(0, lastDot) else fileName
  }

  /** Compute SHA-256 hash of content
    */
  def computeHash(content: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash   = digest.digest(content.getBytes("UTF-8"))
    hash.map("%02x".format(_)).mkString
  }

  /** Validate that required settings are configured
    */
  def validateSettings(
    registryUrl: Option[String],
    apiKey: Option[String],
    groupId: Option[String],
    logger: Logger
  ): Either[String, (String, Option[String], String)] =
    (registryUrl, groupId) match {
      case (Some(url), Some(gid)) =>
        if (url.trim.isEmpty) {
          Left("apicurioRegistryUrl is empty")
        } else if (gid.trim.isEmpty) {
          Left("apicurioGroupId is empty")
        } else {
          Right((url, apiKey, gid))
        }
      case (None, _)              =>
        Left("apicurioRegistryUrl is not set")
      case (_, None)              =>
        Left("apicurioGroupId is not set (this is required, no default is provided)")
    }

  /** Determine file extension based on content-type and artifact type
    */
  def determineFileExtension(contentType: String, artifactType: ArtifactType): String =
    contentType.toLowerCase match {
      case ct if ct.contains("protobuf") => "proto"
      case ct if ct.contains("yaml")     => "yaml"
      case ct if ct.contains("json")     =>
        artifactType match {
          case ArtifactType.Avro       => "avsc"
          case ArtifactType.Protobuf   => "proto" // Shouldn't happen with JSON content-type
          case ArtifactType.JsonSchema => "json"
          case ArtifactType.OpenApi    => "json"
          case ArtifactType.AsyncApi   => "json"
        }
      case _                             => "json" // Default fallback
    }

  /** Save downloaded schema content to file with proper extension based on artifact type and content type
    */
  def saveSchema(
    outputDir: File,
    dependency: ApicurioDependency,
    content: String,
    contentType: String,
    artifactType: ArtifactType,
    logger: Logger
  ): Either[ApicurioError, File] =
    Try {
      val groupPath = dependency.groupId.replace('.', '/')
      val targetDir = outputDir / groupPath
      IO.createDirectory(targetDir)

      val extension  = determineFileExtension(contentType, artifactType)
      val fileName   = s"${dependency.artifactId}.$extension"
      val targetFile = targetDir / fileName

      IO.write(targetFile, content)
      logger.info(
        s"Downloaded schema: ${dependency.groupId}:${dependency.artifactId}:${dependency.version} -> $targetFile"
      )
      targetFile
    }.toEither.left.map(ex => ApicurioError.ConfigurationError(s"Failed to save schema: ${ex.getMessage}"))
}
