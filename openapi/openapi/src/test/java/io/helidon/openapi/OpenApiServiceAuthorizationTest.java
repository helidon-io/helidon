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
package io.helidon.openapi;

import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.media.type.MediaType;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RoutingTest
class OpenApiServiceAuthorizationTest {
    private static final HeaderName HEADER_PREDICATE = HeaderNames.create("X-OpenAPI-Service-Route");

    private final WebClient client;

    OpenApiServiceAuthorizationTest(WebClient client) {
        this.client = client;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder server) {
        server.addFeature(OpenApiFeature.builder()
                                  .servicesDiscoverServices(false)
                                  .staticFile("src/test/resources/greeting.yml")
                                  .permitAll(false)
                                  .addService(new TestOpenApiService())
                                  .build());
    }

    @SetUpRoute
    static void setup(HttpRouting.Builder routing) {
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/openapi",
            "/openapi/",
            "/openapi/ui",
            "/openapi/ui/index.html",
            "/openapi/ui/logo.svg",
            "/located/resource"
    })
    void serviceRoutesRequireAuthorization(String path) {
        ClientResponseTyped<String> response = client.get(path)
                .request(String.class);

        assertThat(response.status(), is(Status.FORBIDDEN_403));
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

    private static final class TestOpenApiService implements OpenApiService {
        @Override
        public boolean supports(ServerRequestHeaders headers) {
            return false;
        }

        @Override
        public void setup(HttpRules routing, String docPath, Function<MediaType, String> content) {
            String uiPath = docPath + "/ui";
            routing.get(docPath + "[/]", (req, res) -> res.send("unprotected"))
                    .get(uiPath + "[/]", (req, res) -> res.send("unprotected"))
                    .get(uiPath + "/index.html", (req, res) -> res.send("unprotected"))
                    .register(uiPath, rules -> rules.get("/logo.svg", (req, res) -> res.send("unprotected")))
                    .registerLocator("/located", request -> Optional.of(
                            (HttpService) rules -> rules.get("/resource", (req, res) -> res.send("unprotected"))))
                    .route(HttpRoute.builder()
                                   .methods(Method.GET)
                                   .path("/header-service")
                                   .headers(headers -> headers.contains(HEADER_PREDICATE))
                                   .handler((req, res) -> res.send("unprotected"))
                                   .build());
        }

        @Override
        public String name() {
            return "test-openapi-service";
        }

        @Override
        public String type() {
            return "test-openapi-service";
        }
    }
}
