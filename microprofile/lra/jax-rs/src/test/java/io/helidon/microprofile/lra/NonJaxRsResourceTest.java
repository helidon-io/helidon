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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.junit.jupiter.api.Test;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@DisableDiscovery
@AddBean(NonJaxRsResourceTest.Callbacks.class)
class NonJaxRsResourceTest {

    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final URI LRA_ID = URI.create("http://coordinator.example/lra/test");
    private static final String CLASS_NAME = Callbacks.class.getName();
    private static final String CALLBACK_TYPE = "afterlra";
    private static final String METHOD_NAME = "afterLra";

    private final BeanManager beanManager;
    private final NonJaxRsCallbackAuthenticator authenticator =
            new NonJaxRsCallbackAuthenticator(Optional.of(TEST_SECRET), false);
    private final String capability = authenticator.capability(LRA_ID, CALLBACK_TYPE, CLASS_NAME, METHOD_NAME);

    @Inject
    NonJaxRsResourceTest(BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    @Test
    void invalidAuthenticatedStatusReturnsEmptyServerError() {
        var client = client(authenticator);
        try (var response = request(client, CLASS_NAME)
                     .queryParam(NonJaxRsCallbackAuthenticator.CAPABILITY_QUERY_PARAMETER, capability)
                     .submit("invalid-status-with-private-details")) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.entity().hasEntity(), is(false));
        }
    }

    @Test
    void invalidUnsignedStatusIsRejectedInStrictMode() {
        var client = client(authenticator);
        try (var response = request(client, CLASS_NAME).submit("invalid-status-with-private-details")) {
            assertThat(response.status(), is(Status.FORBIDDEN_403));
            assertThat(response.entity().hasEntity(), is(false));
        }
    }

    @Test
    void invalidUnsignedStatusReturnsEmptyServerErrorInCompatibilityMode() {
        var compatibilityAuthenticator = new NonJaxRsCallbackAuthenticator(Optional.of(TEST_SECRET), true);
        var client = client(compatibilityAuthenticator);
        try (var response = request(client, CLASS_NAME).submit("invalid-status-with-private-details")) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.entity().hasEntity(), is(false));
        }
    }

    @Test
    void callbackFailureReturnsEmptyServerError() {
        var client = client(authenticator);
        try (var response = request(client, CLASS_NAME)
                     .queryParam(NonJaxRsCallbackAuthenticator.CAPABILITY_QUERY_PARAMETER, capability)
                     .submit(LRAStatus.Closed.name())) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.entity().hasEntity(), is(false));
        }
    }

    @Test
    void callbackWebApplicationExceptionPreservesResponse() {
        var client = client(authenticator);
        try (var response = request(client, CLASS_NAME)
                     .queryParam(NonJaxRsCallbackAuthenticator.CAPABILITY_QUERY_PARAMETER, capability)
                     .submit(LRAStatus.Cancelled.name())) {
            assertThat(response.status(), is(Status.CONFLICT_409));
            assertThat(response.headers().get(HeaderNames.create("X-Participant-Error")).get(), is("expected"));
            assertThat(response.as(String.class), is("Participant declined the callback"));
        }
    }

    @Test
    void missingParticipantPreservesNotFoundResponse() {
        String missingClass = "example.MissingParticipant";
        String missingCapability = authenticator.capability(LRA_ID, CALLBACK_TYPE, missingClass, METHOD_NAME);
        var client = client(authenticator);
        try (var response = request(client, missingClass)
                     .queryParam(NonJaxRsCallbackAuthenticator.CAPABILITY_QUERY_PARAMETER, missingCapability)
                     .submit(LRAStatus.Active.name())) {
            assertThat(response.status(), is(Status.NOT_FOUND_404));
            assertThat(response.entity().hasEntity(), is(false));
        }
    }

    @Test
    void successfulCallbackPreservesEmptyResponse() {
        var client = client(authenticator);
        try (var response = request(client, CLASS_NAME)
                     .queryParam(NonJaxRsCallbackAuthenticator.CAPABILITY_QUERY_PARAMETER, capability)
                     .submit(LRAStatus.Active.name())) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().hasEntity(), is(false));
        }
    }

    private static Http1ClientRequest request(DirectClient client, String className) {
        return client.put(NonJaxRsResource.CONTEXT_PATH_DEFAULT + "/" + CALLBACK_TYPE + "/" + className + "/" + METHOD_NAME)
                .header(HeaderNames.create(LRA_HTTP_CONTEXT_HEADER), LRA_ID.toString())
                .header(HeaderNames.ACCEPT, MediaType.APPLICATION_JSON)
                .header(HeaderNames.CONTENT_TYPE, MediaType.TEXT_PLAIN);
    }

    private DirectClient client(NonJaxRsCallbackAuthenticator callbackAuthenticator) {
        var extension = new LraCdiExtension();
        extension.lraCdiBeanReferences().put(Callbacks.class, beanManager.resolve(beanManager.getBeans(Callbacks.class)));
        var participantService = new ParticipantService(extension,
                                                        beanManager,
                                                        NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                                                        Optional.of(URI.create("http://participant.example")),
                                                        Map.of(),
                                                        Set.of(),
                                                        callbackAuthenticator);
        var resource = new NonJaxRsResource(participantService,
                                           callbackAuthenticator,
                                           NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                                           Config.empty());
        return new DirectClient(HttpRouting.builder().register(resource.contextPath(),
                                                               resource.createNonJaxRsParticipantResource()));
    }

    @ApplicationScoped
    public static class Callbacks {

        @AfterLRA
        public void afterLra(URI lraId, LRAStatus status) {
            if (status == LRAStatus.Closed) {
                throw new IllegalStateException("Private callback failure for " + lraId,
                                                new IllegalArgumentException("Private callback cause"));
            }
            if (status == LRAStatus.Cancelled) {
                throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                                                          .type(MediaType.TEXT_PLAIN_TYPE)
                                                          .header("X-Participant-Error", "expected")
                                                          .entity("Participant declined the callback")
                                                          .build());
            }
        }
    }
}
