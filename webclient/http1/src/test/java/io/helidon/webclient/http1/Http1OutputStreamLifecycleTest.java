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

package io.helidon.webclient.http1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(15)
class Http1OutputStreamLifecycleTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final byte[] UPLOAD = "upload".getBytes(StandardCharsets.US_ASCII);
    private static final String ENTITY = "terminal response";

    @ParameterizedTest(name = "redirect={0}, framing={1}, consume={2}")
    @MethodSource("earlyFinalResponses")
    void earlyFinalResponseClosesIncompleteUpload(boolean redirect, String framing, boolean consume) throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger callbacks = new AtomicInteger();
        CompletableFuture<Void> uploadClosed = new CompletableFuture<>();
        try (TestServer server = new TestServer(listener -> {
            if (redirect) {
                try (Socket first = accept(listener)) {
                    assertThat(readHead(first.getInputStream()), startsWith("POST /upload HTTP/1.1\r\n"));
                    send(first, ("HTTP/1.1 307 Temporary Redirect\r\nLocation: /rejected\r\n"
                            + "Content-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    assertThat(first.getInputStream().read(), is(-1));
                }
            }
            try (Socket first = accept(listener)) {
                assertThat(readHead(first.getInputStream()), containsString("Expect: 100-continue\r\n"));
                send(first, response("417 Expectation Failed", framing));
                assertThat("unfinished upload must end at the request headers", first.getInputStream().read(), is(-1));
                uploadClosed.complete(null);
            }
            try (Socket next = accept(listener)) {
                assertThat(readHead(next.getInputStream()), startsWith("GET /next HTTP/1.1\r\n"));
                send(next, response("200 OK", "length"));
            }
        })) {
            Http1Client client = client(server, exchanges, new AtomicInteger());
            try {
                try (Http1ClientResponse response = client.post("/upload").outputStream(output -> {
                    callbacks.incrementAndGet();
                    output.write(UPLOAD);
                    output.close();
                })) {
                    assertThat(response.status(), is(Status.EXPECTATION_FAILED_417));
                    if (consume) {
                        assertThat(response.entity().asOptional(String.class).orElse(""),
                                   is(framing.equals("empty") ? "" : ENTITY));
                    }
                }

                Exchange exchange = exchanges.get("/upload");
                assertThat("early final response must bypass the upload callback", callbacks.get(), is(0));
                uploadClosed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                assertCompleted(exchange);
                assertThat("upload service response", exchange.status(),
                           is(redirect ? Status.TEMPORARY_REDIRECT_307 : Status.EXPECTATION_FAILED_417));
                if (redirect) {
                    assertCompleted(exchanges.get("/rejected"));
                    assertThat("redirected service response", exchanges.get("/rejected").status(),
                               is(Status.EXPECTATION_FAILED_417));
                }
                try (Http1ClientResponse response = client.get("/next").request()) {
                    assertThat(response.entity().as(String.class), is(ENTITY));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest(name = "early={0}, framing={1}, consume={2}")
    @MethodSource("redirectResponses")
    void redirectedResponseCompletesBothRequests(boolean early, String framing, boolean consume) throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger decoders = new AtomicInteger();
        AtomicInteger connections = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        try (TestServer server = new TestServer(listener -> {
            try (Socket first = accept(listener)) {
                connections.incrementAndGet();
                assertThat(readHead(first.getInputStream()), startsWith("POST /upload HTTP/1.1\r\n"));
                if (!early) {
                    readUpload(first.getInputStream());
                }
                send(first, "HTTP/1.1 303 See Other\r\nLocation: /terminal\r\nContent-Length: 0\r\n\r\n"
                        .getBytes(StandardCharsets.US_ASCII));
                assertThat(first.getInputStream().read(), is(-1));
            }
            try (Socket terminal = accept(listener)) {
                connections.incrementAndGet();
                assertThat(readHead(terminal.getInputStream()), startsWith("GET /terminal HTTP/1.1\r\n"));
                send(terminal, response("200 OK", framing, true));
                String next = readHead(terminal.getInputStream());
                if (!next.isEmpty()) {
                    assertThat(next, startsWith("GET /next HTTP/1.1\r\n"));
                    send(terminal, response("200 OK", "length"));
                    return;
                }
            }
            try (Socket next = accept(listener)) {
                connections.incrementAndGet();
                assertThat(readHead(next.getInputStream()), startsWith("GET /next HTTP/1.1\r\n"));
                send(next, response("200 OK", "length"));
            }
        })) {
            Http1Client client = client(server, exchanges, decoders);
            try {
                try (Http1ClientResponse response = client.post("/upload")
                        .sendExpectContinue(early)
                        .outputStream(output -> {
                            callbacks.incrementAndGet();
                            output.write(UPLOAD);
                            output.close();
                        })) {
                    assertThat(response.status(), is(Status.OK_200));
                    if (consume) {
                        assertThat("terminal entity must be decorated exactly once",
                                   response.entity().asOptional(byte[].class).orElse(new byte[0]),
                                   is((framing.equals("empty") ? "" : ENTITY.toUpperCase(Locale.ROOT))
                                              .getBytes(StandardCharsets.US_ASCII)));
                        if (framing.equals("trailers")) {
                            assertThat(response.trailers().get(HeaderNames.create("Checksum")).get(), is("valid"));
                        }
                    }
                }

                Exchange upload = exchanges.get("/upload");
                Exchange terminalExchange = exchanges.get("/terminal");
                assertThat("early 303 must bypass the callback; completed uploads invoke it once", callbacks.get(),
                           is(early ? 0 : 1));
                assertCompleted(upload);
                assertCompleted(terminalExchange);
                assertThat("upload service must receive its redirect response", upload.status(), is(Status.SEE_OTHER_303));
                assertThat("terminal service must receive its own response", terminalExchange.status(), is(Status.OK_200));
                assertThat("upload service must not transform terminal bytes", upload.transformedBytes().get(), is(0));
                assertThat("terminal decorator byte count", terminalExchange.transformedBytes().get(),
                           is(consume && !framing.equals("empty") ? ENTITY.length() : 0));
                CountingConnection terminal = terminalExchange.connection();
                assertThat("transferred connection must have one owner", terminal.released + terminal.closed, is(1));
                if (consume || framing.equals("empty")) {
                    assertThat("completed response should preserve connection reuse", terminal.released, is(1));
                }
                assertThat("redirect transfer must preserve its existing decoder", decoders.get(),
                           is(framing.equals("gzip") ? 1 : 0));
                try (Http1ClientResponse response = client.get("/next").request()) {
                    assertThat(response.entity().as(String.class), is(ENTITY));
                }
                server.await();
                if (consume || framing.equals("empty")) {
                    assertThat("next request should reuse the terminal connection", connections.get(), is(2));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    void earlyMethodPreservingRedirectInvokesUploadOnce(int status) throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger decoders = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        try (InputStream upload = new ByteArrayInputStream(UPLOAD);
             TestServer server = new TestServer(listener -> {
                 try (Socket first = accept(listener)) {
                     String head = readHead(first.getInputStream());
                     assertThat(head, startsWith("POST /upload HTTP/1.1\r\n"));
                     assertThat(head, containsString("Expect: 100-continue\r\n"));
                     assertThat("redirect response precedes the upload callback", callbacks.get(), is(0));
                     send(first, redirectResponse(status, "/terminal"));
                     assertThat("redirected upload must not emit body bytes", first.getInputStream().read(), is(-1));
                 }
                 try (Socket terminal = accept(listener)) {
                     String head = readHead(terminal.getInputStream());
                     assertThat(head, startsWith("POST /terminal HTTP/1.1\r\n"));
                     assertThat(head, containsString("Expect: 100-continue\r\n"));
                     assertThat("continue response precedes the upload callback", callbacks.get(), is(0));
                     send(terminal, "HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                     readUpload(terminal.getInputStream());
                     send(terminal, response("200 OK", "gzip", true));
                     assertThat(readHead(terminal.getInputStream()), startsWith("GET /next HTTP/1.1\r\n"));
                     send(terminal, response("200 OK", "length"));
                 }
             })) {
            Http1Client client = client(server, exchanges, decoders);
            try {
                try (Http1ClientResponse response = client.post("/upload").outputStream(output -> {
                    assertThat("upload callback count", callbacks.incrementAndGet(), is(1));
                    upload.transferTo(output);
                    output.close();
                })) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.entity().as(byte[].class),
                               is(ENTITY.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)));
                }

                Exchange first = exchanges.get("/upload");
                Exchange terminal = exchanges.get("/terminal");
                assertCompleted(first);
                assertCompleted(terminal);
                assertThat("redirect service response", first.status().code(), is(status));
                assertThat("terminal service response", terminal.status(), is(Status.OK_200));
                assertThat("redirect service decorator byte count", first.transformedBytes().get(), is(0));
                assertThat("terminal decorator byte count", terminal.transformedBytes().get(), is(ENTITY.length()));
                assertThat("terminal decoder count", decoders.get(), is(1));
                assertThat("upload callback count", callbacks.get(), is(1));
                assertThat("terminal connection release count", terminal.connection().released, is(1));
                assertThat("terminal connection close count", terminal.connection().closed, is(0));
                try (Http1ClientResponse response = client.get("/next").request()) {
                    assertThat(response.entity().as(String.class), is(ENTITY));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest(name = "contentLengthZero={0}")
    @ValueSource(booleans = {false, true})
    void emptyUploadNegotiatesContinueOnlyForUnknownLength(boolean contentLengthZero) throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger callbacks = new AtomicInteger();
        try (TestServer server = new TestServer(listener -> {
            try (Socket connection = accept(listener)) {
                String head = readHead(connection.getInputStream());
                assertThat(head, startsWith("POST /upload HTTP/1.1\r\n"));
                if (contentLengthZero) {
                    assertThat(head, containsString("Content-Length: 0\r\n"));
                    assertThat(head, not(containsString("Expect: 100-continue\r\n")));
                    assertThat(head, not(containsString("Transfer-Encoding: chunked\r\n")));
                } else {
                    assertThat(head, containsString("Expect: 100-continue\r\n"));
                    assertThat(head, containsString("Transfer-Encoding: chunked\r\n"));
                    assertThat("continue response precedes the empty callback", callbacks.get(), is(0));
                    send(connection, "HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    assertThat(connection.getInputStream().readNBytes(5), is("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));
                }
                send(connection, response("200 OK", "length"));
                assertThat("empty upload must not leave extra framing", readHead(connection.getInputStream()),
                           startsWith("GET /next HTTP/1.1\r\n"));
                send(connection, response("200 OK", "length"));
            }
        })) {
            Http1Client client = client(server, exchanges, new AtomicInteger());
            try {
                Http1ClientRequest request = client.post("/upload");
                if (contentLengthZero) {
                    request.header(HeaderNames.CONTENT_LENGTH, "0");
                }
                try (Http1ClientResponse response = request.outputStream(output -> {
                    callbacks.incrementAndGet();
                    output.close();
                })) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.entity().as(String.class), is(ENTITY));
                }
                assertThat("empty upload callback count", callbacks.get(), is(1));
                assertCompleted(exchanges.get("/upload"));
                try (Http1ClientResponse response = client.get("/next").request()) {
                    assertThat(response.entity().as(String.class), is(ENTITY));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void continueTimeoutInvokesUploadOnce() throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger callbacks = new AtomicInteger();
        try (InputStream upload = new ByteArrayInputStream(UPLOAD);
             TestServer server = new TestServer(listener -> {
                 try (Socket connection = accept(listener)) {
                     String head = readHead(connection.getInputStream());
                     assertThat(head, startsWith("POST /upload HTTP/1.1\r\n"));
                     assertThat(head, containsString("Expect: 100-continue\r\n"));
                     readUpload(connection.getInputStream());
                     send(connection, response("200 OK", "length"));
                 }
             })) {
            Http1Client client = client(server, exchanges, new AtomicInteger());
            try {
                try (Http1ClientResponse response = client.post("/upload")
                        .readContinueTimeout(Duration.ofMillis(100))
                        .outputStream(output -> {
                            assertThat("timed out upload callback count", callbacks.incrementAndGet(), is(1));
                            upload.transferTo(output);
                            output.close();
                        })) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.entity().as(String.class), is(ENTITY));
                }
                assertThat("continue timeout must send the body exactly once", callbacks.get(), is(1));
                assertCompleted(exchanges.get("/upload"));
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest(name = "status={0}, allowEntity={1}")
    @MethodSource("crossOriginEntityRedirects")
    void crossOriginRedirectRequiresOptInBeforeWritingEntity(int status, boolean allowEntity) throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger decoders = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        try (TestServer destination = new TestServer(listener -> {
            try (Socket terminal = accept(listener)) {
                String head = readHead(terminal.getInputStream());
                assertThat(head, startsWith("POST /terminal HTTP/1.1\r\n"));
                assertThat(head, containsString("Expect: 100-continue\r\n"));
                send(terminal, "HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                if (allowEntity) {
                    readUpload(terminal.getInputStream());
                    send(terminal, response("200 OK", "gzip", true));
                } else {
                    assertThat("forbidden body must not emit any chunk framing or data", terminal.getInputStream().read(),
                               is(-1));
                }
            }
        });
             TestServer origin = new TestServer(listener -> {
                 try (Socket first = accept(listener)) {
                     assertThat(readHead(first.getInputStream()), startsWith("POST /upload HTTP/1.1\r\n"));
                     send(first, redirectResponse(status, "http://127.0.0.1:" + destination.listener.getLocalPort() + "/terminal"));
                     assertThat("redirected upload must not send its body to the first hop", first.getInputStream().read(), is(-1));
                 }
             })) {
            Http1Client client = client(origin, exchanges, decoders, allowEntity);
            try {
                ClientRequest.OutputStreamHandler handler = output -> {
                    callbacks.incrementAndGet();
                    output.write(UPLOAD);
                    output.close();
                };
                if (allowEntity) {
                    try (Http1ClientResponse response = client.post("/upload").outputStream(handler)) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.entity().as(byte[].class),
                                   is(ENTITY.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)));
                    }
                    Exchange terminal = exchanges.get("/terminal");
                    assertCompleted(terminal);
                    assertThat("destination service response", terminal.status(), is(Status.OK_200));
                    assertThat("destination decorator byte count", terminal.transformedBytes().get(), is(ENTITY.length()));
                    assertThat("destination decoder count", decoders.get(), is(1));
                } else {
                    IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                                () -> client.post("/upload").outputStream(handler));
                    assertThat(failure.getMessage(), is("Cross-origin redirect with request entity is disabled."));
                }
                Exchange upload = exchanges.get("/upload");
                assertCompleted(upload);
                assertThat("origin service response", upload.status().code(), is(status));
                assertThat("cross-origin upload callback count", callbacks.get(), is(1));
                origin.await();
                destination.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void crossOriginRestrictionSurvivesAnotherSameOriginRedirect() throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger callbacks = new AtomicInteger();
        try (TestServer destination = new TestServer(listener -> {
            try (Socket redirected = accept(listener)) {
                assertThat(readHead(redirected.getInputStream()), startsWith("POST /redirected HTTP/1.1\r\n"));
                send(redirected, redirectResponse(308, "/terminal"));
                assertThat("intermediate redirect must not receive body bytes", redirected.getInputStream().read(), is(-1));
            }
            try (Socket terminal = accept(listener)) {
                assertThat(readHead(terminal.getInputStream()), startsWith("POST /terminal HTTP/1.1\r\n"));
                send(terminal, "HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                assertThat("original cross-origin restriction must survive the next redirect", terminal.getInputStream().read(),
                           is(-1));
            }
        });
             TestServer origin = new TestServer(listener -> {
                 try (Socket first = accept(listener)) {
                     assertThat(readHead(first.getInputStream()), startsWith("POST /upload HTTP/1.1\r\n"));
                     send(first, redirectResponse(307, "http://127.0.0.1:" + destination.listener.getLocalPort() + "/redirected"));
                     assertThat(first.getInputStream().read(), is(-1));
                 }
             })) {
            Http1Client client = client(origin, exchanges, new AtomicInteger());
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                        client.post("/upload").outputStream(output -> {
                            callbacks.incrementAndGet();
                            output.write(UPLOAD);
                            output.close();
                        }));
                assertThat(failure.getMessage(), is("Cross-origin redirect with request entity is disabled."));
                assertThat("only the terminal hop invokes the callback", callbacks.get(), is(1));
                assertCompleted(exchanges.get("/upload"));
                assertCompleted(exchanges.get("/redirected"));
                assertThat("original service response", exchanges.get("/upload").status().code(), is(307));
                assertThat("intermediate service response", exchanges.get("/redirected").status().code(), is(308));
                origin.await();
                destination.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void crossOriginRedirectAllowsUnknownLengthEmptyUpload() throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger callbacks = new AtomicInteger();
        try (TestServer destination = new TestServer(listener -> {
            try (Socket terminal = accept(listener)) {
                String head = readHead(terminal.getInputStream());
                assertThat(head, startsWith("POST /terminal HTTP/1.1\r\n"));
                assertThat(head, containsString("Expect: 100-continue\r\n"));
                send(terminal, "HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                assertThat(terminal.getInputStream().readNBytes(5), is("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));
                send(terminal, response("200 OK", "length"));
            }
        });
             TestServer origin = new TestServer(listener -> {
                 try (Socket first = accept(listener)) {
                     assertThat(readHead(first.getInputStream()), startsWith("POST /upload HTTP/1.1\r\n"));
                     send(first, redirectResponse(307, "http://127.0.0.1:" + destination.listener.getLocalPort() + "/terminal"));
                     assertThat(first.getInputStream().read(), is(-1));
                 }
             })) {
            Http1Client client = client(origin, exchanges, new AtomicInteger());
            try {
                try (Http1ClientResponse response = client.post("/upload").outputStream(output -> {
                    callbacks.incrementAndGet();
                    output.close();
                })) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.entity().as(String.class), is(ENTITY));
                }
                assertThat("empty cross-origin upload callback count", callbacks.get(), is(1));
                assertCompleted(exchanges.get("/upload"));
                assertCompleted(exchanges.get("/terminal"));
                assertThat("origin service response", exchanges.get("/upload").status().code(), is(307));
                assertThat("destination service response", exchanges.get("/terminal").status(), is(Status.OK_200));
                origin.await();
                destination.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void crossOriginRedirectRejectsKnownPositiveLengthBeforeFollowing() throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger callbacks = new AtomicInteger();
        try (ServerSocket destination = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
             TestServer origin = new TestServer(listener -> {
                 try (Socket first = accept(listener)) {
                     String head = readHead(first.getInputStream());
                     assertThat(head, startsWith("POST /upload HTTP/1.1\r\n"));
                     assertThat(head, containsString("Content-Length: " + UPLOAD.length + "\r\n"));
                     send(first, redirectResponse(307, "http://127.0.0.1:" + destination.getLocalPort() + "/terminal"));
                     assertThat("known entity must not be sent before the redirect", first.getInputStream().read(), is(-1));
                 }
             })) {
            Http1Client client = client(origin, exchanges, new AtomicInteger());
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client.post("/upload")
                        .header(HeaderNames.CONTENT_LENGTH, Integer.toString(UPLOAD.length))
                        .outputStream(output -> {
                            callbacks.incrementAndGet();
                            output.write(UPLOAD);
                            output.close();
                        }));
                assertThat(failure.getMessage(), is("Cross-origin redirect with request entity is disabled."));
                assertThat("known cross-origin entity must be rejected before the callback", callbacks.get(), is(0));
                assertCompleted(exchanges.get("/upload"));
                destination.setSoTimeout(100);
                assertThrows(SocketTimeoutException.class, () -> {
                    try (Socket _ = destination.accept()) {
                        throw new AssertionError("Forbidden cross-origin destination was contacted");
                    }
                });
                origin.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void directOutputStreamResponseIsDecoratedOnce() throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger decoders = new AtomicInteger();
        try (TestServer server = new TestServer(listener -> {
            try (Socket connection = accept(listener)) {
                assertThat(readHead(connection.getInputStream()), startsWith("POST /upload HTTP/1.1\r\n"));
                readUpload(connection.getInputStream());
                send(connection, response("200 OK", "gzip", true));
                assertThat(readHead(connection.getInputStream()), startsWith("GET /next HTTP/1.1\r\n"));
                send(connection, response("200 OK", "length"));
            }
        })) {
            Http1Client client = client(server, exchanges, decoders);
            try {
                try (Http1ClientResponse response = client.post("/upload")
                        .sendExpectContinue(false)
                        .outputStream(output -> {
                            output.write(UPLOAD);
                            output.close();
                        })) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.entity().as(byte[].class),
                               is(ENTITY.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)));
                }

                Exchange upload = exchanges.get("/upload");
                assertCompleted(upload);
                assertThat(upload.status(), is(Status.OK_200));
                assertThat("direct decorator byte count", upload.transformedBytes().get(), is(ENTITY.length()));
                assertThat("direct response must preserve one decoder", decoders.get(), is(1));
                assertThat("direct response connection release count", upload.connection().released, is(1));
                assertThat("direct response connection close count", upload.connection().closed, is(0));
                try (Http1ClientResponse response = client.get("/next").request()) {
                    assertThat(response.entity().as(String.class), is(ENTITY));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void redirectedSubmitResponseIsDecoratedOnce() throws Exception {
        Map<String, Exchange> exchanges = new LinkedHashMap<>();
        AtomicInteger decoders = new AtomicInteger();
        try (TestServer server = new TestServer(listener -> {
            try (Socket upload = accept(listener)) {
                assertThat(readHead(upload.getInputStream()), startsWith("POST /upload HTTP/1.1\r\n"));
                assertThat(upload.getInputStream().readNBytes(UPLOAD.length), is(UPLOAD));
                send(upload, ("HTTP/1.1 303 See Other\r\nLocation: /terminal\r\n"
                        + "Connection: close\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                assertThat(upload.getInputStream().read(), is(-1));
            }
            try (Socket terminal = accept(listener)) {
                assertThat(readHead(terminal.getInputStream()), startsWith("GET /terminal HTTP/1.1\r\n"));
                send(terminal, response("200 OK", "gzip", true));
                assertThat(readHead(terminal.getInputStream()), startsWith("GET /next HTTP/1.1\r\n"));
                send(terminal, response("200 OK", "length"));
            }
        })) {
            Http1Client client = client(server, exchanges, decoders);
            try {
                try (Http1ClientResponse response = client.post("/upload").sendExpectContinue(false).submit(UPLOAD)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.entity().as(byte[].class),
                               is(ENTITY.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)));
                }

                Exchange upload = exchanges.get("/upload");
                Exchange terminal = exchanges.get("/terminal");
                assertCompleted(upload);
                assertCompleted(terminal);
                assertThat("submit service must receive its redirect response", upload.status(), is(Status.SEE_OTHER_303));
                assertThat("terminal service must receive its own response", terminal.status(), is(Status.OK_200));
                assertThat("submit upload must not transform terminal bytes", upload.transformedBytes().get(), is(0));
                assertThat("submit decorator byte count", terminal.transformedBytes().get(), is(ENTITY.length()));
                assertThat("submit response must preserve one decoder", decoders.get(), is(1));
                assertThat("submit terminal connection release count", terminal.connection().released, is(1));
                assertThat("submit terminal connection close count", terminal.connection().closed, is(0));
                try (Http1ClientResponse response = client.get("/next").request()) {
                    assertThat(response.entity().as(String.class), is(ENTITY));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    private static Stream<Arguments> earlyFinalResponses() {
        return Stream.of(false, true)
                .flatMap(redirect -> Stream.of("empty", "length", "chunked")
                        .flatMap(framing -> Stream.of(false, true).map(consume -> Arguments.of(redirect, framing, consume))));
    }

    private static Stream<Arguments> redirectResponses() {
        return Stream.of(false, true)
                .flatMap(early -> Stream.of("empty", "length", "chunked", "trailers", "gzip")
                        .flatMap(framing -> Stream.of(false, true).map(consume -> Arguments.of(early, framing, consume))));
    }

    private static Stream<Arguments> crossOriginEntityRedirects() {
        return Stream.of(307, 308)
                .flatMap(status -> Stream.of(false, true).map(allowEntity -> Arguments.of(status, allowEntity)));
    }

    private static Http1Client client(TestServer server, Map<String, Exchange> exchanges, AtomicInteger decoders) {
        return client(server, exchanges, decoders, false);
    }

    private static Http1Client client(TestServer server,
                                      Map<String, Exchange> exchanges,
                                      AtomicInteger decoders,
                                      boolean followCrossOriginEntityRedirects) {
        return Http1Client.builder()
                .baseUri("http://127.0.0.1:" + server.listener.getLocalPort())
                .readTimeout(TIMEOUT)
                .readContinueTimeout(TIMEOUT)
                .sendExpectContinue(true)
                .followCrossOriginEntityRedirects(followCrossOriginEntityRedirects)
                .contentEncoding(ContentEncodingContext.builder().addContentEncoding(new GzipEncoding(decoders)))
                .addService((chain, request) -> {
                    WebClientServiceResponse response = chain.proceed(request);
                    CountingConnection connection = new CountingConnection((ClientConnection) response.connection());
                    AtomicInteger completions = new AtomicInteger();
                    AtomicInteger transformedBytes = new AtomicInteger();
                    request.whenComplete().thenRun(completions::incrementAndGet);
                    exchanges.put(request.uri().path().path(),
                                  new Exchange(connection,
                                               request.whenComplete().toCompletableFuture(),
                                               completions,
                                               response.status(),
                                               transformedBytes));
                    WebClientServiceResponse.Builder builder = WebClientServiceResponse.builder(response).connection(connection);
                    if (response.headers().contains(HeaderNames.create("X-Toggle-Case"))) {
                        response.inputStream()
                                .ifPresent(input -> builder.inputStream(new CaseTogglingInputStream(input, transformedBytes)));
                    }
                    return builder.build();
                })
                .build();
    }

    private static void assertCompleted(Exchange exchange) {
        assertThat("service completion future", exchange.completion().isDone(), is(true));
        assertThat("service completion callback count", exchange.completions().get(), is(1));
        assertThat("service completion should succeed", exchange.completion().isCompletedExceptionally(), is(false));
    }

    private static Socket accept(ServerSocket listener) throws IOException {
        Socket socket = listener.accept();
        socket.setSoTimeout((int) TIMEOUT.toMillis());
        return socket;
    }

    private static String readHead(InputStream input) throws IOException {
        StringBuilder head = new StringBuilder();
        while (head.length() < 16384) {
            int next = input.read();
            if (next == -1) {
                return head.toString();
            }
            head.append((char) next);
            if (head.toString().endsWith("\r\n\r\n")) {
                return head.toString();
            }
        }
        throw new IOException("Request headers exceed test limit");
    }

    private static void readUpload(InputStream input) throws IOException {
        assertThat(input.readNBytes(3), is("6\r\n".getBytes(StandardCharsets.US_ASCII)));
        assertThat(input.readNBytes(UPLOAD.length), is(UPLOAD));
        assertThat(input.readNBytes(7), is("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] redirectResponse(int status, String location) {
        return ("HTTP/1.1 " + status + " Redirect\r\nLocation: " + location + "\r\nContent-Length: 0\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] response(String status, String framing) throws IOException {
        return response(status, framing, false);
    }

    private static byte[] response(String status, String framing, boolean transform) throws IOException {
        byte[] body = ENTITY.getBytes(StandardCharsets.US_ASCII);
        String headers;
        switch (framing) {
        case "empty" -> {
            body = new byte[0];
            headers = "Content-Length: 0\r\n";
        }
        case "chunked", "trailers" -> {
            String trailers = framing.equals("trailers") ? "Checksum: valid\r\n" : "";
            body = (Integer.toHexString(body.length) + "\r\n" + ENTITY + "\r\n0\r\n" + trailers + "\r\n")
                    .getBytes(StandardCharsets.US_ASCII);
            headers = "Transfer-Encoding: chunked\r\n" + (framing.equals("trailers") ? "Trailer: Checksum\r\n" : "");
        }
        case "gzip" -> {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write(body);
            }
            body = compressed.toByteArray();
            headers = "Content-Encoding: gzip\r\nContent-Length: " + body.length + "\r\n";
        }
        default -> headers = "Content-Length: " + body.length + "\r\n";
        }
        if (transform) {
            headers += "X-Toggle-Case: true\r\n";
        }
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        response.write(("HTTP/1.1 " + status + "\r\n" + headers + "\r\n").getBytes(StandardCharsets.US_ASCII));
        response.write(body);
        return response.toByteArray();
    }

    private static void send(Socket socket, byte[] response) throws IOException {
        socket.getOutputStream().write(response);
        socket.getOutputStream().flush();
    }

    @FunctionalInterface
    private interface ServerTask {
        void run(ServerSocket listener) throws Exception;
    }

    private record Exchange(CountingConnection connection,
                            CompletableFuture<WebClientServiceResponse> completion,
                            AtomicInteger completions,
                            Status status,
                            AtomicInteger transformedBytes) {
    }

    private static final class TestServer implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final Future<?> task;

        private TestServer(ServerTask handler) throws IOException {
            listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            listener.setSoTimeout((int) TIMEOUT.toMillis());
            task = executor.submit(() -> {
                handler.run(listener);
                return null;
            });
        }

        void await() throws Exception {
            task.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        @Override
        public void close() throws IOException {
            listener.close();
            executor.shutdownNow();
            executor.close();
        }
    }

    private static final class CountingConnection implements ClientConnection {
        private final ClientConnection delegate;
        private int released;
        private int closed;

        private CountingConnection(ClientConnection delegate) {
            this.delegate = delegate;
        }

        @Override
        public DataReader reader() {
            return delegate.reader();
        }

        @Override
        public DataWriter writer() {
            return delegate.writer();
        }

        @Override
        public String channelId() {
            return delegate.channelId();
        }

        @Override
        public HelidonSocket helidonSocket() {
            return delegate.helidonSocket();
        }

        @Override
        public void readTimeout(Duration readTimeout) {
            delegate.readTimeout(readTimeout);
        }

        @Override
        public void releaseResource() {
            released++;
            delegate.releaseResource();
        }

        @Override
        public void closeResource() {
            closed++;
            delegate.closeResource();
        }
    }

    private static final class CaseTogglingInputStream extends FilterInputStream {
        private final AtomicInteger transformedBytes;

        private CaseTogglingInputStream(InputStream input, AtomicInteger transformedBytes) {
            super(input);
            this.transformedBytes = transformedBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value == -1) {
                return -1;
            }
            transformedBytes.incrementAndGet();
            return toggleCase(value);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = super.read(bytes, offset, length);
            if (count > 0) {
                transformedBytes.addAndGet(count);
                for (int i = 0; i < count; i++) {
                    bytes[offset + i] = (byte) toggleCase(bytes[offset + i]);
                }
            }
            return count;
        }

        private static int toggleCase(int value) {
            return Character.isUpperCase(value) ? Character.toLowerCase(value) : Character.toUpperCase(value);
        }
    }

    private record GzipEncoding(AtomicInteger decoders) implements ContentEncoding {
        @Override
        public String name() {
            return "gzip";
        }

        @Override
        public String type() {
            return "gzip";
        }

        @Override
        public Set<String> ids() {
            return Set.of("gzip");
        }

        @Override
        public boolean supportsEncoding() {
            return false;
        }

        @Override
        public boolean supportsDecoding() {
            return true;
        }

        @Override
        public ContentDecoder decoder() {
            return network -> {
                decoders.incrementAndGet();
                try {
                    return new GZIPInputStream(network);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }

        @Override
        public ContentEncoder encoder() {
            return ContentEncoder.NO_OP;
        }
    }
}
