package org.scalateams.sbt.apicurio

import org.scalateams.sbt.apicurio.ApicurioModels._
import io.circe.parser._
import io.circe.Json
import sbt.util.Logger

import scala.util.matching.Regex
import scala.annotation.tailrec

/** Utilities for detecting and resolving schema references.
  *
  * Handles:
  *   - Avro schema references (nested record types)
  *   - JSON Schema $ref references
  *   - Protobuf imports
  *   - Dependency ordering for publishing
  */
object SchemaReferenceUtils {

  /** Detected reference to another schema
    */
  case class SchemaReference(
    name: String,                      // Name/identifier of the referenced schema
    groupId: Option[String] = None,    // Group ID if known
    artifactId: Option[String] = None, // Artifact ID if known
    version: Option[String] = None     // Version if known
  )

  /** Schema with its detected references
    */
  case class SchemaWithReferences(
    schema: SchemaFile,
    artifactId: String,
    references: List[SchemaReference])

  /** Detect references in a schema based on its type
    */
  def detectReferences(
    schema: SchemaFile,
    artifactId: String,
    logger: Logger
  ): SchemaWithReferences =
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

  /** Detect Avro schema references. Looks for:
    *   - Record type references in fields
    *   - Array item types
    *   - Union types
    *   - Map value types
    */
  private def detectAvroReferences(content: String, logger: Logger): List[SchemaReference] =
    parse(content) match {
      case Right(json) =>
        // Extract type references from JSON values (pure function)
        def extractTypeRef(typeValue: Json): Set[String] =
          typeValue.fold(
            jsonNull = Set.empty,
            jsonBoolean = _ => Set.empty,
            jsonNumber = _ => Set.empty,
            jsonString = str =>
              // Only add if it's a complex type (not primitive)
              if (!isPrimitiveAvroType(str) && isQualifiedTypeName(str)) Set(str)
              else Set.empty,
            jsonArray = arr => arr.flatMap(extractTypeRef).toSet, // Union types
            jsonObject = obj =>
              // Handle nested record/array/map definitions
              obj.toMap.foldLeft(Set.empty[String]) {
                case (acc, ("type", nestedType)) => acc ++ extractTypeRef(nestedType)
                case (acc, ("items", items))     => acc ++ extractTypeRef(items)  // Array items
                case (acc, ("values", values))   => acc ++ extractTypeRef(values) // Map values
                case (acc, ("fields", fields))   => acc ++ extractTypeRef(fields) // Nested fields
                case (acc, _)                    => acc
              }
          )

        // Find "type" fields in the schema structure (pure recursive function)
        def findTypeFields(j: Json, inFieldContext: Boolean = false): Set[String] =
          j.fold(
            jsonNull = Set.empty,
            jsonBoolean = _ => Set.empty,
            jsonNumber = _ => Set.empty,
            jsonString = _ => Set.empty,
            jsonArray = arr => arr.flatMap(e => findTypeFields(e, inFieldContext)).toSet,
            jsonObject = obj =>
              obj.toMap.foldLeft(Set.empty[String]) {
                case (acc, ("type", value)) if inFieldContext =>
                  // We're in a field definition, extract type references
                  acc ++ extractTypeRef(value)
                case (acc, ("fields", value))                 =>
                  // Entering field definitions
                  acc ++ findTypeFields(value, inFieldContext = true)
                case (acc, ("type", value))                   =>
                  // Top-level type - only recurse if it's a record/array/map
                  value.asString match {
                    case Some("record") | Some("array") | Some("map") =>
                      acc ++ findTypeFields(value, inFieldContext = false)
                    case _                                            => acc
                  }
                case (acc, (_, value)) if inFieldContext      =>
                  // Continue searching in field context
                  acc ++ findTypeFields(value, inFieldContext)
                case (acc, (_, value))                        =>
                  // Continue searching in non-field context
                  acc ++ findTypeFields(value, inFieldContext = false)
              }
          )

        val refs = findTypeFields(json)

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

  /** Check if a string looks like a qualified type name (has dots or starts with uppercase)
    */
  private def isQualifiedTypeName(str: String): Boolean =
    // Type names either contain dots (fully qualified) or start with uppercase (simple record name)
    str.contains(".") || (str.nonEmpty && str.charAt(0).isUpper)

  /** Detect JSON Schema $ref references. Looks for:
    *   - $ref with external references (http:// or apicurio:// URIs)
    *   - $ref with local references to be resolved
    */
  private def detectJsonSchemaReferences(content: String, logger: Logger): List[SchemaReference] =
    parse(content) match {
      case Right(json) =>
        // Pure recursive function to find $ref values
        def findRefs(j: Json): Set[String] =
          j.fold(
            jsonNull = Set.empty,
            jsonBoolean = _ => Set.empty,
            jsonNumber = _ => Set.empty,
            jsonString = _ => Set.empty,
            jsonArray = arr => arr.flatMap(findRefs).toSet,
            jsonObject = obj =>
              obj.toMap.foldLeft(Set.empty[String]) {
                case (acc, ("$ref", value)) =>
                  value.asString match {
                    case Some(refStr)
                        if refStr.startsWith("http://") || refStr.startsWith("https://") ||
                          refStr.startsWith("apicurio://") || refStr.contains("/schemas/") =>
                      acc + refStr
                    case _ => acc
                  }
                case (acc, (_, value))      => acc ++ findRefs(value)
              }
          )

        val refs = findRefs(json)

        refs.toList.map(ref => parseJsonSchemaRef(ref))

      case Left(error) =>
        logger.warn(s"Failed to parse JSON Schema for reference detection: ${error.getMessage}")
        List.empty
    }

  /** Detect Protobuf import statements Looks for: import "path/to/schema.proto";
    */
  private def detectProtobufReferences(content: String, logger: Logger): List[SchemaReference] = {
    val importPattern: Regex = """import\s+"([^"]+)";""".r

    importPattern
      .findAllMatchIn(content)
      .map { m =>
        val importPath = m.group(1)
        val fileName   = importPath.split("/").last
        val artifactId = fileName.stripSuffix(".proto")

        SchemaReference(
          name = importPath,
          artifactId = Some(artifactId)
        )
      }
      .toList
  }

  /** Build a dependency graph and return schemas in topological order (dependencies first, dependents later)
    *
    * Uses purely functional Kahn's algorithm with immutable data structures
    */
  def orderSchemasByDependencies(
    schemas: List[SchemaWithReferences],
    logger: Logger
  ): ApicurioResult[List[SchemaWithReferences]] = {

    val schemaMap = schemas.map(s => s.artifactId -> s).toMap

    // Build dependency map: artifactId -> List of artifact IDs it depends on
    val dependencies: Map[String, List[String]] = schemas.map { schemaWithRefs =>
      val deps = schemaWithRefs.references.flatMap(_.artifactId).distinct
      schemaWithRefs.artifactId -> deps
    }.toMap

    // Calculate in-degrees: count dependencies within this batch
    val inDegree: Map[String, Int] = dependencies.map {
      case (artifact, deps) =>
        val localDeps = deps.filter(schemaMap.contains)
        artifact -> localDeps.size
    }

    // Initial queue: schemas with no dependencies
    val initialQueue: List[String] = schemas
      .map(_.artifactId)
      .filter(id => inDegree.getOrElse(id, 0) == 0)

    // Immutable state for topological sort
    case class SortState(
      queue: List[String],                // Artifacts ready to process
      result: List[SchemaWithReferences], // Sorted result
      inDegree: Map[String, Int]          // Current in-degrees
    )

    // Functional Kahn's algorithm using tail recursion
    @tailrec
    def kahnSort(state: SortState): SortState =
      state.queue match {
        case Nil                       =>
          // Queue empty - done
          state
        case current :: remainingQueue =>
          // Process current artifact
          val updatedResult = schemaMap
            .get(current)
            .map(state.result :+ _)
            .getOrElse(state.result)

          // Find artifacts that depend on current and reduce their in-degree
          val (updatedInDegree, newlyReady) = dependencies.foldLeft((state.inDegree, List.empty[String])) {
            case ((degrees, ready), (artifact, deps)) =>
              if (deps.contains(current) && schemaMap.contains(artifact)) {
                val newDegree      = degrees.getOrElse(artifact, 0) - 1
                val updatedDegrees = degrees + (artifact -> newDegree)
                val updatedReady   = if (newDegree == 0) ready :+ artifact else ready
                (updatedDegrees, updatedReady)
              } else {
                (degrees, ready)
              }
          }

          // Continue with updated state
          kahnSort(
            SortState(
              queue = remainingQueue ++ newlyReady,
              result = updatedResult,
              inDegree = updatedInDegree
            )
          )
      }

    val initialState = SortState(initialQueue, List.empty, inDegree)
    val finalState   = kahnSort(initialState)

    if (finalState.result.size != schemas.size) {
      // Circular dependency detected
      val missing = schemas.map(_.artifactId).toSet -- finalState.result.map(_.artifactId).toSet
      Left(ApicurioError.CircularDependency(missing))
    } else {
      Right(finalState.result)
    }
  }

  /** Resolve schema references to ContentReference objects for Apicurio
    */
  def resolveReferences(
    references: List[SchemaReference],
    groupId: String,
    schemaMap: Map[String, SchemaWithReferences],
    logger: Logger
  ): List[ContentReference] =
    references.flatMap { ref =>
      ref.artifactId match {
        case Some(artifactId) if schemaMap.contains(artifactId) =>
          // Internal reference to a schema in the same publish batch
          Some(
            ContentReference(
              groupId = Some(groupId),
              artifactId = artifactId,
              version = ref.version,
              name = ref.name
            )
          )
        case Some(artifactId)                                   =>
          // External reference - include with provided metadata
          logger.debug(s"External reference: ${ref.name} -> $artifactId")
          Some(
            ContentReference(
              groupId = ref.groupId.orElse(Some(groupId)),
              artifactId = artifactId,
              version = ref.version,
              name = ref.name
            )
          )
        case None                                               =>
          logger.warn(s"Could not resolve artifact ID for reference: ${ref.name}")
          None
      }
    }

  /** Check if an Avro type is a primitive type
    */
  private def isPrimitiveAvroType(typeName: String): Boolean =
    Set("null", "boolean", "int", "long", "float", "double", "bytes", "string").contains(typeName.toLowerCase)

  /** Extract artifact ID from an Avro type name e.g., "com.example.Product" -> "Product"
    */
  private def extractArtifactIdFromTypeName(typeName: String): String =
    typeName.split("\\.").last

  /** Parse a JSON Schema $ref to extract reference information
    */
  private def parseJsonSchemaRef(ref: String): SchemaReference =
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
        case Array(groupId, artifactId)                      =>
          SchemaReference(
            name = ref,
            groupId = Some(groupId),
            artifactId = Some(artifactId)
          )
        case _                                               =>
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

  /** Get all dependencies (transitive) for pulling a schema
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
          case Left(_)         => None
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
                  case Left(err)   =>
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
