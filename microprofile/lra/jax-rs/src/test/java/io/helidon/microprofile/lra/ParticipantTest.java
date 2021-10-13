/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.lra;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.common.reactive.Multi;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.lra.resources.DontEnd;
import io.helidon.microprofile.lra.resources.Work;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.webserver.Service;

import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.hamcrest.core.AnyOf;
import org.junit.jupiter.api.Test;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    Service mockCoordinator() {
        return rules -> rules
                .post("/start", (req, res) -> {
                    String lraId = URI.create("http://localhost:" + port + "/lra-coordinator/xxx-xxx-001").toASCIIString();
                    res.status(201)
                            .addHeader(LRA_HTTP_CONTEXT_HEADER, lraId)
                            .send();
                })
                .put("/{lraId}/close", (req, res) -> {
                    res.send();
                })
                .put("/{lraId}", (req, res) -> {
                    req.content()
                            .as(String.class)
                            .flatMap(s -> Multi.create(Arrays.stream(s.split(","))))
                            .map(s -> s.split(";")[0])
                            .map(s -> s
                                    .replaceAll("^<", "")
                                    .replaceAll(">$", "")
                            )
                            .map(URI::create)
                            .map(URI::getPath)
                            .onComplete(res::send)
                            .onComplete(() -> completed.complete(null))
                            .forEach(paths::add)
                            .exceptionally(res::send);
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
        assertTrue(p.isLraMethod(DontEnd.class.getMethod("startDontEndLRA", URI.class)));
        assertTrue(p.isLraMethod(DontEnd.class.getMethod("endLRA", URI.class)));
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
