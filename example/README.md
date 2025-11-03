# sbt-apicurio Example Project

This is an example project demonstrating how to use the sbt-apicurio plugin.

## Setup

1. Start an Apicurio Registry instance (or use an existing one):

```bash
docker run -d -p 8080:8080 apicurio/apicurio-registry:3.0.0
```

2. Set environment variables (optional):

```bash
export APICURIO_URL="http://localhost:8080"
export APICURIO_API_KEY="your-api-key-if-needed"
```

## Usage

### Discover schemas

```bash
sbt apicurioDiscoverSchemas
```

### Validate configuration

```bash
sbt apicurioValidateSettings
```

### Publish schemas

```bash
sbt apicurioPublish
```

This will publish the example schemas (`UserCreated.avsc` and `OrderPlaced.avsc`) to the registry.

### Pull dependencies

Uncomment the dependency lines in `build.sbt` and run:

```bash
sbt apicurioPull
```

Downloaded schemas will be in `target/schemas/`.

## Schema Files

This example includes two Avro schemas:

- `src/main/schemas/UserCreated.avsc` - User creation event
- `src/main/schemas/OrderPlaced.avsc` - Order placement event

Try modifying these schemas and republishing to see the version increment and compatibility checking in action!
