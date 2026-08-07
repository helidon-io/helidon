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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.AddJaxRs;
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
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@HelidonTest
@DisableDiscovery
@AddJaxRs
@AddExtension(ConfigCdiExtension.class)
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
@AddExtension(LraCdiExtension.class)
@AddBean(LongRunningActionHeaderTargetTest.LeaveParticipantResource.class)
@AddBean(LongRunningActionHeaderTargetTest.EndParticipantResource.class)
@AddConfig(key = NonJaxRsCallbackAuthenticator.CONFIG_SECRET_KEY, value = ParticipantTest.TEST_CALLBACK_SECRET)
@AddConfig(key = ParticipantService.CONFIG_PARTICIPANT_URL_KEY, value = "http://localhost:0")
class LongRunningActionHeaderTargetTest {

    private static final long TIMEOUT_SECONDS = 20L;
    private static final String CONTROLLED_LRA_ID = "controlled-lra";
    private static final String JOIN_REDIRECT_LRA_ID = "join-redirect";
    private static final String CLOSE_REDIRECT_LRA_ID = "close-redirect";
    private static final String CONTROLLED_ROUTE = "/controlled-coordinator";
    private static final String TRUSTED_ROUTE = "/trusted-coordinator";
    private static final String PARTICIPANT_PATH = "header-target-participant/leave";
    private static final String END_PARTICIPANT_PATH = "header-target-participant/end";

    private static volatile int port = -1;
    private static volatile CompletableFuture<Void> observedRequest = new CompletableFuture<>();
    private static final AtomicBoolean PARTICIPANT_INVOKED = new AtomicBoolean();

    @Inject
    CoordinatorLocatorService coordinatorLocatorService;

    @BeforeEach
    void resetState() {
        observedRequest = new CompletableFuture<>();
        PARTICIPANT_INVOKED.set(false);
        coordinatorLocatorService.overrideCoordinatorUriSupplier(() ->
                URI.create("http://localhost:" + port + TRUSTED_ROUTE));
    }

    @Produces
    @ApplicationScoped
    @RoutingPath(CONTROLLED_ROUTE)
    HttpService controlledCoordinator() {
        return rules -> rules.put("/{lraId}", (req, res) -> {
            req.content().as(String.class);
            observedRequest.complete(null);

            res.status(Status.OK_200).send();
        });
    }

    @Produces
    @ApplicationScoped
    @RoutingPath(TRUSTED_ROUTE)
    HttpService redirectingCoordinator() {
        return rules -> rules.put("/" + JOIN_REDIRECT_LRA_ID, (req, res) -> {
            req.content().as(String.class);
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderValues.create(HeaderNames.LOCATION,
                                                "http://localhost:" + port + CONTROLLED_ROUTE + "/"
                                                        + CONTROLLED_LRA_ID))
                    .send();
        }).put("/" + CLOSE_REDIRECT_LRA_ID, (req, res) -> {
            req.content().as(String.class);
            res.status(Status.OK_200).send();
        }).put("/" + CLOSE_REDIRECT_LRA_ID + "/close", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderValues.create(HeaderNames.LOCATION,
                                                "http://localhost:" + port + CONTROLLED_ROUTE + "/"
                                                        + CONTROLLED_LRA_ID))
                    .send();
        });
    }

    private void ready(
            @Observes
            @Priority(PLATFORM_AFTER + 101)
            @Initialized(ApplicationScoped.class) Object event,
            BeanManager beanManager) {
        port = beanManager.getExtension(ServerCdiExtension.class).port();
        coordinatorLocatorService.overrideCoordinatorUriSupplier(() ->
                URI.create("http://localhost:" + port + TRUSTED_ROUTE));
    }

    @Test
    void untrustedLongRunningActionHeaderIsRejected(WebTarget target) throws Exception {
        String controlledLra = "http://localhost:" + port + CONTROLLED_ROUTE + "/" + CONTROLLED_LRA_ID;

        Response response = target.path(PARTICIPANT_PATH)
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, controlledLra)
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(response.getStatus(), is(Response.Status.PRECONDITION_FAILED.getStatusCode()));
        assertThat(observedRequest.isDone(), is(false));
        assertThat(PARTICIPANT_INVOKED.get(), is(false));
    }

    @Test
    void coordinatorRedirectIsNotFollowed(WebTarget target) throws Exception {
        String trustedLra = "http://localhost:" + port + TRUSTED_ROUTE + "/" + JOIN_REDIRECT_LRA_ID;

        Response response = target.path(PARTICIPANT_PATH)
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, trustedLra)
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(response.getStatus(), is(Status.TEMPORARY_REDIRECT_307.code()));
        assertThat(observedRequest.isDone(), is(false));
        assertThat(PARTICIPANT_INVOKED.get(), is(false));
    }

    @Test
    void closeRedirectIsPropagatedFromLraEnd(WebTarget target) throws Exception {
        String trustedLra = "http://localhost:" + port + TRUSTED_ROUTE + "/" + CLOSE_REDIRECT_LRA_ID;

        Response response = target.path(END_PARTICIPANT_PATH)
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, trustedLra)
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(response.getStatus(), is(Response.Status.BAD_GATEWAY.getStatusCode()));
        assertThat(response.getHeaderString(LRA_HTTP_CONTEXT_HEADER), is(trustedLra));
        assertThat(response.getHeaderString(HttpHeaders.LOCATION), nullValue());
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), nullValue());
        assertThat(response.getMediaType(), is(MediaType.TEXT_PLAIN_TYPE));
        assertThat(observedRequest.isDone(), is(false));
        assertThat(PARTICIPANT_INVOKED.get(), is(true));
    }

    @ApplicationScoped
    @Path(PARTICIPANT_PATH)
    public static class LeaveParticipantResource {

        @LRA(value = LRA.Type.MANDATORY)
        @PUT
        public Response leave(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            PARTICIPANT_INVOKED.set(true);
            return Response.ok(lraId.toASCIIString()).build();
        }

        @AfterLRA
        public void afterLra(URI lraId, LRAStatus status) {
        }
    }

    @ApplicationScoped
    @Path(END_PARTICIPANT_PATH)
    public static class EndParticipantResource {

        @LRA(value = LRA.Type.MANDATORY, end = true)
        @PUT
        public Response end(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            PARTICIPANT_INVOKED.set(true);
            return Response.created(URI.create("https://example.invalid/resource-location"))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                    .entity(lraId.toASCIIString())
                    .build();
        }

        @AfterLRA
        public void afterLra(URI lraId, LRAStatus status) {
        }
    }
}
