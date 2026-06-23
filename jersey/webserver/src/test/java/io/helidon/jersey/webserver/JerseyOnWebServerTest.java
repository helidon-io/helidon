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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.config.Config;
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
    private static final AtomicBoolean REQUEST_ROUTE = new AtomicBoolean();
    private static final AtomicReference<String> LAST_ROUTE = new AtomicReference<>();

    private final Http1Client client;

    public JerseyOnWebServerTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    public static void routing(HttpRouting.Builder routing) {
        routing.addFilter((chain, req, res) -> {
            AtomicReference<Supplier<String>> routeSupplier = new AtomicReference<>();
            LAST_ROUTE.set(null);
            if (REQUEST_ROUTE.get()) {
                RoutePathSupport.requestRoute(req.context(), routeSupplier::set);
            }
            chain.proceed();
            LAST_ROUTE.set(routeSupplier.get() == null ? null : routeSupplier.get().get());
        });
        routing.register("/jersey",
                         JaxRsService.create(Config.empty(), ResourceConfig.forApplication(new JaxRsApplication())));
        routing.register("/jersey-relative",
                         JaxRsService.create(Config.empty(), ResourceConfig.forApplication(new RelativePathApplication())));
        routing.register("/jersey-trailing",
                         JaxRsService.create(Config.empty(), ResourceConfig.forApplication(new TrailingSlashPathApplication())));
    }

    @Test
    public void testEndpoint() {
        REQUEST_ROUTE.set(true);

        var response = client.get("/jersey/greet/Joe")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello Joe!"));
        assertThat(LAST_ROUTE.get(), is("/app/greet/{name}"));
    }

    @Test
    public void testRelativeApplicationAndResourcePaths() {
        REQUEST_ROUTE.set(true);

        var response = client.get("/jersey-relative/greet/Joe")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello Joe!"));
        assertThat(LAST_ROUTE.get(), is("/app/greet/{name}"));
    }

    @Test
    public void testTrailingApplicationSlashAndAbsoluteResourcePath() {
        REQUEST_ROUTE.set(true);

        var response = client.get("/jersey-trailing/greet")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello!"));
        assertThat(LAST_ROUTE.get(), is("/foo/bar/greet"));
    }

    @Test
    public void testEndpointWithoutRouteRequest() {
        REQUEST_ROUTE.set(false);

        var response = client.get("/jersey/greet/Joe")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello Joe!"));
        assertThat(LAST_ROUTE.get(), is((String) null));
    }

    @Test
    public void testUnmatchedEndpointDoesNotSetRoute() {
        REQUEST_ROUTE.set(true);

        var response = client.get("/jersey/missing")
                .request();

        assertThat(response.status(), is(Status.NOT_FOUND_404));
        assertThat(LAST_ROUTE.get(), is((String) null));
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
}
