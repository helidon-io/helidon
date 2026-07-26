/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.jersey.webserver;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutePathSupport;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class JerseyOnWebServerTest {
    private static final HeaderName TEST_REQUEST_ID = HeaderNames.create("X-Test-Request-Id");
    private static final HeaderName TEST_REQUEST_ROUTE = HeaderNames.create("X-Test-Request-Route");
    private static final ConcurrentMap<String, CompletableFuture<String>> ROUTES = new ConcurrentHashMap<>();

    private final Http1Client client;

    public JerseyOnWebServerTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    public static void routing(HttpRouting.Builder routing) {
        routing.addFilter((chain, req, res) -> {
            String requestId = req.headers().first(TEST_REQUEST_ID).orElse(null);
            if (requestId == null) {
                chain.proceed();
                return;
            }

            AtomicReference<Supplier<String>> routeSupplier = new AtomicReference<>();
            boolean requestRoute = req.headers().first(TEST_REQUEST_ROUTE)
                    .map(Boolean::parseBoolean)
                    .orElse(true);
            if (requestRoute) {
                RoutePathSupport.requestRoute(req.context(), routeSupplier::set);
            }
            chain.proceed();
            CompletableFuture<String> route = ROUTES.get(requestId);
            if (route != null) {
                route.complete(routeSupplier.get() == null ? null : routeSupplier.get().get());
            }
        });
        routing.register("/jersey",
                         JaxRsService.create(Config.empty(), ResourceConfig.forApplication(new JaxRsApplication())));
        routing.register("/jersey-relative",
                         JaxRsService.create(Config.empty(), ResourceConfig.forApplication(new RelativePathApplication())));
        routing.register("/jersey-trailing",
                         JaxRsService.create(Config.empty(), ResourceConfig.forApplication(new TrailingSlashPathApplication())));
        routing.register("/jersey-locator",
                         JaxRsService.create(Config.empty(), ResourceConfig.forApplication(new LocatorApplication())));
        routing.register("/", JaxRsService.create(Config.empty(), ResourceConfig.forApplication(new RootApplication())));
    }

    @Test
    public void testEndpoint() throws Exception {
        String requestId = requestId();

        var response = client.get("/jersey/greet/Joe")
                .header(TEST_REQUEST_ID, requestId)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello Joe!"));
        assertThat(route(requestId), is("/jersey/greet/{name}"));
    }

    @Test
    public void testRelativeApplicationAndResourcePaths() throws Exception {
        String requestId = requestId();

        var response = client.get("/jersey-relative/greet/Joe")
                .header(TEST_REQUEST_ID, requestId)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello Joe!"));
        assertThat(route(requestId), is("/jersey-relative/greet/{name}"));
    }

    @Test
    public void testTrailingApplicationSlashAndAbsoluteResourcePath() throws Exception {
        String requestId = requestId();

        var response = client.get("/jersey-trailing/greet")
                .header(TEST_REQUEST_ID, requestId)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello!"));
        assertThat(route(requestId), is("/jersey-trailing/greet"));
    }

    @Test
    public void testSubResourceLocatorPath() throws Exception {
        String requestId = requestId();

        var response = client.get("/jersey-locator/widgets/42/details")
                .header(TEST_REQUEST_ID, requestId)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Details 42!"));
        assertThat(route(requestId), is("/jersey-locator/widgets/{id}/details"));
    }

    @Test
    public void testRootMountedRootResourcePath() throws Exception {
        String requestId = requestId();

        var response = client.get("/")
                .header(TEST_REQUEST_ID, requestId)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Root!"));
        assertThat(route(requestId), is("/"));
    }

    @Test
    public void testEndpointWithoutRouteRequest() throws Exception {
        String requestId = requestId();

        var response = client.get("/jersey/greet/Joe")
                .header(TEST_REQUEST_ID, requestId)
                .header(TEST_REQUEST_ROUTE, "false")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello Joe!"));
        assertThat(route(requestId), is((String) null));
    }

    @Test
    public void testUnmatchedEndpointDoesNotSetRoute() throws Exception {
        String requestId = requestId();

        var response = client.get("/jersey/missing")
                .header(TEST_REQUEST_ID, requestId)
                .request();

        assertThat(response.status(), is(Status.NOT_FOUND_404));
        assertThat(route(requestId), is((String) null));
    }

    @Test
    public void testConcurrentRequestsKeepRouteResultsIndependent() throws Exception {
        String rootRequestId = requestId();
        String noRouteRequestId = requestId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        sendConcurrently(ready, start, done, failure, () -> {
            var response = client.get("/")
                    .header(TEST_REQUEST_ID, rootRequestId)
                    .request(String.class);
            assertThat(response.status(), is(Status.OK_200));
        });
        sendConcurrently(ready, start, done, failure, () -> {
            var response = client.get("/jersey/greet/Joe")
                    .header(TEST_REQUEST_ID, noRouteRequestId)
                    .header(TEST_REQUEST_ROUTE, "false")
                    .request(String.class);
            assertThat(response.status(), is(Status.OK_200));
        });

        assertThat(ready.await(5, TimeUnit.SECONDS), is(true));
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS), is(true));
        if (failure.get() != null) {
            throw new AssertionError("Concurrent request failed", failure.get());
        }

        assertThat(route(rootRequestId), is("/"));
        assertThat(route(noRouteRequestId), is((String) null));
    }

    private static String requestId() {
        String requestId = UUID.randomUUID().toString();
        ROUTES.put(requestId, new CompletableFuture<>());
        return requestId;
    }

    private static String route(String requestId) throws Exception {
        try {
            return ROUTES.get(requestId).get(5, TimeUnit.SECONDS);
        } finally {
            ROUTES.remove(requestId);
        }
    }

    private static void sendConcurrently(CountDownLatch ready,
                                         CountDownLatch start,
                                         CountDownLatch done,
                                         AtomicReference<Throwable> failure,
                                         Runnable action) {
        Thread.ofVirtual().start(() -> {
            ready.countDown();
            try {
                start.await();
                action.run();
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                done.countDown();
            }
        });
    }

    @ApplicationPath("/app")
    public static class JaxRsApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(JaxRsEndpoint.class);
        }
    }

    @ApplicationPath("app")
    public static class RelativePathApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(RelativePathEndpoint.class);
        }
    }

    @ApplicationPath("/foo/bar/")
    public static class TrailingSlashPathApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(AbsoluteSimpleEndpoint.class);
        }
    }

    @ApplicationPath("/app")
    public static class LocatorApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(WidgetsEndpoint.class);
        }
    }

    public static class RootApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(RootEndpoint.class);
        }
    }

    @Path("/greet/{name}")
    public static class JaxRsEndpoint {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String greet(@jakarta.ws.rs.PathParam("name") String name) {
            return "Hello " + name + "!";
        }
    }

    @Path("greet/{name}")
    public static class RelativePathEndpoint {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String greet(@jakarta.ws.rs.PathParam("name") String name) {
            return "Hello " + name + "!";
        }
    }

    @Path("/greet")
    public static class AbsoluteSimpleEndpoint {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String greet() {
            return "Hello!";
        }
    }

    @Path("/widgets/{id}")
    public static class WidgetsEndpoint {
        @Path("details")
        public DetailsEndpoint details(@jakarta.ws.rs.PathParam("id") String id) {
            return new DetailsEndpoint(id);
        }
    }

    public static class DetailsEndpoint {
        private final String id;

        DetailsEndpoint(String id) {
            this.id = id;
        }

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String details() {
            return "Details " + id + "!";
        }
    }

    @Path("/")
    public static class RootEndpoint {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String root() {
            return "Root!";
        }
    }
}
