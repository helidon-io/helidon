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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.Status;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.Socket;
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
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.Test;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@DisableDiscovery
@AddExtension(ConfigCdiExtension.class)
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
@AddExtension(LraCdiExtension.class)
@AddBean(ParticipantEndpointTest.ApplicationOne.class)
@AddBean(ParticipantEndpointTest.ApplicationTwo.class)
@AddBean(ParticipantEndpointTest.EncodedApplication.class)
@AddBean(ParticipantEndpointTest.NamedApplicationOne.class)
@AddBean(ParticipantEndpointTest.UnmappedApplication.class)
@AddBean(ParticipantEndpointTest.ApplicationOneParticipant.class)
@AddBean(ParticipantEndpointTest.ApplicationTwoParticipant.class)
@AddBean(ParticipantEndpointTest.EncodedParticipant.class)
@AddBean(ParticipantEndpointTest.NamedParticipant.class)
@AddBean(ParticipantEndpointTest.UnmappedParticipant.class)
@AddBean(ParticipantEndpointTest.AliasedParticipant.class)
@AddBean(ParticipantEndpointTest.NonJaxRsParticipant.class)
@AddConfig(key = "server.sockets.0.name", value = ParticipantEndpointTest.NAMED_LISTENER)
@AddConfig(key = "server.sockets.0.port", value = "0")
@AddConfig(key = "server.sockets.0.host", value = "localhost")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_URL_KEY, value = "http://default.example:0/default")
@AddConfig(key = NonJaxRsCallbackAuthenticator.CONFIG_SECRET_KEY, value = ParticipantTest.TEST_CALLBACK_SECRET)
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".default-one.listener", value = "@default")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".default-one.application-path",
           value = "/application-one")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".default-one.url",
           value = "http://first.example:0/external-one")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".default-two.listener", value = "@default")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".default-two.application-path",
           value = "/application-two")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".default-two.url",
           value = "https://second.example:0/external-two")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".named-one.listener",
           value = ParticipantEndpointTest.NAMED_LISTENER)
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".named-one.application-path",
           value = "/application-one")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".named-one.url",
           value = "https://third.example:0/proxy%2Fthree")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".encoded.listener", value = "@default")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".encoded.application-path",
           value = "/encoded%21")
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".encoded.url",
           value = "https://encoded.example:0/external%21")
class ParticipantEndpointTest {

    static final String NAMED_LISTENER = "participant-listener";

    private static final long TIMEOUT_SECONDS = 10;

    private final BlockingQueue<String> joins = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> leaves = new LinkedBlockingQueue<>();
    private final AtomicInteger starts = new AtomicInteger();
    private final WebTarget defaultTarget;
    private final WebTarget namedTarget;
    private final CoordinatorLocatorService coordinatorLocatorService;
    private volatile int port;

    @Inject
    ParticipantEndpointTest(WebTarget defaultTarget,
                            @Socket(NAMED_LISTENER) WebTarget namedTarget,
                            CoordinatorLocatorService coordinatorLocatorService) {
        this.defaultTarget = defaultTarget;
        this.namedTarget = namedTarget;
        this.coordinatorLocatorService = coordinatorLocatorService;
    }

    @Produces
    @ApplicationScoped
    @RoutingPath("/lra-coordinator")
    HttpService mockCoordinator() {
        return rules -> rules
                .post("/start", (req, res) -> {
                    int id = starts.incrementAndGet();
                    String lraId = "http://localhost:" + req.localPeer().port() + "/lra-coordinator/lra-" + id;
                    res.status(Status.CREATED_201)
                            .header(LRA_HTTP_CONTEXT_HEADER, lraId)
                            .send();
                })
                .put("/{lraId}/close", (req, res) -> {
                    // Rule 0.1 exception to Rule 1.5: Java 21 cannot use "_" for this unused request.
                    res.send();
                })
                .put("/{lraId}", (req, res) -> {
                    joins.add(req.content().as(String.class));
                    res.send();
                })
                .put("/{lraId}/remove", (req, res) -> {
                    leaves.add(req.content().as(String.class));
                    res.send();
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
    void selectsExactApplicationAndListenerEndpoints() throws InterruptedException {
        SharedParticipant.INVOCATIONS.set(0);
        starts.set(0);
        joins.clear();
        leaves.clear();

        String firstJoin = invoke(defaultTarget, "/application-one");
        String secondJoin = invoke(defaultTarget, "/application-two");
        String namedJoin = invoke(namedTarget, "/application-one");
        String encodedJoin = invoke(defaultTarget, "/encoded!");

        int defaultPort = defaultTarget.getUri().getPort();
        int namedPort = namedTarget.getUri().getPort();
        assertThat(firstJoin,
                   containsString("http://first.example:" + defaultPort
                                          + "/external-one/participant/compensate"));
        assertThat(secondJoin,
                   containsString("https://second.example:" + defaultPort
                                          + "/external-two/participant/compensate"));
        assertThat(namedJoin,
                   containsString("https://third.example:" + namedPort
                                          + "/proxy%2Fthree/participant/compensate"));
        assertThat(encodedJoin,
                   containsString("https://encoded.example:" + defaultPort
                                          + "/external%21/participant/compensate"));
        assertThat(SharedParticipant.INVOCATIONS.get(), is(4));

        try (Response response = defaultTarget.path("/unmapped/participant/start")
                .request()
                .put(Entity.text(""))) {
            assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
        }
        assertThat(starts.get(), is(4));
        assertThat(SharedParticipant.INVOCATIONS.get(), is(4));
        assertThat(joins, is(empty()));
    }

    @Test
    void nonJaxRsCallbacksDoNotRequireJaxRsEndpoint() throws InterruptedException {
        joins.clear();

        try (Response response = defaultTarget.path("/unmapped/non-jax-rs/start")
                .request()
                .put(Entity.text(""))) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        }

        String join = joins.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(join, is(notNullValue()));
        assertThat(join,
                   containsString("http://default.example:" + defaultTarget.getUri().getPort()
                                          + "/default/lra-participant/compensate/"
                                          + NonJaxRsParticipant.class.getName()
                                          + "/compensate"));
    }

    @Test
    void participantLinksUseMappedRequestEndpointAndCachedParticipantStillValidatesRequestEndpoint()
            throws InterruptedException {
        joins.clear();
        leaves.clear();
        URI secondLraId = URI.create("http://localhost:" + port + "/lra-coordinator/second");
        URI firstLraId = URI.create("http://localhost:" + port + "/lra-coordinator/first");

        String secondJoin = invoke(defaultTarget, "/application-two/alias/join", secondLraId, joins);
        String secondLeave = invoke(defaultTarget, "/application-two/alias/leave", secondLraId, leaves);
        String firstJoin = invoke(defaultTarget, "/application-one/alias/join", firstLraId, joins);
        String firstLeave = invoke(defaultTarget, "/application-one/alias/leave", firstLraId, leaves);

        assertThat(secondLeave, is(secondJoin));
        assertThat(secondJoin,
                   containsString("https://second.example:" + defaultTarget.getUri().getPort()
                                          + "/external-two/alias/compensate"));
        assertThat(secondJoin,
                   containsString("https://second.example:" + defaultTarget.getUri().getPort()
                                          + "/external-two/alias/leave"));
        assertThat(firstLeave, is(firstJoin));
        assertThat(firstJoin,
                   containsString("http://first.example:" + defaultTarget.getUri().getPort()
                                          + "/external-one/alias/compensate"));
        assertThat(firstJoin,
                   containsString("http://first.example:" + defaultTarget.getUri().getPort()
                                          + "/external-one/alias/leave"));

        joins.clear();
        try (Response response = defaultTarget.path("/unmapped/alias/join")
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, firstLraId.toASCIIString())
                .put(Entity.text(""))) {
            assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
        }
        assertThat(joins, is(empty()));
    }

    private String invoke(WebTarget target, String applicationPath) throws InterruptedException {
        try (Response response = target.path(applicationPath + "/participant/start")
                .request()
                .put(Entity.text(""))) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        }
        String join = joins.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(join, is(notNullValue()));
        return join;
    }

    private String invoke(WebTarget target,
                          String path,
                          URI lraId,
                          BlockingQueue<String> coordinatorRequests) throws InterruptedException {
        try (Response response = target.path(path)
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString())
                .put(Entity.text(""))) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        }
        String request = coordinatorRequests.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(request, is(notNullValue()));
        return request;
    }

    @ApplicationPath("/application-one")
    static class ApplicationOne extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(ApplicationOneParticipant.class, AliasedParticipant.class);
        }
    }

    @ApplicationPath("/application-two")
    static class ApplicationTwo extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(ApplicationTwoParticipant.class, AliasedParticipant.class);
        }
    }

    @ApplicationPath("/application-one")
    @RoutingName(value = NAMED_LISTENER, required = true)
    static class NamedApplicationOne extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(NamedParticipant.class);
        }
    }

    @ApplicationPath("/encoded%21")
    static class EncodedApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(EncodedParticipant.class);
        }
    }

    @ApplicationPath("/unmapped")
    static class UnmappedApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(UnmappedParticipant.class, NonJaxRsParticipant.class, AliasedParticipant.class);
        }
    }

    public static class SharedParticipant {

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

    @ApplicationScoped
    @Path("/participant")
    public static class ApplicationOneParticipant extends SharedParticipant {
    }

    @ApplicationScoped
    @Path("/participant")
    public static class ApplicationTwoParticipant extends SharedParticipant {
    }

    @ApplicationScoped
    @Path("/participant")
    public static class NamedParticipant extends SharedParticipant {
    }

    @ApplicationScoped
    @Path("/participant")
    public static class EncodedParticipant extends SharedParticipant {
    }

    @ApplicationScoped
    @Path("/participant")
    public static class UnmappedParticipant extends SharedParticipant {
    }

    @ApplicationScoped
    @Path("/alias")
    public static class AliasedParticipant {

        @PUT
        @Path("/join")
        @LRA(value = LRA.Type.MANDATORY, end = false)
        public Response join() {
            return Response.ok().build();
        }

        @PUT
        @Path("/compensate")
        @Compensate
        public Response compensate() {
            return Response.ok().build();
        }

        @PUT
        @Path("/leave")
        @Leave
        public Response leave() {
            return Response.ok().build();
        }
    }

    @ApplicationScoped
    @Path("/non-jax-rs")
    public static class NonJaxRsParticipant {

        @PUT
        @Path("/start")
        @LRA(LRA.Type.REQUIRES_NEW)
        public Response start() {
            return Response.ok().build();
        }

        @Compensate
        public Response compensate() {
            return Response.ok().build();
        }
    }
}
