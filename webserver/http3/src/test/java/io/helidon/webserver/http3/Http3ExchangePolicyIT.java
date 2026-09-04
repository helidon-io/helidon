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

package io.helidon.webserver.http3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.Functions.CheckedFunction;
import io.helidon.common.Size;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.ErrorHandling;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static io.helidon.webserver.http3.Http3ClientTestSupport.strictClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class Http3ExchangePolicyIT {
    private static final String SERVER_KEYSTORE = "io/helidon/webserver/http3/server-keystore.p12";
    private static final String CLIENT_TRUSTSTORE = "io/helidon/webserver/http3/client-truststore.p12";
    private static final HeaderName DIRECT_HANDLER = HeaderNames.create("x-direct-handler");
    private static final HeaderName TEST_TRAILER = HeaderNames.create("x-test-trailer");

    @Test
    void normalizesDirectHandlerHeadAndBodylessResponses() throws Exception {
        try (Environment environment = Environment.create(server -> server
                     .contentEncoding(ContentEncodingContext.builder()
                                              .addContentEncoding(GzipEncoding.create())
                                              .build())
                     .errorHandling(ErrorHandling.builder().includeEntity(true).build())
                     .directHandlers(DirectHandlers.builder()
                                             .addHandler(DirectHandler.EventType.OTHER,
                                                         (request, _, _, headers, _) -> {
                                                             Status status = switch (request.path()) {
                                                                 case "/direct-204" -> Status.NO_CONTENT_204;
                                                                 case "/direct-205" -> Status.RESET_CONTENT_205;
                                                                 case "/direct-304" -> Status.NOT_MODIFIED_304;
                                                                 case "/direct-info" -> Status.CONTINUE_100;
                                                                 default -> Status.BAD_REQUEST_400;
                                                             };
                                                             var builder = DirectHandler.TransportResponse.builder()
                                                                     .status(status)
                                                                     .headers(headers)
                                                                     .header(HeaderNames.CONTENT_LENGTH, "23");
                                                             if (!request.path().equals("/direct-head-empty")) {
                                                                 builder.entity("direct")
                                                                         .header(HeaderNames.CONTENT_LENGTH, "23");
                                                             }
                                                             if (!request.path().startsWith("/direct-head")) {
                                                                 builder.header(HeaderValues.TRANSFER_ENCODING_CHUNKED)
                                                                         .header(HeaderNames.TRAILER,
                                                                                 TEST_TRAILER.defaultCase());
                                                             }
                                                             return builder.build();
                                                         })
                                             .build()),
                                                         routing -> routing
                                                                 .post("/direct-204", (req, res) -> decode(req))
                                                                 .post("/direct-205", (req, res) -> decode(req))
                                                                 .post("/direct-304", (req, res) -> decode(req))
                                                                 .post("/direct-info", (req, res) -> decode(req))
                                                                 .head("/direct-head", (req, res) -> decode(req))
                                                                 .head("/direct-head-empty", (req, res) -> decode(req)))) {
            Http3Client client = client();
            try {
                assertDirectBodylessResponse(client, environment, "/direct-204", Status.NO_CONTENT_204, null);
                assertDirectBodylessResponse(client, environment, "/direct-205", Status.RESET_CONTENT_205, "0");
                assertDirectBodylessResponse(client, environment, "/direct-304", Status.NOT_MODIFIED_304, "23");
                assertDirectBodylessResponse(client,
                                             environment,
                                             "/direct-info",
                                             Status.INTERNAL_SERVER_ERROR_500,
                                             "0");

                try (Http3ClientResponse response = client.head(environment.uri("/direct-head").toString())
                        .header(HeaderNames.CONTENT_ENCODING, "unsupported")
                        .request()) {
                    assertAll(
                            () -> assertThat(response.status(), equalTo(Status.BAD_REQUEST_400)),
                            () -> assertThat(response.headers().first(HeaderNames.CONTENT_LENGTH).orElseThrow(),
                                             equalTo("6")),
                            () -> assertThat(response.headers().contains(HeaderNames.TRAILER), is(false))
                    );
                }
                try (Http3ClientResponse response = client.head(environment.uri("/direct-head-empty").toString())
                        .header(HeaderNames.CONTENT_ENCODING, "unsupported")
                        .request()) {
                    assertAll(
                            () -> assertThat(response.status(), equalTo(Status.BAD_REQUEST_400)),
                            () -> assertThat(response.headers().first(HeaderNames.CONTENT_LENGTH).orElseThrow(),
                                             equalTo("23")),
                            () -> assertThat(response.headers().contains(HeaderNames.TRAILER), is(false))
                    );
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void appliesDirectHandlerToUnsupportedContentEncodingAndReusesConnection() throws Exception {
        AtomicBoolean routed = new AtomicBoolean();
        AtomicReference<DirectHandlerMetadata> metadata = new AtomicReference<>();
        try (Environment environment = Environment.create(server -> server
                     .contentEncoding(ContentEncodingContext.builder()
                                              .addContentEncoding(GzipEncoding.create())
                                              .build())
                     .errorHandling(ErrorHandling.builder().includeEntity(true).build())
                     .directHandlers(DirectHandlers.builder()
                                             .addHandler(DirectHandler.EventType.OTHER,
                                                         (request, eventType, status, headers, message) -> {
                                                             metadata.set(new DirectHandlerMetadata(
                                                                     request.protocolVersion(),
                                                                     request.method(),
                                                                     request.path(),
                                                                     request.headers()
                                                                             .first(HeaderNames.CONTENT_ENCODING)
                                                                             .orElseThrow(),
                                                                     eventType,
                                                                     status,
                                                                     headers.size(),
                                                                     message));
                                                             return DirectHandler.TransportResponse.builder()
                                                                         .status(Status.UNPROCESSABLE_CONTENT_422)
                                                                         .headers(headers)
                                                                         .header(DIRECT_HANDLER, "http3")
                                                                         .entity("direct:" + message)
                                                                         .build();
                                                         })
                                             .build()),
                                                         routing -> routing
                                                                 .post("/decode", (req, res) -> {
                                                                     routed.set(true);
                                                                     res.send(req.content().as(String.class));
                                                                 })
                                                                 .get("/socket-id", (req, res) -> res.send(req.socketId())))) {
            Http3Client client = client();
            try {
                String socketId = socketId(client, environment);
                try (Http3ClientResponse response = client.post(environment.uri("/decode").toString())
                        .header(HeaderNames.CONTENT_ENCODING, "unsupported")
                        .submit("payload")) {
                    assertThat(response.status(), equalTo(Status.UNPROCESSABLE_CONTENT_422));
                    assertThat(response.headers().first(DIRECT_HANDLER).orElseThrow(), equalTo("http3"));
                    assertThat(response.as(String.class), equalTo("direct:Unsupported content encoding"));
                    assertThat(routed.get(), is(false));
                }

                assertThat(metadata.get(), equalTo(new DirectHandlerMetadata("HTTP/3",
                                                                              "POST",
                                                                              "/decode",
                                                                              "unsupported",
                                                                              DirectHandler.EventType.OTHER,
                                                                              Status.UNSUPPORTED_MEDIA_TYPE_415,
                                                                              0,
                                                                              "Unsupported content encoding")));
                try (Http3ClientResponse response = client.get(environment.uri("/socket-id").toString()).request()) {
                    assertThat(response.status(), equalTo(Status.OK_200));
                    assertThat(response.as(String.class), equalTo(socketId));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void limitsDecodedPayloadAndReusesConnection() throws Exception {
        byte[] encoded = encode("A".repeat(4096), GZIPOutputStream::new);
        assertThat(encoded.length < 2048, is(true));
        try (Environment environment = Environment.create(server -> server
                     .maxPayloadSize(2048)
                     .contentEncoding(ContentEncodingContext.builder()
                                              .addContentEncoding(GzipEncoding.create())
                                              .build()),
                                                         routing -> routing
                                                                 .post("/decode", (req, res) -> {
                                                                     req.content().as(String.class);
                                                                     res.send();
                                                                 })
                                                                 .get("/socket-id", (req, res) -> res.send(req.socketId())))) {
            Http3Client client = client();
            try {
                String socketId = socketId(client, environment);
                try (Http3ClientResponse response = client.post(environment.uri("/decode").toString())
                        .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                        .header(HeaderNames.CONTENT_ENCODING, "gzip")
                        .submit(encoded)) {
                    assertThat(response.status(), equalTo(Status.REQUEST_ENTITY_TOO_LARGE_413));
                }

                try (Http3ClientResponse response = client.get(environment.uri("/socket-id").toString()).request()) {
                    assertThat(response.status(), equalTo(Status.OK_200));
                    assertThat(response.as(String.class), equalTo(socketId));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void limitsDeflateDecodedPayloadAndReusesConnection() throws Exception {
        byte[] encoded = encode("A".repeat(4096), DeflaterOutputStream::new);
        assertThat(encoded.length < 2048, is(true));
        try (Environment environment = Environment.create(server -> server
                     .maxPayloadSize(2048)
                     .contentEncoding(ContentEncodingContext.builder()
                                              .addContentEncoding(DeflateEncoding.create())
                                              .build()),
                                                         routing -> routing
                                                                 .post("/decode", (req, res) -> {
                                                                     req.content().as(String.class);
                                                                     res.send();
                                                                 })
                                                                 .get("/socket-id", (req, res) -> res.send(req.socketId())))) {
            Http3Client client = client();
            try {
                String socketId = socketId(client, environment);
                try (Http3ClientResponse response = client.post(environment.uri("/decode").toString())
                        .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                        .header(HeaderNames.CONTENT_ENCODING, "deflate")
                        .submit(encoded)) {
                    assertThat(response.status(), equalTo(Status.REQUEST_ENTITY_TOO_LARGE_413));
                }

                try (Http3ClientResponse response = client.get(environment.uri("/socket-id").toString()).request()) {
                    assertThat(response.status(), equalTo(Status.OK_200));
                    assertThat(response.as(String.class), equalTo(socketId));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void limitsDecodedPayloadWhenBufferingAndReusesConnection() throws Exception {
        byte[] encoded = encode("B".repeat(4096), GZIPOutputStream::new);
        assertThat(encoded.length < 2048, is(true));
        Http3Config http3Config = Http3Config.builder()
                .maxBufferedEntitySize(Size.create(8192, Size.Unit.BYTE))
                .buildPrototype();
        try (Environment environment = Environment.create(http3Config,
                                                         server -> server
                                                                 .maxPayloadSize(2048)
                                                                 .contentEncoding(ContentEncodingContext.builder()
                                                                                          .addContentEncoding(
                                                                                                  GzipEncoding.create())
                                                                                          .build()),
                                                         routing -> routing
                                                                 .post("/buffer", (req, res) -> {
                                                                     req.content().buffer();
                                                                     req.content().as(String.class);
                                                                     res.send();
                                                                 })
                                                                 .get("/socket-id", (req, res) -> res.send(req.socketId())))) {
            Http3Client client = client();
            try {
                String socketId = socketId(client, environment);
                try (Http3ClientResponse response = client.post(environment.uri("/buffer").toString())
                        .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                        .header(HeaderNames.CONTENT_ENCODING, "gzip")
                        .submit(encoded)) {
                    assertThat(response.status(), equalTo(Status.REQUEST_ENTITY_TOO_LARGE_413));
                }

                try (Http3ClientResponse response = client.get(environment.uri("/socket-id").toString()).request()) {
                    assertThat(response.status(), equalTo(Status.OK_200));
                    assertThat(response.as(String.class), equalTo(socketId));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void discoversRequestedUriFromDecodedRoutingPath() throws Exception {
        try (Environment environment = Environment.create(server -> {
        }, routing -> routing.any((req, res) -> res.send(req.requestedUri().path().rawPath()
                                                                  + "|"
                                                                  + req.requestedUri().path().path()
                                                                  + "|"
                                                                  + req.requestedUri().toUri().getRawPath())))) {
            Http3Client client = client();
            URI uri = URI.create("https://localhost:" + environment.server().port() + "/encoded%20path");
            try (Http3ClientResponse response = client.get().uri(uri).request()) {
                assertThat(response.status(), equalTo(Status.OK_200));
                assertThat(response.as(String.class), equalTo("/encoded%20path|/encoded path|/encoded%20path"));
            } finally {
                client.closeResource();
            }
        }
    }

    private static Http3Client client() {
        return strictClientBuilder()
                .tls(Tls.builder()
                             .trust(trust -> trust.keystore(store -> store
                                     .passphrase("changeit")
                                     .trustStore(true)
                                     .keystore(Resource.create(CLIENT_TRUSTSTORE))))
                             .applicationProtocols(List.of(Http3Client.PROTOCOL_ID))
                             .enabledProtocols(List.of("TLSv1.3"))
                             .build())
                .build();
    }

    private static String socketId(Http3Client client, Environment environment) throws Exception {
        try (Http3ClientResponse response = client.get(environment.uri("/socket-id").toString()).request()) {
            assertThat(response.status(), equalTo(Status.OK_200));
            return response.as(String.class);
        }
    }

    private static void decode(io.helidon.webserver.http.ServerRequest request) {
        request.content().as(String.class);
    }

    private static void assertDirectBodylessResponse(Http3Client client,
                                                     Environment environment,
                                                     String path,
                                                     Status status,
                                                     String contentLength) throws Exception {
        try (Http3ClientResponse response = client.post(environment.uri(path).toString())
                .header(HeaderNames.CONTENT_ENCODING, "unsupported")
                .submit("payload")) {
            assertThat(response.status(), equalTo(status));
            if (contentLength == null) {
                assertThat(response.headers().contains(HeaderNames.CONTENT_LENGTH), is(false));
            } else {
                assertThat(response.headers().first(HeaderNames.CONTENT_LENGTH).orElseThrow(), equalTo(contentLength));
            }
            assertAll(
                    () -> assertThat(response.headers().contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                    () -> assertThat(response.headers().contains(HeaderNames.TRAILER), is(false))
            );
        }
    }

    private static byte[] encode(String entity, CheckedFunction<OutputStream, OutputStream, IOException> factory) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (OutputStream encoded = factory.apply(output)) {
                encoded.write(entity.getBytes(StandardCharsets.UTF_8));
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record DirectHandlerMetadata(String protocolVersion,
                                         String method,
                                         String path,
                                         String contentEncoding,
                                         DirectHandler.EventType eventType,
                                         Status status,
                                         int responseHeaderCount,
                                         String message) {
    }

    private record Environment(WebServer server) implements AutoCloseable {
        private static Environment create(Consumer<WebServerConfig.Builder> serverConfig,
                                          Consumer<HttpRouting.Builder> routing) throws Exception {
            return create(Http3Config.create(), serverConfig, routing);
        }

        private static Environment create(Http3Config http3Config,
                                          Consumer<WebServerConfig.Builder> serverConfig,
                                          Consumer<HttpRouting.Builder> routing) throws Exception {
            Keys keys = Keys.builder()
                    .keystore(store -> store
                            .passphrase("changeit")
                            .keyAlias("server")
                            .certChainAlias("server")
                            .keystore(Resource.create(SERVER_KEYSTORE)))
                    .build();
            WebServerConfig.Builder builder = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(0)
                    .featuresDiscoverServices(false)
                    .protocolsDiscoverServices(false)
                    .tls(Tls.builder()
                                 .privateKey(keys.privateKey().orElseThrow())
                                 .privateKeyCertChain(keys.certChain())
                                 .build())
                    .addProtocol(http3Config)
                    .routing(routing);
            serverConfig.accept(builder);
            return new Environment(builder.build().start());
        }

        private URI uri(String path) throws Exception {
            return new URI("https", null, "localhost", server.port(), path, null, null);
        }

        @Override
        public void close() {
            server.stop();
        }
    }
}
