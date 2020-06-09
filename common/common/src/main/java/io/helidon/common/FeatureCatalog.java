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
        addSe("io.helidon.grpc.server",
              "gRPC Server",
              "Server for gRPC services",
              "grpc");
        addSe("io.helidon.grpc.client",
              "gRPC Client",
              "Client for gRPC services",
              "grpcClient");
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
        addSe("io.helidon.messaging",
              "Messaging",
              "Reactive messaging support",
              "Messaging");
        addSe("io.helidon.metrics",
              "Metrics",
              "Metrics support",
              "Metrics");
        addSe("io.helidon.metrics.prometheus",
              "Prometheus",
              "Metrics support for Prometheus",
              "WebServer", "Prometheus");
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
        addSe("io.helidon.webclient",
              "WebClient",
              "Helidon WebClient",
              "WebClient");
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

        /*
         * MP Modules
         */
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
        addMp("io.helidon.microprofile.health",
              "Health",
              "MicroProfile Health spec implementation",
              "Health");
        addMp("io.helidon.microprofile.jwt.auth",
              "JWT Auth",
              "MicroProfile JWT Auth spec implementation",
              "Security", "JWTAuth");
        addMp("io.helidon.microprofile.messaging",
              "Messaging",
              "MicroProfile Reactive Messaging spec implementation",
              "Messaging");
        addMp("io.helidon.microprofile.metrics",
              "Metrics",
              "MicroProfile metrics spec implementation",
              "Metrics");
        addMp("io.helidon.microprofile.openapi",
              "Open API",
              "MicroProfile Open API spec implementation",
              "OpenAPI");
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
        addMp("io.helidon.microprofile.tyrus",
              "Websocket",
              "Jakarta Websocket implementation",
              "Websocket");

        // TODO we do not see this package
        add("io.helidon.microprofile.restclient",
            FeatureDescriptor.builder()
                    .name("REST Client")
                    .description("MicroProfile REST client spec implementation")
                    .path("RESTClient")
                    .flavor(HelidonFlavor.MP)
                    .nativeDescription("Does not support execution of default methods on interfaces.")
                    .build());

        /*
         * Common modules
         */
        add("io.helidon.config.encryption",
            "Encryption",
            "Support for secret encryption in config",
            "Config", "Encryption");
        add("io.helidon.config.etcd",
            "etcd",
            "Config source based on etcd",
            "Config", "etcd");
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
            "Db Client",
            "Reactive database client",
            "DbClient");
        add("io.helidon.dbclient.health",
            "Health Check",
            "Reactive database client health check support",
            "DbClient", "Health");
        add("io.helidon.dbclient.jdbc",
            "JDBC",
            "Reactive database client over JDBC",
            "DbClient", "JDBC");
        add("io.helidon.dbclient.metrics",
            "Metrics",
            "Reactive database client metrics support",
            "DbClient", "Metrics");
        add("io.helidon.dbclient.mongo",
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
        add("io.helidon.security.abac.policy.el",
            "EL",
            "ABAC Jakarta Expression Language policy support",
            "Security", "Provider", "ABAC", "Policy", "EL");
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
            "Google Login",
            "Security provider for Google login button authentication and outbound",
            "Security", "Provider", "Google-Login");
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
            "IDCS Role Mapper",
            "Security provider role mapping - Oracle IDCS",
            "Security", "Provider", "IdcsRoleMapper");
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
            "Web Client",
            "Reactive web client",
            "WebClient");
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

        /*
         * Packages that are not a feature
         */
        exclude("io.helidon.common");
        exclude("io.helidon.common.configurable");
        exclude("io.helidon.common.context");
        exclude("io.helidon.common.features");
        exclude("io.helidon.common.http");
        exclude("io.helidon.common.mapper");
        exclude("io.helidon.common.media.type");
        exclude("io.helidon.common.media.type.spi");
        exclude("io.helidon.common.pki");
        exclude("io.helidon.common.reactive");
        exclude("io.helidon.common.serviceloader");
        exclude("io.helidon.config.spi");
        exclude("io.helidon.config.mp");
        exclude("io.helidon.config.mp.spi");
        exclude("io.helidon.health.common");
        exclude("io.helidon.jersey.common");
        exclude("io.helidon.media.common");
        exclude("io.helidon.media.common.spi");
        exclude("io.helidon.security.abac.policy");
        exclude("io.helidon.security.abac.policy.spi");
        exclude("io.helidon.security.annotations");
        exclude("io.helidon.security.integration.common");
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
                if (packageName.contains(".examples.") || packageName.contains(".tests.")) {
                    return Set.of();
                }
            }

            // not a feature
            return null;
        }
        return features;
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
                .description(description)
                .build());
    }

    private static void addSe(String packageName,
                              String name,
                              String description,
                              String... path) {
        add(packageName, FeatureDescriptor.builder()
                .name(name)
                .path(path)
                .description(description)
                .flavor(HelidonFlavor.SE)
                .build());
    }

    private static void addMp(String packageName,
                              String name,
                              String description,
                              String... path) {
        add(packageName, FeatureDescriptor.builder()
                .name(name)
                .path(path)
                .description(description)
                .flavor(HelidonFlavor.MP)
                .build());
    }

    private static void add(String packageName, FeatureDescriptor descriptor) {
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
}

