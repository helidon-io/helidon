/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.stream.Stream;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import static io.helidon.common.testing.junit5.MapMatcher.mapEqualTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
                                  .cors(cors -> cors.enabled(false))
                                  .build())
                .addFeature(OpenApiFeature.builder()
                                    .servicesDiscoverServices(false)
                                    .staticFile("src/test/resources/time-server.yml")
                                    .webContext("/openapi-time")
                                    .name("openapi-time")
                                    .cors(cors -> cors.allowOrigins("http://foo.bar", "http://bar.foo"))
                                    .build())
                .addFeature(OpenApiFeature.builder()
                                    .servicesDiscoverServices(false)
                                    .staticFile("src/test/resources/petstore.yaml")
                                    .webContext("/openapi-petstore")
                                    .name("openapi-petstore")
                                    .cors(cors -> cors.enabled(false))
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

    private static Map<String, Object> parse(String content) {
        return new Yaml().load(content);
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
}
