/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * When updating this class, please keep the grouping (SE/MP/both/excludes) and keep
 * alphabetical order of packages.
 */
final class FeatureCatalog {
    // package name to list of features
    private static final Map<String, Set<FeatureDescriptor>> FEATURES = new HashMap<>();
    private static final Set<String> EXCLUDED = new HashSet<>();

    static {
        /*
         * SE modules
         */
        addSe("io.helidon.config",
              "Config",
              "Configuration module",
              "Config");
        add("io.helidon.grpc.server",
            FeatureDescriptor.builder()
                    .name("gRPC Server")
                    .description("Server for gRPC services")
                    .path("grpc")
                    .flavor(HelidonFlavor.SE)
                    .nativeSupported(false));
        add("io.helidon.grpc.client",
            FeatureDescriptor.builder()
                    .name("gRPC Client")
                    .description("Client for gRPC services")
                    .path("grpcClient")
                    .flavor(HelidonFlavor.SE)
                    .nativeSupported(false));
        addSe("io.helidon.grpc.metrics",
              "Metrics",
              "Metrics for gRPC services",
              "grpc", "Metrics");
        addSe("io.helidon.grpc.metrics",
              "Metrics",
              "Metrics for gRPC client",
              "grpcClient", "Metrics");
        addSe("io.helidon.health",
              "Health",
              "Health checks support",
              "Health");
        addSe("io.helidon.media.jsonp",
              "JSON-P",
              "Media support for Jakarta JSON Processing",
              "WebServer", "Jsonp");
        addSe("io.helidon.media.jsonp",
              "JSON-P",
              "Media support for Jakarta JSON Processing",
              "WebClient", "Jsonp");
        addSe("io.helidon.media.jsonb",
              "JSON-B",
              "Media support for Jakarta JSON Binding",
              "WebServer", "Jsonb");
        addSe("io.helidon.media.jsonb",
              "JSON-B",
              "Media support for Jakarta JSON Binding",
              "WebClient", "Jsonb");
        addSe("io.helidon.media.jackson",
              "Jackson",
              "Media support for Jackson",
              "WebServer", "Jackson");
        addSe("io.helidon.media.jackson",
              "Jackson",
              "Media support for Jackson",
              "WebClient", "Jackson");
        addSe("io.helidon.media.multipart",
              "Multi-part",
              "Media support for Multi-part entities",
              "WebServer", "Multipart");
        addSe("io.helidon.media.multipart",
              "Multi-part",
              "Media support for Multi-part entities",
              "WebClient", "Multipart");
        add("io.helidon.messaging",
            FeatureDescriptor.builder()
                    .name("Messaging")
                    .description("Reactive messaging support")
                    .path("Messaging")
                    .flavor(HelidonFlavor.SE)
                    .experimental(true));
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
        addSe("io.helidon.security",
              "Security",
              "Security support",
              "Security");
        addSe("io.helidon.tracing",
              "Tracing",
              "Tracing support",
              "Tracing");
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
        add("io.helidon.webserver.tyrus",
            FeatureDescriptor.builder()
                    .flavor(HelidonFlavor.SE)
                    .name("Websocket")
                    .description("Jakarta Websocket implementation")
                    .path("WebServer", "Websocket")
                    .nativeSupported(true)
                    .nativeDescription("Server only"));

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
        add("io.helidon.dbclient",
            FeatureDescriptor.builder()
                    .name("Db Client")
                    .description("Reactive database client")
                    .path("DbClient")
                    .experimental(true));
        add("io.helidon.dbclient.health",
            "Health Check",
            "Reactive database client health check support",
            "DbClient", "Health");
        add("io.helidon.dbclient.jsonp",
            "JSON-P",
            "JSON Processing mapping DbRow",
            "DbClient", "JSON-P");
        add("io.helidon.dbclient.jdbc",
            FeatureDescriptor.builder()
                    .name("JDBC")
                    .description("Reactive database client over JDBC")
                    .path("DbClient", "JDBC")
                    .nativeDescription("Tested with Helidon Oracle and H2 drivers (see examples)"));
        add("io.helidon.dbclient.metrics",
            "Metrics",
            "Reactive database client metrics support",
            "DbClient", "Metrics");
        add("io.helidon.dbclient.mongodb",
            "mongo",
            "Reactive database client with reactive mongo driver",
            "DbClient", "mongo");
        add("io.helidon.dbclient.tracing",
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
                    .nativeSupported(false));
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
                    .nativeSupported(false));
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
        add("io.helidon.webclient",
            FeatureDescriptor.builder()
                    .name("Web Client")
                    .description("Reactive web client")
                    .path("WebClient")
                    .experimental(true));
        add("io.helidon.webclient.metrics",
            "Metrics",
            "Reactive web client support for metrics",
            "WebClient", "Metrics");
        add("io.helidon.webclient.security",
            "Security",
            "Reactive web client support for security",
            "WebClient", "Security");
        add("io.helidon.webclient.tracing",
            "Tracing",
            "Reactive web client support for tracing",
            "WebClient", "Tracing");
        add("io.helidon.logging.log4j",
            FeatureDescriptor.builder()
                    .name("Log4j")
                    .path("Logging", "Log4j")
                    .description("Log4j MDC support")
                    .nativeDescription("Only programmatic configuration supported, does not work with Helidon loggers"));

        /*
         * Packages that are not a feature
         */
        exclude("io.helidon.bundles.config");
        exclude("io.helidon.common");
        exclude("io.helidon.common.configurable");
        exclude("io.helidon.common.context");
        exclude("io.helidon.common.features");
        exclude("io.helidon.common.http");
        exclude("io.helidon.common.mapper");
        exclude("io.helidon.common.mapper.spi");
        exclude("io.helidon.common.media.type");
        exclude("io.helidon.common.media.type.spi");
        exclude("io.helidon.common.pki");
        exclude("io.helidon.common.reactive");
        exclude("io.helidon.common.serviceloader");
        exclude("io.helidon.config.spi");
        exclude("io.helidon.config.mp");
        exclude("io.helidon.config.mp.spi");
        exclude("io.helidon.dbclient.common");
        exclude("io.helidon.dbclient.jdbc.spi");
        exclude("io.helidon.dbclient.metrics.jdbc");
        exclude("io.helidon.dbclient.spi");
        exclude("io.helidon.health.common");
        exclude("io.helidon.integrations.cdi.delegates");
        exclude("io.helidon.integrations.cdi.referencecountedcontext");
        exclude("io.helidon.integrations.cdi.jpa.jaxb");
        exclude("io.helidon.integrations.datasource.cdi");
        exclude("io.helidon.integrations.datasource.hikaricp.cdi");
        exclude("io.helidon.integrations.db.h2");
        exclude("io.helidon.integrations.graal.nativeimage.extension");
        exclude("io.helidon.integrations.graal.mp.nativeimage.extension");
        exclude("io.helidon.integrations.jta.weld");
        exclude("io.helidon.jersey.common");
        exclude("io.helidon.logging.common");
        exclude("io.helidon.logging.jul");
        exclude("io.helidon.media.common");
        exclude("io.helidon.media.common.spi");
        exclude("io.helidon.openapi.internal");
        exclude("io.helidon.security.abac.policy");
        exclude("io.helidon.security.abac.policy.spi");
        exclude("io.helidon.security.annotations");
        exclude("io.helidon.security.integration.common");
        exclude("io.helidon.security.integration.jersey.client");
        exclude("io.helidon.security.internal");
        exclude("io.helidon.security.jwt");
        exclude("io.helidon.security.jwt.jwk");
        exclude("io.helidon.security.providers.abac.spi");
        exclude("io.helidon.security.providers.common");
        exclude("io.helidon.security.providers.common.spi");
        exclude("io.helidon.security.providers.httpauth.spi");
        exclude("io.helidon.security.providers.oidc.common");
        exclude("io.helidon.security.spi");
        exclude("io.helidon.security.util");
        exclude("io.helidon.tracing.config");
        exclude("io.helidon.tracing.jersey.client.internal");
        exclude("io.helidon.tracing.spi");
        exclude("io.helidon.tracing.tracerresolver");
        exclude("io.helidon.webclient.jaxrs");
        exclude("io.helidon.webclient.spi");
    }

    static Set<FeatureDescriptor> get(String packageName) {
        Set<FeatureDescriptor> features = FEATURES.get(packageName);
        if (features == null) {
            if (packageName.startsWith("io.helidon.")) {
                // now let's see if excluded
                if (EXCLUDED.contains(packageName)) {
                    return Set.of();
                }
                // now let's see if a test or an example (we consider these not to be features as well
                if (packageName.contains(".examples.")
                        || packageName.contains(".tests.")
                        || packageName.endsWith(".tests")
                        || packageName.endsWith(".example")) {
                    return Set.of();
                }
            }

            // not a feature
            return null;
        }
        return features;
    }

    // hide utility class constructor
    private FeatureCatalog() {
    }

    private static void exclude(String packageName) {
        EXCLUDED.add(packageName);
    }

    private static void add(String packageName,
                            String name,
                            String description,
                            String... path) {
        add(packageName, FeatureDescriptor.builder()
                .name(name)
                .path(path)
                .description(description));
    }

    private static void addSe(String packageName,
                              String name,
                              String description,
                              String... path) {
        add(packageName, FeatureDescriptor.builder()
                .name(name)
                .path(path)
                .description(description)
                .flavor(HelidonFlavor.SE));
    }

    private static void addMp(String packageName,
                              String name,
                              String description,
                              String... path) {
        add(packageName, FeatureDescriptor.builder()
                .name(name)
                .path(path)
                .description(description)
                .flavor(HelidonFlavor.MP));
    }

    private static void add(String packageName, Supplier<FeatureDescriptor> descriptorBuilder) {
        FeatureDescriptor descriptor = descriptorBuilder.get();
        Set<FeatureDescriptor> featureDescriptors = ensurePackage(packageName);
        if (!featureDescriptors.add(descriptor)) {
            throw new IllegalStateException("Feature "
                                                    + descriptor.name()
                                                    + " on path "
                                                    + descriptor.stringPath()
                                                    + " is registered more than once in package "
                                                    + packageName);
        }
    }

    private static Set<FeatureDescriptor> ensurePackage(String packageName) {
        return FEATURES.computeIfAbsent(packageName, it -> new HashSet<>());
    }

    // this section can be used to print native image support for all features. Commented out not to pollute production code
    /*
    public static void main(String[] args) {
        List<FeatureDescriptor> allFeatures = FEATURES.values()
                .stream()
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(FeatureDescriptor::stringPath))
                .collect(Collectors.toList());

        print(HelidonFlavor.SE, allFeatures);
//        print(HelidonFlavor.MP, allFeatures);
    }

    private static void print(HelidonFlavor flavor, List<FeatureDescriptor> allFeatures) {
        String last = null;
        for (FeatureDescriptor it : allFeatures) {
            if (it.hasFlavor(flavor)) {
                System.out.println("|" + supported(it)
                                           + " |" + feature(last, it)
                                           + " |" + component(it)
                                           + " |" + description(it));
                last = root(it);
            }
        }

    }

    private static String component(FeatureDescriptor it) {
        List<String> components = new ArrayList<>(Arrays.asList(it.path()));

        if (components.size() <= 2) {
            return it.name();
        }
        // remove first (root component is listed already as feature)
        components.remove(0);
        // remove last (me)
        components.remove(components.size() - 1);

        // The rest is prefix
        return String.join("/", components) + ": " + it.name();
    }

    private static String feature(String last, FeatureDescriptor it) {
        String root = root(it);
        if (root.equals(last)) {
            return "{nbsp}";
        }
        return it.name();
    }

    private static String root(FeatureDescriptor it) {
        return it.path()[0];
    }

    private static String description(FeatureDescriptor it) {
        if (it.nativeDescription().isBlank()) {
            if (it.nativeSupported()) {
                return "{nbsp}";
            }
            return "Not yet tested.";
        }
        return it.nativeDescription();
    }

    private static String supported(FeatureDescriptor it) {
        if (it.nativeSupported()) {
            if (it.nativeDescription().isBlank()) {
                return "✅";
            }
            return "\uD83D\uDD36";
        } else {
            return "❓";
        }
    }
     */
}
