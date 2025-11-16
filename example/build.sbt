name := "sbt-apicurio-example"
organization := "org.scalateams.example"
version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.12"

// Enable the Apicurio plugin
enablePlugins(ApicurioPlugin)

// Required settings
apicurioRegistryUrl := sys.env.getOrElse("APICURIO_URL", "http://localhost:8080")
apicurioGroupId := "com.example.myservice"

// Optional settings
apicurioApiKey := sys.env.get("APICURIO_API_KEY")
apicurioCompatibilityLevel := CompatibilityLevel.Backward

// Schema paths (default is fine, but showing how to configure)
apicurioSchemaPaths := Seq(
  sourceDirectory.value / "main" / "schemas"
)

// Pull dependencies from other services
apicurioPullDependencies := Seq(
  // Example dependencies - uncomment when registry has these artifacts
  // schema("com.example.catalog", "CatalogItemCreated", "latest"),
  // schema("com.example.order", "OrderPlaced", "latest")
)

// Optional: Recursively pull transitive dependencies
// apicurioPullRecursive := true
