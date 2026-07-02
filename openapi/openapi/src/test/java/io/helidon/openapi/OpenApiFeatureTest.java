/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import io.helidon.common.Builder;
import io.helidon.common.LazyValue;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.openapi.spi.OpenApiDocumentSource;
import io.helidon.openapi.spi.OpenApiManagerProvider;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.spi.OpenApiVersionProvider;
import io.helidon.openapi.v30.OpenApi30Version;
import io.helidon.openapi.v30.OpenApi30VersionConfig;
import io.helidon.openapi.v30.OpenApi30VersionProvider;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import static io.helidon.common.testing.junit5.MapMatcher.mapEqualTo;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link io.helidon.openapi.OpenApiFeature}.
 */
@RoutingTest
@SuppressWarnings("HttpUrlsUsage")
class OpenApiFeatureTest {

    private final WebClient client;

    OpenApiFeatureTest(WebClient client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.addFeature(OpenApiFeature.builder()
                                  .servicesDiscoverServices(false)
                                  .staticFile("src/test/resources/greeting.yml")
                                  .webContext("/openapi-greeting")
                                  .build())
                .addFeature(OpenApiFeature.builder()
                                    .servicesDiscoverServices(false)
                                    .staticFile("src/test/resources/time-server.yml")
                                    .webContext("/openapi-time")
                                    .name("openapi-time")
                                    .build())
                .addFeature(OpenApiFeature.builder()
                                    .servicesDiscoverServices(false)
                                    .staticFile("src/test/resources/petstore.yaml")
                                    .webContext("/openapi-petstore")
                                    .name("openapi-petstore")
                                    .build())
                .addFeature(OpenApiFeature.builder()
                                    .servicesDiscoverServices(false)
                                    .staticFile("exact-static.yaml")
                                    .webContext("/openapi-exact-static")
                                    .name("openapi-exact-static")
                                    .build());

    }

    @SetUpRoute
    static void setup(HttpRouting.Builder routing) {
    }

    @Test
    void testGreetingAsYAML() {
        ClientResponseTyped<String> response = client.get("/openapi-greeting")
                .accept(MediaTypes.APPLICATION_OPENAPI_YAML)
                .request(String.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(parse(response.entity()), mapEqualTo(parse(resource("/greeting.yml"))));
    }

    @Test
    void testExactStaticWhenFormatAndVersionMatch() {
        ClientResponseTyped<String> response = client.get("/openapi-exact-static")
                .accept(MediaTypes.APPLICATION_OPENAPI_YAML)
                .request(String.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(resource("/exact-static.yaml")));
    }

    static Stream<MediaType> checkExplicitResponseMediaTypeViaHeaders() {
        return Stream.of(MediaTypes.APPLICATION_OPENAPI_YAML,
                         MediaTypes.APPLICATION_YAML,
                         MediaTypes.APPLICATION_OPENAPI_JSON,
                         MediaTypes.APPLICATION_JSON);
    }

    @ParameterizedTest
    @MethodSource()
    void checkExplicitResponseMediaTypeViaHeaders(MediaType testMediaType) {
        ClientResponseTyped<String> response = client.get("/openapi-petstore")
                .accept(testMediaType)
                .request(String.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat("Response headers",
                   response.headers(),
                   HttpHeaderMatcher.hasHeader(HeaderNames.X_CONTENT_TYPE_OPTIONS, "nosniff"));
        HttpMediaType contentType = response.headers().contentType().orElseThrow();

        if (contentType.test(MediaTypes.APPLICATION_OPENAPI_YAML)
                || contentType.test(MediaTypes.APPLICATION_YAML)) {

            assertThat(parse(response.entity()), mapEqualTo(parse(resource("/petstore.yaml"))));
        } else if (contentType.test(MediaTypes.APPLICATION_OPENAPI_JSON)
                || contentType.test(MediaTypes.APPLICATION_JSON)) {

            // parsing normalizes the entity, so we can compare the entity to the original YAML
            assertThat(parse(response.entity()), mapEqualTo(parse(resource("/petstore.yaml"))));
        } else {
            throw new AssertionError("Expected either JSON or YAML response but received " + contentType);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"JSON", "YAML"})
    void checkExplicitResponseMediaTypeViaQueryParam(String format) {
        ClientResponseTyped<String> response = client.get("/openapi-petstore")
                .queryParam("format", format)
                .accept(MediaTypes.APPLICATION_JSON)
                .request(String.class);
        assertThat(response.status(), is(Status.OK_200));

        switch (format) {
            // parsing normalizes the entity, so we can compare the entity to the original YAML
            case "YAML", "JSON" -> assertThat(parse(response.entity()), mapEqualTo(parse(resource("/petstore.yaml"))));
            default -> throw new AssertionError("Format not supported: " + format);
        }
    }

    @Test
    void testUnrestrictedCorsAsIs() {
        ClientResponseTyped<String> response = client.get("/openapi-time")
                .accept(MediaTypes.APPLICATION_OPENAPI_YAML)
                .request(String.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(parse(response.entity()), mapEqualTo(parse(resource("/time-server.yml"))));
    }

    @Test
    void testUnrestrictedCorsWithHeaders() {
        ClientResponseTyped<String> response = client.get("/openapi-time")
                .accept(MediaTypes.APPLICATION_OPENAPI_YAML)
                .header(HeaderNames.ORIGIN, "http://foo.bar")
                .header(HeaderNames.HOST, "localhost")
                .request(String.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(parse(response.entity()), mapEqualTo(parse(resource("/time-server.yml"))));
    }

    @Test
    void serviceConstructorUsesServerFeatureConfig() {
        Config config = Config.just(ConfigSources.create(Map.of("server.features.openapi.web-context", "/from-server-feature",
                                                               "openapi.web-context", "/from-top-level")));
        OpenApiFeature feature = new OpenApiFeature(config, List::of);

        assertThat(feature.prototype().webContext(), is("/from-server-feature"));
        assertThat(feature.prototype().isEnabled(), is(true));
    }

    @Test
    void serviceConstructorLetsProviderHandleNamedOpenApiConfig() {
        Config config = Config.just(ConfigSources.create(Map.of("server.features.admin-openapi.type", "openapi",
                                                               "server.features.admin-openapi.web-context", "/admin-openapi",
                                                               "openapi.web-context", "/from-top-level")));
        OpenApiFeature feature = new OpenApiFeature(config, List::of);

        assertThat(feature.prototype().isEnabled(), is(false));
        assertThat(feature.prototype().name(), is("openapi-service-registry"));
    }

    @Test
    void serviceConstructorLetsProviderHandleListOpenApiConfig() {
        Config config = Config.just(ConfigSources.create("""
                server:
                  features:
                    - type: openapi
                      web-context: /admin-openapi
                openapi:
                  web-context: /from-top-level
                """, MediaTypes.APPLICATION_YAML));
        OpenApiFeature feature = new OpenApiFeature(config, List::of);

        assertThat(feature.prototype().isEnabled(), is(false));
        assertThat(feature.prototype().name(), is("openapi-service-registry"));
    }

    @Test
    void disabledFeatureDoesNotLoadStaticContentOrInitializeSources(@TempDir Path tempDir) throws IOException {
        Path staticDirectory = tempDir.resolve("openapi-dir");
        Files.createDirectory(staticDirectory);
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .isEnabled(false)
                .staticFile(staticDirectory.toString())
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config,
                                                    () -> {
                                                        throw new AssertionError("Disabled OpenAPI feature must not"
                                                                                         + " discover document sources.");
                                                    },
                                                    () -> {
                                                        throw new AssertionError("Disabled OpenAPI feature must not"
                                                                                         + " discover version providers.");
                                                    });

        feature.setup(new TestFeatureContext("admin"));
        feature.initialize();

        assertThat(feature.prototype().isEnabled(), is(false));
        assertThat(feature.contentOpenApiVersionLoaded(), is(false));
    }

    @Test
    void disabledServiceRegistryStandbyDoesNotDiscoverProviders() {
        Config config = Config.just(ConfigSources.create(Map.of("server.features.admin-openapi.type", "openapi")));
        ServiceRegistryManager registryManager = failingVersionProviderRegistry();
        try {
            OpenApiFeature feature = new OpenApiFeature(registryManager.registry(),
                                                        config,
                                                        () -> {
                                                            throw new AssertionError("Disabled OpenAPI feature must not"
                                                                                             + " discover version providers.");
                                                        });

            feature.setup(new TestFeatureContext("admin"));
            feature.initialize();

            assertThat(feature.prototype().isEnabled(), is(false));
            assertThat(feature.prototype().name(), is("openapi-service-registry"));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void disabledCanonicalFeatureDoesNotDiscoverProviders() {
        Config config = Config.just(ConfigSources.create(Map.of("server.features.openapi.enabled", "false")));
        ServiceRegistryManager registryManager = failingVersionProviderRegistry();
        try {
            OpenApiFeature feature = new OpenApiFeature(registryManager.registry(),
                                                        config,
                                                        () -> {
                                                            throw new AssertionError("Disabled OpenAPI feature must not"
                                                                                             + " discover version providers.");
                                                        });

            feature.setup(new TestFeatureContext("admin"));
            feature.initialize();

            assertThat(feature.prototype().isEnabled(), is(false));
            assertThat(feature.prototype().openApiVersion().isEmpty(), is(true));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void disabledProviderCreatedFeatureDoesNotDiscoverProviders(@TempDir Path tempDir) throws IOException {
        Path staticDirectory = tempDir.resolve("openapi-dir");
        Files.createDirectory(staticDirectory);
        Config config = Config.just(ConfigSources.create(Map.of("enabled", "false",
                                                               "static-file", staticDirectory.toString())));
        OpenApiFeature feature = new OpenApiFeatureProvider().create(config, "openapi");

        feature.setup(new TestFeatureContext("admin"));
        feature.initialize();

        assertThat(feature.prototype().isEnabled(), is(false));
        assertThat(feature.prototype().openApiVersion().isEmpty(), is(true));
    }

    @Test
    void disabledCreateFromConfigDoesNotDiscoverProviders(@TempDir Path tempDir) throws IOException {
        Path staticDirectory = tempDir.resolve("openapi-dir");
        Files.createDirectory(staticDirectory);
        Config config = Config.just(ConfigSources.create(Map.of("openapi.enabled", "false",
                                                               "openapi.static-file", staticDirectory.toString())));
        OpenApiFeature feature = OpenApiFeature.create(config.get("openapi"));

        feature.setup(new TestFeatureContext("admin"));
        feature.initialize();

        assertThat(feature.prototype().isEnabled(), is(false));
        assertThat(feature.prototype().openApiVersion().isEmpty(), is(true));
    }

    @Test
    void disabledCreateFromConsumerDoesNotDiscoverProviders(@TempDir Path tempDir) throws IOException {
        Path staticDirectory = tempDir.resolve("openapi-dir");
        Files.createDirectory(staticDirectory);
        OpenApiFeature feature = OpenApiFeature.create(builder -> builder.isEnabled(false)
                .staticFile(staticDirectory.toString()));

        feature.setup(new TestFeatureContext("admin"));
        feature.initialize();

        assertThat(feature.prototype().isEnabled(), is(false));
        assertThat(feature.prototype().openApiVersion().isEmpty(), is(true));
    }

    @Test
    void disabledBuilderDoesNotDiscoverProviders(@TempDir Path tempDir) throws IOException {
        Path staticDirectory = tempDir.resolve("openapi-dir");
        Files.createDirectory(staticDirectory);
        OpenApiFeature feature = OpenApiFeature.builder()
                .isEnabled(false)
                .staticFile(staticDirectory.toString())
                .build();

        feature.setup(new TestFeatureContext("admin"));
        feature.initialize();

        assertThat(feature.prototype().isEnabled(), is(false));
        assertThat(feature.prototype().openApiVersion().isEmpty(), is(true));
    }

    @Test
    void serviceConstructorDiscoversOpenApiVersionFromRegistry() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        try {
            OpenApiFeature feature = new OpenApiFeature(manager.registry(), Config.empty(), List::of);

            assertThat(feature.prototype().openApiVersion().orElseThrow().type(), is("3.0"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void injectedConstructorUsesConfiguredGeneratedDocumentSourceSelection() {
        RecordingOpenApiManager openApiManager = new RecordingOpenApiManager();
        Config config = Config.just(ConfigSources.create(Map.of(
                "server.features.openapi.generated.mode", "GENERATED_ONLY",
                "server.features.openapi.generated.document-sources.0", SelectedOpenApi.class.getCanonicalName(),
                "server.features.openapi.manager.test.enabled", "true")));
        ServiceRegistryManager registryManager = documentSourceRegistry(openApiManager);
        try {
            OpenApiFeature feature = new OpenApiFeature(registryManager.registry(), config, List::of);

            feature.initialize();

            Map<String, Object> document = parse(openApiManager.content());
            assertThat(map(document, "info").get("title"), is("Selected API"));
            assertThat(map(document, "paths").containsKey("/generated"), is(true));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void missingConfiguredGeneratedDocumentSourceFailsWithoutFallback() {
        RecordingOpenApiManager openApiManager = new RecordingOpenApiManager();
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .generatedDocumentSources(List.of("missing.Source"))
                .openApiVersion(OpenApi30Version.create())
                .manager(openApiManager)
                .buildPrototype();
        ServiceRegistryManager registryManager = documentSourceRegistry();
        try {
            OpenApiFeature feature = new OpenApiFeature(registryManager.registry(), config);

            IllegalStateException ex = assertThrows(IllegalStateException.class, feature::initialize);
            assertThat(ex.getMessage(), containsString("Configured OpenAPI document source missing.Source was not found"));
            assertThat(openApiManager.contents(), is(List.of()));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void mergeStaticDocumentUsesParserForDeclaredVersion(@TempDir Path tempDir) throws IOException {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        OpenApiVersion staticVersion = new TestOpenApiVersion("3.1", "3.1.0", false);
        Path staticFile = tempDir.resolve("static-3.1.yaml");
        Files.writeString(staticFile, OPENAPI_31_DOCUMENT);
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .staticFile(staticFile.toString())
                .generatedMode(OpenApiGeneratedMode.MERGE)
                .openApiVersion(renderVersion)
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config,
                                                    () -> List.of(generatedPathSource()),
                                                    () -> List.of(provider("3.1", staticVersion)));

        assertThat(feature.contentOpenApiVersionLoaded(), is(false));
        feature.initialize();

        assertThat(feature.contentOpenApiVersionLoaded(), is(true));
        assertThat(parse(manager.content()).get("openapi"), is("3.0.3"));
    }

    @Test
    void runtimeBuilderUsesSuppliedRegistryForStaticVersionParser(@TempDir Path tempDir) throws IOException {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        OpenApiVersion staticVersion = new TestOpenApiVersion("3.1", "3.1.0", false);
        Path staticFile = tempDir.resolve("static-3.1.yaml");
        Files.writeString(staticFile, OPENAPI_31_DOCUMENT);
        ServiceRegistryConfig registryConfig = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(testDescriptor(OpenApiVersionProvider.class,
                                                     "OpenApi31VersionProvider",
                                                     provider("3.1", staticVersion)))
                .build();
        ServiceRegistryManager registryManager = ServiceRegistryManager.create(registryConfig);
        try {
            OpenApiFeature feature = OpenApiFeature.builder()
                    .serviceRegistry(registryManager.registry())
                    .servicesDiscoverServices(false)
                    .staticFile(staticFile.toString())
                    .generatedMode(OpenApiGeneratedMode.MERGE)
                    .openApiVersion(renderVersion)
                    .manager(manager)
                    .build();

            feature.initialize();

            assertThat(parse(manager.content()).get("openapi"), is("3.0.3"));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void mergeYamlStaticDocumentUsesRootOpenApiVersion(@TempDir Path tempDir) throws IOException {
        mergeStaticDocumentUsesRootVersion(tempDir.resolve("nested-openapi.yaml"), """
                x-nested:
                  openapi: 9.9.9
                openapi: 3.1.0
                info:
                  title: Static API
                  version: 1.0.0
                """);
    }

    @Test
    void mergeYamlStaticDocumentUsesQuotedRootOpenApiVersion(@TempDir Path tempDir) throws IOException {
        mergeStaticDocumentUsesRootVersion(tempDir.resolve("quoted-openapi.yaml"), """
                x-nested:
                  openapi: 9.9.9
                "openapi": "3.1.0"
                info:
                  title: Static API
                  version: 1.0.0
                """);
    }

    @Test
    void mergeYamlStaticDocumentUsesFlowRootOpenApiVersion(@TempDir Path tempDir) throws IOException {
        mergeStaticDocumentUsesRootVersion(tempDir.resolve("flow-openapi.yaml"), """
                {x-nested: {openapi: 9.9.9}, openapi: 3.1.0, info: {title: Static API, version: 1.0.0}}
                """);
    }

    @Test
    void mergeJsonStaticDocumentUsesRootOpenApiVersion(@TempDir Path tempDir) throws IOException {
        mergeStaticDocumentUsesRootVersion(tempDir.resolve("nested-openapi.json"), """
                {"x-nested":{"openapi":"9.9.9"},"openapi":"3.1.0","info":{"title":"Static API","version":"1.0.0"}}
                """);
    }

    @Test
    void mergeJsonStaticDocumentUsesEscapedRootOpenApiVersion(@TempDir Path tempDir) throws IOException {
        mergeStaticDocumentUsesRootVersion(tempDir.resolve("escaped-openapi.json"), """
                {"x-nested":{"openapi":"9.9.9"},"\\u006fpenapi":"3\\u002e1\\u002e0","info":{"title":"Static API","version":"1.0.0"}}
                """);
    }

    @Test
    void mergeStaticDocumentCachesParsedStaticDocument(@TempDir Path tempDir) throws IOException {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        CountingOpenApiVersion staticVersion = new CountingOpenApiVersion("3.1", "3.1.0");
        Path staticFile = tempDir.resolve("static-3.1.yaml");
        Files.writeString(staticFile, OPENAPI_31_DOCUMENT);
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .staticFile(staticFile.toString())
                .generatedMode(OpenApiGeneratedMode.MERGE)
                .openApiVersion(renderVersion)
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config,
                                                    () -> List.of(generatedPathSource()),
                                                    () -> List.of(provider("3.1", staticVersion)));

        assertThat(feature.contentOpenApiVersionLoaded(), is(false));
        feature.setup(new TestFeatureContext("admin"));
        feature.initialize();

        assertThat(feature.contentOpenApiVersionLoaded(), is(true));
        assertThat(staticVersion.parseCount(), is(2));
        assertThat(manager.contents().size(), is(2));

        feature.initialize();

        assertThat(staticVersion.parseCount(), is(2));
        assertThat(manager.contents().size(), is(2));
    }

    @Test
    void initializeBeforeSetupWarmsModelsCreatedDuringSetup(@TempDir Path tempDir) throws IOException {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        CountingOpenApiVersion staticVersion = new CountingOpenApiVersion("3.1", "3.1.0");
        Path staticFile = tempDir.resolve("static-3.1.yaml");
        Files.writeString(staticFile, OPENAPI_31_DOCUMENT);
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .staticFile(staticFile.toString())
                .generatedMode(OpenApiGeneratedMode.MERGE)
                .openApiVersion(renderVersion)
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config,
                                                    () -> List.of(generatedPathSource()),
                                                    () -> List.of(provider("3.1", staticVersion)));

        feature.initialize();

        assertThat(staticVersion.parseCount(), is(1));
        assertThat(manager.contents().size(), is(1));

        feature.setup(new TestFeatureContext("admin"));

        assertThat(staticVersion.parseCount(), is(2));
        assertThat(manager.contents().size(), is(2));

        feature.initialize();

        assertThat(staticVersion.parseCount(), is(2));
        assertThat(manager.contents().size(), is(2));
    }

    @Test
    void mergeStaticDocumentParsesStaticDocumentWithListenerContext(@TempDir Path tempDir) throws IOException {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        ListenerOpenApiVersion staticVersion = new ListenerOpenApiVersion("3.1", "3.1.0");
        Path staticFile = tempDir.resolve("static-3.1.yaml");
        Files.writeString(staticFile, OPENAPI_31_DOCUMENT);
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .staticFile(staticFile.toString())
                .generatedMode(OpenApiGeneratedMode.MERGE)
                .openApiVersion(renderVersion)
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config,
                                                    () -> List.of(generatedPathSource()),
                                                    () -> List.of(provider("3.1", staticVersion)));

        feature.setup(new TestFeatureContext("admin"));
        feature.initialize();

        List<String> titles = manager.contents()
                .stream()
                .map(OpenApiFeatureTest::parse)
                .map(document -> map(document, "info"))
                .map(info -> (String) info.get("title"))
                .toList();
        assertThat(titles.contains(WebServer.DEFAULT_SOCKET_NAME), is(true));
        assertThat(titles.contains("admin"), is(true));
    }

    @Test
    void mergeStaticDocumentUsesRootVersionWhenListenerHasNoGeneratedSource(@TempDir Path tempDir) throws IOException {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        OpenApiVersion staticVersion = new TestOpenApiVersion("3.1", "3.1.0", false);
        Path staticFile = tempDir.resolve("static-3.1.yaml");
        Files.writeString(staticFile, OPENAPI_31_DOCUMENT);
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .staticFile(staticFile.toString())
                .generatedMode(OpenApiGeneratedMode.MERGE)
                .openApiVersion(renderVersion)
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config,
                                                    () -> List.of(generatedSource("private", "/private")),
                                                    () -> List.of(provider("3.1", staticVersion)));

        feature.setup(new TestFeatureContext("admin"));
        feature.initialize();

        List<String> versions = manager.contents()
                .stream()
                .map(OpenApiFeatureTest::parse)
                .map(document -> (String) document.get("openapi"))
                .toList();
        assertThat(versions, is(List.of("3.0.3", "3.0.3")));
    }

    @Test
    void generatedOnlyIgnoresStaticDocumentVersion(@TempDir Path tempDir) throws IOException {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        Path staticFile = tempDir.resolve("missing-version.yaml");
        Files.writeString(staticFile, """
                info:
                  title: Broken Static API
                  version: 1.0.0
                """);
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .staticFile(staticFile.toString())
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .openApiVersion(OpenApi30Version.create())
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config, () -> List.of(generatedSource()), List::of);

        assertThat(feature.contentOpenApiVersionLoaded(), is(false));
        feature.initialize();

        assertThat(feature.contentOpenApiVersionLoaded(), is(false));
        Map<String, Object> document = parse(manager.content());
        assertThat(map(document, "info").get("title"), is("Generated API"));
        assertThat(map(document, "paths").containsKey("/generated"), is(true));
    }

    @Test
    void staticOnlyServesStaticDocumentAsIs(@TempDir Path tempDir) throws IOException {
        staticModeServesStaticDocumentAsIs(tempDir, OpenApiGeneratedMode.STATIC_ONLY);
    }

    @Test
    void staticFirstServesStaticDocumentAsIs(@TempDir Path tempDir) throws IOException {
        staticModeServesStaticDocumentAsIs(tempDir, OpenApiGeneratedMode.STATIC_FIRST);
    }

    @Test
    void generatedFallbackWithoutStaticDocumentUsesGeneratedSources() {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.STATIC_FIRST)
                .openApiVersion(OpenApi30Version.create())
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config, () -> List.of(generatedSource()), List::of);

        feature.initialize();

        Map<String, Object> document = parse(manager.content());
        assertThat(map(document, "info").get("title"), is("Generated API"));
        assertThat(map(document, "paths").containsKey("/generated"), is(true));
    }

    private static void staticModeServesStaticDocumentAsIs(Path tempDir, OpenApiGeneratedMode mode) throws IOException {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        Path staticFile = tempDir.resolve("static-3.1.yaml");
        Files.writeString(staticFile, OPENAPI_31_DOCUMENT);
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .staticFile(staticFile.toString())
                .generatedMode(mode)
                .openApiVersion(renderVersion)
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config,
                                                    () -> List.of(generatedSource()),
                                                    () -> List.of(provider("3.1",
                                                                           new TestOpenApiVersion("3.1", "3.1.0", true))));

        assertThat(feature.contentOpenApiVersionLoaded(), is(false));
        feature.initialize();

        assertThat(feature.contentOpenApiVersionLoaded(), is(false));
        assertThat(manager.content(), is(OPENAPI_31_DOCUMENT));
    }

    @Test
    void generatedOperationIdsCanBeConfigured() {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .generatedOperationIds(Map.of("com.example.GeneratedEndpoint#get()", "configuredGet"))
                .openApiVersion(OpenApi30Version.create())
                .manager(manager)
                .buildPrototype();
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/generated",
                      path -> path.operation(
                              "GET",
                              operation -> operation.operationId(OpenApiDocumentContextSupport.operationId(
                                      context,
                                      "com.example.GeneratedEndpoint#get()",
                                      "generatedGet"))
                                      .response("200", "Generated response.")));
        OpenApiFeature feature = new OpenApiFeature(config, () -> List.of(source), List::of);

        feature.initialize();

        Map<String, Object> document = parse(manager.content());
        assertThat(map(map(map(document, "paths"), "/generated"), "get").get("operationId"), is("configuredGet"));
    }

    @Test
    void generatedDocumentContextLeavesConfigExpressionsLiteralByDefault() {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        Config sourceConfig = Config.just(ConfigSources.create(Map.of("openapi.title", "Configured API",
                                                                     "openapi.host", "api.example.com")));
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .openApiVersion(OpenApi30Version.create())
                .manager(manager)
                .buildPrototype();
        OpenApiDocumentSource source = (context, document) -> document
                .info(OpenApiDocumentContextSupport.resolveExpression(context, "${openapi.title:Generated API}"),
                      "1.0.0")
                .paths(Map.of())
                .server(server -> server.url(OpenApiDocumentContextSupport.resolveExpression(
                        context,
                        "https://${openapi.host:localhost}")));
        OpenApiFeature feature = new OpenApiFeature(sourceConfig, config, () -> List.of(source), List::of);

        feature.initialize();

        Map<String, Object> document = parse(manager.content());
        assertThat(map(document, "info").get("title"), is("${openapi.title:Generated API}"));
        assertThat(map(list(document, "servers").getFirst()).get("url"), is("https://${openapi.host:localhost}"));
    }

    @Test
    void generatedDocumentContextResolvesConfigExpressions() {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        Config sourceConfig = Config.just(ConfigSources.create(Map.of("openapi.title", "Configured API",
                                                                     "openapi.host", "api.example.com")));
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .generatedResolveConfigExpressions(true)
                .openApiVersion(OpenApi30Version.create())
                .manager(manager)
                .buildPrototype();
        OpenApiDocumentSource source = (context, document) -> document
                .info(OpenApiDocumentContextSupport.resolveExpression(context, "${openapi.title:Generated API}"),
                      "1.0.0")
                .paths(Map.of())
                .server(server -> server.url(OpenApiDocumentContextSupport.resolveExpression(
                        context,
                        "https://${openapi.host:localhost}")));
        OpenApiFeature feature = new OpenApiFeature(sourceConfig, config, () -> List.of(source), List::of);

        feature.initialize();

        Map<String, Object> document = parse(manager.content());
        assertThat(map(document, "info").get("title"), is("Configured API"));
        assertThat(map(list(document, "servers").getFirst()).get("url"), is("https://api.example.com"));
    }

    @Test
    void runtimeBuilderUsesSuppliedRegistryAndSourceConfigForGeneratedDocumentSources() {
        RecordingOpenApiManager openApiManager = new RecordingOpenApiManager();
        Config sourceConfig = Config.just(ConfigSources.create(Map.of(
                "openapi.title", "Configured Builder API",
                "server.features.openapi.generated.mode", "GENERATED_ONLY",
                "server.features.openapi.generated.resolve-config-expressions", "true",
                "server.features.openapi.generated.document-sources.0", ConfigExpressionOpenApi.class.getCanonicalName())));
        ServiceRegistryManager registryManager = documentSourceRegistry();
        try {
            OpenApiFeature feature = OpenApiFeature.builder()
                    .serviceRegistry(registryManager.registry())
                    .config(sourceConfig.get("server.features.openapi"))
                    .servicesDiscoverServices(false)
                    .openApiVersion(OpenApi30Version.create())
                    .manager(openApiManager)
                    .build();

            feature.initialize();

            Map<String, Object> document = parse(openApiManager.content());
            assertThat(map(document, "info").get("title"), is("Configured Builder API"));
            assertThat(map(document, "paths").containsKey("/generated"), is(true));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void configuredGeneratedDocumentSourceUsesDottedNamedByTypeName() {
        RecordingOpenApiManager openApiManager = new RecordingOpenApiManager();
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .generatedDocumentSources(List.of(SelectedOpenApi.class.getCanonicalName()))
                .openApiVersion(OpenApi30Version.create())
                .manager(openApiManager)
                .buildPrototype();
        ServiceRegistryManager registryManager = documentSourceRegistry();
        try {
            OpenApiFeature feature = new OpenApiFeature(registryManager.registry(), config);

            feature.initialize();

            Map<String, Object> document = parse(openApiManager.content());
            assertThat(map(document, "info").get("title"), is("Selected API"));
            assertThat(map(document, "paths").containsKey("/generated"), is(true));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void multipleNamedGeneratedDocumentSourcesRequireConfiguration() {
        RecordingOpenApiManager openApiManager = new RecordingOpenApiManager();
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .openApiVersion(OpenApi30Version.create())
                .manager(openApiManager)
                .buildPrototype();
        ServiceRegistryManager registryManager = documentSourceRegistry();
        try {
            OpenApiFeature feature = new OpenApiFeature(registryManager.registry(), config);

            IllegalStateException ex = assertThrows(IllegalStateException.class, feature::initialize);
            assertThat(ex.getMessage(), containsString("generated.document-sources"));
            assertThat(ex.getMessage(), containsString(SelectedOpenApi.class.getCanonicalName()));
            assertThat(ex.getMessage(), containsString(OtherOpenApi.class.getCanonicalName()));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void initializeWarmsConfiguredListenerModelsAfterSetup() {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .openApiVersion(OpenApi30Version.create())
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config, () -> List.of(
                generatedSource(WebServer.DEFAULT_SOCKET_NAME, "/default"),
                generatedSource("admin", "/admin")), List::of);

        feature.setup(new TestFeatureContext("admin"));
        feature.initialize();

        List<Map<String, Object>> documents = manager.contents()
                .stream()
                .map(OpenApiFeatureTest::parse)
                .toList();
        assertThat(documents.size(), is(2));
        assertThat(documents.stream().anyMatch(it -> map(it, "paths").containsKey("/default")), is(true));
        assertThat(documents.stream().anyMatch(it -> map(it, "paths").containsKey("/admin")), is(true));
    }

    @Test
    void serializesListenerModelInitialization() throws Exception {
        CountDownLatch firstDescribe = new CountDownLatch(1);
        CountDownLatch concurrentDescribe = new CountDownLatch(1);
        CountDownLatch releaseFirstDescribe = new CountDownLatch(1);
        CountDownLatch startRequests = new CountDownLatch(1);
        AtomicInteger activeDescribes = new AtomicInteger();
        OpenApiDocumentSource source = (context, document) -> {
            int active = activeDescribes.incrementAndGet();
            try {
                if (active == 1) {
                    firstDescribe.countDown();
                    if (!releaseFirstDescribe.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release the first OpenAPI source invocation.");
                    }
                } else {
                    concurrentDescribe.countDown();
                }
                document.info(context.listener(), "1.0.0")
                        .paths(Map.of());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            } finally {
                activeDescribes.decrementAndGet();
            }
        };
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .openApiVersion(OpenApi30Version.create())
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config, () -> List.of(source), List::of);
        WebServer webServer = WebServer.builder()
                .port(0)
                .putSocket("admin", listener -> listener.port(0).name("admin"))
                .addFeature(feature)
                .build();
        WebClient concurrentClient = WebClient.create();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            webServer.start();
            var defaultRequest = executor.submit(() -> {
                startRequests.await();
                return concurrentClient.get("http://localhost:" + webServer.port() + "/openapi")
                        .accept(MediaTypes.APPLICATION_OPENAPI_YAML)
                        .request(String.class);
            });
            var adminRequest = executor.submit(() -> {
                startRequests.await();
                return concurrentClient.get("http://localhost:" + webServer.port("admin") + "/openapi")
                        .accept(MediaTypes.APPLICATION_OPENAPI_YAML)
                        .request(String.class);
            });

            startRequests.countDown();
            assertThat(firstDescribe.await(10, TimeUnit.SECONDS), is(true));
            boolean overlapped = concurrentDescribe.await(2, TimeUnit.SECONDS);
            releaseFirstDescribe.countDown();

            assertThat(defaultRequest.get().status(), is(Status.OK_200));
            assertThat(adminRequest.get().status(), is(Status.OK_200));
            assertThat(overlapped, is(false));
        } finally {
            releaseFirstDescribe.countDown();
            concurrentClient.closeResource();
            webServer.stop();
        }
    }

    @Test
    void serializesManagerLoadingAndFormatting() throws Exception {
        CountDownLatch formatStarted = new CountDownLatch(1);
        CountDownLatch loadDuringFormat = new CountDownLatch(1);
        CountDownLatch releaseFormat = new CountDownLatch(1);
        AtomicBoolean formatting = new AtomicBoolean();
        OpenApiManager<String> manager = new OpenApiManager<>() {
            @Override
            public String load(String content) {
                if (formatting.get()) {
                    loadDuringFormat.countDown();
                }
                return content;
            }

            @Override
            public String format(String model, OpenApiFormat format) {
                formatting.set(true);
                formatStarted.countDown();
                try {
                    if (!releaseFormat.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release OpenAPI formatting.");
                    }
                    return model;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                } finally {
                    formatting.set(false);
                }
            }

            @Override
            public String name() {
                return "test";
            }

            @Override
            public String type() {
                return "test";
            }
        };
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .openApiVersion(OpenApi30Version.create())
                .manager(manager)
                .buildPrototype();
        OpenApiFeature firstFeature = new OpenApiFeature(config, () -> List.of(
                generatedSource(WebServer.DEFAULT_SOCKET_NAME, "/first")), List::of);
        OpenApiFeature secondFeature = new OpenApiFeature(config, () -> List.of(
                generatedSource(WebServer.DEFAULT_SOCKET_NAME, "/second")), List::of);
        WebServer webServer = WebServer.builder()
                .port(0)
                .addFeature(firstFeature)
                .build();
        WebClient client = WebClient.create();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            webServer.start();
            var formattedRequest = executor.submit(() -> client.get("http://localhost:" + webServer.port() + "/openapi")
                    .accept(MediaTypes.APPLICATION_OPENAPI_YAML)
                    .request(String.class));
            assertThat(formatStarted.await(10, TimeUnit.SECONDS), is(true));

            var modelInitialization = executor.submit(() -> {
                secondFeature.initialize();
                return null;
            });
            boolean overlapped = loadDuringFormat.await(2, TimeUnit.SECONDS);
            releaseFormat.countDown();

            assertThat(formattedRequest.get().status(), is(Status.OK_200));
            modelInitialization.get();
            assertThat(overlapped, is(false));
        } finally {
            releaseFormat.countDown();
            client.closeResource();
            webServer.stop();
        }
    }

    @Test
    void formatsAfterConcurrentModelInitializationWithoutLockInversion() throws Exception {
        CountDownLatch modelLoading = new CountDownLatch(1);
        CountDownLatch requestReadingModel = new CountDownLatch(1);
        CountDownLatch continueModelLoading = new CountDownLatch(1);
        AtomicBoolean lockInverted = new AtomicBoolean();
        ReentrantLock managerLock = new ReentrantLock();
        OpenApiManager<String> manager = new OpenApiManager<>() {
            @Override
            public String load(String content) {
                return content;
            }

            @Override
            public String format(String model, OpenApiFormat format) {
                return model;
            }

            @Override
            public String name() {
                return "test";
            }

            @Override
            public String type() {
                return "test";
            }
        };
        LazyValue<Object> delegateModel = LazyValue.create(() -> {
            modelLoading.countDown();
            try {
                if (!continueModelLoading.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to continue OpenAPI model loading.");
                }
                if (!managerLock.tryLock(2, TimeUnit.SECONDS)) {
                    lockInverted.set(true);
                    return "model";
                }
                try {
                    return manager.load("model");
                } finally {
                    managerLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });
        AtomicInteger modelReads = new AtomicInteger();
        LazyValue<Object> model = new LazyValue<>() {
            @Override
            public Object get() {
                if (modelReads.incrementAndGet() == 2) {
                    requestReadingModel.countDown();
                }
                return delegateModel.get();
            }

            @Override
            public boolean isLoaded() {
                return delegateModel.isLoaded();
            }
        };
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .webContext("/lock-order")
                .servicesDiscoverServices(false)
                .buildPrototype();
        OpenApiHttpFeature httpFeature = new OpenApiHttpFeature(config,
                                                                manager,
                                                                model,
                                                                managerLock,
                                                                Optional.empty(),
                                                                OpenApiFormat.UNSUPPORTED);
        WebServer webServer = WebServer.builder()
                .port(0)
                .routing(routing -> routing.addFeature(httpFeature))
                .build();
        WebClient webClient = WebClient.create();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            webServer.start();
            var modelInitialization = executor.submit(model::get);
            assertThat(modelLoading.await(10, TimeUnit.SECONDS), is(true));

            var formattedRequest = executor.submit(() -> webClient.get("http://localhost:" + webServer.port() + "/lock-order")
                    .accept(MediaTypes.APPLICATION_OPENAPI_YAML)
                    .request(String.class));
            assertThat(requestReadingModel.await(10, TimeUnit.SECONDS), is(true));
            continueModelLoading.countDown();

            modelInitialization.get();
            assertThat(formattedRequest.get().status(), is(Status.OK_200));
            assertThat(lockInverted.get(), is(false));
        } finally {
            continueModelLoading.countDown();
            webClient.closeResource();
            webServer.stop();
        }
    }

    @Test
    void serializesModelInitializationAcrossFeatures() throws Exception {
        CountDownLatch firstDescribe = new CountDownLatch(1);
        CountDownLatch concurrentDescribe = new CountDownLatch(1);
        CountDownLatch releaseFirstDescribe = new CountDownLatch(1);
        CountDownLatch startInitialization = new CountDownLatch(1);
        AtomicInteger activeDescribes = new AtomicInteger();
        OpenApiDocumentSource source = (context, document) -> {
            int active = activeDescribes.incrementAndGet();
            try {
                if (active == 1) {
                    firstDescribe.countDown();
                    if (!releaseFirstDescribe.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release the first OpenAPI source invocation.");
                    }
                } else {
                    concurrentDescribe.countDown();
                }
                document.info(context.listener(), "1.0.0")
                        .paths(Map.of());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            } finally {
                activeDescribes.decrementAndGet();
            }
        };
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .generatedMode(OpenApiGeneratedMode.GENERATED_ONLY)
                .openApiVersion(OpenApi30Version.create())
                .buildPrototype();
        OpenApiFeature firstFeature = new OpenApiFeature(config, () -> List.of(source), List::of);
        OpenApiFeature secondFeature = new OpenApiFeature(config, () -> List.of(source), List::of);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstInitialization = executor.submit(() -> {
                startInitialization.await();
                firstFeature.initialize();
                return null;
            });
            var secondInitialization = executor.submit(() -> {
                startInitialization.await();
                secondFeature.initialize();
                return null;
            });

            startInitialization.countDown();
            assertThat(firstDescribe.await(10, TimeUnit.SECONDS), is(true));
            boolean overlapped = concurrentDescribe.await(2, TimeUnit.SECONDS);
            releaseFirstDescribe.countDown();

            firstInitialization.get();
            secondInitialization.get();
            assertThat(overlapped, is(false));
        } finally {
            releaseFirstDescribe.countDown();
        }
    }

    @Test
    void openApi30ParserRequiresOpenApi30Document() {
        OpenApiDocumentContext context = context(OpenApi30Version.create());

        assertThrows(IllegalStateException.class,
                     () -> OpenApi30Version.create().parse(context,
                                                           OPENAPI_31_DOCUMENT,
                                                           MediaTypes.APPLICATION_OPENAPI_YAML));
    }

    @Test
    void openApi30ParserConvertsToCanonicalSchema() {
        OpenApiDocumentContext context = context(OpenApi30Version.create());
        OpenApiDocument document = OpenApi30Version.create()
                .parse(context, resource("/static-3.0.yaml"), MediaTypes.APPLICATION_OPENAPI_YAML);

        Map<String, Object> status = schemaProperty(document, "StaticItem", "status");

        assertThat(status.containsKey("nullable"), is(false));
        assertThat(status.get("type"), is(List.of("string", "null")));
        assertThat(((List<?>) status.get("enum")).contains(null), is(false));
    }

    @Test
    void openApi30VersionRejectsNullArguments() {
        OpenApi30Version version = OpenApi30Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = OpenApiDocument.builder().build();

        assertThrows(NullPointerException.class, () -> OpenApi30Version.create((OpenApi30VersionConfig) null));
        assertThrows(NullPointerException.class, () -> version.parse(null, "", MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThrows(NullPointerException.class, () -> version.parse(context, null, MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThrows(NullPointerException.class, () -> version.parse(context, "", null));
        assertThrows(NullPointerException.class, () -> version.render(null, document));
        assertThrows(NullPointerException.class, () -> version.render(context, null));
    }

    @Test
    void openApiDocumentSourceRejectsNullContext() {
        OpenApiDocumentSource source = (context, document) -> {
        };

        assertThrows(NullPointerException.class, () -> source.supports(null));
    }

    @Test
    void openApiProvidersRejectNullConfigAndName() {
        assertThrows(NullPointerException.class, () -> new OpenApiFeatureProvider().create(null, "openapi"));
        assertThrows(NullPointerException.class, () -> new OpenApiFeatureProvider().create(Config.empty(), null));
        assertThrows(NullPointerException.class, () -> new OpenApi30VersionProvider().create(null, "3.0"));
        assertThrows(NullPointerException.class, () -> new OpenApi30VersionProvider().create(Config.empty(), null));
    }

    @Test
    void openApi30RendererDropsOrTranslatesNewerVersionFields() {
        OpenApiDocumentContext context = context(OpenApi30Version.create());
        OpenApiDocument document = newerVersionDocument();

        Map<String, Object> rendered = parse(OpenApi30Version.create().render(context, document));

        assertThat(rendered.get("openapi"), is("3.0.3"));
        assertThat(rendered.containsKey("jsonSchemaDialect"), is(false));
        assertThat(rendered.containsKey("$self"), is(false));
        assertThat(rendered.containsKey("webhooks"), is(false));

        Map<?, ?> info = map(rendered, "info");
        assertThat(info.containsKey("summary"), is(false));
        assertThat(map(info, "license").containsKey("identifier"), is(false));

        Map<?, ?> server = (Map<?, ?>) ((List<?>) rendered.get("servers")).getFirst();
        assertThat(server.containsKey("name"), is(false));

        Map<?, ?> tag = (Map<?, ?>) ((List<?>) rendered.get("tags")).getFirst();
        assertThat(tag.containsKey("summary"), is(false));
        assertThat(tag.containsKey("kind"), is(false));
        assertThat(tag.containsKey("parent"), is(false));

        Map<?, ?> staticPath = map(map(rendered, "paths"), "/static/{id}");
        assertThat(staticPath.containsKey("query"), is(false));
        assertThat(staticPath.containsKey("additionalOperations"), is(false));

        Map<String, Object> status = schemaProperty(rendered, "StaticItem", "status");
        assertThat(status.get("type"), is("string"));
        assertThat(status.get("nullable"), is(true));
        assertThat(((List<?>) status.get("enum")).contains(null), is(true));

        Map<String, Object> union = schemaProperty(rendered, "StaticItem", "union");
        assertThat(union.containsKey("type"), is(false));
        assertThat(union.containsKey("nullable"), is(false));
        List<Object> oneOf = list(union, "oneOf");
        assertThat(oneOf.size(), is(3));
        assertThat(map(oneOf.get(0)).get("type"), is("string"));
        assertThat(map(oneOf.get(1)).get("type"), is("integer"));
        assertThat(map(oneOf.get(2)).get("nullable"), is(true));
        assertThat(list(map(oneOf.get(2)), "enum"), is(singleValueList(null)));

        Map<String, Object> bounded = schemaProperty(rendered, "StaticItem", "bounded");
        assertThat(((Number) bounded.get("maximum")).doubleValue(), is(10.0));
        assertThat(bounded.get("exclusiveMaximum"), is(true));
        assertThat(((Number) bounded.get("minimum")).doubleValue(), is(1.0));
        assertThat(bounded.get("exclusiveMinimum"), is(true));

        Map<String, Object> payload = schemaProperty(rendered, "StaticItem", "payload");
        assertThat(payload, is(Map.of()));

        Map<String, Object> mode = schemaProperty(rendered, "StaticItem", "mode");
        assertThat(mode.containsKey("const"), is(false));
        assertThat(mode.get("enum"), is(List.of("modern")));

        Map<?, ?> securityScheme = map(map(map(rendered, "components"), "securitySchemes"), "bearerAuth");
        assertThat(securityScheme.containsKey("deprecated"), is(false));
    }

    private static Map<String, Object> parse(String content) {
        return new Yaml().load(content);
    }

    private static OpenApiDocument newerVersionDocument() {
        return OpenApiDocument.builder()
                .openapi("3.2.0")
                .info(info -> info.title("Static 3.2 API")
                        .version("3.2.0")
                        .license(license -> license.name("Apache License 2.0")
                                .identifier("Apache-2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0"))
                        .summary("Static fixture with OpenAPI 3.2-only fields."))
                .server(server -> server.url("https://api.example.test")
                        .name("primary"))
                .tag(tag -> tag.name("static")
                        .description("Static resources")
                        .summary("Static")
                        .kind("nav")
                        .parent("root"))
                .path("/static/{id}",
                      path -> path.operation("GET",
                                             operation -> operation.operationId("staticGet")
                                                     .response("200", "Static response.")))
                .components(components -> components
                        .schema("StaticItem",
                                JsonObject.builder()
                                        .set("type", "object")
                                        .set("properties", properties -> properties
                                                .set("status", statusSchema())
                                                .set("union", JsonObject.builder()
                                                        .setValues("type", List.of(JsonString.create("string"),
                                                                                   JsonString.create("integer"),
                                                                                   JsonString.create("null")))
                                                        .build())
                                                .set("bounded", JsonObject.builder()
                                                        .set("exclusiveMaximum", 10)
                                                        .set("exclusiveMinimum", 1)
                                                        .build())
                                                .set("payload", JsonBoolean.TRUE)
                                                .set("mode", JsonObject.builder()
                                                        .set("const", "modern")
                                                        .build()))
                                        .build())
                        .securityScheme("bearerAuth", security -> security.type("http")
                                .scheme("bearer")
                                .deprecated(true)))
                .build();
    }

    private static JsonObject statusSchema() {
        return JsonObject.builder()
                .setValues("type", List.of(JsonString.create("string"), JsonString.create("null")))
                .setValues("enum", List.of(JsonString.create("new"), JsonString.create("done"), JsonNull.instance()))
                .build();
    }

    private static OpenApiDocumentContext context(OpenApiVersion version) {
        return new OpenApiDocumentContextImpl("openapi",
                                              "/openapi",
                                              "default",
                                              OpenApiGeneratedMode.STATIC_ONLY,
                                              version);
    }

    private static Map<String, Object> schemaProperty(OpenApiDocument document, String schemaName, String propertyName) {
        return schemaProperty(parse(document.toJsonObject().toString()), schemaName, propertyName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperty(Map<String, Object> document, String schemaName, String propertyName) {
        return (Map<String, Object>) map(map(map(map(document, "components"), "schemas"), schemaName), "properties")
                .get(propertyName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<?, ?> map, String name) {
        return (Map<String, Object>) map.get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object object) {
        return (Map<String, Object>) object;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<?, ?> map, String name) {
        return (List<Object>) map.get(name);
    }

    private static List<Object> singleValueList(Object value) {
        List<Object> result = new ArrayList<>();
        result.add(value);
        return result;
    }

    private static String resource(String path) {
        try {
            URL resource = OpenApiFeature.class.getResource(path);
            if (resource != null) {
                try (InputStream is = resource.openStream()) {
                    return new String(is.readAllBytes());
                }
            }
            throw new IllegalArgumentException("Resource not found: " + path);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static OpenApiVersionProvider provider(String type, OpenApiVersion version) {
        return new OpenApiVersionProvider() {
            @Override
            public String configKey() {
                return type;
            }

            @Override
            public OpenApiVersion create(Config config, String name) {
                return version;
            }
        };
    }

    private static OpenApiDocumentSource generatedSource() {
        return (context, document) -> document.info("Generated API", "1.0.0")
                .path("/generated",
                      path -> path.operation("GET",
                                                     operation -> operation.operationId("generatedGet")
                                                             .response("200", "Generated response.")));
    }

    private static OpenApiDocumentSource generatedPathSource() {
        return (context, document) -> document.path("/generated",
                                                    path -> path.operation("GET",
                                                                           operation -> operation.operationId("generatedGet")
                                                                                   .response("200",
                                                                                             "Generated response.")));
    }

    private static OpenApiDocumentSource generatedSource(String listener, String path) {
        return new OpenApiDocumentSource() {
            @Override
            public boolean supports(OpenApiDocumentContext context) {
                return listener.equals(context.listener());
            }

            @Override
            public void describe(OpenApiDocumentContext context, OpenApiDocument.Builder document) {
                document.info("Generated API", "1.0.0")
                        .path(path,
                              targetPath -> targetPath.operation("GET",
                                                                 operation -> operation
                                                                         .operationId(path.substring(1) + "Get")
                                                                         .response("200", "Generated response.")));
            }
        };
    }

    private static ServiceRegistryManager documentSourceRegistry() {
        return documentSourceRegistry(null);
    }

    private static ServiceRegistryManager documentSourceRegistry(RecordingOpenApiManager manager) {
        ServiceRegistryConfig.Builder builder = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(testDescriptor(
                        OpenApiVersionProvider.class,
                        "OpenApi30VersionProvider",
                        new OpenApi30VersionProvider()))
                .addServiceDescriptor(documentSourceDescriptor(
                        "SelectedDocument",
                        SelectedOpenApi.class.getCanonicalName(),
                        (context, document) -> document.info("Selected API", "1.0.0")))
                .addServiceDescriptor(documentSourceDescriptor(
                        "OtherDocument",
                        OtherOpenApi.class.getCanonicalName(),
                        (context, document) -> document.info("Other API", "1.0.0")))
                .addServiceDescriptor(documentSourceDescriptor(
                        "ConfiguredDocument",
                        ConfigExpressionOpenApi.class.getCanonicalName(),
                        (context, document) -> document.info(OpenApiDocumentContextSupport.resolveExpression(
                                context,
                                "${openapi.title:Fallback API}"), "1.0.0")))
                .addServiceDescriptor(documentSourceDescriptor(
                        "Endpoint",
                        null,
                        (context, document) -> document.path(
                                "/generated",
                                path -> path.operation("GET",
                                                       operation -> operation
                                                               .operationId("get")
                                                               .response("200", "OK")))));
        if (manager != null) {
            builder.addServiceDescriptor(testDescriptor(
                    OpenApiManagerProvider.class,
                    "RecordingOpenApiManagerProvider",
                    managerProvider(manager)));
        }
        return ServiceRegistryManager.create(builder.build());
    }

    private static ServiceRegistryManager failingVersionProviderRegistry() {
        ServiceRegistryConfig config = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(testDescriptor(
                        OpenApiVersionProvider.class,
                        "FailingOpenApiVersionProvider",
                        new FailingOpenApiVersionProvider()))
                .build();
        return ServiceRegistryManager.create(config);
    }

    private static ServiceDescriptor<OpenApiDocumentSource> documentSourceDescriptor(String type,
                                                                                     String name,
                                                                                     OpenApiDocumentSource source) {
        return new TestServiceDescriptor<>(OpenApiDocumentSource.class, type, source, name);
    }

    private static <T> ServiceDescriptor<T> testDescriptor(Class<T> contractType, String type, T instance) {
        return new TestServiceDescriptor<>(contractType, type, instance, null);
    }

    private static OpenApiManagerProvider managerProvider(RecordingOpenApiManager manager) {
        return new OpenApiManagerProvider() {
            @Override
            public String configKey() {
                return manager.type();
            }

            @Override
            public OpenApiManager<?> create(Config config, String name) {
                return manager;
            }
        };
    }

    private static final class SelectedOpenApi {
    }

    private static final class OtherOpenApi {
    }

    private static final class ConfigExpressionOpenApi {
    }

    private static final class FailingOpenApiVersionProvider implements OpenApiVersionProvider {
        @Override
        public String configKey() {
            return "failing";
        }

        @Override
        public OpenApiVersion create(Config config, String name) {
            throw new AssertionError("Disabled OpenAPI feature must not create version providers.");
        }
    }

    private static final String OPENAPI_31_DOCUMENT = """
            openapi: 3.1.0
            info:
              title: Static API
              version: 1.0.0
            paths: {}
            """;

    private static void mergeStaticDocumentUsesRootVersion(Path staticFile, String content) throws IOException {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        OpenApiVersion staticVersion = new TestOpenApiVersion("3.1", "3.1.0", false);
        Files.writeString(staticFile, content);
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .staticFile(staticFile.toString())
                .generatedMode(OpenApiGeneratedMode.MERGE)
                .openApiVersion(renderVersion)
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config,
                                                    () -> List.of(generatedPathSource()),
                                                    () -> List.of(provider("3.1", staticVersion)));

        feature.initialize();

        assertThat(parse(manager.content()).get("openapi"), is("3.0.3"));
    }

    private record TestOpenApiVersion(String type, String version, boolean failParse) implements OpenApiVersion {
        @Override
        public OpenApiDocument parse(OpenApiDocumentContext context, String content, MediaType mediaType) {
            if (failParse) {
                throw new AssertionError("Configured render version must not parse static content.");
            }
            return OpenApiDocument.builder()
                    .openapi(version)
                    .info("Static API", "1.0.0")
                    .paths(Map.of())
                    .build();
        }

        @Override
        public String render(OpenApiDocumentContext context, OpenApiDocument document) {
            return OpenApi30Version.create().render(context, document);
        }

        @Override
        public String name() {
            return type;
        }
    }

    private static final class CountingOpenApiVersion implements OpenApiVersion {
        private final String type;
        private final String version;
        private int parseCount;

        private CountingOpenApiVersion(String type, String version) {
            this.type = type;
            this.version = version;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public OpenApiDocument parse(OpenApiDocumentContext context, String content, MediaType mediaType) {
            parseCount++;
            return OpenApiDocument.builder()
                    .openapi(version)
                    .info("Static API", "1.0.0")
                    .build();
        }

        @Override
        public String render(OpenApiDocumentContext context, OpenApiDocument document) {
            return OpenApi30Version.create().render(context, document);
        }

        @Override
        public String name() {
            return type;
        }

        @Override
        public String type() {
            return type;
        }

        int parseCount() {
            return parseCount;
        }
    }

    private static final class ListenerOpenApiVersion implements OpenApiVersion {
        private final String type;
        private final String version;

        private ListenerOpenApiVersion(String type, String version) {
            this.type = type;
            this.version = version;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public OpenApiDocument parse(OpenApiDocumentContext context, String content, MediaType mediaType) {
            return OpenApiDocument.builder()
                    .openapi(version)
                    .info(context.listener(), "1.0.0")
                    .build();
        }

        @Override
        public String render(OpenApiDocumentContext context, OpenApiDocument document) {
            return OpenApi30Version.create().render(context, document);
        }

        @Override
        public String name() {
            return type;
        }

        @Override
        public String type() {
            return type;
        }
    }

    private static final class RecordingOpenApiManager implements OpenApiManager<String> {
        private final List<String> contents = new ArrayList<>();

        @Override
        public String load(String content) {
            contents.add(content);
            return content;
        }

        @Override
        public String format(String model, OpenApiFormat format) {
            return model;
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public String type() {
            return "test";
        }

        String content() {
            return contents.getLast();
        }

        List<String> contents() {
            return contents;
        }
    }

    private static final class TestServiceDescriptor<T> implements ServiceDescriptor<T> {
        private final ResolvedType contract;
        private final TypeName serviceType;
        private final TypeName descriptorType;
        private final Set<Qualifier> qualifiers;
        private final T instance;

        private TestServiceDescriptor(Class<T> contractType, String type, T instance, String name) {
            this.contract = ResolvedType.create(contractType);
            this.serviceType = TypeName.create("io.helidon.openapi.OpenApiFeatureTest." + type);
            this.descriptorType = TypeName.create("io.helidon.openapi.OpenApiFeatureTest."
                                                          + type
                                                          + "__ServiceDescriptor");
            this.qualifiers = name == null ? Set.of() : Set.of(Qualifier.createNamed(name));
            this.instance = instance;
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata metadata) {
            return instance;
        }

        @Override
        public TypeName serviceType() {
            return serviceType;
        }

        @Override
        public TypeName descriptorType() {
            return descriptorType;
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of(contract);
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return qualifiers;
        }
    }

    private static final class TestFeatureContext implements ServerFeature.ServerFeatureContext {
        private final Set<String> sockets;

        private TestFeatureContext(String... sockets) {
            this.sockets = Set.of(sockets);
        }

        @Override
        public WebServerConfig serverConfig() {
            return WebServerConfig.create();
        }

        @Override
        public Set<String> sockets() {
            return sockets;
        }

        @Override
        public boolean socketExists(String socketName) {
            return WebServer.DEFAULT_SOCKET_NAME.equals(socketName) || sockets.contains(socketName);
        }

        @Override
        public ServerFeature.SocketBuilders socket(String socketName) {
            if (!socketExists(socketName)) {
                throw new NoSuchElementException("Socket " + socketName + " is not defined");
            }
            return new TestSocketBuilders();
        }
    }

    private static final class TestSocketBuilders implements ServerFeature.SocketBuilders {
        @Override
        public ListenerConfig listener() {
            return ListenerConfig.create();
        }

        @Override
        public HttpRouting.Builder httpRouting() {
            return HttpRouting.builder();
        }

        @Override
        public ServerFeature.RoutingBuilders routingBuilders() {
            return new ServerFeature.RoutingBuilders() {
                @Override
                public boolean hasRouting(Class<?> builderType) {
                    return false;
                }

                @Override
                public <T extends Builder<T, ?>> T routingBuilder(Class<T> builderType) {
                    if (builderType == HttpRouting.Builder.class) {
                        return builderType.cast(HttpRouting.builder());
                    }
                    throw new NoSuchElementException("Routing not available for type: " + builderType);
                }
            };
        }
    }
}
