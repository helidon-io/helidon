/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.openapi.ui;

import java.util.Map;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.openapi.OpenApiFeature;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tests {@link OpenApiUi}.
 */
@ServerTest
class OpenApiUiTest {

    private static final MediaType[] SIMULATED_BROWSER_ACCEPT = new MediaType[] {
            MediaTypes.TEXT_HTML,
            MediaTypes.APPLICATION_XHTML_XML,
            HttpMediaType.builder()
                    .mediaType(MediaTypes.APPLICATION_XML)
                    .parameters(Map.of("q", "0.9"))
                    .build(),
            MediaTypes.create("image", "webp"),
            MediaTypes.create("image", "apng"),
            HttpMediaType.builder()
                    .mediaType(MediaTypes.WILDCARD)
                    .parameters(Map.of("q", "0.8"))
                    .build()
    };

    private final WebClient client;

    OpenApiUiTest(WebClient client) {
        this.client = client;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder server) {
        server.addFeature(OpenApiFeature.builder()
                                  .servicesDiscoverServices(false)
                                  .staticFile("src/test/resources/greeting.yml")
                                  .cors(cors -> cors.enabled(false))
                                  .addService(OpenApiUi.create())
                                  .build())
                .addFeature(OpenApiFeature.builder()
                                    .servicesDiscoverServices(false)
                                    .staticFile("src/test/resources/greeting.yml")
                                    .webContext("/openapi-greeting")
                                    .name("openapi-greeting")
                                    .cors(cors -> cors.enabled(false))
                                    .addService(OpenApiUi.create())
                                    .build())
                .addFeature(OpenApiFeature.builder()
                                    .servicesDiscoverServices(false)
                                    .staticFile("src/test/resources/greeting.yml")
                                    .cors(cors -> cors.enabled(false))
                                    .addService(OpenApiUi.builder()
                                                        .webContext("/my-ui")
                                                        .build())
                                    .name("openapi-ui")
                                    .build());
    }

    @SetUpRoute
    static void setup(HttpRouting.Builder routing) {
    }

    @Test
    void testDefault() {
        ClientResponseTyped<String> response = client.get("/openapi/ui")
                .accept(MediaTypes.TEXT_HTML)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), startsWith("<!DOCTYPE html>"));
    }

    @Test
    void testDefaultWithTrailingSlash() {
        ClientResponseTyped<String> response = client.get("/openapi/ui/")
                .accept(MediaTypes.TEXT_HTML)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), startsWith("<!DOCTYPE html>"));
    }

    @Test
    void testAlternateOpenApiWebContext() {
        ClientResponseTyped<String> response = client.get("/openapi-greeting/ui")
                .accept(MediaTypes.TEXT_HTML)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), startsWith("<!DOCTYPE html>"));
    }

    @Test
    void testMainEndpoint() {
        ClientResponseTyped<String> response = client.get("/openapi")
                .accept(SIMULATED_BROWSER_ACCEPT)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), startsWith("<!DOCTYPE html>"));
    }

    @Test
    void testMainEndpointWithTrailingSlash() {
        ClientResponseTyped<String> response = client.get("/openapi/")
                .accept(SIMULATED_BROWSER_ACCEPT)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), startsWith("<!DOCTYPE html>"));
    }

    @Test
    void testAlternateUiWebContext() {
        ClientResponseTyped<String> response = client.get("/my-ui")
                .accept(MediaTypes.TEXT_HTML)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), startsWith("<!DOCTYPE html>"));
    }
}
