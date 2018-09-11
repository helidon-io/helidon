/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.ReactiveStreamsAdapter;
import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.SocketConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.BareRequest;
import io.helidon.webserver.spi.BareResponse;

import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
     * To show how the request body is handled in chunks, do<ol>
     * <li>Add {@code Thread.sleap(1000) to {@link BaseSubscriber#hookOnNext(Object)}} bellow</li>
     * <li>And then disable request chunks by enabling {@link io.netty.handler.codec.http.HttpObjectAggregator} in
     * {@link HttpInitializer#initChannel(io.netty.channel.socket.SocketChannel)}</li>
     * </ol>
     *
     * @throws InterruptedException if the main thread is interrupted
     */
    static void main(String[] args) throws InterruptedException {
        WebServer webServer = WebServer.create(
                configuration(8080, InetAddress.getLoopbackAddress()),
                routing((breq, bres) -> {
                    long id = new SecureRandom().nextLong();
                    System.out.println("Received request .. ID: " + id);

                    Flux<DataChunk> publisher = ReactiveStreamsAdapter.publisherFromFlow(breq.bodyPublisher());

                    SubmissionPublisher<DataChunk> responsePublisher = new SubmissionPublisher<>(ForkJoinPool.commonPool(),
                                                                                                 1024);
                    responsePublisher.subscribe(bres);

                    // Read request and immediately write to response
                    publisher.subscribe(new BaseSubscriber<DataChunk>() {

                        private volatile Subscription subscription;

                        @Override
                        protected void hookOnSubscribe(Subscription subscription) {
                            System.out.println("Subscribe");
                            this.subscription = subscription;

                            request(1L);

                            bres.writeStatusAndHeaders(Http.Status.CREATED_201, Collections.emptyMap());
                        }

                        @Override
                        protected void hookOnNext(DataChunk chunk) {
                            DataChunk responseChunk = DataChunk.create(true, chunk.data(), chunk::release);
                            responsePublisher.submit(responseChunk);

                            ForkJoinPool.commonPool().submit(() -> {
                                try {
                                    Thread.sleep(1);
                                    subscription.request(ThreadLocalRandom.current().nextLong(1, 3));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(e);
                                }
                            });

                        }

                        @Override
                        protected void hookFinally(SignalType type) {
                            System.out.println("Final execution");
                            responsePublisher.close();
                        }

                        @Override
                        protected void hookOnError(Throwable throwable) {
                            LOGGER.log(Level.WARNING, "An error occurred during the flow consumption!", throwable);
                        }
                    });
                }));

        webServer.start();

        Thread.currentThread().join();
    }

    private static Routing routing(BiConsumer<BareRequest, BareResponse> allHandler) {
        return allHandler::accept;
    }

    private static ServerConfiguration configuration(int port, InetAddress inetAddress) {
        return ServerConfiguration.builder()
                                  .port(port)
                                  .bindAddress(inetAddress)
                                  .build();
    }

    @Test
    public void testShutdown() throws Exception {
        WebServer webServer = WebServer.create(routing((bareRequest, bareResponse) -> {
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
            assertThat(webServer.configuration().sockets().get(ServerConfiguration.DEFAULT_SOCKET_NAME).port(),
                       Is.is(webServer.configuration().port()));
        } finally {
            webServer.shutdown()
                     .toCompletableFuture()
                     .join();
        }
    }

    @Test
    public void testMultiplePortsSuccessStart() throws Exception {
        WebServer webServer = WebServer.create(ServerConfiguration.builder()
                                                                  .addSocket("1", (SocketConfiguration) null)
                                                                  .addSocket("2", (SocketConfiguration) null)
                                                                  .addSocket("3", (SocketConfiguration) null)
                                                                  .addSocket("4", (SocketConfiguration) null),
                                               Routing.builder());

        webServer.start()
                 .toCompletableFuture()
                 .join();

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
        WebServer webServer = WebServer.create(
                ServerConfiguration.builder()
                                   .port(samePort)
                                   .addSocket("third", SocketConfiguration.builder().port(samePort)),
                Routing.builder());

        assertStartFailure(webServer);
    }

    @Test
    public void testManyPortsButTwoTheSame() throws Exception {
        int samePort = 9999;
        WebServer webServer = WebServer.create(
                ServerConfiguration.builder()
                                   .port(samePort)
                                   .addSocket("1", (SocketConfiguration) null)
                                   .addSocket("2", SocketConfiguration.builder().port(samePort))
                                   .addSocket("3", (SocketConfiguration) null)
                                   .addSocket("4", (SocketConfiguration) null)
                                   .addSocket("5", (SocketConfiguration) null)
                                   .addSocket("6", (SocketConfiguration) null),
                Routing.builder());

        assertStartFailure(webServer);
    }

    private void assertStartFailure(WebServer webServer) throws InterruptedException {
        try {
            webServer.start()
                     .toCompletableFuture()
                     .join();

            fail("Should have failed!");
        } catch (CompletionException e) {
            assertThat(e.getMessage(), containsString("WebServer was unable to start"));
            CompletableFuture<WebServer> shutdownFuture = webServer.whenShutdown().toCompletableFuture();
            assertTrue(shutdownFuture.isDone() && !shutdownFuture.isCompletedExceptionally(),
                    "Shutdown future not as expected: " + shutdownFuture);
        } catch (Exception e) {
            e.printStackTrace();
            fail("No other exception expected!");
        } finally {
            webServer.shutdown()
                     .toCompletableFuture()
                     .join();
        }
    }

    @Test
    public void unpairedRoutingCausesAFailure() throws Exception {
        try {
            WebServer webServer = WebServer.builder(Routing.builder())
                                           .configuration(ServerConfiguration.builder()
                                                                             .addSocket("matched", SocketConfiguration.builder()))
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
    public void additionalPairedRoutingsDoWork() throws Exception {
        WebServer webServer = WebServer.builder(Routing.builder())
                                       .configuration(ServerConfiguration.builder()
                                                                         .addSocket("matched", SocketConfiguration.builder()))
                                       .addNamedRouting("matched", Routing.builder())
                                       .build();

        assertNotNull(webServer.configuration().socket("matched"));
    }
}
