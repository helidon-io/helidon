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

package io.helidon.messaging.tests.custom.connector;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.messaging.IncomingConnectorContext;
import io.helidon.messaging.MessagingChannel;
import io.helidon.messaging.MessagingException;
import io.helidon.messaging.MessagingGraph;
import io.helidon.messaging.spi.IncomingChannel;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserveProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(30)
@Testing.Test(perMethod = true)
@Execution(ExecutionMode.SAME_THREAD)
@SuppressWarnings({"helidon:api:internal", "helidon:api:preview"})
class MessagingHealthObserverTest {
    private static final Duration WAIT = Duration.ofSeconds(5);

    private ServiceRegistryManager manager;
    private WebServer server;

    @BeforeEach
    void setUp() {
        manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, Config.empty())
                .build());
        Services.registry(manager.registry());
        // Construct the observer before any graph to verify that discovered aggregate checks remain live.
        var observer = manager.registry().get(HealthObserveProvider.class).create(Config.empty(), "health", manager.registry());
        server = WebServer.builder().port(0).addFeature(ObserveFeature.just(observer)).build();
        server.start();
    }

    @AfterEach
    void tearDown() {
        try {
            if (server != null) {
                server.stop();
            }
        } finally {
            if (manager != null) {
                manager.shutdown();
            }
        }
    }

    @Test
    void managedObserverTracksGraphsCreatedAfterIt() throws Exception {
        assertThat(status(server, "ready"), is(204));
        try (MessagingGraph graph = MessagingGraph.builder().build()) {
            assertThat(status(server, "ready"), is(503));
            assertThat(status(server, "live"), is(204));

            graph.start();
            assertThat(status(server, "ready"), is(204));
            assertThat(status(server, "live"), is(204));
        }
        assertThat(status(server, "ready"), is(204));
        assertThat(status(server, "live"), is(204));
    }

    @Test
    void managedObserverRetainsTerminalFailureUntilExplicitClose() throws Exception {
        FailingIncoming source = new FailingIncoming();
        MessagingChannel<String> channel = MessagingChannel.create("events", String.class);
        MessagingGraph graph = MessagingGraph.builder()
                .shutdownTimeout(WAIT)
                .channel(channel)
                .incomingChannel(channel, source)
                .payloadSink(channel, _ -> { })
                .build();
        try {
            graph.start();
            assertThat("Source did not start", source.running.await(WAIT.toMillis(), TimeUnit.MILLISECONDS), is(true));
            assertThat(status(server, "live"), is(204));

            source.fail.countDown();
            awaitStatus(server, "live", 503);
            assertThat(status(server, "ready"), is(503));
            assertThat(status(server, "live"), is(503));

            assertThrows(MessagingException.class, graph::close);
            assertThat(status(server, "ready"), is(204));
            assertThat(status(server, "live"), is(204));
        } finally {
            source.fail.countDown();
            try {
                graph.close();
            } catch (MessagingException _) {
                // Closing this failed graph continues to report its terminal source failure.
            }
        }
    }

    private static int status(WebServer server, String type) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + server.port()
                                                                              + "/observe/health/" + type)
                .toURL().openConnection();
        connection.setConnectTimeout((int) WAIT.toMillis());
        connection.setReadTimeout((int) WAIT.toMillis());
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private static void awaitStatus(WebServer server, String type, int expected) throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        int actual;
        do {
            actual = status(server, type);
        } while (actual != expected && System.nanoTime() < deadline);
        assertThat("Health endpoint did not reach the expected status", actual, is(expected));
    }

    private static final class FailingIncoming implements IncomingChannel {
        private final CountDownLatch running = new CountDownLatch(1);
        private final CountDownLatch fail = new CountDownLatch(1);

        @Override
        public void run(IncomingConnectorContext context) {
            if (context.awaitRunning()) {
                running.countDown();
                try {
                    fail.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                throw new IllegalStateException("Terminal source failure");
            }
        }

        @Override
        public void drain() {
            fail.countDown();
        }

        @Override
        public void forceClose() {
            fail.countDown();
        }

        @Override
        public void close() {
            fail.countDown();
        }
    }
}
