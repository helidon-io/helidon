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

package io.helidon.webclient.websocket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HttpTransportObserver;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverLifecycle;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverProvider;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.websocket.WsListener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(20)
class WsClientLifecycleTest {
    @Test
    void standaloneClientReusesObserverOwnershipAndAwaitsCleanup() throws Exception {
        var provider = new TestProvider();
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = respond(executor, server, "403 Forbidden", 2);
            WsClient client = WsClient.builder()
                    .baseUri(uri(server))
                    .readTimeout(Duration.ofSeconds(5))
                    .connectTimeout(Duration.ofSeconds(5))
                    .shareConnectionCache(false)
                    .addService(provider)
                    .build();
            try {
                for (int i = 0; i < 2; i++) {
                    assertThrows(WsClientException.class, () -> client.connect("/", new WsListener() { }));
                }
                requests.get(5, TimeUnit.SECONDS);
                assertThat("one observer lifecycle must serve both upgrade attempts", provider.starts.get(), is(1));

                var completion = client.closeResourceAsync().toCompletableFuture();
                client.closeResource();

                assertThat("cleanup is initiated exactly once", provider.stops.get(), is(1));
                assertThat("asynchronous observer cleanup must remain observable", completion.isDone(), is(false));
                provider.completion.complete(null);
                completion.get(5, TimeUnit.SECONDS);
            } finally {
                provider.completion.complete(null);
                client.closeResource();
            }
        }
    }

    @Test
    void closingWebSocketClientPreservesOtherProtocolOwners() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = respond(executor, server, "200 OK", 2);
            WebClient client = WebClient.builder()
                    .baseUri(uri(server))
                    .readTimeout(Duration.ofSeconds(5))
                    .connectTimeout(Duration.ofSeconds(5))
                    .shareConnectionCache(false)
                    .build();
            Http1Client http1 = client.client(Http1Client.PROTOCOL);
            WsClient websocket = client.client(WsClient.PROTOCOL);
            try {
                websocket.closeResourceAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);

                try (var response = http1.get().request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                try (var response = client.get().request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                requests.get(5, TimeUnit.SECONDS);
            } finally {
                websocket.closeResource();
                http1.closeResource();
                client.closeResource();
            }
        }
    }

    @Test
    void existingImplementationsInheritNoopCleanup() {
        WsClient client = new WsClient() {
            @Override
            public void connect(URI uri, WsListener listener) {
            }

            @Override
            public void connect(String path, WsListener listener) {
            }

            @Override
            public WsClientConfig prototype() {
                return WsClientConfig.create();
            }
        };

        client.closeResource();
        assertThat(client.closeResourceAsync().toCompletableFuture().isDone(), is(true));
    }

    private static URI uri(ServerSocket server) throws Exception {
        return new URI("http", null, server.getInetAddress().getHostAddress(), server.getLocalPort(), null, null, null);
    }

    private static Future<?> respond(ExecutorService executor, ServerSocket server, String status, int count) throws Exception {
        server.setSoTimeout(5000);
        return executor.submit(() -> {
            for (int i = 0; i < count; i++) {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String line;
                    do {
                        line = reader.readLine();
                    } while (line != null && !line.isEmpty());
                    String response = "HTTP/1.1 " + status + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    assertThat("client must close each response connection", socket.getInputStream().read(), is(-1));
                }
            }
            return null;
        });
    }

    private static final class TestProvider implements WebClientService, ObserverProvider {
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger stops = new AtomicInteger();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            return chain.proceed(request);
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Object scope() {
            return this;
        }

        @Override
        public ObserverLifecycle createObserver() {
            return new ObserverLifecycle() {
                @Override
                public HttpTransportObserver start() {
                    starts.incrementAndGet();
                    return HttpTransportObserver.noop();
                }

                @Override
                public CompletionStage<Void> stop() {
                    stops.incrementAndGet();
                    return completion;
                }
            };
        }
    }
}
