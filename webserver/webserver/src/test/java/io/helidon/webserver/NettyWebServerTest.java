/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.net.InetAddress;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;

import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.OptionalMatcher.present;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The NettyWebServerTest.
 */
public class NettyWebServerTest {

    private static final Logger LOGGER = Logger.getLogger(NettyWebServerTest.class.getName());

    /**
     * Start the test and then run:
     * <pre><code>
     *     seq 1000 | head -c 1000 | curl -X PUT -Ssf http://localhost:8080 --data-binary @- http://localhost:8080 --data ahoj
     * </code></pre>
     * <p>
     *
     * @throws InterruptedException if the main thread is interrupted
     */
    static void main(String[] args) throws InterruptedException {
        WebServer webServer = WebServer.builder()
                .port(8080)
                .host("localhost")
                .routing(routing((breq, bres) -> {
                    SubmissionPublisher<DataChunk> responsePublisher = new SubmissionPublisher<>(ForkJoinPool.commonPool(), 1024);
                    responsePublisher.subscribe(bres);

                    final AtomicReference<Subscription> subscription = new AtomicReference<>();

                    // Read request and immediately write to response
                    Multi.create(breq.bodyPublisher()).subscribe((DataChunk chunk) -> {
                        DataChunk responseChunk = DataChunk.create(true, chunk::release, chunk.data());
                        responsePublisher.submit(responseChunk);
                        ForkJoinPool.commonPool().submit(() -> {
                            try {
                                Thread.sleep(1);
                                subscription.get().request(ThreadLocalRandom.current().nextLong(1, 3));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }
                        });
                    }, (Throwable ex) -> {
                        LOGGER.log(Level.WARNING,
                                   "An error occurred during the flow consumption!",
                                   ex);
                    }, responsePublisher::close, (Subscription s) -> {
                        subscription.set(s);
                        s.request(1);
                        bres.writeStatusAndHeaders(Http.Status.CREATED_201,
                                                   Collections.emptyMap());
                    });
                }))
                .build();

        webServer.start();
        Thread.currentThread().join();
    }

    private static Routing routing(BiConsumer<BareRequest, BareResponse> allHandler) {
        return allHandler::accept;
    }

    @Test
    public void testShutdown() throws Exception {
        WebServer webServer = WebServer.create(
                routing((bareRequest, bareResponse) -> {
                }));

        long startNanos = System.nanoTime();
        webServer.start().toCompletableFuture().get(10, TimeUnit.SECONDS);
        long shutdownStartNanos = System.nanoTime();
        webServer.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
        long endNanos = System.nanoTime();

        System.out.println("Start took: " + TimeUnit.MILLISECONDS.convert(shutdownStartNanos - startNanos,
                                                                          TimeUnit.NANOSECONDS) + " ms.");
        System.out.println("Shutdown took: " + TimeUnit.MILLISECONDS.convert(endNanos - shutdownStartNanos,
                                                                             TimeUnit.NANOSECONDS) + " ms.");
    }

    @Test
    public void testSinglePortsSuccessStart() throws Exception {
        WebServer webServer = WebServer.create(Routing.builder());

        webServer.start()
                .toCompletableFuture()
                .join();

        try {
            assertThat(webServer.port(), greaterThan(0));
            assertThat(webServer.configuration().sockets().entrySet(), IsCollectionWithSize.hasSize(1));
            assertThat(webServer.configuration().sockets()
                               .get(WebServer.DEFAULT_SOCKET_NAME).port(), Is.is(webServer.configuration().port()));
        } finally {
            webServer.shutdown()
                    .toCompletableFuture()
                    .join();
        }
    }

    @Test
    public void testMultiplePortsSuccessStart() {
        WebServer webServer = WebServer.builder()
                .host("localhost")
                .addSocket(SocketConfiguration.create("1"))
                .addSocket(SocketConfiguration.create("2"))
                .addSocket(SocketConfiguration.create("3"))
                .addSocket(SocketConfiguration.create("4"))
                .build();

        webServer.start()
                .await();

        try {
            assertThat(webServer.port(), greaterThan(0));
            assertThat(webServer.port("1"), allOf(greaterThan(0), not(webServer.port())));
            assertThat(webServer.port("2"),
                       allOf(greaterThan(0), not(webServer.port()), not(webServer.port("1"))));
            assertThat(webServer.port("3"),
                       allOf(greaterThan(0), not(webServer.port()), not(webServer.port("1")), not(webServer.port("2"))));
            assertThat(webServer.port("4"),
                       allOf(greaterThan(0),
                             not(webServer.port()),
                             not(webServer.port("1")),
                             not(webServer.port("2")),
                             not(webServer.port("3"))));
        } finally {
            webServer.shutdown()
                    .toCompletableFuture()
                    .join();
        }
    }

    @Test
    public void testMultiplePortsAllTheSame() throws Exception {
        int samePort = 9999;
        WebServer webServer = WebServer.builder()
                .host("localhost")
                .port(samePort)
                .addSocket(SocketConfiguration.builder().port(samePort).name("third"))
                .build();

        assertStartFailure(webServer);
    }

    @Test
    public void testManyPortsButTwoTheSame() throws Exception {
        int samePort = 9999;
        WebServer webServer = WebServer.builder()
                .host("localhost")
                .port(samePort)
                .addSocket(SocketConfiguration.create("1"))
                .addSocket(SocketConfiguration.builder()
                        .name("2")
                        .port(samePort))
                .addSocket(SocketConfiguration.create("3"))
                .addSocket(SocketConfiguration.create("4"))
                .addSocket(SocketConfiguration.create("5"))
                .addSocket(SocketConfiguration.create("6"))
                .build();

        assertStartFailure(webServer);
    }

    private void assertStartFailure(WebServer webServer) {

        try {
            webServer.start()
                    .await();

            fail("Should have failed!");
        } catch (CompletionException e) {
            assertThat(e.getMessage(), containsString("WebServer was unable to start"));
            CompletableFuture<WebServer> shutdownFuture = webServer.whenShutdown().toCompletableFuture();
            assertThat("Shutdown future not as expected: " + shutdownFuture,
                       shutdownFuture.isDone() && !shutdownFuture.isCompletedExceptionally(),
                       is(true));

        } catch (Exception e) {
            fail("No other exception expected!", e);
        } finally {
            webServer.shutdown()
                    .toCompletableFuture()
                    .join();
        }
    }

    @Test
    public void unpairedRoutingCausesAFailure() throws Exception {
        try {
            WebServer webServer = WebServer.builder()
                    .host("localhost")
                    .addSocket(SocketConfiguration.create("matched"))
                    .addNamedRouting("unmatched-first", Routing.builder())
                    .addNamedRouting("matched", Routing.builder())
                    .addNamedRouting("unmatched-second", Routing.builder())
                    .build();

            fail("Should have thrown an exception: " + webServer);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), allOf(containsString("unmatched-first"),
                                             containsString("unmatched-second")));
        }
    }

    @Test
    public void additionalPairedRoutingsDoWork() {
        WebServer webServer = WebServer.builder()
                .host("localhost")
                .addSocket(SocketConfiguration.create("matched"))
                .addNamedRouting("matched", Routing.builder())
                .build();

        assertThat(webServer.configuration().namedSocket("matched"), notNullValue());
    }

    @Test
    public void additionalCoupledPairedRoutingsDoWork() {
        WebServer webServer = WebServer.builder()
                .host("localhost")
                .addSocket(SocketConfiguration.builder()
                                   .name("matched")
                                   .build(),
                           Routing.builder().build())
                .build();

        assertThat(webServer.configuration().namedSocket("matched"), present());
    }
}
