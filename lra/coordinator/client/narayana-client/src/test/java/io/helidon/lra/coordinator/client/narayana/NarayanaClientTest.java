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

package io.helidon.lra.coordinator.client.narayana;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.faulttolerance.RetryTimeoutException;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.CoordinatorConnectionException;
import io.helidon.lra.coordinator.client.Participant;
import io.helidon.lra.coordinator.client.PropagatedHeaders;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class NarayanaClientTest {

    private static final AtomicBoolean REDIRECT_TARGET_REACHED = new AtomicBoolean();
    private static final AtomicBoolean SIBLING_TARGET_REACHED = new AtomicBoolean();
    private static final Participant PARTICIPANT = new Participant() {
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

    private static WebServer server;

    private String previousTrustedCoordinators;

    @BeforeAll
    static void startServer() {
        server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .routing(rules -> rules
                        .post("/configured/start", (req, res) -> res.status(Status.CREATED_201)
                                .header(HeaderNames.LOCATION, serverUri("/node/first") + "?token=one")
                                .send())
                        .put("/configured/redirect", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                                .header(HeaderNames.LOCATION, serverUri("/redirected/target").toASCIIString())
                                .send())
                        .put("/configured/close-redirect/close", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                                .header(HeaderNames.LOCATION, serverUri("/redirected/target").toASCIIString())
                                .send())
                        .put("/redirected/target", (req, res) -> {
                            REDIRECT_TARGET_REACHED.set(true);
                            res.status(Status.OK_200).send();
                        })
                        .get("/status", (req, res) -> {
                            SIBLING_TARGET_REACHED.set(true);
                            res.send(LRAStatus.Active.name());
                        })
                        .get("/{coordinator}/{lraId}/status", (req, res) -> res.send(LRAStatus.Active.name())))
                .build()
                .start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void saveTrustedCoordinatorConfig() {
        previousTrustedCoordinators = System.getProperty(CoordinatorClient.CONF_KEY_COORDINATOR_TRUSTED_URLS);
        System.clearProperty(CoordinatorClient.CONF_KEY_COORDINATOR_TRUSTED_URLS);
        REDIRECT_TARGET_REACHED.set(false);
        SIBLING_TARGET_REACHED.set(false);
    }

    @AfterEach
    void restoreTrustedCoordinatorConfig() {
        if (previousTrustedCoordinators == null) {
            System.clearProperty(CoordinatorClient.CONF_KEY_COORDINATOR_TRUSTED_URLS);
        } else {
            System.setProperty(CoordinatorClient.CONF_KEY_COORDINATOR_TRUSTED_URLS, previousTrustedCoordinators);
        }
    }

    @Test
    void configuredCoordinatorIsTrusted() {
        NarayanaClient client = client(URI.create(serverUri("/configured").toASCIIString().replace("http", "HTTP")));

        LRAStatus status = client.status(
                URI.create(serverUri("/configured/id").toASCIIString().replace("http", "HTTP") + "?ClientID=example"),
                PropagatedHeaders.noop());

        assertThat(status, is(LRAStatus.Active));
    }

    @Test
    void additionalTrustedCoordinatorsAreLoadedFromConfig() {
        System.setProperty(CoordinatorClient.CONF_KEY_COORDINATOR_TRUSTED_URLS,
                           serverUri("/unused") + ", " + serverUri("/trusted"));
        NarayanaClient client = client(serverUri("/configured"));

        LRAStatus status = client.status(serverUri("/trusted/id"), PropagatedHeaders.noop());

        assertThat(status, is(LRAStatus.Active));
    }

    @Test
    void successfulStartTrustsOnlyReturnedLraId() {
        NarayanaClient client = client(serverUri("/configured"));

        URI lraId = client.start("client", PropagatedHeaders.noop(), 0);

        assertThat(client.status(lraId, PropagatedHeaders.noop()), is(LRAStatus.Active));
        assertRejected(client, serverUri("/node/second").toASCIIString());
        assertRejected(client, serverUri("/node/first") + "?token=two");
    }

    @Test
    void coordinatorRedirectsAreNotFollowed() {
        NarayanaClient client = client(serverUri("/configured"));

        CoordinatorConnectionException exception = assertConnectionException(
                () -> client.join(serverUri("/configured/redirect"), PropagatedHeaders.noop(), 0, PARTICIPANT));

        assertThat(exception.status(), is(Status.TEMPORARY_REDIRECT_307.code()));
        assertThat(REDIRECT_TARGET_REACHED.get(), is(false));
    }

    @Test
    void closeRedirectsAreNotTreatedAsSuccess() {
        NarayanaClient client = client(serverUri("/configured"));

        CoordinatorConnectionException exception = assertConnectionException(
                () -> client.close(serverUri("/configured/close-redirect"), PropagatedHeaders.noop()));

        assertThat(exception.status(), is(Status.TEMPORARY_REDIRECT_307.code()));
        assertThat(REDIRECT_TARGET_REACHED.get(), is(false));
    }

    @Test
    void configuredCoordinatorSupplierTrustsOnlyCurrentBase() {
        AtomicReference<URI> configuredCoordinator = new AtomicReference<>(serverUri("/configured"));
        NarayanaClient client = new NarayanaClient();
        client.init(configuredCoordinator::get, Duration.ofSeconds(1));

        assertThat(client.status(serverUri("/configured/id"), PropagatedHeaders.noop()), is(LRAStatus.Active));
        URI learnedLraId = client.start("client", PropagatedHeaders.noop(), 0);

        configuredCoordinator.set(serverUri("/trusted"));

        assertThat(client.status(serverUri("/trusted/id"), PropagatedHeaders.noop()), is(LRAStatus.Active));
        assertThat(client.status(learnedLraId, PropagatedHeaders.noop()), is(LRAStatus.Active));
        assertRejected(client, serverUri("/configured/other").toASCIIString());
    }

    @Test
    void configuredCoordinatorTrustsOnlyImmediateLraChildren() {
        NarayanaClient client = client(serverUri("/configured"));

        assertThat(client.status(serverUri("/configured/id"), PropagatedHeaders.noop()), is(LRAStatus.Active));
        assertRejected(client, serverUri("/configured/alternate/id").toASCIIString());
        assertRejected(client, serverUri("/configured-other/id").toASCIIString());
    }

    @Test
    void untrustedCoordinatorComponentsAreRejected() {
        NarayanaClient client = client(serverUri("/configured"));

        assertRejected(client, "https://127.0.0.1:" + server.port() + "/configured/id");
        assertRejected(client, "http://localhost:" + server.port() + "/configured/id");
        assertRejected(client, "http://127.0.0.1:1/configured/id");
    }

    @Test
    void ambiguousCoordinatorUrisAreRejected() {
        NarayanaClient client = client(serverUri("/configured"));

        assertRejected(client, "relative/lra/id");
        assertRejected(client, "file:///tmp/lra/id");
        assertRejected(client, "http://user@127.0.0.1:" + server.port() + "/configured/id");
        assertRejected(client, serverUri("/configured/id") + "#fragment");
        assertRejected(client, serverUri("/configured/../admin/id").toASCIIString());
        assertRejected(client, serverUri("/configured/%2e%2e/admin/id").toASCIIString());
        assertRejected(client, serverUri("/configured%2fid").toASCIIString());
        assertRejected(client, serverUri("/configured%5cid").toASCIIString());
        assertRejected(client, serverUri("/configured/..%3bignored").toASCIIString());
        assertRejected(client, serverUri("/configured").toASCIIString());
        assertRejected(client, serverUri("/configured/id/").toASCIIString());
        assertThat(SIBLING_TARGET_REACHED.get(), is(false));
    }

    @Test
    void ipv6AddressHexDigitsAreCaseInsensitive() {
        NarayanaClient client = client(URI.create("http://[2001:DB8::1]:9/coordinator"));

        CoordinatorConnectionException exception = assertConnectionException(
                () -> client.status(URI.create("http://[2001:db8::1]:9/coordinator/id"), PropagatedHeaders.noop()));

        assertThat(exception.status(), is(500));
    }

    @Test
    void invalidExplicitCoordinatorBaseIsRejectedAtInitialization() {
        System.setProperty(CoordinatorClient.CONF_KEY_COORDINATOR_TRUSTED_URLS, "file:///tmp/lra");
        NarayanaClient client = new NarayanaClient();

        assertThrows(IllegalArgumentException.class,
                     () -> client.init(() -> serverUri("/configured"), Duration.ofSeconds(1)));
    }

    @Test
    void invalidCurrentCoordinatorBaseIsRejectedAtInitialization() {
        NarayanaClient client = new NarayanaClient();

        assertThrows(IllegalArgumentException.class,
                     () -> client.init(() -> serverUri("/configured;matrix"), Duration.ofSeconds(1)));
    }

    @Test
    void invalidDynamicCurrentCoordinatorBaseFailsClosed() {
        AtomicReference<URI> configuredCoordinator = new AtomicReference<>(serverUri("/configured"));
        NarayanaClient client = new NarayanaClient();
        client.init(configuredCoordinator::get, Duration.ofSeconds(1));

        configuredCoordinator.set(serverUri("/configured;matrix"));

        CoordinatorConnectionException exception = assertThrows(CoordinatorConnectionException.class,
                                                                () -> client.status(serverUri("/configured/id"),
                                                                                    PropagatedHeaders.noop()));

        assertThat(exception.status(), is(500));
    }

    private static NarayanaClient client(URI coordinator) {
        NarayanaClient client = new NarayanaClient();
        client.init(() -> coordinator, Duration.ofSeconds(1));
        return client;
    }

    private static URI serverUri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private static CoordinatorConnectionException assertConnectionException(Executable executable) {
        RuntimeException exception = assertThrows(RuntimeException.class, executable);
        Throwable cause = exception instanceof RetryTimeoutException ? exception.getCause() : exception;
        assertThat(cause, instanceOf(CoordinatorConnectionException.class));
        return (CoordinatorConnectionException) cause;
    }

    private static void assertRejected(NarayanaClient client, String uri) {
        CoordinatorConnectionException exception = assertThrows(CoordinatorConnectionException.class,
                                                                 () -> client.status(URI.create(uri),
                                                                                     PropagatedHeaders.noop()));
        assertThat(exception.status(), is(412));
        assertThat(exception.getMessage(), is("Untrusted LRA coordinator"));
    }
}
