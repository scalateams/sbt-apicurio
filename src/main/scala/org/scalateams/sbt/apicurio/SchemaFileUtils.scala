package org.scalateams.sbt.apicurio

import org.scalateams.sbt.apicurio.ApicurioModels._
import sbt._
import sbt.util.Logger

import java.security.MessageDigest
import scala.io.Source
import scala.util.Using

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
      SchemaFile(file, content, hash, artifactType)
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

  /** Save downloaded schema content to file
    */
  def saveSchema(
    outputDir: File,
    dependency: ApicurioDependency,
    content: String,
    logger: Logger
  ): Either[ApicurioError, File] =
    try {
      val groupPath = dependency.groupId.replace('.', '/')
      val targetDir = outputDir / groupPath
      IO.createDirectory(targetDir)

      val fileName   = s"${dependency.artifactId}.json" // Default to .json, could be smarter
      val targetFile = targetDir / fileName

      IO.write(targetFile, content)
      logger.info(
        s"Downloaded schema: ${dependency.groupId}:${dependency.artifactId}:${dependency.version} -> $targetFile"
      )
      Right(targetFile)
    } catch {
      case ex: Exception =>
        Left(ApicurioError.ConfigurationError(s"Failed to save schema: ${ex.getMessage}"))
    }
}
