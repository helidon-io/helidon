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

package io.helidon.microprofile.lra;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.Status;
import io.helidon.microprofile.config.ConfigCdiExtension;
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
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@DisableDiscovery
@AddExtension(ConfigCdiExtension.class)
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
@AddExtension(LraCdiExtension.class)
@AddExtension(ParticipantRuntimeValidationTest.RuntimeResourceIndexExtension.class)
@AddBean(ParticipantRuntimeValidationTest.RuntimeApplication.class)
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_URL_KEY, value = "ftp://participant.example/default")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".runtime.listener", value = "@default")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".runtime.application-path", value = "/runtime")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".runtime.url",
           value = "https://participant.example/runtime")
class ParticipantRuntimeValidationTest {

    private final AtomicInteger starts = new AtomicInteger();
    private final WebTarget target;
    private final CoordinatorLocatorService coordinatorLocatorService;
    private volatile int port;

    @Inject
    ParticipantRuntimeValidationTest(WebTarget target, CoordinatorLocatorService coordinatorLocatorService) {
        this.target = target;
        this.coordinatorLocatorService = coordinatorLocatorService;
    }

    @Produces
    @ApplicationScoped
    @RoutingPath("/lra-coordinator")
    HttpService mockCoordinator() {
        return rules -> rules.post("/start", (req, res) -> {
            starts.incrementAndGet();
            String lraId = "http://localhost:" + req.localPeer().port() + "/lra-coordinator/unexpected";
            res.status(Status.CREATED_201)
                    .header(LRA_HTTP_CONTEXT_HEADER, lraId)
                    .send();
        });
    }

    // Rule 0.1 exception to Rule 1.5: Java 21 cannot use "_" for this unused CDI event.
    void ready(@Observes
               @Priority(PLATFORM_AFTER + 101)
               @Initialized(ApplicationScoped.class) Object event,
               BeanManager beanManager) {
        port = beanManager.getExtension(ServerCdiExtension.class).port();
        coordinatorLocatorService.overrideCoordinatorUriSupplier(
                () -> URI.create("http://localhost:" + port + "/lra-coordinator"));
    }

    @Test
    void invalidDefaultUrlPreventsCoordinatorStartForProgrammaticResource() {
        RuntimeParticipant.INVOCATIONS.set(0);

        try (Response response = target.path("/runtime/participant/start")
                .request()
                .put(Entity.text(""))) {
            assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
        }
        assertThat(starts.get(), is(0));
        assertThat(RuntimeParticipant.INVOCATIONS.get(), is(0));
    }

    @ApplicationPath("/runtime")
    static class RuntimeApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(RuntimeParticipant.class);
        }
    }

    @Path("/participant")
    public static class RuntimeParticipant {

        private static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @PUT
        @Path("/start")
        @LRA(LRA.Type.REQUIRES_NEW)
        public Response start() {
            INVOCATIONS.incrementAndGet();
            return Response.ok().build();
        }

        @PUT
        @Path("/compensate")
        @Compensate
        public Response compensate() {
            return Response.ok().build();
        }
    }

    public static class RuntimeResourceIndexExtension implements Extension {

        // Rule 0.1 exception to Rule 1.5: Java 21 cannot use "_" for this unused CDI event.
        void index(@Observes AfterTypeDiscovery event, BeanManager beanManager) {
            beanManager.getExtension(LraCdiExtension.class)
                    .runtimeIndex(DotName.createSimple(RuntimeParticipant.class.getName()));
        }
    }
}
