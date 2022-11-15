# New Feature Catalog

Current `FeatureCatalog` explicitly mentions all Helidon features and adds them.
This requires us to keep track of features separately from the feature sources, which is hard to refactor (and keep in sync).
To remedy this situation, the following approach is now to be used:
- Each feature `module-info.java` is to be annotated with `@Feature` annotation
- For incubating features (production ready, but being worked on), use `@Incubating`
- For experimental features (not production ready, for preview only), use `@Experimental`
- For information related to Native image, use `@Aot`

An annotation processor is available to process this annotation and generate a runtime property file with module information.

## Module info updates

The module info must require the API, as it is used within the file. As the annotations are source only, we can use 
`requires static`:

```java
requires static io.helidon.common.features.api;
```

Module info example:
```java
import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Incubating;

/**
 * GraphQL server integration with Helidon Reactive WebServer.
 */
@Incubating
@Feature(value = "GraphQL", in = HelidonFlavor.SE, invalidIn = {HelidonFlavor.MP, HelidonFlavor.NIMA})
@Aot(description = "Incubating support, tested on limited use cases")
module io.helidon.reactive.graphql.server {
    requires static io.helidon.common.features.api;
    // other module dependencies and configuration
}
```

## Dependency to be added
```xml
<dependency>
    <groupId>io.helidon.common.features</groupId>
    <artifactId>helidon-common-features-api</artifactId>
    <scope>provided</scope>
    <optional>true</optional>
</dependency>
```

## Annotation processor setup
This example provides full `plugins` tag, if exists, update only relevant sections.
```xml
<plugins>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
            <annotationProcessorPaths>
                <path>
                    <groupId>io.helidon.common.features</groupId>
                    <artifactId>helidon-common-features-processor</artifactId>
                    <version>${helidon.version}</version>
                </path>
            </annotationProcessorPaths>
        </configuration>
    </plugin>
</plugins>
```

## TODO
The following modules should be refactored:
```java
        addSe("io.helidon.metrics",
              "Metrics",
              "Metrics support",
              "Metrics");
        add("io.helidon.metrics.prometheus",
            FeatureDescriptor.builder()
                    .name("Prometheus")
                    .description("Metrics support for Prometheus")
                    .path("WebServer", "Prometheus")
                    .nativeSupported(false)
                    .flavor(HelidonFlavor.SE)
        );
        addSe("io.helidon.openapi",
              "OpenAPI",
              "Open API support",
              "OpenAPI");
        addSe("io.helidon.webserver",
              "WebServer",
              "Helidon WebServer",
              "WebServer");
        addSe("io.helidon.webserver.accesslog",
              "Access Log",
              "Access log support",
              "WebServer", "AccessLog");
        addSe("io.helidon.webserver.cors",
              "CORS",
              "CORS support for WebServer",
              "WebServer", "CORS");
        addSe("io.helidon.webserver.jersey",
              "Jersey",
              "WebServer integration with Jersey",
              "WebServer", "Jersey");
        add("io.helidon.scheduling",
            FeatureDescriptor.builder()
                    .flavor(HelidonFlavor.SE)
                    .name("Scheduling")
                    .description("Scheduling of periodical tasks")
                    .path("Scheduling")
                    .nativeSupported(true));
        add("io.helidon.webserver.tyrus",
            FeatureDescriptor.builder()
                    .flavor(HelidonFlavor.SE)
                    .name("Websocket")
                    .description("Jakarta Websocket implementation")
                    .path("WebServer", "Websocket")
                    .nativeSupported(true)
                    .nativeDescription("Server only"));
        add("io.helidon.integrations.micrometer",
            FeatureDescriptor.builder()
                    .name("Micrometer")
                    .description("Micrometer integration")
                    .path("Micrometer")
                    .experimental(true)
                    .nativeSupported(true)
                    .flavor(HelidonFlavor.SE));
        add("io.helidon.integrations.oci.connect",
            FeatureDescriptor.builder()
                    .name("OCI")
                    .description("OCI Integration")
                    .path("OCI")
                    .flavor(HelidonFlavor.SE)
                    .experimental(true));
        add("io.helidon.integrations.vault",
            FeatureDescriptor.builder()
                    .name("HCP Vault")
                    .description("Hashicorp Vault Integration")
                    .path("HCP Vault")
                    .flavor(HelidonFlavor.SE)
                    .experimental(true));
        add("io.helidon.integrations.microstream",
            FeatureDescriptor.builder()
                    .name("Microstream")
                    .description("Microstream Integration")
                    .path("Microstream")
                    .flavor(HelidonFlavor.SE)
                    .experimental(true)
                    .nativeSupported(false));
        /*
         * MP Modules
         */
        add("io.helidon.integrations.cdi.eclipselink",
            FeatureDescriptor.builder()
                    .name("EclipseLink")
                    .description("EclipseLink support for Helidon MP")
                    .path("JPA", "EclipseLink")
                    .flavor(HelidonFlavor.MP)
                    .nativeSupported(false));
        add("io.helidon.integrations.cdi.hibernate",
            FeatureDescriptor.builder()
                    .name("Hibernate")
                    .description("Hibernate support for Helidon MP")
                    .path("JPA", "Hibernate")
                    .flavor(HelidonFlavor.MP)
                    .nativeDescription("Experimental support, tested on limited use cases"));
        add("io.helidon.integrations.cdi.jpa",
            FeatureDescriptor.builder()
                    .name("JPA")
                    .description("Jakarta persistence API support for Helidon MP")
                    .flavor(HelidonFlavor.MP)
                    .path("JPA"));
        add("io.helidon.integrations.jta.cdi",
            FeatureDescriptor.builder()
                    .name("JTA")
                    .description("Jakarta transaction API support for Helidon MP")
                    .path("JTA")
                    .flavor(HelidonFlavor.MP)
                    .nativeDescription("Experimental support, tested on limited use cases"));
        addMp("io.helidon.microprofile.accesslog",
              "Access Log",
              "Access log support",
              "Server", "AccessLog");
        addMp("io.helidon.microprofile.cdi",
              "CDI",
              "Jakarta CDI implementation",
              "CDI");
        addMp("io.helidon.microprofile.config",
              "Config",
              "MicroProfile configuration spec implementation",
              "Config");
        addMp("io.helidon.microprofile.cors",
              "CORS",
              "CORS support for Server",
              "Server", "CORS");
        addMp("io.helidon.microprofile.faulttolerance",
              "Fault Tolerance",
              "MicroProfile Fault Tolerance spec implementation",
              "FT");
        add("io.helidon.microprofile.graphql.server",
            FeatureDescriptor.builder()
                    .name("GraphQL")
                    .description("MicroProfile GraphQL spec implementation")
                    .path("GraphQL")
                    .nativeDescription("Experimental support, tested on limited use cases")
                    .flavor(HelidonFlavor.MP)
                    .experimental(true));
        add("io.helidon.microprofile.grpc.server",
            FeatureDescriptor.builder()
                    .name("gRPC Server")
                    .description("Server for gRPC services")
                    .path("grpc")
                    .flavor(HelidonFlavor.MP)
                    .nativeSupported(false));
        add("io.helidon.microprofile.grpc.client",
            FeatureDescriptor.builder()
                    .name("gRPC Client")
                    .description("Client for gRPC services")
                    .path("grpcClient")
                    .flavor(HelidonFlavor.MP)
                    .nativeSupported(false));
        addMp("io.helidon.microprofile.grpc.metrics",
              "Metrics",
              "Metrics for gRPC client",
              "grpcClient", "Metrics"
        );
        addMp("io.helidon.microprofile.grpc.metrics",
              "Metrics",
              "Metrics for gRPC server",
              "grpcServer", "Metrics"
        );
        addMp("io.helidon.microprofile.health",
              "Health",
              "MicroProfile Health spec implementation",
              "Health");
        addMp("io.helidon.microprofile.jwt.auth",
              "JWT Auth",
              "MicroProfile JWT Auth spec implementation",
              "Security", "JWTAuth");
        add("io.helidon.microprofile.messaging",
            FeatureDescriptor.builder()
                    .name("Messaging")
                    .description("MicroProfile Reactive Messaging spec implementation")
                    .path("Messaging")
                    .flavor(HelidonFlavor.MP)
                    .experimental(true));
        addMp("io.helidon.microprofile.metrics",
              "Metrics",
              "MicroProfile metrics spec implementation",
              "Metrics");
        addMp("io.helidon.microprofile.openapi",
              "Open API",
              "MicroProfile Open API spec implementation",
              "OpenAPI");
        add("io.helidon.microprofile.reactive",
            FeatureDescriptor.builder()
                    .name("Reactive")
                    .description("MicroProfile Reactive Stream operators")
                    .path("Reactive")
                    .flavor(HelidonFlavor.MP)
                    .experimental(true));
        addMp("io.helidon.microprofile.security",
              "Security",
              "Security support",
              "Security");
        addMp("io.helidon.microprofile.server",
              "Server",
              "Server for Helidon MP",
              "Server");
        addMp("io.helidon.microprofile.server",
              "JAX-RS",
              "Jakarta JAX-RS implementation (Jersey)",
              "JAX-RS");
        addMp("io.helidon.microprofile.tracing",
              "Tracing",
              "MicroProfile tracing spec implementation",
              "Tracing");

        add("io.helidon.microprofile.tyrus",
            FeatureDescriptor.builder()
                    .flavor(HelidonFlavor.MP)
                    .name("Websocket")
                    .description("Jakarta Websocket implementation")
                    .path("Websocket")
                    .nativeSupported(false));

        add("io.helidon.microprofile.restclient",
            FeatureDescriptor.builder()
                    .name("REST Client")
                    .description("MicroProfile REST client spec implementation")
                    .path("RESTClient")
                    .flavor(HelidonFlavor.MP)
                    .nativeDescription("Does not support execution of default methods on interfaces."));

        add("io.helidon.integrations.micronaut.cdi",
            FeatureDescriptor.builder()
                    .name("Micronaut")
                    .description("Micronaut integration")
                    .path("CDI", "Micronaut")
                    .flavor(HelidonFlavor.MP)
                    .experimental(true)
        );

        add("io.helidon.integrations.micronaut.cdi.data",
            FeatureDescriptor.builder()
                    .name("Micronaut Data")
                    .description("Micronaut Data integration")
                    .path("CDI", "Micronaut", "Data")
                    .flavor(HelidonFlavor.MP)
                    .experimental(true)
        );

        add("io.helidon.microprofile.scheduling",
            FeatureDescriptor.builder()
                    .name("Scheduling")
                    .description("Task scheduling")
                    .path("Scheduling")
                    .flavor(HelidonFlavor.MP)
                    .nativeSupported(true)
                    .experimental(true)
        );

        add("io.helidon.integrations.micrometer.cdi",
            FeatureDescriptor.builder()
                    .name("Micrometer")
                    .description("Micrometer integration")
                    .path("Micrometer")
                    .experimental(true)
                    .nativeSupported(true)
                    .flavor(HelidonFlavor.MP));

        add("io.helidon.integrations.oci.cdi",
            FeatureDescriptor.builder()
                    .name("OCI")
                    .description("OCI Integration")
                    .path("OCI")
                    .flavor(HelidonFlavor.MP)
                    .experimental(true));

        add("io.helidon.integrations.vault.cdi",
            FeatureDescriptor.builder()
                    .name("HCP Vault")
                    .description("Hashicorp Vault Integration")
                    .path("HCP Vault")
                    .flavor(HelidonFlavor.MP)
                    .experimental(true));

        add("io.helidon.microprofile.lra",
            FeatureDescriptor.builder()
                    .name("Long Running Actions")
                    .description("MicroProfile Long Running Actions")
                    .path("LRA")
                    .flavor(HelidonFlavor.MP)
                    .nativeSupported(true)
                    .experimental(true));

        add("io.helidon.integrations.microstream.cdi",
            FeatureDescriptor.builder()
                    .name("Microstream")
                    .description("Microstream Integration")
                    .path("Microstream")
                    .flavor(HelidonFlavor.MP)
                    .experimental(true)
                    .nativeSupported(false));
        /*
         * Common modules
         */
        add("io.helidon.config.encryption",
            "Encryption",
            "Support for secret encryption in config",
            "Config", "Encryption");
        add("io.helidon.config.etcd",
            FeatureDescriptor.builder()
                    .name("etcd")
                    .description("Config source based on etcd")
                    .path("Config", "etcd")
                    .nativeSupported(false));
        add("io.helidon.config.git",
            "git",
            "Config source based on a git repository",
            "Config", "git");
        add("io.helidon.config.hocon",
            "HOCON",
            "HOCON media type support for config",
            "Config", "HOCON");
        add("io.helidon.config.objectmapping",
            "Object Mapping",
            "Object mapping support for Config",
            "Config", "ObjectMapping");
        add("io.helidon.config.yaml",
            "YAML",
            "YAML media type support for config",
            "Config", "YAML");
        add("io.helidon.reactive.dbclient",
            FeatureDescriptor.builder()
                    .name("Db Client")
                    .description("Reactive database client")
                    .path("DbClient")
                    .experimental(true));
        add("io.helidon.reactive.dbclient.health",
            "Health Check",
            "Reactive database client health check support",
            "DbClient", "Health");
        add("io.helidon.reactive.dbclient.jsonp",
            "JSON-P",
            "JSON Processing mapping DbRow",
            "DbClient", "JSON-P");
        add("io.helidon.reactive.dbclient.jdbc",
            FeatureDescriptor.builder()
                    .name("JDBC")
                    .description("Reactive database client over JDBC")
                    .path("DbClient", "JDBC")
                    .nativeDescription("Tested with Helidon Oracle and H2 drivers (see examples)"));
        add("io.helidon.reactive.dbclient.metrics",
            "Metrics",
            "Reactive database client metrics support",
            "DbClient", "Metrics");
        add("io.helidon.reactive.dbclient.mongodb",
            "mongo",
            "Reactive database client with reactive mongo driver",
            "DbClient", "mongo");
        add("io.helidon.reactive.dbclient.tracing",
            "Tracing",
            "Reactive database client tracing support",
            "DbClient", "Tracing");
        add("io.helidon.health.checks",
            "Built-ins",
            "Built in health checks",
            "Health", "Builtins");
        add("io.helidon.messaging.connectors.kafka",
            FeatureDescriptor.builder()
                    .name("Kafka Connector")
                    .description("Reactive messaging connector for Kafka")
                    .path("Messaging", "Kafka")
                    .experimental(true)
                    .nativeSupported(true));
        add("io.helidon.messaging.connectors.jms",
            FeatureDescriptor.builder()
                    .name("JMS Connector")
                    .description("Reactive messaging connector for JMS")
                    .path("Messaging", "JMS")
                    .experimental(true)
                    .nativeSupported(false));
        add("io.helidon.messaging.connectors.aq",
            FeatureDescriptor.builder()
                    .name("Oracle AQ Connector")
                    .description("Reactive messaging connector for Oracle AQ")
                    .path("Messaging", "OracleAQ")
                    .experimental(true)
                    .nativeSupported(false));
        add("io.helidon.security.abac.policy.el",
            FeatureDescriptor.builder()
                    .name("EL")
                    .description("ABAC Jakarta Expression Language policy support")
                    .path("Security", "Provider", "ABAC", "Policy", "EL")
                    .nativeSupported(true)
                    .nativeDescription("Properties used in expressions must have reflection configuration added"));
        add("io.helidon.security.abac.role",
            "Role",
            "ABAC Role based attribute validator",
            "Security", "Provider", "ABAC", "Role");
        add("io.helidon.security.abac.scope",
            "Scope",
            "ABAC Scope based attribute validator",
            "Security", "Provider", "ABAC", "Scope");
        add("io.helidon.security.abac.time",
            "Time",
            "ABAC Time based attribute validator",
            "Security", "Provider", "ABAC", "Time");
        add("io.helidon.security.integration.grpc",
            "gRPC",
            "Security integration with gRPC",
            "Security", "Integration", "gRPC");
        add("io.helidon.security.integration.jersey",
            "Jersey",
            "Security integration with Jersey (JAX-RS implementation)",
            "Security", "Integration", "Jersey");
        add("io.helidon.security.integration.webserver",
            "WebServer",
            "Security integration with web server",
            "Security", "Integration", "WebServer");
        add("io.helidon.security.providers.abac",
            "ABAC",
            "Security provider for attribute based access control",
            "Security", "Provider", "ABAC");
        add("io.helidon.security.providers.google.login",
            FeatureDescriptor.builder()
                    .name("Google Login")
                    .description("Security provider for Google login button authentication and outbound")
                    .path("Security", "Provider", "Google-Login")
                    .nativeSupported(false));
        add("io.helidon.security.providers.header",
            "Header",
            "Security provider for header based authentication",
            "Security", "Provider", "Header");
        add("io.helidon.security.providers.httpauth",
            "HTTP Basic",
            "Security provider for HTTP Basic authentication and outbound",
            "Security", "Provider", "HttpBasic");
        add("io.helidon.security.providers.httpauth",
            "HTTP Digest",
            "Security provider for HTTP Digest authentication",
            "Security", "Provider", "HttpDigest");
        add("io.helidon.security.providers.httpsign",
            "HTTP Signatures",
            "Security provider for HTTP Signature authentication and outbound",
            "Security", "Provider", "HttpSign");
        add("io.helidon.security.providers.config.vault",
            "Config Vault",
            "Security", "Provider", "ConfigVault");
        add("io.helidon.security.providers.idcs.mapper",
            FeatureDescriptor.builder()
                    .name("IDCS Role Mapper")
                    .description("Security provider role mapping - Oracle IDCS")
                    .path("Security", "Provider", "IdcsRoleMapper")
                    .nativeSupported(false));
        add("io.helidon.security.providers.jwt",
            "JWT",
            "Security provider for JWT based authentication",
            "Security", "Provider", "JWT");
        add("io.helidon.security.providers.oidc",
            "OIDC",
            "Security provider for Open ID Connect authentication",
            "Security", "OIDC");
        add("io.helidon.tracing.jaeger",
            "Jaeger",
            "Jaeger tracer integration",
            "Tracing", "Jaeger");
        add("io.helidon.metrics.jaeger",
            "Jaeger metrics",
            "Jaeger tracer metrics integration",
            "Metrics", "Jaeger");
        add("io.helidon.tracing.jersey",
            "Jersey Server",
            "Tracing integration with Jersey server",
            "Tracing", "Integration", "Jersey");
        add("io.helidon.tracing.jersey.client",
            "Jersey Client",
            "Tracing integration with Jersey client",
            "Tracing", "Integration", "JerseyClient");
        add("io.helidon.tracing.zipkin",
            "Zipkin",
            "Zipkin tracer integration",
            "Tracing", "Zipkin");
        add("io.helidon.integrations.neo4j",
            FeatureDescriptor.builder()
                    .name("Neo4j integration")
                    .description("Integration with Neo4j driver")
                    .path("Neo4j")
                    .experimental(true)
                    .nativeSupported(true));
        add("io.helidon.integrations.neo4j.health",
            FeatureDescriptor.builder()
                    .name("Neo4j Health")
                    .description("Health check for Neo4j integration")
                    .path("Neo4j", "Health"));
        add("io.helidon.integrations.neo4j.metrics",
            FeatureDescriptor.builder()
                    .name("Neo4j Metrics")
                    .description("Metrics for Neo4j integration")
                    .path("Neo4j", "Metrics"));
        add("io.helidon.reactive.webclient",
            FeatureDescriptor.builder()
                    .name("Web Client")
                    .description("Reactive web client")
                    .path("WebClient")
                    .experimental(true));
        add("io.helidon.reactive.webclient.metrics",
            "Metrics",
            "Reactive web client support for metrics",
            "WebClient", "Metrics");
        add("io.helidon.reactive.webclient.security",
            "Security",
            "Reactive web client support for security",
            "WebClient", "Security");
        add("io.helidon.reactive.webclient.tracing",
            "Tracing",
            "Reactive web client support for tracing",
            "WebClient", "Tracing");
        add("io.helidon.logging.log4j",
            FeatureDescriptor.builder()
                    .name("Log4j")
                    .path("Logging", "Log4j")
                    .description("Log4j MDC support")
                    .nativeDescription("Only programmatic configuration supported, does not work with Helidon loggers"));
        add("io.helidon.webserver.staticcontent",
            "Static Content",
            "Static content support for webserver",
            "WebServer", "Static Content");
        add("io.helidon.integrations.oci.objectstorage",
            "OCI Object Storage",
            "Integration with OCI Object Storage",
            "OCI", "Object Storage");
        add("io.helidon.integrations.oci.vault",
            "OCI Vault",
            "Integration with OCI Vault",
            "OCI", "Vault");
        add("io.helidon.integrations.oci.telemetry",
            "OCI Telemetry",
            "Integration with OCI Telemetry",
            "OCI", "Telemetry");
        add("io.helidon.integrations.vault.auths.approle",
            "AppRole",
            "AppRole Authentication Method",
            "HCP Vault", "Auth", "AppRole");
        add("io.helidon.integrations.vault.auths.k8s",
            "k8s",
            "Kubernetes Authentication Method",
            "HCP Vault", "Auth", "k8s");
        add("io.helidon.integrations.vault.auths.token",
            "Token",
            "Token Authentication Method",
            "HCP Vault", "Auth", "Token");
        add("io.helidon.integrations.vault.secrets.cubbyhole",
            "Cubbyhole",
            "Cubbyhole Secrets Engine",
            "HCP Vault", "Secrets", "Cubbyhole");
        add("io.helidon.integrations.vault.secrets.database",
            "Database",
            "Database Secrets Engine",
            "HCP Vault", "Secrets", "Database");
        add("io.helidon.integrations.vault.secrets.kv1",
            "K/V 1",
            "Key/Value Version 1 Secrets Engine",
            "HCP Vault", "Secrets", "K/V 1");
        add("io.helidon.integrations.vault.secrets.kv2",
            "K/V 2",
            "Key/Value Version 2 Secrets Engine",
            "HCP Vault", "Secrets", "K/V 2");
        add("io.helidon.integrations.vault.secrets.pki",
            "PKI",
            "PKI Secrets Engine",
            "HCP Vault", "Secrets", "PKI");
        add("io.helidon.integrations.vault.secrets.transit",
            "Transit",
            "Transit Secrets Engine",
            "HCP Vault", "Secrets", "Transit");
        add("io.helidon.integrations.vault.sys",
            "Sys",
            "System operations",
            "HCP Vault", "Sys");
```