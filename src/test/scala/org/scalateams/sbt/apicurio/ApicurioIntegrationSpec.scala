package org.scalateams.sbt.apicurio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, Tag}
import sbt.util.Logger
import java.io.File
import scala.util.{Success, Failure}

object IntegrationTest extends Tag("org.scalateams.sbt.apicurio.IntegrationTest")

/**
 * Integration tests for Apicurio plugin.
 * 
 * These tests require a running Apicurio Registry instance.
 * Set environment variables to run:
 *   APICURIO_TEST_URL - Registry URL (default: http://localhost:8080)
 *   APICURIO_TEST_API_KEY - Optional API key
 * 
 * To run: sbt "testOnly *IntegrationSpec"
 * To skip: sbt "testOnly * -- -l org.scalateams.sbt.apicurio.IntegrationTest"
 */
class ApicurioIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  // Test configuration
  private val registryUrl = sys.env.getOrElse("APICURIO_TEST_URL", "http://localhost:8080")
  private val apiKey = sys.env.get("APICURIO_TEST_API_KEY")
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
        case Failure(_: ArtifurioNotFoundException) =>
          // 404 is expected, but means the service is reachable
          apicurioAvailable = true
        case Success(_) =>
          // Artifact exists somehow, but service is reachable
          apicurioAvailable = true
        case Failure(ex) =>
          println(s"Apicurio not available: ${ex.getMessage}")
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
  
  private def assumeApicurioAvailable(): Unit = {
    assume(apicurioAvailable, "Apicurio Registry not available for testing")
  }

  behavior of "SchemaFileUtils"

  it should "discover Avro schema files" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    schemas should not be empty
    schemas.exists(_.file.getName == "TestUser.avsc") shouldBe true
  }

  it should "discover JSON Schema files" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    schemas.exists(_.file.getName == "TestProduct.json") shouldBe true
  }

  it should "discover Protobuf schema files" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    schemas.exists(_.file.getName == "TestOrder.proto") shouldBe true
  }

  it should "discover OpenAPI schema files" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    schemas.exists(_.file.getName == "TestAPI.yaml") shouldBe true
  }

  it should "correctly identify artifact types" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    val avroSchema = schemas.find(_.file.getName == "TestUser.avsc")
    avroSchema.map(_.artifactType) shouldBe Some(ApicurioModels.ArtifactType.Avro)
    
    val jsonSchema = schemas.find(_.file.getName == "TestProduct.json")
    jsonSchema.map(_.artifactType) shouldBe Some(ApicurioModels.ArtifactType.JsonSchema)
    
    val protoSchema = schemas.find(_.file.getName == "TestOrder.proto")
    protoSchema.map(_.artifactType) shouldBe Some(ApicurioModels.ArtifactType.Protobuf)
  }

  it should "compute consistent hashes for schema content" in {
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    schemas.foreach { schema =>
      val hash1 = SchemaFileUtils.computeHash(schema.content)
      val hash2 = SchemaFileUtils.computeHash(schema.content)
      hash1 shouldBe hash2
    }
  }

  behavior of "ApicurioClient - Publishing"

  it should "create an Avro artifact in Apicurio" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    val avroSchema = schemas.find(_.file.getName == "TestUser.avsc").get
    val artifactId = "TestUser"
    
    try {
      val result = client.createArtifact(
        testGroupId,
        artifactId,
        avroSchema.artifactType,
        avroSchema.content
      )
      
      result shouldBe a[Success[_]]
      result.get.artifact.artifactId shouldBe artifactId
      result.get.artifact.groupId shouldBe testGroupId
      result.get.artifact.artifactType shouldBe "AVRO"
      result.get.version.version shouldBe "1"
      result.get.version.contentId should be > 0L
      result.get.version.globalId should be > 0L
    } finally {
      client.close()
    }
  }

  it should "create a JSON Schema artifact in Apicurio" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    val jsonSchema = schemas.find(_.file.getName == "TestProduct.json").get
    val artifactId = "TestProduct"
    
    try {
      val result = client.createArtifact(
        testGroupId,
        artifactId,
        jsonSchema.artifactType,
        jsonSchema.content
      )
      
      result shouldBe a[Success[_]]
      result.get.artifact.artifactId shouldBe artifactId
      result.get.artifact.artifactType shouldBe "JSON"
      result.get.version.version shouldBe "1"
    } finally {
      client.close()
    }
  }

  it should "create a Protobuf artifact in Apicurio" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    val protoSchema = schemas.find(_.file.getName == "TestOrder.proto").get
    val artifactId = "TestOrder"
    
    try {
      // Note: Protobuf schemas need to be sent as JSON-wrapped strings for Apicurio 3.x
      // The content should be the raw proto file content
      val result = client.createArtifact(
        testGroupId,
        artifactId,
        protoSchema.artifactType,
        protoSchema.content
      )
      
      result shouldBe a[Success[_]]
      result.get.artifact.artifactId shouldBe artifactId
      result.get.artifact.artifactType shouldBe "PROTOBUF"
    } finally {
      client.close()
    }
  }

  it should "create an OpenAPI artifact in Apicurio" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    val openApiSchema = schemas.find(_.file.getName == "TestAPI.yaml").get
    val artifactId = "TestAPI"
    
    try {
      val result = client.createArtifact(
        testGroupId,
        artifactId,
        openApiSchema.artifactType,
        openApiSchema.content
      )
      
      result shouldBe a[Success[_]]
      result.get.artifact.artifactId shouldBe artifactId
      result.get.artifact.artifactType shouldBe "OPENAPI"
    } finally {
      client.close()
    }
  }

  behavior of "ApicurioClient - Retrieving"

  it should "retrieve artifact metadata" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    
    try {
      val result = client.getArtifactMetadata(testGroupId, "TestUser")
      
      result shouldBe a[Success[_]]
      result.get.artifactId shouldBe "TestUser"
      result.get.groupId shouldBe testGroupId
      result.get.artifactType shouldBe "AVRO"
    } finally {
      client.close()
    }
  }

  it should "retrieve version content" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    
    try {
      val result = client.getVersionContent(testGroupId, "TestUser", "1")
      
      result shouldBe a[Success[_]]
      result.get should include ("TestUser")
      result.get should include ("com.example.test")
    } finally {
      client.close()
    }
  }

  it should "retrieve latest version" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    
    try {
      val result = client.getLatestVersion(testGroupId, "TestUser")
      
      result shouldBe a[Success[_]]
      result.get.version should not be empty
      result.get.artifactId shouldBe "TestUser"
      result.get.groupId shouldBe testGroupId
    } finally {
      client.close()
    }
  }

  it should "pull schema content by version" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    val outputDir = new File("target/test-pulled-schemas")
    
    try {
      val dependency = ApicurioModels.ApicurioDependency(testGroupId, "TestUser", "1")
      
      val contentResult = client.getVersionContent(testGroupId, "TestUser", "1")
      contentResult shouldBe a[Success[_]]
      
      val saveResult = SchemaFileUtils.saveSchema(outputDir, dependency, contentResult.get, testLogger)
      saveResult shouldBe a[Success[_]]
      
      val savedFile = saveResult.get
      savedFile.exists() shouldBe true
      savedFile.getName shouldBe "TestUser.json"
    } finally {
      client.close()
    }
  }

  it should "pull latest version when 'latest' is specified" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    
    try {
      val result = client.getVersionContent(testGroupId, "TestProduct", "latest")
      
      result shouldBe a[Success[_]]
      result.get should include ("TestProduct")
    } finally {
      client.close()
    }
  }

  behavior of "ApicurioClient - Version Management"

  it should "create a new version for existing artifact" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val schemas = SchemaFileUtils.discoverSchemas(Seq(testResourcesDir), testLogger)
    
    val avroSchema = schemas.find(_.file.getName == "TestUser.avsc").get
    // Modify content slightly to create a new version
    val modifiedContent = avroSchema.content.replace("\"isActive\"", "\"active\"")
    
    try {
      val result = client.createVersion(testGroupId, "TestUser", modifiedContent)
      
      result shouldBe a[Success[_]]
      result.get.artifactId shouldBe "TestUser"
      result.get.version.toInt should be > 1
    } finally {
      client.close()
    }
  }

  behavior of "End-to-End Workflow"

  it should "publish and pull all schema types" taggedAs IntegrationTest in {
    assumeApicurioAvailable()
    
    val client = new ApicurioClient(registryUrl, apiKey, testLogger)
    val testResourcesDir = new File("src/test/resources/test-schemas")
    val outputDir = new File("target/test-pulled-all-schemas")
    
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
          ApicurioModels.CompatibilityLevel.Backward
        )
        
        publishResult shouldBe a[Success[_]]
        artifactId
      }
      
      // Pull all published schemas
      publishedArtifacts.foreach { artifactId =>
        val dependency = ApicurioModels.ApicurioDependency(testGroupId, artifactId, "latest")
        val contentResult = client.getVersionContent(dependency.groupId, dependency.artifactId, "latest")
        
        contentResult shouldBe a[Success[_]]
        
        val saveResult = SchemaFileUtils.saveSchema(outputDir, dependency, contentResult.get, testLogger)
        saveResult shouldBe a[Success[_]]
        saveResult.get.exists() shouldBe true
      }
      
      // Verify all pulled files exist
      val pulledFiles = outputDir.listFiles()
        .filter(_.isDirectory)
        .flatMap(_.listFiles())
        .flatMap(_.listFiles())
      
      pulledFiles should have length publishedArtifacts.size
      
    } finally {
      client.close()
    }
  }
}

/**
 * Simple test logger for capturing test output
 */
class TestLogger extends Logger {
  private val messages = scala.collection.mutable.ArrayBuffer[String]()
  
  override def trace(t: => Throwable): Unit = {}
  override def success(message: => String): Unit = messages += s"[success] $message"
  override def log(level: sbt.util.Level.Value, message: => String): Unit = {
    messages += s"[${level.toString}] $message"
  }
  
  def getMessages: Seq[String] = messages.toSeq
  def clear(): Unit = messages.clear()
}
