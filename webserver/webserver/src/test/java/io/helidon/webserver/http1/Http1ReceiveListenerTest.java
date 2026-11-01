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

package io.helidon.webserver.http1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.spi.ProtocolUpgradeHandler;
import io.helidon.webserver.http1.spi.Http1RoutedUpgrade;
import io.helidon.webserver.http1.spi.Http1RoutedUpgrader;
import io.helidon.webserver.http1.spi.Http1UpgradeResult;
import io.helidon.webserver.http1.spi.Http1Upgrader;
import io.helidon.webserver.spi.ServerConnection;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http1ReceiveListenerTest {
    private static final String REQUEST = "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
    private static final String UPGRADE_REQUEST =
            "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nUpgrade: test\r\n\r\n";
    private static final String KEEP_ALIVE_UPGRADE_REQUEST =
            "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: upgrade\r\nUpgrade: test\r\n\r\n";

    @Test
    void receivesProtocolDetectionPrefetchAndRemainingRequestOnce() throws InterruptedException {
        DataReader reader = reader("GET ", REQUEST.substring(4));
        assertThat(reader.startsWith("GET".getBytes(StandardCharsets.US_ASCII)), is(true));
        var listener = new RecordingListener();
        Http1Connection connection = connection(reader, listener, Map.of());

        connection.handle(FixedLimit.create());

        assertThat(listener.received, contains("GET ", REQUEST.substring(4)));
        assertThat(listener.prologues, hasSize(1));
    }

    @Test
    void stopsForwardingRawInputAfterUpgradeHandoff() throws InterruptedException {
        DataReader reader = reader(UPGRADE_REQUEST, "prepare", "upgraded");
        var listener = new RecordingListener();
        var consumed = new ArrayList<String>();
        var upgraded = mock(ServerConnection.class);
        doAnswer(_ -> {
            consumed.add(reader.readAsciiString(8));
            return null;
        }).when(upgraded).handle(any(Limit.class));
        var upgrader = mock(Http1Upgrader.class);
        when(upgrader.upgrade(any(), any(), any())).thenAnswer(_ -> {
            consumed.add(reader.readAsciiString(7));
            return upgraded;
        });

        connection(reader, listener, Map.of("test", upgrader)).handle(FixedLimit.create());

        verify(upgraded).handle(any(Limit.class));
        assertThat(consumed, contains("prepare", "upgraded"));
        assertThat(listener.received, contains(UPGRADE_REQUEST, "prepare"));
    }

    @Test
    void preservesListenerInstalledDuringUpgradeConstruction() throws InterruptedException {
        DataReader reader = reader(UPGRADE_REQUEST, "prepare", "upgraded");
        var original = new RecordingListener();
        var replacement = new RecordingListener();
        var upgraded = mock(ServerConnection.class);
        doAnswer(_ -> {
            reader.readAsciiString(8);
            return null;
        }).when(upgraded).handle(any(Limit.class));
        var upgrader = mock(Http1Upgrader.class);
        when(upgrader.upgrade(any(), any(), any())).thenAnswer(invocation -> {
            ConnectionContext context = invocation.getArgument(0);
            reader.listener(replacement, context);
            reader.readAsciiString(7);
            return upgraded;
        });

        connection(reader, original, Map.of("test", upgrader)).handle(FixedLimit.create());

        verify(upgraded).handle(any(Limit.class));
        assertThat(original.received, contains(UPGRADE_REQUEST));
        assertThat(replacement.received, contains("prepare", "upgraded"));
    }

    @Test
    void declinedUpgradeDoesNotLoseRawInput() throws InterruptedException {
        DataReader reader = reader(UPGRADE_REQUEST, "prepare");
        var listener = new RecordingListener();
        var upgrader = mock(Http1Upgrader.class);
        when(upgrader.upgrade(any(), any(), any())).thenAnswer(_ -> {
            reader.readAsciiString(7);
            return null;
        });

        connection(reader, listener, Map.of("test", upgrader)).handle(FixedLimit.create());

        verify(upgrader).upgrade(any(), any(), any());
        assertThat(listener.received, contains(UPGRADE_REQUEST, "prepare"));
    }

    @Test
    void declinedUpgradeRestoresHttp1ListenerAfterReplacement() throws InterruptedException {
        DataReader reader = reader(KEEP_ALIVE_UPGRADE_REQUEST, REQUEST);
        var original = new RecordingListener();
        var replacement = new RecordingListener();
        var upgrader = mock(Http1Upgrader.class);
        when(upgrader.upgrade(any(), any(), any())).thenAnswer(invocation -> {
            ConnectionContext context = invocation.getArgument(0);
            reader.listener(replacement, context);
            return null;
        });

        connection(reader, original, Map.of("test", upgrader)).handle(FixedLimit.create());

        verify(upgrader).upgrade(any(), any(), any());
        assertAll(
                () -> assertThat(original.prologues, hasSize(2)),
                () -> assertThat(original.received, contains(KEEP_ALIVE_UPGRADE_REQUEST, REQUEST)),
                () -> assertThat(replacement.received, empty())
        );
    }

    @Test
    void emptyRoutedUpgradeRestoresHttp1ListenerAfterReplacement() throws InterruptedException {
        DataReader reader = reader(KEEP_ALIVE_UPGRADE_REQUEST, REQUEST);
        var original = new RecordingListener();
        var replacement = new RecordingListener();
        var upgrader = mock(Http1RoutedUpgrader.class);
        when(upgrader.routedUpgrade(any(), any(), any())).thenAnswer(invocation -> {
            ConnectionContext context = invocation.getArgument(0);
            reader.listener(replacement, context);
            return Optional.empty();
        });

        connection(reader, original, Map.of("test", upgrader)).handle(FixedLimit.create());

        verify(upgrader).routedUpgrade(any(), any(), any());
        assertAll(
                () -> assertThat(original.prologues, hasSize(2)),
                () -> assertThat(original.received, contains(KEEP_ALIVE_UPGRADE_REQUEST, REQUEST)),
                () -> assertThat(replacement.received, empty())
        );
    }

    @Test
    void respondedRoutedUpgradeRestoresHttp1ListenerForNextRequest() throws InterruptedException {
        DataReader reader = reader(KEEP_ALIVE_UPGRADE_REQUEST, REQUEST);
        var original = new RecordingListener();
        var replacement = new RecordingListener();
        var upgrader = mock(Http1RoutedUpgrader.class);
        when(upgrader.routedUpgrade(any(), any(), any())).thenAnswer(invocation -> {
            ConnectionContext context = invocation.getArgument(0);
            Http1RoutedUpgrade prepared = response -> {
                reader.listener(replacement, context);
                response.send(Status.BAD_REQUEST_400);
                return Http1UpgradeResult.responded();
            };
            return Optional.of(prepared);
        });

        connection(reader, original, Map.of("test", upgrader)).handle(FixedLimit.create());

        verify(upgrader).routedUpgrade(any(), any(), any());
        assertAll(
                () -> assertThat(original.prologues, hasSize(2)),
                () -> assertThat(original.received, contains(KEEP_ALIVE_UPGRADE_REQUEST, REQUEST)),
                () -> assertThat(replacement.received, empty())
        );
    }

    @Test
    void stopsForwardingRawInputAfterRoutedUpgradeHandoff() throws InterruptedException {
        DataReader reader = reader(UPGRADE_REQUEST, "upgraded");
        var listener = new RecordingListener();
        var upgraded = mock(ServerConnection.class);
        doAnswer(_ -> {
            reader.readAsciiString(8);
            return null;
        }).when(upgraded).handle(any(Limit.class));
        var upgrader = mock(Http1RoutedUpgrader.class);
        when(upgrader.routedUpgrade(any(), any(), any()))
                .thenReturn(Optional.of(_ -> Http1UpgradeResult.upgraded(upgraded)));

        connection(reader, listener, Map.of("test", upgrader)).handle(FixedLimit.create());

        verify(upgraded).handle(any(Limit.class));
        assertThat(listener.received, contains(UPGRADE_REQUEST));
    }

    @Test
    void routedUpgradeSeesFilterUpdatedRequestHeaders() throws InterruptedException {
        var originalHeaders = new AtomicReference<Headers>();
        var callbackHeaders = new AtomicReference<Headers>();
        var filterHeader = HeaderNames.create("X-Filter");
        var upgrader = mock(Http1RoutedUpgrader.class);
        when(upgrader.routedUpgrade(any(), any(), any())).thenAnswer(invocation -> {
            originalHeaders.set(invocation.getArgument(2));
            Http1RoutedUpgrade prepared = response -> {
                callbackHeaders.set(response.requestHeaders());
                response.send(Status.BAD_REQUEST_400);
                return Http1UpgradeResult.responded();
            };
            return Optional.of(prepared);
        });
        var routing = HttpRouting.builder()
                .addFilter((chain, request, _) -> {
                    request.header(HeaderValues.create(HeaderNames.HOST, "filtered.example"));
                    request.header(HeaderValues.create(filterHeader, "present"));
                    chain.proceed();
                });

        connection(reader(UPGRADE_REQUEST), new RecordingListener(), Map.of("test", upgrader), routing)
                .handle(FixedLimit.create());

        assertThat(callbackHeaders.get(), notNullValue());
        assertThat(callbackHeaders.get().get(HeaderNames.HOST).get(), is("filtered.example"));
        assertThat(callbackHeaders.get().get(filterHeader).get(), is("present"));
        assertThat(originalHeaders.get().get(HeaderNames.HOST).get(), is("localhost"));
        assertThat(originalHeaders.get().contains(filterHeader), is(false));
    }

    @Test
    void inapplicableUpgradeResumesOrdinaryRoutingOnce() throws InterruptedException {
        var calls = new ArrayList<String>();
        var upgrader = mock(Http1RoutedUpgrader.class);
        when(upgrader.routedUpgrade(any(), any(), any())).thenReturn(Optional.of(response -> {
            calls.add("upgrade:" + response.requestHeaders().get(HeaderNames.HOST).get());
            return Http1UpgradeResult.notApplicable();
        }));
        var routing = HttpRouting.builder()
                .addFilter((chain, request, _) -> {
                    calls.add("filter");
                    request.header(HeaderValues.create(HeaderNames.HOST, "filtered.example"));
                    chain.proceed();
                })
                .any(new ProtocolUpgradeHandler() {
                    @Override
                    public void handleProtocolUpgrade(ServerRequest request, ServerResponse response) {
                        calls.add("policy");
                        response.next();
                    }

                    @Override
                    public void handle(ServerRequest request, ServerResponse response) {
                        calls.add("ordinary-policy");
                        response.next();
                    }
                })
                .get("/", (request, response) -> {
                    calls.add("endpoint:" + request.headers().get(HeaderNames.HOST).get());
                    response.send("fallback");
                });

        connection(reader(UPGRADE_REQUEST), new RecordingListener(), Map.of("test", upgrader), routing)
                .handle(FixedLimit.create());

        assertThat(calls, contains("filter", "policy", "upgrade:filtered.example", "ordinary-policy",
                                   "endpoint:filtered.example"));
    }

    @Test
    void inapplicableUpgradeToUnmatchedRouteSendsOneNotFoundAndKeepsAlive() throws InterruptedException {
        var routing = HttpRouting.builder().get("/", (_, response) -> response.send("continued"));

        String wire = inapplicableUpgradeErrorResponse(routing);

        assertThat(Pattern.compile("HTTP/1\\.1 [^\\r]+")
                           .matcher(wire).results().map(MatchResult::group).toList(),
                   contains("HTTP/1.1 404 Not Found", "HTTP/1.1 200 OK"));
        assertThat(wire, containsString("continued"));
    }

    @Test
    void inapplicableUpgradeHandlerErrorSendsOneHandledResponseAndKeepsAlive() throws InterruptedException {
        var routing = HttpRouting.builder()
                .get("/missing", (_, _) -> {
                    throw new IllegalStateException("rejected");
                })
                .error(IllegalStateException.class, (_, response, _) -> response.status(Status.CONFLICT_409).send("handled"))
                .get("/", (_, response) -> response.send("continued"));

        String wire = inapplicableUpgradeErrorResponse(routing);

        assertThat(Pattern.compile("HTTP/1\\.1 [^\\r]+")
                           .matcher(wire).results().map(MatchResult::group).toList(),
                   contains("HTTP/1.1 409 Conflict", "HTTP/1.1 200 OK"));
        assertThat(wire, containsString("handled"));
        assertThat(wire, containsString("continued"));
    }

    private static String inapplicableUpgradeErrorResponse(HttpRouting.Builder routing) throws InterruptedException {
        var wire = new StringBuilder();
        var writer = mock(DataWriter.class, invocation -> {
            for (Object argument : invocation.getArguments()) {
                if (argument instanceof BufferData buffer) {
                    wire.append(new String(buffer.readBytes(), StandardCharsets.US_ASCII));
                }
            }
            return null;
        });
        var upgrader = mock(Http1RoutedUpgrader.class);
        when(upgrader.routedUpgrade(any(), any(), any()))
                .thenReturn(Optional.of(_ -> Http1UpgradeResult.notApplicable()));
        connection(reader(KEEP_ALIVE_UPGRADE_REQUEST.replace("GET / ", "GET /missing "), REQUEST),
                   new RecordingListener(), Map.of("test", upgrader), routing, writer)
                .handle(FixedLimit.create());
        return wire.toString();
    }

    private static DataReader reader(String... chunks) {
        var bytes = List.of(chunks).stream()
                .map(chunk -> chunk.getBytes(StandardCharsets.US_ASCII))
                .iterator();
        return DataReader.create(() -> bytes.hasNext() ? bytes.next() : null);
    }

    private static Http1Connection connection(DataReader reader,
                                              Http1ConnectionListener listener,
                                              Map<String, Http1Upgrader> upgraders) {
        return connection(reader, listener, upgraders,
                          HttpRouting.builder().get("/", (_, response) -> response.send("done")));
    }

    private static Http1Connection connection(DataReader reader,
                                              Http1ConnectionListener listener,
                                              Map<String, Http1Upgrader> upgraders,
                                              HttpRouting.Builder routing) {
        return connection(reader, listener, upgraders, routing, mock(DataWriter.class));
    }

    private static Http1Connection connection(DataReader reader,
                                              Http1ConnectionListener listener,
                                              Map<String, Http1Upgrader> upgraders,
                                              HttpRouting.Builder routing,
                                              DataWriter writer) {
        var listenerContext = mock(ListenerContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(ContentEncodingContext.create());
        when(listenerContext.mediaContext()).thenReturn(MediaContext.create());
        when(listenerContext.config()).thenReturn(WebServer.builder().buildPrototype());
        when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());
        var peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        var context = mock(ConnectionContext.class);
        when(context.listenerContext()).thenReturn(listenerContext);
        when(context.dataWriter()).thenReturn(writer);
        when(context.dataReader()).thenReturn(reader);
        when(context.router()).thenReturn(Router.builder().addRouting(routing).build());
        when(context.remotePeer()).thenReturn(peerInfo);
        when(context.localPeer()).thenReturn(peerInfo);

        return new Http1Connection(context,
                                   Http1Config.builder().addReceiveListener(listener).build(),
                                   upgraders);
    }

    private static class RecordingListener implements Http1ConnectionListener {
        private final List<String> received = new ArrayList<>();
        private final List<HttpPrologue> prologues = new ArrayList<>();

        @Override
        public void data(ConnectionContext context, byte[] data, int position, int length) {
            received.add(new String(data, position, length, StandardCharsets.US_ASCII));
        }

        @Override
        public void prologue(ConnectionContext context, HttpPrologue prologue) {
            prologues.add(prologue);
        }
    }
}
