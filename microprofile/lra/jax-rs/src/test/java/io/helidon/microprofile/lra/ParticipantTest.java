/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.lra;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.http.Status;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.lra.resources.DontEnd;
import io.helidon.microprofile.lra.resources.Work;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.webserver.http.HttpService;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.hamcrest.core.AnyOf;
import org.junit.jupiter.api.Test;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@HelidonTest
@DisableDiscovery
// Helidon MP
@AddExtension(ConfigCdiExtension.class)
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
// LRA client
@AddExtension(LraCdiExtension.class)
// Test resources
@AddBean(ParticipantTest.TestResource.class)
// Override context
@AddConfig(key = NonJaxRsResource.CONFIG_CONTEXT_PATH_KEY, value = ParticipantTest.CUSTOM_CONTEXT)
class ParticipantTest {

    private static final long TIMEOUT_SEC = 10L;
    static final String CUSTOM_CONTEXT = "custom-lra-context";

    private volatile int port = -1;

    private List<String> paths = Collections.synchronizedList(new ArrayList<>());
    private CompletableFuture<Void> completed = new CompletableFuture<>();

    @Inject
    CoordinatorClient coordinatorClient;

    @Inject
    CoordinatorLocatorService coordinatorLocatorService;

    @Produces
    @ApplicationScoped
    @RoutingPath("/lra-coordinator")
    HttpService mockCoordinator() {
        return rules -> rules
                .post("/start", (req, res) -> {
                    String lraId = URI.create("http://localhost:" + port + "/lra-coordinator/xxx-xxx-001").toASCIIString();
                    res.status(Status.CREATED_201)
                            .header(LRA_HTTP_CONTEXT_HEADER, lraId)
                            .send();
                })
                .put("/{lraId}/close", (req, res) -> {
                    res.send();
                })
                .put("/{lraId}", (req, res) -> {
                    Arrays.stream(req.content().as(String.class).split(","))
                            .map(s -> s.split(";")[0])
                            .map(s -> s
                                    .replaceAll("^<", "")
                                    .replaceAll(">$", "")
                            )
                            .map(URI::create)
                            .map(URI::getPath)
                            .forEach(paths::add);
                    res.send();
                    completed.complete(null);
                });
    }

    private void ready(
            @Observes
            @Priority(PLATFORM_AFTER + 101)
            @Initialized(ApplicationScoped.class) Object event,
            BeanManager bm) {
        port = bm.getExtension(ServerCdiExtension.class).port();
        // Provide LRA client with coordinator loadbalancer url
        coordinatorLocatorService.overrideCoordinatorUriSupplier(() ->
                URI.create("http://localhost:" + port + "/lra-coordinator"));
    }

    @Test
    void configurableContext(WebTarget target) throws Exception {
        Response response = target.path("participant-test")
                .path("start")
                .request()
                .header(Work.HEADER_KEY, Work.NOOP)
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), AnyOf.anyOf(is(200), is(204)));

        completed.get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(paths.size(), is(2));
        paths.forEach(path -> assertThat(path, startsWith("/" + CUSTOM_CONTEXT)));
    }

    @Test
    void methodScan() throws NoSuchMethodException {
        ParticipantImpl p = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                DontEnd.class);
        assertThat(p.isLraMethod(DontEnd.class.getMethod("startDontEndLRA", URI.class)), is(true));
        assertThat(p.isLraMethod(DontEnd.class.getMethod("endLRA", URI.class)), is(true));
    }

    @ApplicationScoped
    @Path("/participant-test")
    public static class TestResource {

        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW)
        @Path("/start")
        public void start(
                @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                @HeaderParam(Work.HEADER_KEY) Work work) {
            work.doWork(lraId);
        }

        @Complete
        public Response complete(URI lraId) {
            return Response.ok(ParticipantStatus.Completed.name()).build();
        }

        @Compensate
        public Response compensate(URI lraId) {
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }
}
