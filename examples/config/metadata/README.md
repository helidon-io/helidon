# Configuration metadata usage example

This example reads configuration metadata from the classpath and creates a full
`application.yaml` content with all possible configuration.

## Setup

This application does not have any root configuration on classpath, so it would generate an empty file.
Add at least one module to `pom.xml` that can be configured to see the output.

Example:
```xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-oidc</artifactId>
</dependency>
```

## Build and run

```bash
mvn package
java -jar target/helidon-examples-config-metadata.jar > application.yaml
```
