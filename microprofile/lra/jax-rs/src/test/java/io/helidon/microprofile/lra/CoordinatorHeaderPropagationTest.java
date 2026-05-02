/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import io.helidon.common.context.Contexts;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.Participant;
import io.helidon.lra.coordinator.client.PropagatedHeaders;
import io.helidon.lra.coordinator.client.narayana.NarayanaClient;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.lra.resources.Work;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Service;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.hamcrest.core.AnyOf;
import org.junit.jupiter.api.Test;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
@AddBean(CoordinatorHeaderPropagationTest.TestResourceLeave.class)
@AddBean(CoordinatorHeaderPropagationTest.TestResourceJaxRsCompensate.class)
@AddBean(CoordinatorHeaderPropagationTest.TestResourceNonJaxRsCompensate.class)
// Override context
@AddConfig(key = CoordinatorClient.CONF_KEY_COORDINATOR_HEADERS_PROPAGATION_PREFIX + ".0", value = "Xxx-tmm-")
@AddConfig(key = CoordinatorClient.CONF_KEY_COORDINATOR_HEADERS_PROPAGATION_PREFIX + ".1", value = "xbb-tmm-")
@AddConfig(key = CoordinatorClient.CONF_KEY_COORDINATOR_HEADERS_PROPAGATION_PREFIX + ".2", value = "xcc-tmm-")
@AddConfig(key = CoordinatorClient.CONF_KEY_COORDINATOR_ALLOWED_URI + ".0",
           value = "http://localhost:80/configured-coordinator")
class CoordinatorHeaderPropagationTest {

    private static final long TIMEOUT_SEC = 45L;
    private static final String PROPAGATED_HEADER = "xxx-tmm-propagated-header";
    private static final String EXTRA_COORDINATOR_PROPAGATED_HEADER = "xBb-tmm-extra-start-header";
    private static final String NOT_PROPAGATED_HEADER = "non-propagated-header";

    private static volatile int port = -1;

    private static final Map<String, List<String>> startHeadersCoordinator = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, List<String>> startHeadersParticipant = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, List<String>> secondStartHeadersParticipant = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, List<String>> thirdStartHeadersParticipant = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, List<String>> calledByCompensateHeadersParticipant = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, List<String>> leaveHeadersParticipant = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, List<String>> afterHeadersParticipant = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, List<String>> joinHeadersCoordinator = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, List<String>> closeHeadersCoordinator = Collections.synchronizedMap(new HashMap<>());
    private static final CompletableFuture<Void> completedJaxRs = new CompletableFuture<>();
    private static final CompletableFuture<Void> completedNonJaxRs = new CompletableFuture<>();
    private static final AtomicLong lraIndex = new AtomicLong();
    private static final Map<String, Map<String, URI>> lraMap = Collections.synchronizedMap(new HashMap<>());
    private static final AtomicBoolean foreignCoordinatorCalled = new AtomicBoolean();
    private static final AtomicBoolean directCoordinatorCalled = new AtomicBoolean();

    @Inject
    CoordinatorClient coordinatorClient;

    @Inject
    CoordinatorLocatorService coordinatorLocatorService;

    @Inject
    @ConfigProperty(name = CoordinatorClient.CONF_KEY_COORDINATOR_ALLOWED_URI)
    Set<String> configuredAllowedCoordinatorUris;

    @Produces
    @ApplicationScoped
    @RoutingPath("/lra-coordinator")
    Service mockCoordinator() {
        return rules -> rules
                .post("/start", (req, res) -> {
                    startHeadersCoordinator.putAll(req.headers().toMap());
                    boolean directCoordinator = req.queryParams().first("ClientID")
                            .filter("direct"::equalsIgnoreCase)
                            .isPresent();
                    String lraId = URI.create("http://localhost:"
                            + port
                            + (directCoordinator ? "/direct-coordinator/direct-" : "/lra-coordinator/xxx-xxx-")
                            + lraIndex.incrementAndGet()).toASCIIString();

                    lraMap.put(lraId, new ConcurrentHashMap<>());

                    res.status(201)
                            .addHeader(LRA_HTTP_CONTEXT_HEADER, lraId)
                            .addHeader(NOT_PROPAGATED_HEADER, "not this extra one!")
                            .addHeader(EXTRA_COORDINATOR_PROPAGATED_HEADER, "yes extra start header!")
                            .send();
                })
                .put("/{lraId}/remove", (req, res) -> {
                    //mock leave
                    res.send();
                })
                .put("/{lraId}/close", (req, res) -> {
                    closeHeadersCoordinator.putAll(req.headers().toMap());
                    String lraId = "http://localhost:" + port + "/lra-coordinator/" + req.path().param("lraId");
                    if (lraMap.get(lraId).get("complete") == null) {
                        //no complete resource
                        // after lra
                        if (lraMap.get(lraId).get("after") != null) {
                            WebClient.builder()
                                    .baseUri(lraMap.get(lraId).get("after").toASCIIString())
                                    .build()
                                    .put()
                                    .addHeader(LRA_HTTP_CONTEXT_HEADER, lraId)
                                    .headers(reqHeaders -> {
                                        // relay all incoming headers
                                        req.headers().toMap().forEach(reqHeaders::add);
                                        return reqHeaders;
                                    })
                                    .submit(LRAStatus.Closing.name())
                                    .onError(res::send)
                                    .forSingle(wcr2 -> {
                                        res.send();
                                    });
                        } else {
                            res.send();
                        }

                        return;
                    }

                    WebClient.builder()
                            .baseUri(lraMap.get(lraId).get("complete").toASCIIString())
                            .build()
                            .put()
                            .addHeader(LRA_HTTP_CONTEXT_HEADER, lraId)
                            .headers(reqHeaders -> {
                                // relay all incoming headers
                                req.headers().toMap().forEach(reqHeaders::add);
                                return reqHeaders;
                            })
                            .submit()
                            .onError(res::send)
                            .forSingle(wcr1 -> res.send());
                })
                .put("/{lraId}/cancel", (req, res) -> {
                    closeHeadersCoordinator.putAll(req.headers().toMap());
                    String lraId = "http://localhost:" + port + "/lra-coordinator/" + req.path().param("lraId");
                    WebClient.builder()
                            .baseUri(lraMap.get(lraId).get("compensate").toASCIIString())
                            .build()
                            .put()
                            .addHeader(LRA_HTTP_CONTEXT_HEADER, lraId)
                            .headers(reqHeaders -> {
                                // relay all incoming headers
                                req.headers().toMap().forEach(reqHeaders::add);
                                return reqHeaders;
                            })
                            .submit()
                            .onError(res::send)
                            .forSingle(wcResponse -> res.send());
                })
                //join
                .put("/{lraId}", (req, res) -> {
                    joinHeadersCoordinator.putAll(req.headers().toMap());
                    String lraId = "http://localhost:" + port + "/lra-coordinator/" + req.path().param("lraId");
                    req.content()
                            .as(String.class)
                            .flatMap(s -> Multi.create(Arrays.stream(s.split(","))))
                            .peek(s -> {
                                String[] split = s.split(";");
                                URI uri = URI.create(split[0]
                                        .replaceAll("^<", "")
                                        .replaceAll(">$", ""));
                                String uriType = split[1].replaceAll("rel=\"([a-z]+)\"", "$1");
                                lraMap.get(lraId).put(uriType.trim(), uri);
                            })
                            .onComplete(res::send)
                            .onError(res::send)
                            .ignoreElements();
                });
    }

    @Produces
    @ApplicationScoped
    @RoutingPath("/direct-coordinator")
    Service directCoordinator() {
        return rules -> rules
                .put("/{lraId}", (req, res) -> {
                    directCoordinatorCalled.set(true);
                    res.send();
                });
    }

    @Produces
    @ApplicationScoped
    @RoutingPath("/foreign-coordinator")
    Service foreignCoordinator() {
        return rules -> rules.any((req, res) -> {
            foreignCoordinatorCalled.set(true);
            res.status(200).send();
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
    void jaxRsHeaderPropagation(WebTarget target) throws Exception {
        headerPropagationTest(target, "headers-test-jaxrs", completedJaxRs);
    }

    @Test
    void nonJaxRsHeaderPropagation(WebTarget target) throws Exception {
        headerPropagationTest(target, "headers-test", completedNonJaxRs);
    }

    void headerPropagationTest(WebTarget target, String basePath, CompletableFuture<Void> completedFuture) throws Exception {
        reset();

        Response response = target.path(basePath)
                .path("start")
                .request()
                .header(Work.HEADER_KEY, Work.NOOP)
                .header(PROPAGATED_HEADER, "yes me!")
                .header(NOT_PROPAGATED_HEADER, "not me!")
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), AnyOf.anyOf(is(200), is(204)));

        completedFuture.get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(startHeadersParticipant, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(startHeadersParticipant, hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!")));

        assertThat(startHeadersCoordinator, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(startHeadersCoordinator, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));

        assertThat(joinHeadersCoordinator, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(joinHeadersCoordinator, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));

        assertThat(closeHeadersCoordinator, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(closeHeadersCoordinator, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));

        assertThat(secondStartHeadersParticipant, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(secondStartHeadersParticipant, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));

        assertThat(thirdStartHeadersParticipant, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(thirdStartHeadersParticipant, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));

        assertThat(calledByCompensateHeadersParticipant, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(calledByCompensateHeadersParticipant, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));

        // Headers returned by coordinator's start resource get propagated too
        assertThat(startHeadersCoordinator, hasEntry(EXTRA_COORDINATOR_PROPAGATED_HEADER, List.of("yes extra start header!")));
        assertThat(secondStartHeadersParticipant, hasEntry(EXTRA_COORDINATOR_PROPAGATED_HEADER, List.of("yes extra start header!")));
        assertThat(secondStartHeadersParticipant, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not this extra one!"))));

    }

    @Test
    void headerPropagationLeaveTest(WebTarget target) throws Exception {
        reset();

        Response response = target.path("headers-test-leave")
                .path("start")
                .request()
                .header(Work.HEADER_KEY, Work.NOOP)
                .header(PROPAGATED_HEADER, "yes me!")
                .header(NOT_PROPAGATED_HEADER, "not me!")
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), AnyOf.anyOf(is(200), is(204)));

        assertThat(startHeadersParticipant, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(startHeadersParticipant, hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!")));
        assertThat(startHeadersCoordinator, not(hasEntry(EXTRA_COORDINATOR_PROPAGATED_HEADER, List.of("yes extra start header!"))));

        assertThat(startHeadersCoordinator, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(startHeadersCoordinator, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));

        assertThat(joinHeadersCoordinator, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(joinHeadersCoordinator, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));

        assertThat(closeHeadersCoordinator, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(closeHeadersCoordinator, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));

        // test after
        assertThat(afterHeadersParticipant, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(afterHeadersParticipant, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));

        // leave mock lra, not the one created above, that ended already
        response = target.path("headers-test-leave")
                .path("leave")
                .request()
                .header(Work.HEADER_KEY, Work.NOOP)
                .header(LRA_HTTP_CONTEXT_HEADER, "http://localhost:" + port + "/lra-coordinator/xxx-xxx-0")
                .header(PROPAGATED_HEADER, "yes me!")
                .header(NOT_PROPAGATED_HEADER, "not me!")
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), AnyOf.anyOf(is(200), is(204)));

        assertThat(leaveHeadersParticipant, hasEntry(PROPAGATED_HEADER, List.of("yes me!")));
        assertThat(leaveHeadersParticipant, not(hasEntry(NOT_PROPAGATED_HEADER, List.of("not me!"))));
    }

    @Test
    void rejectForeignLraContextHeader(WebTarget target) throws Exception {
        reset();

        Response response = target.path("headers-test-jaxrs")
                .path("existing")
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, "http://localhost:" + port + "/foreign-coordinator/existing")
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), is(Response.Status.PRECONDITION_FAILED.getStatusCode()));
        assertThat(foreignCoordinatorCalled.get(), is(false));
    }

    @Test
    void rejectForeignLeaveContextHeader(WebTarget target) throws Exception {
        reset();

        Response response = target.path("headers-test-leave")
                .path("leave")
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, "http://localhost:" + port + "/foreign-coordinator/existing")
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), is(Response.Status.PRECONDITION_FAILED.getStatusCode()));
        assertThat(foreignCoordinatorCalled.get(), is(false));
    }

    @Test
    void rejectForeignNestedLraContextHeader(WebTarget target) throws Exception {
        reset();

        Response response = target.path("headers-test-jaxrs")
                .path("nested")
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, "http://localhost:" + port + "/foreign-coordinator/existing")
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), is(Response.Status.PRECONDITION_FAILED.getStatusCode()));
        assertThat(foreignCoordinatorCalled.get(), is(false));
    }

    @Test
    void rejectDotSegmentLraContextHeader(WebTarget target) throws Exception {
        reset();

        Response response = target.path("headers-test-jaxrs")
                .path("existing")
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, "http://localhost:" + port
                        + "/foreign-coordinator/%2e%2e/lra-coordinator/existing")
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), is(Response.Status.PRECONDITION_FAILED.getStatusCode()));
        assertThat(foreignCoordinatorCalled.get(), is(false));
    }

    @Test
    void rejectMalformedLraContextHeader(WebTarget target) throws Exception {
        reset();

        Response response = target.path("headers-test-jaxrs")
                .path("existing")
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, "http://[")
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), is(Response.Status.PRECONDITION_FAILED.getStatusCode()));
        assertThat(foreignCoordinatorCalled.get(), is(false));
    }

    @Test
    void bindAllowedCoordinatorUrisFromConfig() {
        assertThat(configuredAllowedCoordinatorUris, hasItem("http://localhost:80/configured-coordinator"));
        assertThat(coordinatorLocatorService.allowedCoordinatorUris(),
                   hasItem(URI.create("http://localhost:80/configured-coordinator")));
    }

    @Test
    void rejectInvalidAllowedCoordinatorUriConfig() {
        DeploymentException exception = assertThrows(DeploymentException.class,
                () -> new CoordinatorLocatorService(
                        Optional.empty(),
                        "http://localhost:" + port + "/lra-coordinator",
                        Set.of("http://["),
                        TIMEOUT_SEC,
                        TimeUnit.SECONDS)
                        .allowedCoordinatorUris());

        assertThat(exception.getMessage(), containsString(CoordinatorClient.CONF_KEY_COORDINATOR_ALLOWED_URI));
        assertThat(exception.getMessage(), containsString("http://["));
    }

    @Test
    void rejectAllowedCoordinatorUriConfigForUnsupportedClient() {
        DeploymentException exception = assertThrows(DeploymentException.class,
                () -> new CoordinatorLocatorService(
                        Optional.empty(),
                        "http://localhost:" + port + "/lra-coordinator",
                        Set.of("http://example.com/lra-coordinator"),
                        TIMEOUT_SEC,
                        TimeUnit.SECONDS)
                        .applyAllowedCoordinatorUris(new UnsupportedAllowedCoordinatorClient()));

        assertThat(exception.getMessage(), containsString(CoordinatorClient.CONF_KEY_COORDINATOR_ALLOWED_URI));
        assertThat(exception.getMessage(), containsString(UnsupportedAllowedCoordinatorClient.class.getName()));
        assertThat(exception.getMessage(), containsString("http://example.com/lra-coordinator"));
    }

    @Test
    void rejectHostlessAllowedCoordinatorUriConfigForNarayanaClient() {
        DeploymentException exception = assertThrows(DeploymentException.class,
                () -> new CoordinatorLocatorService(
                        Optional.empty(),
                        "http://localhost:" + port + "/lra-coordinator",
                        Set.of("/lra-coordinator"),
                        TIMEOUT_SEC,
                        TimeUnit.SECONDS)
                        .applyAllowedCoordinatorUris(new NarayanaClient()));

        assertThat(exception.getMessage(), containsString(CoordinatorClient.CONF_KEY_COORDINATOR_ALLOWED_URI));
        assertThat(exception.getMessage(), containsString(NarayanaClient.class.getName()));
        assertThat(exception.getMessage(), containsString("/lra-coordinator"));
    }

    @Test
    void allowConfiguredLraContextHeader() {
        reset();

        CoordinatorClient client = new CoordinatorLocatorService(
                Optional.empty(),
                "http://localhost:" + port + "/lra-coordinator",
                Set.of("http://localhost:" + port + "/foreign-coordinator"),
                TIMEOUT_SEC,
                TimeUnit.SECONDS)
                .coordinatorClient();

        client.join(URI.create("http://localhost:" + port + "/foreign-coordinator/existing"),
                    PropagatedHeaders.noop(),
                    0,
                    noopParticipant())
                .await(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(foreignCoordinatorCalled.get(), is(true));
    }

    @Test
    void allowCoordinatorReturnedFromStartResponse() {
        reset();

        URI lraId = coordinatorClient.start("direct", PropagatedHeaders.noop(), 0)
                .await(TIMEOUT_SEC, TimeUnit.SECONDS);

        coordinatorClient.join(lraId, PropagatedHeaders.noop(), 0, noopParticipant())
                .await(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(lraId, is(URI.create("http://localhost:" + port + "/direct-coordinator/direct-1")));
        assertThat(directCoordinatorCalled.get(), is(true));
    }

    @Test
    void allowTrustedNestedLraContextHeader(WebTarget target) throws Exception {
        reset();

        URI parentLraId = URI.create("http://localhost:" + port + "/lra-coordinator/parent");
        Response response = target.path("headers-test-jaxrs")
                .path("nested")
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, parentLraId.toASCIIString())
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(response.getHeaderString(LRA_HTTP_CONTEXT_HEADER), is(parentLraId.toASCIIString()));
        assertThat(lraIndex.get(), is(1L));
    }

    private static void reset() {
        lraMap.clear();
        lraIndex.set(0);
        foreignCoordinatorCalled.set(false);
        directCoordinatorCalled.set(false);
        Stream.of(
                        startHeadersCoordinator,
                        startHeadersParticipant,
                        secondStartHeadersParticipant,
                        thirdStartHeadersParticipant,
                        calledByCompensateHeadersParticipant,
                        joinHeadersCoordinator,
                        leaveHeadersParticipant,
                        afterHeadersParticipant,
                        closeHeadersCoordinator
                )
                .forEach(Map::clear);
    }

    private static Participant noopParticipant() {
        return new Participant() {
            @Override
            public Optional<URI> compensate() {
                return Optional.empty();
            }

            @Override
            public Optional<URI> complete() {
                return Optional.empty();
            }

            @Override
            public Optional<URI> forget() {
                return Optional.empty();
            }

            @Override
            public Optional<URI> leave() {
                return Optional.empty();
            }

            @Override
            public Optional<URI> after() {
                return Optional.empty();
            }

            @Override
            public Optional<URI> status() {
                return Optional.empty();
            }
        };
    }

    private static class UnsupportedAllowedCoordinatorClient implements CoordinatorClient {

        @Override
        public void init(Supplier<URI> coordinatorUriSupplier, long timeout, TimeUnit timeoutUnit) {
        }

        @Override
        public Single<URI> start(String clientID, PropagatedHeaders headers, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Single<URI> start(URI parentLRA, String clientID, PropagatedHeaders headers, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Single<Optional<URI>> join(URI lraId,
                                          PropagatedHeaders headers,
                                          long timeLimit,
                                          Participant participant) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Single<Void> cancel(URI lraId, PropagatedHeaders headers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Single<Void> close(URI lraId, PropagatedHeaders headers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Single<Void> leave(URI lraId, PropagatedHeaders headers, Participant participant) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Single<LRAStatus> status(URI lraId, PropagatedHeaders headers) {
            throw new UnsupportedOperationException();
        }
    }


    @ApplicationScoped
    @Path("/headers-test-leave")
    public static class TestResourceLeave {
        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW)
        @Path("/start")
        public void start(
                @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                @Context HttpHeaders headers,
                @HeaderParam(Work.HEADER_KEY) Work work) throws ExecutionException, InterruptedException, TimeoutException {
            startHeadersParticipant.putAll(headers.getRequestHeaders());
            work.doWork(lraId);
        }

        @Leave
        @Path("/leave")
        @PUT
        public Response leaveWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            Contexts.context()
                    .flatMap(c -> c.get(PropagatedHeaders.class.getName(), PropagatedHeaders.class))
                    .map(PropagatedHeaders::toMap)
                    .ifPresent(leaveHeadersParticipant::putAll);
            return Response.ok().build();
        }

        @AfterLRA
        public void notifyLRAFinished(URI lraId, LRAStatus status) {
            Contexts.context()
                    .flatMap(c -> c.get(PropagatedHeaders.class.getName(), PropagatedHeaders.class))
                    .map(PropagatedHeaders::toMap)
                    .ifPresent(afterHeadersParticipant::putAll);
        }
    }

    @ApplicationScoped
    @Path("/headers-test-jaxrs")
    public static class TestResourceJaxRsCompensate {
        private static final String BASE_PATH = "/headers-test-jaxrs";

        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW)
        @Path("/start")
        public void start(
                @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                @Context HttpHeaders headers,
                @HeaderParam(Work.HEADER_KEY) Work work) throws ExecutionException, InterruptedException, TimeoutException {
            startHeadersParticipant.putAll(headers.getRequestHeaders());
            work.doWork(lraId);

            ClientBuilder.newBuilder()
                    .build()
                    .target("http://localhost:" + port + BASE_PATH + "/secondStart")
                    .request()
                    .put(Entity.text(""));
        }

        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW)
        @Path("/secondStart")
        public void secondStart(
                @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                @Context HttpHeaders headers) {
            secondStartHeadersParticipant.putAll(headers.getRequestHeaders());
        }

        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW)
        @Path("/thirdStart")
        public void thirdStart(
                @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                @Context HttpHeaders headers) {
            thirdStartHeadersParticipant.putAll(headers.getRequestHeaders());
            throw new RuntimeException("BOOM -> Compensate!");
        }

        @PUT
        @LRA(value = LRA.Type.MANDATORY, end = false)
        @Path("/existing")
        public Response existingLra(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            return Response.ok().build();
        }

        @PUT
        @LRA(value = LRA.Type.NESTED, end = false)
        @Path("/nested")
        public Response nestedLra(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            return Response.ok().build();
        }

        @GET
        @LRA(value = LRA.Type.NOT_SUPPORTED)
        @Path("/calledByCompensate")
        public Response calledByCompensate(@Context HttpHeaders headers) {
            calledByCompensateHeadersParticipant.putAll(headers.getRequestHeaders());
            completedJaxRs.complete(null);
            return Response.ok().build();
        }

        @Complete
        @Path("complete")
        @PUT
        public Response complete(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            // Only first lra triggers the third
            if (lraId.toASCIIString().contains("xxx-xxx-1")) {
                ClientBuilder.newBuilder()
                        .build()
                        .target("http://localhost:" + port + BASE_PATH + "/thirdStart")
                        .request()
                        .put(Entity.text(""));
            }

            return Response.ok(ParticipantStatus.Completed.name()).build();
        }

        @Compensate
        @Path("compensate")
        @PUT
        public Response compensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            ClientBuilder.newBuilder()
                    .build()
                    .target("http://localhost:" + port + BASE_PATH + "/calledByCompensate")
                    .request()
                    .get();
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }


    @ApplicationScoped
    @Path(TestResourceNonJaxRsCompensate.BASE_PATH)
    public static class TestResourceNonJaxRsCompensate {

        private static final String BASE_PATH = "/headers-test";

        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW)
        @Path("/start")
        public void start(
                @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                @Context HttpHeaders headers,
                @HeaderParam(Work.HEADER_KEY) Work work) throws ExecutionException, InterruptedException, TimeoutException {
            startHeadersParticipant.putAll(headers.getRequestHeaders());
            work.doWork(lraId);

            ClientBuilder.newBuilder()
                    .build()
                    .target("http://localhost:" + port + BASE_PATH + "/secondStart")
                    .request()
                    .put(Entity.text(""));
        }

        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW)
        @Path("/secondStart")
        public void secondStart(
                @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                @Context HttpHeaders headers) {
            secondStartHeadersParticipant.putAll(headers.getRequestHeaders());
        }

        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW)
        @Path("/thirdStart")
        public void thirdStart(
                @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                @Context HttpHeaders headers) {
            thirdStartHeadersParticipant.putAll(headers.getRequestHeaders());
            throw new RuntimeException("BOOM -> Compensate!");
        }

        @GET
        @LRA(value = LRA.Type.NOT_SUPPORTED)
        @Path("/calledByCompensate")
        public Response calledByCompensate(@Context HttpHeaders headers) {
            calledByCompensateHeadersParticipant.putAll(headers.getRequestHeaders());
            completedNonJaxRs.complete(null);
            return Response.ok().build();
        }

        @Complete
        public Response complete(URI lraId) {
            // Only first lra triggers the third
            if (lraId.toASCIIString().contains("xxx-xxx-1")) {
                ClientBuilder.newBuilder()
                        .build()
                        .target("http://localhost:" + port + BASE_PATH + "/thirdStart")
                        .request()
                        .put(Entity.text(""));
            }

            return Response.ok(ParticipantStatus.Completed.name()).build();
        }

        @Compensate
        public Response compensate(URI lraId) {
            ClientBuilder.newBuilder()
                    .build()
                    .target("http://localhost:" + port + BASE_PATH + "/calledByCompensate")
                    .request()
                    .get();
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }
}
