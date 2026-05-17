/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.function.Function;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.openapi.OpenApiFeature;
import io.helidon.openapi.OpenApiService;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class OpenApiUiPermitAllTest {
    private static final HeaderName HEADER_PREDICATE = HeaderNames.create("X-OpenAPI-Service-Route");

    private final WebClient client;

    OpenApiUiPermitAllTest(WebClient client) {
        this.client = client;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder server) {
        server.addFeature(OpenApiFeature.builder()
                                  .servicesDiscoverServices(false)
                                  .staticFile("src/test/resources/greeting.yml")
                                  .cors(cors -> cors.enabled(false))
                                  .permitAll(false)
                                  .addService(OpenApiUi.create())
                                  .build())
                .addFeature(OpenApiFeature.builder()
                                  .servicesDiscoverServices(false)
                                  .staticFile("src/test/resources/greeting.yml")
                                  .webContext("/my-openapi")
                                  .name("my-openapi")
                                  .cors(cors -> cors.enabled(false))
                                  .permitAll(false)
                                  .addService(OpenApiUi.builder()
                                                      .webContext("/my-ui")
                                                      .build())
                                  .build())
                .addFeature(OpenApiFeature.builder()
                                  .servicesDiscoverServices(false)
                                  .staticFile("src/test/resources/greeting.yml")
                                  .webContext("/")
                                  .name("root-openapi")
                                  .cors(cors -> cors.enabled(false))
                                  .permitAll(false)
                                  .build())
                .addFeature(OpenApiFeature.builder()
                                  .servicesDiscoverServices(false)
                                  .staticFile("src/test/resources/greeting.yml")
                                  .webContext("/header-openapi")
                                  .name("header-openapi")
                                  .cors(cors -> cors.enabled(false))
                                  .permitAll(false)
                                  .addService(new HeaderPredicateService())
                                  .build());
    }

    @SetUpRoute
    static void setup(HttpRouting.Builder routing) {
    }

    @Test
    void exactPathRequiresAuthorization() {
        ClientResponseTyped<String> response = client.get("/openapi")
                .accept(MediaTypes.TEXT_YAML)
                .request(String.class);

        assertThat(response.status(), is(Status.FORBIDDEN_403));
    }

    @Test
    void trailingSlashRequiresAuthorization() {
        ClientResponseTyped<String> response = client.get("/openapi/")
                .accept(MediaTypes.TEXT_YAML)
                .request(String.class);

        assertThat(response.status(), is(Status.FORBIDDEN_403));
    }

    @Test
    void uiIndexRequiresAuthorization() {
        ClientResponseTyped<String> response = client.get("/openapi/ui/index.html")
                .accept(MediaTypes.TEXT_HTML)
                .request(String.class);

        assertThat(response.status(), is(Status.FORBIDDEN_403));
    }

    @Test
    void uiRedirectRequiresAuthorization() {
        ClientResponseTyped<String> response = client.get("/openapi/ui")
                .accept(MediaTypes.TEXT_HTML)
                .request(String.class);

        assertThat(response.status(), is(Status.FORBIDDEN_403));
    }

    @Test
    void uiStaticContentRequiresAuthorization() {
        ClientResponseTyped<String> response = client.get("/openapi/ui/logo.png")
                .request(String.class);

        assertThat(response.status(), is(Status.FORBIDDEN_403));
    }

    @Test
    void customUiIndexRequiresAuthorization() {
        ClientResponseTyped<String> response = client.get("/my-ui/index.html")
                .accept(MediaTypes.TEXT_HTML)
                .request(String.class);

        assertThat(response.status(), is(Status.FORBIDDEN_403));
    }

    @Test
    void customUiRedirectRequiresAuthorization() {
        ClientResponseTyped<String> response = client.get("/my-ui")
                .accept(MediaTypes.TEXT_HTML)
                .request(String.class);

        assertThat(response.status(), is(Status.FORBIDDEN_403));
    }

    @Test
    void customUiStaticContentRequiresAuthorization() {
        ClientResponseTyped<String> response = client.get("/my-ui/logo.png")
                .request(String.class);

        assertThat(response.status(), is(Status.FORBIDDEN_403));
    }

    @Test
    void rootContextDoesNotProtectUnrelatedPaths() {
        ClientResponseTyped<String> response = client.get("/not-openapi")
                .accept(MediaTypes.TEXT_PLAIN)
                .request(String.class);

        assertThat(response.status(), is(Status.NOT_FOUND_404));
    }

    @Test
    void headerPredicateServiceIgnoresRequestsWithoutHeader() {
        ClientResponseTyped<String> response = client.get("/header-service")
                .request(String.class);

        assertThat(response.status(), is(Status.NOT_FOUND_404));
    }

    @Test
    void headerPredicateServiceRequiresAuthorization() {
        ClientResponseTyped<String> response = client.get("/header-service")
                .header(HEADER_PREDICATE, "present")
                .request(String.class);

        assertThat(response.status(), is(Status.FORBIDDEN_403));
    }

    private static final class HeaderPredicateService implements OpenApiService {
        @Override
        public boolean supports(ServerRequestHeaders headers) {
            return false;
        }

        @Override
        public void setup(HttpRules routing, String docPath, Function<MediaType, String> content) {
            routing.route(HttpRoute.builder()
                                  .methods(Method.GET)
                                  .path("/header-service")
                                  .headers(headers -> headers.contains(HEADER_PREDICATE))
                                  .handler((req, res) -> res.send("protected"))
                                  .build());
        }

        @Override
        public String name() {
            return "header-predicate";
        }

        @Override
        public String type() {
            return "header-predicate";
        }
    }
}
