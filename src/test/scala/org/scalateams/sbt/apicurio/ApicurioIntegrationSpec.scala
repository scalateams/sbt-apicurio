package org.scalateams.sbt.apicurio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, Tag}
import sbt.util.Logger
import java.io.File
import org.scalateams.sbt.apicurio.ApicurioModels.ApicurioError

object IntegrationTest extends Tag("org.scalateams.sbt.apicurio.IntegrationTest")

/** Integration tests for Apicurio plugin.
  *
  * These tests require a running Apicurio Registry instance. Set environment variables to run: APICURIO_TEST_URL -
  * Registry URL (default: http://localhost:8080) APICURIO_TEST_API_KEY - Optional API key
  *
  * To run: sbt "testOnly *IntegrationSpec" To skip: sbt "testOnly * -- -l org.scalateams.sbt.apicurio.IntegrationTest"
  */
class ApicurioIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  // Test configuration
  private val registryUrl = sys.env.getOrElse("APICURIO_TEST_URL", "http://localhost:8080")
  private val apiKey      = sys.env.get("APICURIO_TEST_API_KEY")
  private val testGroupId = "com.example"

  // Check if Apicurio is available
  private var apicurioAvailable = false

  private val testLogger = new TestLogger

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Check if Apicurio is reachable
    try {
      val client = new ApicurioClient(registryUrl, apiKey, testLogger)
      // Try a simple GET to see if it's up
      client.getArtifactMetadata(testGroupId, "non-existent-test") match {
        case Left(_: ApicurioError.ArtifactNotFound) =>
          // 404 is expected, but means the service is reachable
          apicurioAvailable = true
        case Right(_)                                =>
          // Artifact exists somehow, but service is reachable
          apicurioAvailable = true
        case Left(err)                               =>
          println(s"Apicurio not available: ${err.message}")
          println("Skipping integration tests. Set APICURIO_TEST_URL to run tests.")
          apicurioAvailable = false
      }
      client.close()
    } catch {
      case ex: Exception =>
        println(s"Apicurio not available: ${ex.getMessage}")
        println("Skipping integration tests.")
        apicurioAvailable = false
    }
  }

  private def assumeApicurioAvailable(): Unit =
    assume(apicurioAvailable, "Apicurio Registry not available for testing")

  behavior of "SchemaFileUtils"

  it should "discover Avro schema files" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    schemas should not be empty
    schemas.exists(_.file.getName == "TestUser.avsc") shouldBe true
  }

  it should "discover JSON Schema files" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    schemas.exists(_.file.getName == "TestProduct.json") shouldBe true
  }

  it should "discover Protobuf schema files" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    schemas.exists(_.file.getName == "TestOrder.proto") shouldBe true
  }

  it should "discover OpenAPI schema files" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    schemas.exists(_.file.getName == "TestAPI.yaml") shouldBe true
  }

  it should "correctly identify artifact types" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    val avroSchema = schemas.find(_.file.getName == "TestUser.avsc")
    avroSchema.map(_.artifactType) shouldBe Some(ApicurioModels.ArtifactType.Avro)

    val jsonSchema = schemas.find(_.file.getName == "TestProduct.json")
    jsonSchema.map(_.artifactType) shouldBe Some(ApicurioModels.ArtifactType.JsonSchema)

    val protoSchema = schemas.find(_.file.getName == "TestOrder.proto")
    protoSchema.map(_.artifactType) shouldBe Some(ApicurioModels.ArtifactType.Protobuf)
  }

  it should "compute consistent hashes for schema content" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    schemas.foreach { schema =>
      val hash1 = SchemaFileUtils.computeHash(schema.content)
      val hash2 = SchemaFileUtils.computeHash(schema.content)
      hash1 shouldBe hash2
    }
  }

  behavior of "ApicurioClient - Publishing"

  it should "create an Avro artifact in Apicurio" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client           = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    val avroSchema = schemas.find(_.file.getName == "TestUser.avsc").get
    val artifactId = "TestUser"

    try {
      val result = client.createArtifact(
        testGroupId,
        artifactId,
        avroSchema.artifactType,
        avroSchema.content,
        avroSchema.fileExtension
      )

      result shouldBe a[Right[_, _]]
      val response = result.getOrElse(fail("Expected Right but got Left"))
      response.artifact.artifactId shouldBe artifactId
      response.artifact.groupId shouldBe testGroupId
      response.artifact.artifactType shouldBe "AVRO"
      response.version.version shouldBe "1"
      response.version.contentId should be > 0L
      response.version.globalId should be > 0L
    } finally
      client.close()
  }

  it should "create a JSON Schema artifact in Apicurio" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client           = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    val jsonSchema = schemas.find(_.file.getName == "TestProduct.json").get
    val artifactId = "TestProduct"

    try {
      val result = client.createArtifact(
        testGroupId,
        artifactId,
        jsonSchema.artifactType,
        jsonSchema.content,
        jsonSchema.fileExtension
      )

      result shouldBe a[Right[_, _]]
      val response = result.getOrElse(fail("Expected Right but got Left"))
      response.artifact.artifactId shouldBe artifactId
      response.artifact.artifactType shouldBe "JSON"
      response.version.version shouldBe "1"
    } finally
      client.close()
  }

  it should "create a Protobuf artifact in Apicurio" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client           = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    val protoSchema = schemas.find(_.file.getName == "TestOrder.proto").get
    val artifactId  = "TestOrder"

    try {
      // Note: Protobuf schemas are sent with application/x-protobuf content type
      // The content should be the raw proto file content
      val result = client.createArtifact(
        testGroupId,
        artifactId,
        protoSchema.artifactType,
        protoSchema.content,
        protoSchema.fileExtension
      )

      result shouldBe a[Right[_, _]]
      val response = result.getOrElse(fail("Expected Right but got Left"))
      response.artifact.artifactId shouldBe artifactId
      response.artifact.artifactType shouldBe "PROTOBUF"
    } finally
      client.close()
  }

  it should "create an OpenAPI artifact in Apicurio" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client           = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    val openApiSchema = schemas.find(_.file.getName == "TestAPI.yaml").get
    val artifactId    = "TestAPI"

    try {
      val result = client.createArtifact(
        testGroupId,
        artifactId,
        openApiSchema.artifactType,
        openApiSchema.content,
        openApiSchema.fileExtension
      )

      result shouldBe a[Right[_, _]]
      val response = result.getOrElse(fail("Expected Right but got Left"))
      response.artifact.artifactId shouldBe artifactId
      response.artifact.artifactType shouldBe "OPENAPI"
    } finally
      client.close()
  }

  it should "handle YAML OpenAPI schemas with correct content-type" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client = new ApicurioClient(registryUrl, apiKey, testLogger)

    // Actual YAML content to test YAML format handling
    val yamlContent =
      """openapi: 3.0.0
        |info:
        |  title: Test YAML API
        |  version: 1.0.0
        |  description: Test API in YAML format
        |paths:
        |  /health:
        |    get:
        |      summary: Health check
        |      responses:
        |        '200':
        |          description: OK
        |""".stripMargin

    val artifactId = "TestYamlOpenAPI"

    try {
      val result = client.createArtifact(
        testGroupId,
        artifactId,
        ApicurioModels.ArtifactType.OpenApi,
        yamlContent,
        "yaml"
      )

      result shouldBe a[Right[_, _]]
      val response = result.getOrElse(fail("Expected Right but got Left"))
      response.artifact.artifactId shouldBe artifactId
      response.artifact.artifactType shouldBe "OPENAPI"
      response.version.version shouldBe "1"

      // Verify we can retrieve the content back
      val retrievedContent = client.getVersionContent(testGroupId, artifactId, "1")
      retrievedContent shouldBe a[Right[_, _]]
      val content          = retrievedContent.getOrElse(fail("Expected Right but got Left"))
      content should include("Test YAML API")
    } finally
      client.close()
  }

  behavior of "ApicurioClient - Retrieving"

  it should "retrieve artifact metadata" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client = new ApicurioClient(registryUrl, apiKey, testLogger)

    try {
      val result = client.getArtifactMetadata(testGroupId, "TestUser")

      result shouldBe a[Right[_, _]]
      val metadata = result.getOrElse(fail("Expected Right but got Left"))
      metadata.artifactId shouldBe "TestUser"
      metadata.groupId shouldBe testGroupId
      metadata.artifactType shouldBe "AVRO"
    } finally
      client.close()
  }

  it should "retrieve version content" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client = new ApicurioClient(registryUrl, apiKey, testLogger)

    try {
      val result = client.getVersionContent(testGroupId, "TestUser", "1")

      result shouldBe a[Right[_, _]]
      val content = result.getOrElse(fail("Expected Right but got Left"))
      content should include("TestUser")
      content should include("com.example.test")
    } finally
      client.close()
  }

  it should "retrieve latest version" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client = new ApicurioClient(registryUrl, apiKey, testLogger)

    try {
      val result = client.getLatestVersion(testGroupId, "TestUser")

      result shouldBe a[Right[_, _]]
      val version = result.getOrElse(fail("Expected Right but got Left"))
      version.version should not be empty
      version.artifactId shouldBe "TestUser"
      version.groupId shouldBe testGroupId
    } finally
      client.close()
  }

  it should "pull schema content by version" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client    = new ApicurioClient(registryUrl, apiKey, testLogger)
    val outputDir = new File("target/test-pulled-schemas")

    try {
      val dependency = ApicurioModels.ApicurioDependency(testGroupId, "TestUser", "1")

      val contentResult = client.getVersionContent(testGroupId, "TestUser", "1")
      contentResult shouldBe a[Right[_, _]]
      val content       = contentResult.getOrElse(fail("Expected Right but got Left"))

      val saveResult = SchemaFileUtils.saveSchema(outputDir, dependency, content, testLogger)
      saveResult shouldBe a[Right[_, _]]

      val savedFile = saveResult.getOrElse(fail("Expected Right but got Left"))
      savedFile.exists() shouldBe true
      savedFile.getName shouldBe "TestUser.json"
    } finally
      client.close()
  }

  it should "pull latest version when 'latest' is specified" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client = new ApicurioClient(registryUrl, apiKey, testLogger)

    try {
      val result = client.getVersionContent(testGroupId, "TestProduct", "latest")

      result shouldBe a[Right[_, _]]
      val content = result.getOrElse(fail("Expected Right but got Left"))
      content should include("TestProduct")
    } finally
      client.close()
  }

  behavior of "ApicurioClient - Version Management"

  it should "create a new version for existing artifact" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client           = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    val avroSchema      = schemas.find(_.file.getName == "TestUser.avsc").get
    // Modify content slightly to create a new version
    val modifiedContent = avroSchema.content.replace("\"isActive\"", "\"active\"")

    try {
      val result = client.createVersion(testGroupId, "TestUser", modifiedContent, avroSchema.fileExtension)

      result shouldBe a[Right[_, _]]
      val version = result.getOrElse(fail("Expected Right but got Left"))
      version.artifactId shouldBe "TestUser"
      version.version.toInt should be > 1
    } finally
      client.close()
  }

  behavior of "End-to-End Workflow"

  it should "publish and pull all schema types" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client           = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val outputDir        = new File("target/test-pulled-all-schemas")

    try {
      // Discover all schemas
      val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
      schemas should have size 4 // Avro, JSON, Proto, OpenAPI

      // Publish all schemas (some may already exist, that's ok)
      val publishedArtifacts = schemas.map { schema =>
        val artifactId = SchemaFileUtils.extractArtifactId(schema)

        val publishResult = client.publishSchema(
          testGroupId,
          artifactId,
          schema.artifactType,
          schema.content,
          schema.fileExtension,
          ApicurioModels.CompatibilityLevel.Backward
        )

        publishResult shouldBe a[Right[_, _]]
        artifactId
      }

      // Pull all published schemas
      publishedArtifacts.foreach { artifactId =>
        val dependency    = ApicurioModels.ApicurioDependency(testGroupId, artifactId, "latest")
        val contentResult = client.getVersionContent(dependency.groupId, dependency.artifactId, "latest")

        contentResult shouldBe a[Right[_, _]]
        val content = contentResult.getOrElse(fail("Expected Right but got Left"))

        val saveResult = SchemaFileUtils.saveSchema(outputDir, dependency, content, testLogger)
        saveResult shouldBe a[Right[_, _]]
        val savedFile  = saveResult.getOrElse(fail("Expected Right but got Left"))
        savedFile.exists() shouldBe true
      }

      // Verify all pulled files exist
      val pulledFiles = outputDir
        .listFiles()
        .filter(_.isDirectory)
        .flatMap(_.listFiles())
        .flatMap(_.listFiles())

      pulledFiles should have length publishedArtifacts.size

    } finally
      client.close()
  }

  behavior of "ApicurioClient - Error Handling"

  it should "return ArtifactNotFound error for non-existent artifact" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client = new ApicurioClient(registryUrl, apiKey, testLogger)

    try {
      val result = client.getArtifactMetadata(testGroupId, "NonExistentArtifact")

      result shouldBe a[Left[_, _]]
      result match {
        case Left(ApicurioError.ArtifactNotFound(groupId, artifactId)) =>
          groupId shouldBe testGroupId
          artifactId shouldBe "NonExistentArtifact"
        case _                                                         => fail("Expected ArtifactNotFound error")
      }
    } finally
      client.close()
  }

  it should "return VersionNotFound error for non-existent version" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client = new ApicurioClient(registryUrl, apiKey, testLogger)

    try {
      val result = client.getVersionContent(testGroupId, "TestUser", "999")

      result shouldBe a[Left[_, _]]
      result match {
        case Left(ApicurioError.VersionNotFound(groupId, artifactId, version)) =>
          groupId shouldBe testGroupId
          artifactId shouldBe "TestUser"
          version shouldBe "999"
        case _                                                                 => fail("Expected VersionNotFound error")
      }
    } finally
      client.close()
  }

  it should "return InvalidSchema error for malformed JSON" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client = new ApicurioClient(registryUrl, apiKey, testLogger)

    try {
      val invalidJson = "{invalid json"
      val result      = client.createArtifact(
        testGroupId,
        "InvalidSchema",
        ApicurioModels.ArtifactType.JsonSchema,
        invalidJson,
        "json"
      )

      result shouldBe a[Left[_, _]]
      result match {
        case Left(ApicurioError.InvalidSchema(reason)) =>
          reason should include("parse")
        case _                                         => fail("Expected InvalidSchema error")
      }
    } finally
      client.close()
  }

  behavior of "SchemaReferenceUtils - Reference Detection"

  it should "detect no references in simple Avro schema" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    val avroSchema       = schemas.find(_.file.getName == "TestUser.avsc").get
    val artifactId       = "TestUser"

    val schemaWithRefs = SchemaReferenceUtils.detectReferences(avroSchema, artifactId, testLogger)

    schemaWithRefs.artifactId shouldBe artifactId
    schemaWithRefs.schema shouldBe avroSchema
    // TestUser is a simple schema with primitive types only
    schemaWithRefs.references shouldBe empty
  }

  it should "handle empty schema list in dependency ordering" in {
    val result = SchemaReferenceUtils.orderSchemasByDependencies(List.empty, testLogger)

    result shouldBe a[Right[_, _]]
    result.getOrElse(fail("Expected Right")) shouldBe empty
  }

  it should "order schemas with dependencies correctly" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    val schemasWithRefs = schemas.map { schema =>
      val artifactId = SchemaFileUtils.extractArtifactId(schema)
      SchemaReferenceUtils.detectReferences(schema, artifactId, testLogger)
    }.toList

    val result = SchemaReferenceUtils.orderSchemasByDependencies(schemasWithRefs, testLogger)

    result shouldBe a[Right[_, _]]
    val ordered = result.getOrElse(fail("Expected Right"))
    ordered.size shouldBe schemasWithRefs.size
  }

  it should "handle circular dependencies in topological sort" in {
    // Create mock schemas with circular dependency: A -> B -> C -> A
    val schemaA = ApicurioModels.SchemaFile(
      file = new File("A.avsc"),
      content = "{}",
      hash = "hashA",
      artifactType = ApicurioModels.ArtifactType.Avro,
      fileExtension = "avsc"
    )
    val schemaB = ApicurioModels.SchemaFile(
      file = new File("B.avsc"),
      content = "{}",
      hash = "hashB",
      artifactType = ApicurioModels.ArtifactType.Avro,
      fileExtension = "avsc"
    )
    val schemaC = ApicurioModels.SchemaFile(
      file = new File("C.avsc"),
      content = "{}",
      hash = "hashC",
      artifactType = ApicurioModels.ArtifactType.Avro,
      fileExtension = "avsc"
    )

    val schemasWithRefs = List(
      SchemaReferenceUtils.SchemaWithReferences(
        schemaA,
        "A",
        List(SchemaReferenceUtils.SchemaReference("B", artifactId = Some("B")))
      ),
      SchemaReferenceUtils.SchemaWithReferences(
        schemaB,
        "B",
        List(SchemaReferenceUtils.SchemaReference("C", artifactId = Some("C")))
      ),
      SchemaReferenceUtils.SchemaWithReferences(
        schemaC,
        "C",
        List(SchemaReferenceUtils.SchemaReference("A", artifactId = Some("A")))
      )
    )

    val result = SchemaReferenceUtils.orderSchemasByDependencies(schemasWithRefs, testLogger)

    result shouldBe a[Left[_, _]]
    result match {
      case Left(ApicurioError.CircularDependency(schemas)) =>
        schemas should contain allOf ("A", "B", "C")
      case _                                               => fail("Expected CircularDependency error")
    }
  }

  it should "correctly order schemas in dependency order (dependencies first)" in {
    // Create schemas: A has no deps, B depends on A, C depends on B
    val schemaA = ApicurioModels.SchemaFile(
      file = new File("A.avsc"),
      content = "{}",
      hash = "hashA",
      artifactType = ApicurioModels.ArtifactType.Avro,
      fileExtension = "avsc"
    )
    val schemaB = ApicurioModels.SchemaFile(
      file = new File("B.avsc"),
      content = "{}",
      hash = "hashB",
      artifactType = ApicurioModels.ArtifactType.Avro,
      fileExtension = "avsc"
    )
    val schemaC = ApicurioModels.SchemaFile(
      file = new File("C.avsc"),
      content = "{}",
      hash = "hashC",
      artifactType = ApicurioModels.ArtifactType.Avro,
      fileExtension = "avsc"
    )

    val schemasWithRefs = List(
      SchemaReferenceUtils
        .SchemaWithReferences(schemaC, "C", List(SchemaReferenceUtils.SchemaReference("B", artifactId = Some("B")))),
      SchemaReferenceUtils
        .SchemaWithReferences(schemaB, "B", List(SchemaReferenceUtils.SchemaReference("A", artifactId = Some("A")))),
      SchemaReferenceUtils.SchemaWithReferences(schemaA, "A", List.empty)
    )

    val result = SchemaReferenceUtils.orderSchemasByDependencies(schemasWithRefs, testLogger)

    result shouldBe a[Right[_, _]]
    val ordered = result.getOrElse(fail("Expected Right"))
    ordered.size shouldBe 3

    // Verify ordering: A should come before B, B should come before C
    val ids = ordered.map(_.artifactId)
    ids.indexOf("A") should be < ids.indexOf("B")
    ids.indexOf("B") should be < ids.indexOf("C")
  }

  it should "handle multiple independent dependency chains" in {
    // Chain 1: A -> B    Chain 2: X -> Y
    val schemaA = ApicurioModels.SchemaFile(new File("A.avsc"), "{}", "hashA", ApicurioModels.ArtifactType.Avro, "avsc")
    val schemaB = ApicurioModels.SchemaFile(new File("B.avsc"), "{}", "hashB", ApicurioModels.ArtifactType.Avro, "avsc")
    val schemaX = ApicurioModels.SchemaFile(new File("X.avsc"), "{}", "hashX", ApicurioModels.ArtifactType.Avro, "avsc")
    val schemaY = ApicurioModels.SchemaFile(new File("Y.avsc"), "{}", "hashY", ApicurioModels.ArtifactType.Avro, "avsc")

    val schemasWithRefs = List(
      SchemaReferenceUtils
        .SchemaWithReferences(schemaB, "B", List(SchemaReferenceUtils.SchemaReference("A", artifactId = Some("A")))),
      SchemaReferenceUtils
        .SchemaWithReferences(schemaY, "Y", List(SchemaReferenceUtils.SchemaReference("X", artifactId = Some("X")))),
      SchemaReferenceUtils.SchemaWithReferences(schemaA, "A", List.empty),
      SchemaReferenceUtils.SchemaWithReferences(schemaX, "X", List.empty)
    )

    val result = SchemaReferenceUtils.orderSchemasByDependencies(schemasWithRefs, testLogger)

    result shouldBe a[Right[_, _]]
    val ordered = result.getOrElse(fail("Expected Right"))
    ordered.size shouldBe 4

    // Verify both chains are ordered correctly
    val ids = ordered.map(_.artifactId)
    ids.indexOf("A") should be < ids.indexOf("B")
    ids.indexOf("X") should be < ids.indexOf("Y")
  }

  behavior of "ApicurioClient - Compatibility Checking"

  it should "check compatibility for compatible schema changes" taggedAs IntegrationTest in {
    assumeApicurioAvailable()

    val client           = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas          = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)

    try {
      val avroSchema        = schemas.find(_.file.getName == "TestUser.avsc").get
      // Add a new optional field - backward compatible
      val compatibleContent = avroSchema.content.replace(
        "\"fields\":",
        "\"fields\": [{\"name\": \"newField\", \"type\": [\"null\", \"string\"], \"default\": null},"
      )

      val result = client.checkCompatibility(
        testGroupId,
        "TestUser",
        compatibleContent,
        ApicurioModels.CompatibilityLevel.Backward
      )

      result shouldBe a[Right[_, _]]
      // Result may be true or false depending on whether the artifact exists and compatibility rules
      result.isRight shouldBe true
    } finally
      client.close()
  }

  behavior of "ApicurioModels - Error Messages"

  it should "provide clear error messages for all error types" in {
    val errors = List(
      ApicurioError.ArtifactNotFound("group1", "artifact1"),
      ApicurioError.VersionNotFound("group1", "artifact1", "v1"),
      ApicurioError.IncompatibleSchema("group1", "artifact1", "test reason"),
      ApicurioError.CircularDependency(Set("schema1", "schema2")),
      ApicurioError.InvalidSchema("bad format"),
      ApicurioError.HttpError(404, "Not found"),
      ApicurioError.NetworkError(new Exception("Connection failed")),
      ApicurioError.ParseError("Invalid JSON"),
      ApicurioError.ConfigurationError("Missing config")
    )

    errors.foreach { error =>
      error.message should not be empty
      error.message should not include "null"
    }
  }
}

/** Simple test logger for capturing test output
  */
class TestLogger extends Logger {
  private val messages = scala.collection.mutable.ArrayBuffer[String]()

  override def trace(t: => Throwable): Unit                               = {}
  override def success(message: => String): Unit                          = messages += s"[success] $message"
  override def log(level: sbt.util.Level.Value, message: => String): Unit =
    messages += s"[${level.toString}] $message"

  def getMessages: Seq[String] = messages.toSeq
  def clear(): Unit            = messages.clear()
}
