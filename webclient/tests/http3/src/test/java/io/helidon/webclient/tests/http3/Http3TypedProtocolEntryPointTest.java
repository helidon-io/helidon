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

package io.helidon.webclient.tests.http3;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.tls.Tls;
import io.helidon.http.Status;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class Http3TypedProtocolEntryPointTest {
    private static final String SERVER_KEYSTORE = "server.p12";
    private static final String CLIENT_TRUSTSTORE = "client.p12";
    private static final char[] KEY_PASSWORD = "password".toCharArray();
    private static final String HELLO = "Hello";
    private static final String PAYLOAD = "payload";
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofMillis(250);

    @Test
    void shouldUseHttp3WhenTypedClientIsObtainedFromWebClientProtocol() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing.get("/hello",
                                                                                                        (_, res) -> res.send(HELLO)))) {
            WebClient webClient = strictWebClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolPreference(Http1Client.PROTOCOL_ID)
                    .build();

            Http3Client client = webClient.client(Http3Client.PROTOCOL);
            try {
                try (Http3ClientResponse response = client.get("/hello").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is(HELLO));
                }
            } finally {
                client.closeResource();
                webClient.closeResource();
            }
        }
    }

    @Test
    void shouldReplaceCachedTypedClientOnlyAfterSessionTerminationCompletes() throws Exception {
        CountDownLatch producerStarted = new CountDownLatch(1);
        CountDownLatch producerAllowed = new CountDownLatch(1);
        try (TestEnvironment environment = TestEnvironment.createSharedListener(
                routing -> routing.post("/held-request", (req, res) -> res.send(req.content().as(String.class))))) {
            WebClient webClient = strictWebClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .build();
            Http3Client client = webClient.client(Http3Client.PROTOCOL);
            AtomicReference<Throwable> requestFailure = new AtomicReference<>();
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            Thread requestThread = Thread.ofPlatform()
                    .unstarted(() -> {
                        try (Http3ClientResponse ignored = client.post("/held-request").outputStream(outputStream -> {
                            producerStarted.countDown();
                            boolean interrupted = false;
                            for (;;) {
                                try {
                                    producerAllowed.await();
                                    break;
                                } catch (InterruptedException _) {
                                    interrupted = true;
                                }
                            }
                            if (interrupted) {
                                Thread.currentThread().interrupt();
                            }
                            outputStream.close();
                        })) {
                            // The close below terminates this request before a response is available.
                        } catch (Throwable failure) {
                            requestFailure.set(failure);
                        }
                    });
            Thread closeThread = Thread.ofPlatform()
                    .unstarted(() -> {
                        try {
                            client.closeResource();
                        } catch (Throwable failure) {
                            closeFailure.set(failure);
                        }
                    });
            Http3Client replacement = null;
            try {
                requestThread.start();
                assertThat(producerStarted.await(10, TimeUnit.SECONDS), is(true));
                closeThread.start();

                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean closingObserved = false;
                while (System.nanoTime() < deadline) {
                    try {
                        client.get();
                    } catch (IllegalStateException _) {
                        closingObserved = true;
                        break;
                    }
                    Thread.onSpinWait();
                }

                assertThat(closingObserved, is(true));
                assertThat(closeThread.isAlive(), is(true));
                assertThat(webClient.client(Http3Client.PROTOCOL), sameInstance(client));
            } finally {
                producerAllowed.countDown();
                requestThread.join();
                if (closeThread.getState() != Thread.State.NEW) {
                    closeThread.join();
                }
            }

            try {
                assertThat(closeFailure.get(), nullValue());
                assertThat(requestFailure.get(), notNullValue());
                replacement = webClient.client(Http3Client.PROTOCOL);
                assertThat(replacement, not(sameInstance(client)));
            } finally {
                if (replacement != null) {
                    replacement.closeResource();
                }
                webClient.closeResource();
            }
        }
    }

    @Test
    void shouldRejectCloseFromDnsResolverWithoutChangingLifecycle() throws Exception {
        AtomicReference<Http3Client> clientRef = new AtomicReference<>();
        AtomicReference<IllegalStateException> closeFailure = new AtomicReference<>();
        AtomicReference<Throwable> requestFailure = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUri("https://reentrant-close.invalid")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .dnsResolver((_, _) -> {
                    try {
                        clientRef.get().closeResource();
                    } catch (IllegalStateException failure) {
                        closeFailure.set(failure);
                    }
                    throw new IllegalStateException("Stop after exercising reentrant close");
                })
                .tls(Tls.builder().trustAll(true).build())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .build();
        Http3Client client = webClient.client(
                Http3Client.PROTOCOL,
                Http3ClientProtocolConfig.builder()
                        .priorKnowledge(true)
                        .build());
        clientRef.set(client);
        Thread requestThread = Thread.ofVirtual()
                .unstarted(() -> {
                    try {
                        client.get("/").request();
                    } catch (Throwable failure) {
                        requestFailure.set(failure);
                    }
                });

        try {
            requestThread.start();
            requestThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat("Closing from the DNS resolver must fail instead of deadlocking.",
                       requestThread.isAlive(),
                       is(false));
            assertThat(requestFailure.get(), notNullValue());
            assertThat(closeFailure.get(), notNullValue());
            assertThat(closeFailure.get().getMessage(), containsString("must not be closed"));
            assertThat(client.get(), notNullValue());
        } finally {
            if (requestThread.isAlive()) {
                requestThread.interrupt();
            } else {
                client.closeResource();
                webClient.closeResource();
            }
        }
    }

    @Test
    void shouldRejectCloseFromRequestProducerWithoutChangingLifecycle() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListener(
                routing -> routing.post("/echo", (req, res) -> res.send(req.content().as(String.class))))) {
            WebClient webClient = strictWebClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .build();
            Http3Client client = webClient.client(Http3Client.PROTOCOL);
            AtomicReference<IllegalStateException> closeFailure = new AtomicReference<>();

            try {
                try (Http3ClientResponse response = client.post("/echo").outputStream(outputStream -> {
                    try {
                        client.closeResource();
                    } catch (IllegalStateException failure) {
                        closeFailure.set(failure);
                    }
                    outputStream.write(PAYLOAD.getBytes(StandardCharsets.UTF_8));
                    outputStream.close();
                })) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.entity().as(String.class), is(PAYLOAD));
                }

                assertThat(closeFailure.get(), notNullValue());
                assertThat(closeFailure.get().getMessage(), containsString("must not be closed"));
                assertThat(client.get(), notNullValue());
            } finally {
                client.closeResource();
                webClient.closeResource();
            }
        }
    }

    @Test
    void shouldFallbackUncommittedGetAndPostOnTransportFailureWithConfiguredTypedProtocol() throws Exception {
        try (Http1OnlyTlsEnvironment environment = Http1OnlyTlsEnvironment.create()) {
            WebClient webClient = WebClient.builder()
                    .baseUri(environment.baseUri())
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .tls(environment.clientTls())
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolPreference(Http1Client.PROTOCOL_ID)
                    .build();

            Http3Client client = webClient.client(Http3Client.PROTOCOL, Http3ClientProtocolConfig.builder()
                    .initialResponseTimeout(HANDSHAKE_TIMEOUT)
                    .handshakeTimeout(HANDSHAKE_TIMEOUT)
                    .build());

            try {
                try (Http3ClientResponse response = client.get("/hello").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is(HELLO));
                }
                assertThat(environment.http1GetCount(), is(1));

                try (Http3ClientResponse response = client.post("/echo").submit(PAYLOAD)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is(PAYLOAD));
                }
                assertThat(environment.http1PostCount(), is(1));
            } finally {
                client.closeResource();
                webClient.closeResource();
            }
        }
    }

    @Test
    void shouldInvokeOneShotBodyOnceWhenHttp3CannotStartAndFallbackIsAllowed() throws Exception {
        AtomicInteger producerCount = new AtomicInteger();

        try (Http1OnlyTlsEnvironment environment = Http1OnlyTlsEnvironment.create()) {
            WebClient webClient = WebClient.builder()
                    .baseUri(environment.baseUri())
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .tls(environment.clientTls())
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolPreference(Http1Client.PROTOCOL_ID)
                    .build();

            Http3Client client = webClient.client(Http3Client.PROTOCOL, Http3ClientProtocolConfig.builder()
                    .initialResponseTimeout(HANDSHAKE_TIMEOUT)
                    .handshakeTimeout(HANDSHAKE_TIMEOUT)
                    .build());

            try {
                try (Http3ClientResponse response = client.post("/echo").outputStream(outputStream -> {
                    producerCount.incrementAndGet();
                    outputStream.write(PAYLOAD.getBytes(StandardCharsets.UTF_8));
                    outputStream.close();
                })) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is(PAYLOAD));
                }
                assertThat(producerCount.get(), is(1));
                assertThat(environment.http1PostCount(), is(1));
            } finally {
                client.closeResource();
                webClient.closeResource();
            }
        }
    }

    private static final class Http1OnlyTlsEnvironment implements AutoCloseable {
        private final WebServer server;
        private final Http3TlsSupport.Http3TlsMaterials tlsMaterials;
        private final AtomicInteger getCount;
        private final AtomicInteger postCount;

        private Http1OnlyTlsEnvironment(WebServer server,
                                        Http3TlsSupport.Http3TlsMaterials tlsMaterials,
                                        AtomicInteger getCount,
                                        AtomicInteger postCount) {
            this.server = server;
            this.tlsMaterials = tlsMaterials;
            this.getCount = getCount;
            this.postCount = postCount;
        }

        private static Http1OnlyTlsEnvironment create() throws Exception {
            Http3TlsSupport.Http3TlsMaterials tlsMaterials = Http3TlsSupport.load();
            AtomicInteger getCount = new AtomicInteger();
            AtomicInteger postCount = new AtomicInteger();

            WebServer server = WebServer.builder()
                    .port(0)
                    .bindingsDiscoverServices(false)
                    .addBinding(TcpTransportConfig.create())
                    .protocolsDiscoverServices(false)
                    .tls(tlsMaterials.serverTls())
                    .addProtocol(Http1Config.create())
                    .routing(routing -> routing
                            .get("/hello", (_, res) -> {
                                getCount.incrementAndGet();
                                res.send(HELLO);
                            })
                            .post("/echo", (req, res) -> {
                                postCount.incrementAndGet();
                                res.send(req.content().as(String.class));
                            }))
                    .build()
                    .start();

            return new Http1OnlyTlsEnvironment(server, tlsMaterials, getCount, postCount);
        }

        private String baseUri() {
            return "https://localhost:" + server.port();
        }

        private Tls clientTls() {
            return tlsMaterials.clientTls();
        }

        private int http1GetCount() {
            return getCount.get();
        }

        private int http1PostCount() {
            return postCount.get();
        }

        @Override
        public void close() {
            server.stop();
        }
    }
}
