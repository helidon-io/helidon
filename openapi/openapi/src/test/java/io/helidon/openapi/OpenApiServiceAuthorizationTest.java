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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.helidon.common.media.type.MediaType;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.HttpServiceLocator;
import io.helidon.webserver.http.ServerRequest;
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
    void headerPredicateMissThenProtectedMatch() {
        ClientResponseTyped<String> withoutHeader = client.get("/header-service")
                .request(String.class);

        assertThat(withoutHeader.status(), is(Status.NOT_FOUND_404));

        ClientResponseTyped<String> withHeader = client.get("/header-service")
                .header(HEADER_PREDICATE, "present")
                .request(String.class);

        assertThat(withHeader.status(), is(Status.FORBIDDEN_403));
    }

    @Test
    void securityDoesNotApplyToUnrelatedRootRoute() {
        WebServer server = WebServer.builder()
                .port(0)
                .routing(routing -> routing.get("/unrelated", (req, res) -> res.send("unrelated")))
                .addFeature(OpenApiFeature.builder()
                                    .servicesDiscoverServices(false)
                                    .staticFile("src/test/resources/greeting.yml")
                                    .webContext("/")
                                    .permitAll(false)
                                    .name("root-openapi")
                                    .addService(new RootOpenApiService())
                                    .build())
                .build();

        try {
            server.start();
            WebClient serverClient = testClient(server);
            ClientResponseTyped<String> response = serverClient.get("/unrelated")
                    .request(String.class);

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity(), is("unrelated"));
            assertThat(serverClient.get("/").request(String.class).status(), is(Status.FORBIDDEN_403));
            assertThat(serverClient.get("/root-openapi-service").request(String.class).status(),
                       is(Status.FORBIDDEN_403));
        } finally {
            if (server.isRunning()) {
                server.stop();
            }
        }
    }

    @Test
    void locatedServiceWrapperCacheIsIdentityBounded() {
        var locator = new BoundedLocator();
        WebServer server = testServer(new LocatorOpenApiService("/bounded/{service}", locator));

        try {
            server.start();
            WebClient serverClient = testClient(server);

            assertThat(serverClient.get("/bounded/one/resource").request(String.class).status(),
                       is(Status.FORBIDDEN_403));
            assertThat(serverClient.get("/bounded/one/resource").request(String.class).status(),
                       is(Status.FORBIDDEN_403));
            assertThat(serverClient.get("/bounded/two/resource").request(String.class).status(),
                       is(Status.SERVICE_UNAVAILABLE_503));
        } finally {
            if (server.isRunning()) {
                server.stop();
            }
        }
    }

    @Test
    void locatorAndLocatedServiceRetainLifecycle() {
        var service = new LifecycleService("/resource");
        var locator = new LifecycleLocator(service);
        WebServer server = testServer(new LocatorOpenApiService("/located", locator));

        try {
            server.start();
            WebClient serverClient = testClient(server);

            assertThat(serverClient.get("/located/resource").request(String.class).status(), is(Status.FORBIDDEN_403));
            assertThat("locator beforeStart", locator.beforeStartCount.get(), is(1));
            assertThat("locator afterStart", locator.afterStartCount.get(), is(1));
            assertThat("located service routing", service.routingCount.get(), is(1));
            assertThat("located service beforeStart", service.beforeStartCount.get(), is(1));
            assertThat("located service afterStart", service.afterStartCount.get(), is(1));
        } finally {
            if (server.isRunning()) {
                server.stop();
            }
        }

        assertThat("locator afterStop", locator.afterStopCount.get(), is(1));
        assertThat("located service afterStop", service.afterStopCount.get(), is(1));
    }

    private static WebServer testServer(OpenApiService service) {
        return WebServer.builder()
                .port(0)
                .addFeature(OpenApiFeature.builder()
                                    .servicesDiscoverServices(false)
                                    .staticFile("src/test/resources/greeting.yml")
                                    .permitAll(false)
                                    .addService(service)
                                    .build())
                .build();
    }

    private static WebClient testClient(WebServer server) {
        return WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .build();
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

    private static final class BoundedLocator implements HttpServiceLocator {
        private final Map<String, HttpService> services = Map.of(
                "one", rules -> rules.get("/resource", (req, res) -> res.send("unprotected")),
                "two", rules -> rules.get("/resource", (req, res) -> res.send("unprotected")));

        @Override
        public Optional<HttpService> locate(ServerRequest request) {
            return request.path().pathParameters().first("service").map(services::get);
        }

        @Override
        public int maxServiceCacheSize() {
            return 1;
        }
    }

    private static final class LocatorOpenApiService implements OpenApiService {
        private final String path;
        private final HttpServiceLocator locator;

        private LocatorOpenApiService(String path, HttpServiceLocator locator) {
            this.path = path;
            this.locator = locator;
        }

        @Override
        public boolean supports(ServerRequestHeaders headers) {
            return false;
        }

        @Override
        public void setup(HttpRules routing, String docPath, Function<MediaType, String> content) {
            routing.registerLocator(path, locator);
        }

        @Override
        public String name() {
            return "locator-test-openapi-service";
        }

        @Override
        public String type() {
            return "locator-test-openapi-service";
        }
    }

    private static final class RootOpenApiService implements OpenApiService {
        @Override
        public boolean supports(ServerRequestHeaders headers) {
            return false;
        }

        @Override
        public void setup(HttpRules routing, String docPath, Function<MediaType, String> content) {
            routing.get("/root-openapi-service", (req, res) -> res.send("unprotected"));
        }

        @Override
        public String name() {
            return "root-openapi-service";
        }

        @Override
        public String type() {
            return "root-openapi-service";
        }
    }

    private static final class LifecycleLocator implements HttpServiceLocator {
        private final HttpService service;
        private final AtomicInteger beforeStartCount = new AtomicInteger();
        private final AtomicInteger afterStartCount = new AtomicInteger();
        private final AtomicInteger afterStopCount = new AtomicInteger();

        private LifecycleLocator(HttpService service) {
            this.service = service;
        }

        @Override
        public Optional<HttpService> locate(ServerRequest request) {
            return Optional.of(service);
        }

        @Override
        public void beforeStart() {
            beforeStartCount.incrementAndGet();
        }

        @Override
        public void afterStart(WebServer webServer) {
            afterStartCount.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStopCount.incrementAndGet();
        }
    }

    private static final class LifecycleService implements HttpService {
        private final String routePath;
        private final AtomicInteger routingCount = new AtomicInteger();
        private final AtomicInteger beforeStartCount = new AtomicInteger();
        private final AtomicInteger afterStartCount = new AtomicInteger();
        private final AtomicInteger afterStopCount = new AtomicInteger();

        private LifecycleService(String routePath) {
            this.routePath = routePath;
        }

        @Override
        public void routing(HttpRules rules) {
            routingCount.incrementAndGet();
            rules.get(routePath, (req, res) -> res.send("unprotected"));
        }

        @Override
        public void beforeStart() {
            beforeStartCount.incrementAndGet();
        }

        @Override
        public void afterStart(WebServer webServer) {
            afterStartCount.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStopCount.incrementAndGet();
        }
    }
}
