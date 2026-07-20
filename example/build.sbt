name         := "sbt-apicurio-example"
organization := "org.scalateams.example"
version      := "0.1.0-SNAPSHOT"

scalaVersion := "3.8.4"

// Enable the Apicurio plugin
enablePlugins(ApicurioPlugin)

// Required settings (component-based URL)
apicurioRegistryHost   := sys.env.getOrElse("APICURIO_HOST", "localhost")
apicurioRegistryScheme := "http"
apicurioRegistryPort   := Some(8080)
apicurioGroupId        := "com.example.myservice"

// Optional Keycloak OAuth2 authentication (unauthenticated by default)
apicurioKeycloakConfig := None
// apicurioKeycloakConfig := Some(keycloak(
//   url = sys.env.getOrElse("KEYCLOAK_URL", ""),
//   realm = sys.env.getOrElse("KEYCLOAK_REALM", ""),
//   clientId = sys.env.getOrElse("KEYCLOAK_CLIENT_ID", ""),
//   clientSecret = sys.env.getOrElse("KEYCLOAK_CLIENT_SECRET", "")
// ))

apicurioCompatibilityLevel := CompatibilityLevel.Backward

// Schema paths (default is fine; shown for illustration)
apicurioSchemaPaths := Seq(
  sourceDirectory.value / "main" / "schemas"
)

// Pull dependencies from other services
apicurioPullDependencies := Seq(
  // schema("com.example.catalog", "CatalogItemCreated", "latest"),
  // schema("com.example.order", "OrderPlaced", "latest")
)

// Optional: recursively pull transitive dependencies
// apicurioPullRecursive := true
