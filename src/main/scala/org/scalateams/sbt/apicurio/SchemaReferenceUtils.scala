package org.scalateams.sbt.apicurio

import org.scalateams.sbt.apicurio.ApicurioModels._
import io.circe.parser._
import io.circe.Json
import sbt.util.Logger

import scala.collection.mutable
import scala.util.matching.Regex

/**
 * Utilities for detecting and resolving schema references.
 *
 * Handles:
 * - Avro schema references (nested record types)
 * - JSON Schema $ref references
 * - Protobuf imports
 * - Dependency ordering for publishing
 */
object SchemaReferenceUtils {

  /**
   * Detected reference to another schema
   */
  case class SchemaReference(
    name: String,                    // Name/identifier of the referenced schema
    groupId: Option[String] = None,  // Group ID if known
    artifactId: Option[String] = None, // Artifact ID if known
    version: Option[String] = None   // Version if known
  )

  /**
   * Schema with its detected references
   */
  case class SchemaWithReferences(
    schema: SchemaFile,
    artifactId: String,
    references: List[SchemaReference]
  )

  /**
   * Detect references in a schema based on its type
   */
  def detectReferences(
    schema: SchemaFile,
    artifactId: String,
    logger: Logger
  ): SchemaWithReferences = {
    schema.artifactType match {
      case ArtifactType.Avro =>
        val refs = detectAvroReferences(schema.content, logger)
        SchemaWithReferences(schema, artifactId, refs)

      case ArtifactType.JsonSchema =>
        val refs = detectJsonSchemaReferences(schema.content, logger)
        SchemaWithReferences(schema, artifactId, refs)

      case ArtifactType.Protobuf =>
        val refs = detectProtobufReferences(schema.content, logger)
        SchemaWithReferences(schema, artifactId, refs)

      case _ =>
        // OpenAPI and AsyncAPI typically embed schemas
        SchemaWithReferences(schema, artifactId, List.empty)
    }
  }

  /**
   * Detect Avro schema references.
   * Looks for:
   * - Record type references in fields
   * - Array item types
   * - Union types
   * - Map value types
   */
  private def detectAvroReferences(content: String, logger: Logger): List[SchemaReference] = {
    parse(content) match {
      case Right(json) =>
        val refs = mutable.Set[String]()

        // Only extract type references from specific schema contexts
        def extractTypeRef(typeValue: Json): Unit = {
          typeValue.fold(
            jsonNull = (),
            jsonBoolean = _ => (),
            jsonNumber = _ => (),
            jsonString = str => {
              // Only add if it's a complex type (not primitive)
              if (!isPrimitiveAvroType(str) && isQualifiedTypeName(str)) {
                refs += str
              }
            },
            jsonArray = arr => arr.foreach(extractTypeRef), // Union types
            jsonObject = obj => {
              // Handle nested record/array/map definitions
              obj.toMap.foreach {
                case ("type", nestedType) => extractTypeRef(nestedType)
                case ("items", items) => extractTypeRef(items) // Array items
                case ("values", values) => extractTypeRef(values) // Map values
                case ("fields", fields) => extractTypeRef(fields) // Nested fields
                case _ => ()
              }
            }
          )
        }

        // Find "type" fields in the schema structure
        def findTypeFields(j: Json, inFieldContext: Boolean = false): Unit = {
          j.fold(
            jsonNull = (),
            jsonBoolean = _ => (),
            jsonNumber = _ => (),
            jsonString = _ => (),
            jsonArray = arr => arr.foreach(e => findTypeFields(e, inFieldContext)),
            jsonObject = obj => {
              obj.toMap.foreach {
                case ("type", value) if inFieldContext =>
                  // We're in a field definition, extract type references
                  extractTypeRef(value)
                case ("fields", value) =>
                  // Entering field definitions
                  findTypeFields(value, inFieldContext = true)
                case ("type", value) =>
                  // Top-level type - only recurse if it's a record/array/map
                  value.asString match {
                    case Some("record") | Some("array") | Some("map") =>
                      findTypeFields(value, inFieldContext = false)
                    case _ => ()
                  }
                case (_, value) if inFieldContext =>
                  // Continue searching in field context
                  findTypeFields(value, inFieldContext)
                case (_, value) =>
                  // Continue searching in non-field context
                  findTypeFields(value, inFieldContext = false)
              }
            }
          )
        }

        findTypeFields(json)

        // Convert to SchemaReference objects
        refs.toList.map { ref =>
          SchemaReference(
            name = ref,
            artifactId = Some(extractArtifactIdFromTypeName(ref))
          )
        }

      case Left(error) =>
        logger.warn(s"Failed to parse Avro schema for reference detection: ${error.getMessage}")
        List.empty
    }
  }

  /**
   * Check if a string looks like a qualified type name (has dots or starts with uppercase)
   */
  private def isQualifiedTypeName(str: String): Boolean = {
    // Type names either contain dots (fully qualified) or start with uppercase (simple record name)
    str.contains(".") || (str.nonEmpty && str.charAt(0).isUpper)
  }

  /**
   * Detect JSON Schema $ref references.
   * Looks for:
   * - $ref with external references (http:// or apicurio:// URIs)
   * - $ref with local references to be resolved
   */
  private def detectJsonSchemaReferences(content: String, logger: Logger): List[SchemaReference] = {
    parse(content) match {
      case Right(json) =>
        val refs = mutable.Set[String]()

        def findRefs(j: Json): Unit = {
          j.fold(
            jsonNull = (),
            jsonBoolean = _ => (),
            jsonNumber = _ => (),
            jsonString = _ => (),
            jsonArray = arr => arr.foreach(findRefs),
            jsonObject = obj => {
              obj.toMap.foreach {
                case ("$ref", value) =>
                  value.asString.foreach { refStr =>
                    // Extract reference if it's external
                    if (refStr.startsWith("http://") || refStr.startsWith("https://") ||
                        refStr.startsWith("apicurio://") || refStr.contains("/schemas/")) {
                      refs += refStr
                    }
                  }
                case (_, value) => findRefs(value)
              }
            }
          )
        }

        findRefs(json)

        refs.toList.map { ref =>
          parseJsonSchemaRef(ref)
        }

      case Left(error) =>
        logger.warn(s"Failed to parse JSON Schema for reference detection: ${error.getMessage}")
        List.empty
    }
  }

  /**
   * Detect Protobuf import statements
   * Looks for: import "path/to/schema.proto";
   */
  private def detectProtobufReferences(content: String, logger: Logger): List[SchemaReference] = {
    val importPattern: Regex = """import\s+"([^"]+)";""".r

    importPattern.findAllMatchIn(content).map { m =>
      val importPath = m.group(1)
      val fileName = importPath.split("/").last
      val artifactId = fileName.stripSuffix(".proto")

      SchemaReference(
        name = importPath,
        artifactId = Some(artifactId)
      )
    }.toList
  }

  /**
   * Build a dependency graph and return schemas in topological order
   * (dependencies first, dependents later)
   */
  def orderSchemasByDependencies(
    schemas: List[SchemaWithReferences],
    logger: Logger
  ): ApicurioResult[List[SchemaWithReferences]] = {

    // Build adjacency list: artifactId -> List of artifact IDs it depends on
    val dependencies = mutable.Map[String, List[String]]()
    val schemaMap = schemas.map(s => s.artifactId -> s).toMap

    schemas.foreach { schemaWithRefs =>
      val deps = schemaWithRefs.references.flatMap(_.artifactId).distinct
      dependencies(schemaWithRefs.artifactId) = deps
    }

    // Topological sort using Kahn's algorithm
    val inDegree = mutable.Map[String, Int]().withDefaultValue(0)

    // Calculate in-degrees: count how many dependencies each schema has (within this batch)
    dependencies.foreach { case (artifact, deps) =>
      val localDeps = deps.filter(schemaMap.contains)
      inDegree(artifact) = localDeps.size
    }

    // Start with schemas that have no dependencies
    val queue = mutable.Queue[String]()
    schemas.foreach { s =>
      if (inDegree(s.artifactId) == 0) {
        queue.enqueue(s.artifactId)
      }
    }

    val result = mutable.ListBuffer[SchemaWithReferences]()

    while (queue.nonEmpty) {
      val current = queue.dequeue()
      schemaMap.get(current).foreach(result += _)

      // Find all schemas that depend on current and reduce their in-degree
      dependencies.foreach { case (artifact, deps) =>
        if (deps.contains(current) && schemaMap.contains(artifact)) {
          inDegree(artifact) = inDegree(artifact) - 1
          if (inDegree(artifact) == 0) {
            queue.enqueue(artifact)
          }
        }
      }
    }

    if (result.size != schemas.size) {
      // Circular dependency detected
      val missing = schemas.map(_.artifactId).toSet -- result.map(_.artifactId).toSet
      Left(ApicurioError.CircularDependency(missing))
    } else {
      Right(result.toList)
    }
  }

  /**
   * Resolve schema references to ContentReference objects for Apicurio
   */
  def resolveReferences(
    references: List[SchemaReference],
    groupId: String,
    schemaMap: Map[String, SchemaWithReferences],
    logger: Logger
  ): List[ContentReference] = {
    references.flatMap { ref =>
      ref.artifactId match {
        case Some(artifactId) if schemaMap.contains(artifactId) =>
          // Internal reference to a schema in the same publish batch
          Some(ContentReference(
            groupId = Some(groupId),
            artifactId = artifactId,
            version = ref.version,
            name = ref.name
          ))
        case Some(artifactId) =>
          // External reference - include with provided metadata
          logger.debug(s"External reference: ${ref.name} -> $artifactId")
          Some(ContentReference(
            groupId = ref.groupId.orElse(Some(groupId)),
            artifactId = artifactId,
            version = ref.version,
            name = ref.name
          ))
        case None =>
          logger.warn(s"Could not resolve artifact ID for reference: ${ref.name}")
          None
      }
    }
  }

  /**
   * Check if an Avro type is a primitive type
   */
  private def isPrimitiveAvroType(typeName: String): Boolean = {
    Set("null", "boolean", "int", "long", "float", "double", "bytes", "string").contains(typeName.toLowerCase)
  }

  /**
   * Extract artifact ID from an Avro type name
   * e.g., "com.example.Product" -> "Product"
   */
  private def extractArtifactIdFromTypeName(typeName: String): String = {
    typeName.split("\\.").last
  }

  /**
   * Parse a JSON Schema $ref to extract reference information
   */
  private def parseJsonSchemaRef(ref: String): SchemaReference = {
    // Handle apicurio:// URIs: apicurio://groupId/artifactId/versions/version
    if (ref.startsWith("apicurio://")) {
      val parts = ref.stripPrefix("apicurio://").split("/")
      parts match {
        case Array(groupId, artifactId, "versions", version) =>
          SchemaReference(
            name = ref,
            groupId = Some(groupId),
            artifactId = Some(artifactId),
            version = Some(version)
          )
        case Array(groupId, artifactId) =>
          SchemaReference(
            name = ref,
            groupId = Some(groupId),
            artifactId = Some(artifactId)
          )
        case _ =>
          SchemaReference(name = ref)
      }
    }
    // Handle HTTP URLs pointing to schemas
    else if (ref.contains("/schemas/")) {
      val artifactId = ref.split("/schemas/").last.split("/").head
      SchemaReference(
        name = ref,
        artifactId = Some(artifactId)
      )
    }
    // Generic reference
    else {
      SchemaReference(name = ref)
    }
  }

  /**
   * Get all dependencies (transitive) for pulling a schema
   */
  def getTransitiveDependencies(
    groupId: String,
    artifactId: String,
    version: String,
    client: ApicurioClient,
    logger: Logger,
    visited: Set[String] = Set.empty
  ): ApicurioResult[List[ApicurioDependency]] = {

    val key = s"$groupId:$artifactId:$version"
    if (visited.contains(key)) {
      return Right(List.empty)
    }

    logger.debug(s"Fetching dependencies for $key")

    client.getVersionContent(groupId, artifactId, version) match {
      case Right(content) =>
        // Detect references in the content
        val artifactType = client.getArtifactMetadata(groupId, artifactId) match {
          case Right(metadata) => ArtifactType.fromString(metadata.artifactType)
          case Left(_) => None
        }

        artifactType match {
          case Some(aType) =>
            val schemaFile = SchemaFile(
              file = new java.io.File(artifactId),
              content = content,
              hash = "",
              artifactType = aType
            )

            val refs = detectReferences(schemaFile, artifactId, logger).references

            // Recursively get dependencies
            val deps = refs.flatMap { ref =>
              ref.artifactId.map { depArtifactId =>
                val depGroupId = ref.groupId.getOrElse(groupId)
                val depVersion = ref.version.getOrElse("latest")

                val transitiveDeps = getTransitiveDependencies(
                  depGroupId,
                  depArtifactId,
                  depVersion,
                  client,
                  logger,
                  visited + key
                ) match {
                  case Right(deps) => deps
                  case Left(err) =>
                    logger.warn(s"Failed to fetch transitive dependencies for $depArtifactId: ${err.message}")
                    List.empty
                }

                ApicurioDependency(depGroupId, depArtifactId, depVersion) :: transitiveDeps
              }
            }.flatten

            Right(deps.distinct)

          case None =>
            Right(List.empty)
        }

      case Left(err) =>
        Left(err)
    }
  }
}
