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

package io.helidon.webclient.http2;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http2FallbackResponseTest {
    private static final String BODY = "fallback response body";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unreadResponseCloseCompletesServiceTrailers(boolean decorateResponse) throws Exception {
        CompletableFuture<Void> requestComplete = new CompletableFuture<>();
        AtomicReference<CompletableFuture<ClientResponseTrailers>> serviceTrailers = new AtomicReference<>();
        AtomicInteger decoratedResourceCloses = new AtomicInteger();
        WebServer server = startServer();
        try {
            Http2Client client = Http2Client.builder()
                    .servicesDiscoverServices(false)
                    .shareConnectionCache(false)
                    .baseUri("http://127.0.0.1:" + server.port())
                    .addService((chain, request) -> {
                        request.whenComplete().thenRun(() -> requestComplete.complete(null));
                        WebClientServiceResponse response = chain.proceed(request);
                        serviceTrailers.set(response.trailers().toCompletableFuture());
                        return decorateResponse
                                ? WebClientServiceResponse.builder(response)
                                        .connection(decoratedResourceCloses::incrementAndGet)
                                        .build()
                                : response;
                    })
                    .build();
            try {
                Http2ClientResponse response = client.get("/body").request();
                try (response) {
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat("Trailers should remain pending while the body is unread",
                               serviceTrailers.get().isDone(),
                               is(false));
                }

                requestComplete.get(5, TimeUnit.SECONDS);
                assertThat("Closing the unread fallback must complete service trailers",
                           serviceTrailers.get().isDone(),
                           is(true));
                ExecutionException failure = assertThrows(ExecutionException.class,
                                                          () -> serviceTrailers.get().get(5, TimeUnit.SECONDS));
                assertThat(failure.getCause(), instanceOf(IllegalStateException.class));

                response.close();
                assertThat("A decorated response resource must be closed only once",
                           decoratedResourceCloses.get(),
                           is(decorateResponse ? 1 : 0));
            } finally {
                client.closeResource();
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void consumedResponseClosePreservesSuccessfulServiceTrailers() throws Exception {
        CompletableFuture<Void> requestComplete = new CompletableFuture<>();
        AtomicReference<CompletableFuture<ClientResponseTrailers>> serviceTrailers = new AtomicReference<>();
        WebServer server = startServer();
        try {
            Http2Client client = Http2Client.builder()
                    .servicesDiscoverServices(false)
                    .shareConnectionCache(false)
                    .baseUri("http://127.0.0.1:" + server.port())
                    .addService((chain, request) -> {
                        request.whenComplete().thenRun(() -> requestComplete.complete(null));
                        WebClientServiceResponse response = chain.proceed(request);
                        serviceTrailers.set(response.trailers().toCompletableFuture());
                        return response;
                    })
                    .build();
            try {
                ClientResponseTrailers trailers;
                try (Http2ClientResponse response = client.get("/body").request()) {
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is(BODY));
                    trailers = serviceTrailers.get().get(5, TimeUnit.SECONDS);
                    assertThat(trailers.size(), is(0));
                }

                requestComplete.get(5, TimeUnit.SECONDS);
                assertThat(serviceTrailers.get().get(5, TimeUnit.SECONDS), sameInstance(trailers));
            } finally {
                client.closeResource();
            }
        } finally {
            server.stop();
        }
    }

    private static WebServer startServer() {
        return WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .protocolsDiscoverServices(false)
                .addConnectionSelector(Http1ConnectionSelector.builder()
                                               .config(Http1Config.create())
                                               .build())
                .routing(routing -> routing.get("/body", (_, response) -> response.send(BODY)))
                .build()
                .start();
    }
}
