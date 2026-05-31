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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.spi.OpenApiVersionProvider;
import io.helidon.openapi.v30.OpenApi30Version;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
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
                                    .staticFile("src/test/resources/exact-static.yaml")
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
    void staticDocumentUsesParserForDeclaredVersion(@TempDir Path tempDir) throws IOException {
        RecordingOpenApiManager manager = new RecordingOpenApiManager();
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        OpenApiVersion staticVersion = new TestOpenApiVersion("3.1", "3.1.0", false);
        Path staticFile = tempDir.resolve("static-3.1.yaml");
        Files.writeString(staticFile, OPENAPI_31_DOCUMENT);
        OpenApiFeatureConfig config = OpenApiFeatureConfig.builder()
                .servicesDiscoverServices(false)
                .staticFile(staticFile.toString())
                .generatedMode(OpenApiGeneratedMode.STATIC_ONLY)
                .openApiVersion(renderVersion)
                .manager(manager)
                .buildPrototype();
        OpenApiFeature feature = new OpenApiFeature(config,
                                                    List::of,
                                                    () -> List.of(provider("3.1", staticVersion)));

        feature.initialize();

        assertThat(parse(manager.content()).get("openapi"), is("3.0.3"));
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
        assertThat(((List<?>) status.get("enum")).contains(null), is(true));
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
        assertThat(((List<?>) status.get("enum")).contains(null), is(false));

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
                .info("Static 3.2 API", "3.2.0")
                .infoSummary("Static fixture with OpenAPI 3.2-only fields.")
                .addServer(OpenApiDocument.Server.builder("https://api.example.test")
                                   .name("primary")
                                   .build())
                .addTag(OpenApiDocument.Tag.builder("static")
                                .description("Static resources")
                                .summary("Static")
                                .kind("nav")
                                .parent("root")
                                .build())
                .putOperation("/static/{id}",
                              "GET",
                              OpenApiDocument.Operation.builder()
                                      .operationId("staticGet")
                                      .response("200", "Static response.")
                                      .build())
                .putSchema("StaticItem",
                           JsonObject.builder()
                                   .set("type", "object")
                                   .set("properties", properties -> properties
                                           .set("status", statusSchema())
                                           .set("payload", JsonBoolean.TRUE)
                                           .set("mode", JsonObject.builder()
                                                   .set("const", "modern")
                                                   .build()))
                                   .build())
                .putSecurityScheme("bearerAuth",
                                   OpenApiDocument.SecurityScheme.builder("http")
                                           .scheme("bearer")
                                           .deprecated(true)
                                           .build())
                .build();
    }

    private static JsonObject statusSchema() {
        return JsonObject.builder()
                .setValues("type", List.of(JsonString.create("string"), JsonString.create("null")))
                .setValues("enum", List.of(JsonString.create("new"), JsonString.create("done"), JsonNull.instance()))
                .build();
    }

    private static OpenApiDocumentContext context(OpenApiVersion version) {
        return new OpenApiDocumentContext("openapi",
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

    private static final String OPENAPI_31_DOCUMENT = """
            openapi: 3.1.0
            info:
              title: Static API
              version: 1.0.0
            """;

    private record TestOpenApiVersion(String type, String version, boolean failParse) implements OpenApiVersion {
        @Override
        public OpenApiDocument parse(OpenApiDocumentContext context, String content, MediaType mediaType) {
            if (failParse) {
                throw new AssertionError("Configured render version must not parse static content.");
            }
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
    }

    private static final class RecordingOpenApiManager implements OpenApiManager<String> {
        private String content;

        @Override
        public String load(String content) {
            this.content = content;
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
            return content;
        }
    }
}
