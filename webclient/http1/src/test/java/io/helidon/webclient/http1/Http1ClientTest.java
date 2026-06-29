/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Http1HeadersParser;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http1.Http1LoggingConnectionListener;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaContextConfig;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class Http1ClientTest {
    public static final String VALID_HEADER_VALUE = "Valid-Header-Value";
    public static final String VALID_HEADER_NAME = "Valid-Header-Name";
    public static final String BAD_HEADER_PATH = "/badHeader";
    public static final String HEADER_NAME_VALUE_DELIMETER = "->";
    public static final String PROXY_HOST = "http://www-proxy-hqdc.us.oracle.com";
    public static final String PROXY_PORT = "80";
    private static final Header REQ_CHUNKED_HEADER = HeaderValues.create(
            HeaderNames.create("X-Req-Chunked"), "true");
    private static final Header REQ_EXPECT_100_HEADER_NAME = HeaderValues.create(
            HeaderNames.create("X-Req-Expect100"), "true");
    private static final HeaderName REQ_CONTENT_LENGTH_HEADER_NAME = HeaderNames.create("X-Req-ContentLength");
    private static final long NO_CONTENT_LENGTH = -1L;
    private static final Http1Client client = Http1Client.builder()
            .sendExpectContinue(false)
            .build();
    private static final int dummyPort = 1234;
    private static final String TARGET_HOST = "www.oracle.com";
    private static final String TARGET_URI_PATH = "/test";
    private static final String CLIENT_SEND_LOGGER_NAME = Http1LoggingConnectionListener.class.getName() + ".cl-send";

    @Test
    void testMaxHeaderSizeFail() {
        Http1Client client = Http1Client.create(builder -> builder.protocolConfig(pc -> pc.maxHeaderSize(15)));

        validateFailedResponse(client, new FakeHttp1ClientConnection(), "Header size exceeded");
    }

    @Test
    void testMaxHeaderSizeSuccess() {
        Http1Client client = Http1Client.builder()
                .protocolConfig(pc -> pc.maxHeaderSize(500))
                .build();

        validateSuccessfulResponse(client, new FakeHttp1ClientConnection());
    }

    @Test
    void testMaxStatusLineLengthFail() {
        Http1Client client = Http1Client.builder()
                .protocolConfig(it -> it.maxStatusLineLength(1))
                .build();
        validateFailedResponse(client, new FakeHttp1ClientConnection(), "HTTP Response did not contain HTTP status line");
    }

    @Test
    void testMaxHeaderLineLengthSuccess() {
        Http1Client client = Http1Client.builder()
                .protocolConfig(it -> it.maxStatusLineLength(20))
                .build();
        validateSuccessfulResponse(client, new FakeHttp1ClientConnection());
    }

    @Test
    void testMediaContext() {
        Http1Client client = Http1Client.builder()
                .mediaContext(new CustomizedMediaContext())
                .build();
        validateSuccessfulResponse(client, new FakeHttp1ClientConnection());
    }

    @Test
    void testResponseWithoutKeepAliveHeaderReusesConnection() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(false);

        Http1ClientResponse response = client.put("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .submit("Sending Something");

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.headers(), noHeader(HeaderNames.CONNECTION));
        assertThat(response.entity().as(String.class), is("Sending Something"));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testCloseDelimitedResponseClosesConnectionAfterEntityConsumed() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection();
        Http1ClientResponse response = new Http1ClientResponseImpl(HttpClientConfig.builder().build(),
                                                                   Http1ClientProtocolConfig.create(),
                                                                   Status.OK_200,
                                                                   Method.GET,
                                                                   ClientRequestHeaders.create(WritableHeaders.create()),
                                                                   ClientResponseHeaders.create(WritableHeaders.create()),
                                                                   connection,
                                                                   new ByteArrayInputStream(
                                                                           "body".getBytes(StandardCharsets.US_ASCII)),
                                                                   MediaContext.create(),
                                                                   ClientUri.create(URI.create("http://localhost/test")),
                                                                   new CompletableFuture<>());

        try (response) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_LENGTH));
            assertThat(response.entity().as(String.class), is("body"));
        }

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void testTransferEncodingOverridesContentLengthZero() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.OK_200,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.CONTENT_LENGTH_ZERO,
                                                                                     HeaderValues.TRANSFER_ENCODING_CHUNKED),
                                                                             "4\r\nbody\r\n0\r\n\r\n"
                                                                                     .getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("body"));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testTransferEncodingIgnoresInvalidContentLength() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.OK_200,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.create(
                                                                                     HeaderNames.CONTENT_LENGTH,
                                                                                     "invalid"),
                                                                                     HeaderValues.TRANSFER_ENCODING_CHUNKED),
                                                                             "4\r\nbody\r\n0\r\n\r\n"
                                                                                     .getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("body"));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testUnreadTransferEncodingWithContentLengthZeroClosesConnection() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.OK_200,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.CONTENT_LENGTH_ZERO,
                                                                                     HeaderValues.TRANSFER_ENCODING_CHUNKED),
                                                                             null);

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void testUnreadNonChunkedTransferEncodingWithContentLengthZeroClosesConnection() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.OK_200,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.CONTENT_LENGTH_ZERO,
                                                                                     HeaderValues.create(
                                                                                             HeaderNames.TRANSFER_ENCODING,
                                                                                             "gzip")),
                                                                             null);

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void testHeadResponseWithoutLengthReusesConnection() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.OK_200, false);

        try (Http1ClientResponse response = client.head("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_LENGTH));
            assertThat(response.entity().hasEntity(), is(false));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testHeadResponseWithTrailerHeaderReusesConnection() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.OK_200,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.create(
                                                                                     HeaderNames.TRAILER,
                                                                                     "X-Test")),
                                                                             null);

        try (Http1ClientResponse response = client.head("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.TRAILER, "X-Test"));
            assertThrows(IllegalStateException.class, response::trailers);
            assertThat(response.entity().hasEntity(), is(false));
            assertThat(response.trailers().size(), is(0));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testHeadResetContentWithFramingMetadataReusesConnection() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.TRANSFER_ENCODING_CHUNKED),
                                                                             null);

        try (Http1ClientResponse response = client.head("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.RESET_CONTENT_205));
            assertThat(response.entity().hasEntity(), is(false));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testHeadResponseWithBufferedEntityClosesConnection() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.OK_200,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.create(
                                                                                     HeaderNames.CONTENT_LENGTH, "4")),
                                                                             "body".getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.head("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, "4"));
            assertThat(response.entity().hasEntity(), is(false));
        }

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @ParameterizedTest
    @MethodSource("noBodyStatusResponses")
    void testHeaderTerminatedNoBodyStatusResponseWithoutLengthReusesConnection(Status status) {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(status, false);

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(status));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_LENGTH));
            assertThat(response.entity().hasEntity(), is(false));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testResetContentResponseWithoutLengthClosesConnection() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205, false);

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.RESET_CONTENT_205));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_LENGTH));
            assertThat(response.entity().hasEntity(), is(false));
        }

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void testResetContentResponseWithContentLengthZeroReusesConnection() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205, true);

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.RESET_CONTENT_205));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, "0"));
            assertThat(response.entity().hasEntity(), is(false));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testResetContentResponseWithChunkedTerminatorReusesConnectionWithoutEntityAccess() throws IOException {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.TRANSFER_ENCODING_CHUNKED),
                                                                             "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.RESET_CONTENT_205));
            assertThat(response.headers(), hasHeader(HeaderNames.TRANSFER_ENCODING, "chunked"));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testResetContentResponseWithChunkedTerminatorExtensionReusesConnection() throws IOException {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.TRANSFER_ENCODING_CHUNKED),
                                                                             "0;ext=value\r\n\r\n"
                                                                                     .getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.RESET_CONTENT_205));
            assertThat(response.headers(), hasHeader(HeaderNames.TRANSFER_ENCODING, "chunked"));
            assertThat(response.entity().hasEntity(), is(false));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testResetContentResponseWithContentLengthBodyClosesConnectionWithoutEntity() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.create(
                                                                                     HeaderNames.CONTENT_LENGTH, "4")),
                                                                             "body".getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.RESET_CONTENT_205));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, "4"));
            assertThat(response.entity().hasEntity(), is(false));
        }

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void testResetContentResponseWithChunkedBodyClosesConnectionWithoutEntity() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.TRANSFER_ENCODING_CHUNKED),
                                                                             "4\r\nbody\r\n0\r\n\r\n"
                                                                                     .getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.RESET_CONTENT_205));
            assertThat(response.headers(), hasHeader(HeaderNames.TRANSFER_ENCODING, "chunked"));
            assertThat(response.entity().hasEntity(), is(false));
        }

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void testResetContentResponseWithChunkedTrailersReusesConnectionAfterTrailersRead() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.TRANSFER_ENCODING_CHUNKED,
                                                                                     HeaderValues.create(
                                                                                             HeaderNames.TRAILER,
                                                                                             "X-Test")),
                                                                             "0;ext=value\r\nX-Test: value\r\n\r\n"
                                                                                     .getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.RESET_CONTENT_205));
            assertThat(response.headers(), hasHeader(HeaderNames.TRANSFER_ENCODING, "chunked"));
            assertThat(response.entity().hasEntity(), is(false));
            assertThat(response.trailers(), hasHeader(HeaderNames.create("X-Test"), "value"));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testResetContentResponseWithChunkedBodyDoesNotExposeBodyAsTrailers() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.TRANSFER_ENCODING_CHUNKED,
                                                                                     HeaderValues.create(
                                                                                             HeaderNames.TRAILER,
                                                                                             "X-Test")),
                                                                             ("10\r\n"
                                                                                     + "X-Test: body\r\n\r\n"
                                                                                     + "\r\n0\r\n"
                                                                                     + "X-Test: trailer\r\n\r\n")
                                                                                     .getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.RESET_CONTENT_205));
            assertThat(response.entity().hasEntity(), is(false));
            assertThrows(IllegalStateException.class, response::trailers);
        }

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void testResetContentResponseWithChunkedBodyRejectsRepeatedTrailerAccess() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.TRANSFER_ENCODING_CHUNKED,
                                                                                     HeaderValues.create(
                                                                                             HeaderNames.TRAILER,
                                                                                             "X-Test")),
                                                                             ("13\r\n"
                                                                                     + "0\r\nX-Test: body\r\n\r\n"
                                                                                     + "\r\n0\r\n"
                                                                                     + "X-Test: trailer\r\n\r\n")
                                                                                     .getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.entity().hasEntity(), is(false));
            assertThrows(IllegalStateException.class, response::trailers);
            IllegalStateException exception = assertThrows(IllegalStateException.class, response::trailers);
            assertThat(exception.getMessage(), is("Response framing cannot be read after a previous failure"));
        }

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void testResetContentResponseWithInvalidChunkSizeRejectsRepeatedTrailerAccess() {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.RESET_CONTENT_205,
                                                                             true,
                                                                             false,
                                                                             List.of(HeaderValues.TRANSFER_ENCODING_CHUNKED,
                                                                                     HeaderValues.create(
                                                                                             HeaderNames.TRAILER,
                                                                                             "X-Test")),
                                                                             ("invalid\r\n"
                                                                                     + "0\r\n"
                                                                                     + "X-Test: value\r\n\r\n")
                                                                                     .getBytes(StandardCharsets.US_ASCII));

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.entity().hasEntity(), is(false));
            assertThrows(IllegalArgumentException.class, response::trailers);
            IllegalStateException exception = assertThrows(IllegalStateException.class, response::trailers);
            assertThat(exception.getMessage(), is("Response framing cannot be read after a previous failure"));
        }

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @ParameterizedTest
    @MethodSource("notModifiedResponseMetadata")
    void testNotModifiedResponseWithEntityMetadataReusesConnection(Header responseHeader) {
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection(Status.NOT_MODIFIED_304,
                                                                             true,
                                                                             false,
                                                                             List.of(responseHeader),
                                                                             null);

        try (Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(connection)
                .request()) {
            assertThat(response.status(), is(Status.NOT_MODIFIED_304));
            assertThat(response.headers(), hasHeader(HeaderNames.create(responseHeader.name()), responseHeader.get()));
            assertThat(response.entity().hasEntity(), is(false));
        }

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void testRequestHeaderLoggingRedactsUnsafeValues() {
        Logger logger = Logger.getLogger(CLIENT_SEND_LOGGER_NAME);
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.FINER);

        try {
            Http1LoggingConnectionListener listener = Http1LoggingConnectionListener.create(HttpLogConfig.create(),
                                                                                            "cl-send");
            Headers headers = WritableHeaders.create()
                    .add(HeaderNames.AUTHORIZATION, "Bearer secret-token")
                    .add(HeaderNames.COOKIE, "session=secret-cookie")
                    .add(HeaderNames.create("X-Safe"), "first\r\nForged: value")
                    .add(HeaderNames.CONTENT_TYPE, "text/plain");

            Http1CallChainBase.writeHeaders(new FakeHttp1ClientConnection(),
                                            headers,
                                            BufferData.growing(128),
                                            false,
                                            listener);

            assertThat(messages.size(), is(1));
            String message = messages.getFirst();
            assertThat(message, containsString("Authorization: <redacted>"));
            assertThat(message, containsString("Cookie: <redacted>"));
            assertThat(message, containsString("Content-Type: text/plain"));
            assertThat(message, not(containsString("Bearer secret-token")));
            assertThat(message, not(containsString("session=secret-cookie")));
            assertThat(message, not(containsString("\r")));
            assertThat(message, not(containsString("\nForged:")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void testRequestHeaderLoggingReportsRawValuesWhenUnsafeEnabled() {
        Logger logger = Logger.getLogger(CLIENT_SEND_LOGGER_NAME);
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.FINER);

        try {
            Http1LoggingConnectionListener listener = Http1LoggingConnectionListener.create(HttpLogConfig.builder()
                                                                                                    .unsafeRawData(true)
                                                                                                    .build(),
                                                                                            "cl-send");
            Headers headers = WritableHeaders.create()
                    .add(HeaderNames.AUTHORIZATION, "Bearer secret-token")
                    .add(HeaderNames.COOKIE, "session=secret-cookie");

            Http1CallChainBase.writeHeaders(new FakeHttp1ClientConnection(),
                                            headers,
                                            BufferData.growing(128),
                                            false,
                                            listener);

            assertThat(messages.size(), is(1));
            String message = messages.getFirst();
            assertThat(message, containsString("Authorization: Bearer secret-token"));
            assertThat(message, containsString("Cookie: session=secret-cookie"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void testProtocolLogConfigUsed() {
        Http1ClientConfig config = Http1ClientConfig.builder()
                .protocolConfig(it -> it.log(log -> log.sendLog(false)
                        .receiveLog(false)))
                .buildPrototype();
        Http1ClientImpl client = new Http1ClientImpl(null, config);

        assertThat(client.sendListener().enabled(), is(false));
        assertThat(client.recvListener().enabled(), is(false));
    }

    @Test
    void testInterimResponseIsSkipped() {
        String rawResponse = "HTTP/1.1 103 Early Hints\r\n"
                + "Link: </style.css>; rel=preload\r\n"
                + "\r\n"
                + "HTTP/1.1 200 OK\r\n"
                + "Content-Length: 2\r\n"
                + "\r\n"
                + "OK";

        Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(new FakeHttp1ClientConnection(rawResponse))
                .request();

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity().as(String.class), is("OK"));
    }

    @Test
    void testSwitchingProtocolsWithCustomReasonIsNotSkipped() {
        String rawResponse = "HTTP/1.1 101 Custom Upgrade\r\n"
                + "Upgrade: test\r\n"
                + "\r\n"
                + "upgraded-data\r\n";

        Http1ClientResponse response = client.get("http://localhost:" + dummyPort + "/test")
                .connection(new FakeHttp1ClientConnection(rawResponse))
                .request();

        assertThat(response.status().code(), is(Status.SWITCHING_PROTOCOLS_101.code()));
        assertThat(response.status().reasonPhrase(), is("Custom Upgrade"));
    }

    @Test
    void testInterimResponseBeforeContinueIsSkipped() {
        String rawContinueResponse = "HTTP/1.1 103 Early Hints\r\n"
                + "Link: </style.css>; rel=preload\r\n"
                + "\r\n"
                + "HTTP/1.1 100 Continue\r\n"
                + "\r\n";
        String rawResponse = "HTTP/1.1 200 OK\r\n"
                + "Content-Length: 2\r\n"
                + "\r\n"
                + "OK";
        Http1Client expectContinueClient = Http1Client.builder()
                .sendExpectContinue(true)
                .build();
        Http1ClientRequest request = expectContinueClient.put("http://localhost:" + dummyPort + "/test");
        request.connection(new FakeHttp1ClientConnection(rawResponse, rawContinueResponse));

        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, new String[] {"OK"});

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity().as(String.class), is("OK"));
    }

    @Test
    void testChunk() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        Http1ClientRequest request = getHttp1ClientRequest(Method.PUT, "/test");
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
    }

    @Test
    void testNoChunk() {
        String[] requestEntityParts = {"First"};
        long contentLength = requestEntityParts[0].length();

        Http1ClientRequest request = getHttp1ClientRequest(Method.PUT, "/test")
                .header(HeaderNames.CONTENT_LENGTH, String.valueOf(contentLength));
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, false, contentLength, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkNoContentLength() {
        String[] requestEntityParts = {"First"};

        Http1ClientRequest request = getHttp1ClientRequest(Method.PUT, "/test");
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkTransferEncodingChunked() {
        String[] requestEntityParts = {"First"};

        Http1ClientRequest request = getHttp1ClientRequest(Method.PUT, "/test")
                .header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testExpect100() {
        LogConfig.configureRuntime();
        String[] requestEntityParts = {"First", "Second", "Third"};

        Http1Client client = Http1Client.builder()
                .sendExpectContinue(true)
                .build();
        Http1ClientRequest request = client.put("http://localhost:" + dummyPort + "/test");
        request.connection(new FakeHttp1ClientConnection());

        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
        assertThat(response.headers(), hasHeader(REQ_EXPECT_100_HEADER_NAME));
    }

    // validates that HEAD is not allowed with entity payload
    @Test
    void testHeadMethod() {
        String url = "http://localhost:" + dummyPort + "/test";
        ClientConnection http1ClientConnection = new FakeHttp1ClientConnection();
        assertThrows(IllegalArgumentException.class, () ->
                client.head(url).connection(http1ClientConnection).submit("Foo Bar"));
        assertThrows(IllegalArgumentException.class, () ->
                client.head(url).connection(http1ClientConnection).outputStream(it -> {
                    it.write("Foo Bar".getBytes(StandardCharsets.UTF_8));
                    it.close();
                }));
        client.head(url).connection(http1ClientConnection).request();
        http1ClientConnection.closeResource();
    }

    @Test
    void testSkipUrlEncoding() {
        //Fill with chars which should be encoded
        Http1ClientRequest request = getHttp1ClientRequest(Method.PUT, "/ěščžř")
                .queryParam("specialChar+", "someValue,").fragment("someFragment,");
        URI uri = request.resolvedUri().toUri();
        assertThat(uri.getRawPath(), is("/%C4%9B%C5%A1%C4%8D%C5%BE%C5%99"));
        assertThat(uri.getRawQuery(), is("specialChar%2B=someValue%2C"));
        assertThat(uri.getRawFragment(), is("someFragment%2C"));

        request = request.skipUriEncoding(true);
        uri = request.resolvedUri().toUri();
        assertThat(uri.getRawPath(), is("/ěščžř"));
        assertThat(uri.getRawQuery(), is("specialChar+=someValue,"));
        assertThat(uri.getRawFragment(), is("someFragment,"));
    }

    @ParameterizedTest
    @MethodSource("relativeUris")
    void testRelativeUris(ProxyConfiguration proxyConfig,
                          RelativeUrisValue relativeUris,
                          boolean outputStream,
                          String requestUri,
                          String expectedUriStart) {
        Proxy proxy = null;
        switch (proxyConfig) {
        case UNSET -> {
        } // proxy is already initialized to null which is the goal of this condition, so no-op
        case NO_PROXY -> proxy = Proxy.noProxy();
        case HTTP -> proxy = createHttpProxyBuilder().build();
        case HTTP_SET_NO_PROXY_HOST -> proxy = createHttpProxyBuilder().addNoProxy(TARGET_HOST).build();
        case SYSTEM_UNSET -> proxy = Proxy.create();
        case SYSTEM_SET_PROXY -> {
            proxy = Proxy.create();
            System.setProperty("http.proxyHost", PROXY_HOST);
            System.setProperty("http.proxyPort", PROXY_PORT);
        }
        case SYSTEM_SET_PROXY_AND_NON_PROXY_HOST -> {
            proxy = Proxy.create();
            System.setProperty("http.proxyHost", PROXY_HOST);
            System.setProperty("http.proxyPort", PROXY_PORT);
            System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1|10.*.*.*|*.example.com|etc|" + TARGET_HOST);
        }
        }

        Http1Client client;
        switch (relativeUris) {
        case TRUE -> client = Http1Client.builder().relativeUris(true).build();
        case FALSE -> client = Http1Client.builder().relativeUris(false).build();
        default -> client = Http1Client.create();   // Don't set relativeUris and accept whatever is the default
        }
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection();
        Http1ClientRequest request = proxy != null ? client.put(requestUri).proxy(proxy) : client.put(requestUri);
        request.connection(connection);
        HttpClientResponse response = outputStream
                ? getHttp1ClientResponseFromOutputStream(request, new String[] {"Sending Something"})
                : request.submit("Sending Something");

        assertThat(response.status(), is(Status.OK_200));
        StringTokenizer st = new StringTokenizer(connection.getPrologue(), " ");
        // skip method part
        st.nextToken();
        // Validate URI part
        assertThat(st.nextToken(), startsWith(expectedUriStart));

        // Clear proxy system properties that were set
        switch (proxyConfig) {
        case SYSTEM_SET_PROXY -> {
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
        }
        case SYSTEM_SET_PROXY_AND_NON_PROXY_HOST -> {
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
            System.clearProperty("http.nonProxyHosts");
        }
        }
    }

    @Test
    void testUnixDomainSocketUsesRelativeUriAndNoProxyConnectionHeader(@TempDir Path tempDir) throws Exception {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(tempDir.resolve("helidon-http1-test.sock"));
        WebClient webClient = WebClient.builder()
                .shareConnectionCache(false)
                .protocolPreference(List.of(Http1Client.PROTOCOL_ID))
                .build();
        try (RawHttp1CaptureServer server = RawHttp1CaptureServer.start(address)) {
            HttpClientRequest request = webClient.put("http://" + TARGET_HOST + ":1111" + TARGET_URI_PATH)
                    .address(address)
                    .proxy(createHttpProxyBuilder().build());

            try (HttpClientResponse response = request.submit("Sending Something")) {
                CapturedHttp1Request capturedRequest = server.awaitRequest();

                assertThat(response.status(), is(Status.OK_200));
                StringTokenizer st = new StringTokenizer(capturedRequest.prologue(), " ");
                st.nextToken();
                assertThat(st.nextToken(), is(TARGET_URI_PATH));
                assertThat(capturedRequest.hasHeader("Proxy-Connection"), is(false));
            }
        } finally {
            webClient.closeResource();
        }
    }

    @ParameterizedTest
    @MethodSource("headerValues")
    void testHeaderValues(List<String> headerValues, boolean expectsValid) {
        Http1Client clientValidateRequestHeaders = Http1Client.builder()
                .protocolConfig(it -> {
                    it.validateRequestHeaders(true);
                    it.validateResponseHeaders(false);
                })
                .build();
        Http1ClientRequest request = clientValidateRequestHeaders.get("http://localhost:" + dummyPort + "/test");
        request.header(HeaderValues.create("HeaderName", headerValues));
        request.connection(new FakeHttp1ClientConnection());
        if (expectsValid) {
            HttpClientResponse response = request.request();
            assertThat(response.status(), is(Status.OK_200));
        } else {
            assertThrows(IllegalArgumentException.class, () -> request.request());
        }
    }

    @ParameterizedTest
    @MethodSource("headers")
    void testHeaders(Header header, boolean expectsValid) {
        Http1Client clientValidateRequestHeaders = Http1Client.builder()
                .protocolConfig(it -> {
                    it.validateRequestHeaders(true);
                    it.validateResponseHeaders(false);
                })
                .build();
        Http1ClientRequest request = clientValidateRequestHeaders.get("http://localhost:" + dummyPort + "/test");
        request.connection(new FakeHttp1ClientConnection());
        request.header(header);
        if (expectsValid) {
            HttpClientResponse response = request.request();
            assertThat(response.status(), is(Status.OK_200));
        } else {
            assertThrows(IllegalArgumentException.class, () -> request.request());
        }
    }

    @ParameterizedTest
    @MethodSource("headers")
    void testDisableHeaderValidation(Header header, boolean expectsValid) {
        Http1Client clientWithDisabledHeaderValidation = Http1Client.builder()
                .protocolConfig(it -> {
                    it.validateRequestHeaders(false);
                    it.validateResponseHeaders(false);
                })
                .build();
        Http1ClientRequest request = clientWithDisabledHeaderValidation.put("http://localhost:" + dummyPort + "/test");
        request.header(header);
        request.connection(new FakeHttp1ClientConnection());
        HttpClientResponse response = request.submit("Sending Something");
        if (expectsValid) {
            assertThat(response.status(), is(Status.OK_200));
        } else {
            assertThat(response.status(), is(Status.BAD_REQUEST_400));
        }
    }

    @ParameterizedTest
    @MethodSource("responseHeaders")
    void testHeadersFromResponse(String headerName, String headerValue, boolean expectsValid) {
        Http1Client clientValidateResponseHeaders = Http1Client.builder()
                .protocolConfig(it -> {
                    it.validateRequestHeaders(false);
                    it.validateResponseHeaders(true);
                })
                .build();
        Http1ClientRequest request = clientValidateResponseHeaders.get("http://localhost:" + dummyPort + BAD_HEADER_PATH);
        request.connection(new FakeHttp1ClientConnection());
        String headerNameAndValue = headerName + HEADER_NAME_VALUE_DELIMETER + headerValue;
        if (expectsValid) {
            HttpClientResponse response = request.submit(headerNameAndValue);
            assertThat(response.status(), is(Status.OK_200));
            String responseHeaderValue = response.headers().get(HeaderNames.create(headerName)).values();
            assertThat(responseHeaderValue, is(headerValue.trim()));
        } else {
            assertThrows(IllegalArgumentException.class, () -> request.submit(headerNameAndValue));
        }
    }

    @ParameterizedTest
    @MethodSource("responseHeadersForDisabledValidation")
    void testDisableValidationForHeadersFromResponse(String headerName, String headerValue) {
        Http1Client clientWithNoHeaderValidation = Http1Client.builder()
                .protocolConfig(it -> {
                    it.validateRequestHeaders(false);
                    it.validateResponseHeaders(false);
                })
                .build();
        Http1ClientRequest request = clientWithNoHeaderValidation.put("http://localhost:" + dummyPort + BAD_HEADER_PATH);
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = request.submit(headerName + HEADER_NAME_VALUE_DELIMETER + headerValue);
        assertThat(response.status(), is(Status.OK_200));
        String responseHeaderValue = response.headers().get(HeaderNames.create(headerName)).values();
        assertThat(responseHeaderValue, is(headerValue.trim()));
    }

    private static void validateSuccessfulResponse(Http1Client client, ClientConnection connection) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.put("http://localhost:" + dummyPort + "/test");
        if (connection != null) {
            request.connection(connection);
        }
        Http1ClientResponse response = request.submit(requestEntity);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity().as(String.class), is(requestEntity));
    }

    private static void validateFailedResponse(Http1Client client, ClientConnection connection, String errorMessage) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.put("http://localhost:" + dummyPort + "/test");
        if (connection != null) {
            request.connection(connection);
        }
        IllegalStateException ie = assertThrows(IllegalStateException.class, () -> request.submit(requestEntity));
        if (errorMessage != null) {
            assertThat(ie.getMessage(), containsString(errorMessage));
        }
    }

    private static void validateChunkTransfer(Http1ClientResponse response, boolean chunked, long contentLength, String entity) {
        assertThat(response.status(), is(Status.OK_200));
        if (contentLength == NO_CONTENT_LENGTH) {
            assertThat(response.headers(), noHeader(REQ_CONTENT_LENGTH_HEADER_NAME));
        } else {
            assertThat(response.headers(), hasHeader(REQ_CONTENT_LENGTH_HEADER_NAME, String.valueOf(contentLength)));
        }
        if (chunked) {
            assertThat(response.headers(), hasHeader(REQ_CHUNKED_HEADER));
        } else {
            assertThat(response.headers(), noHeader(REQ_CHUNKED_HEADER.headerName()));
        }
        String responseEntity = response.entity().as(String.class);
        assertThat(responseEntity, is(entity));
    }

    private static Http1ClientRequest getHttp1ClientRequest(Method method, String uriPath) {
        return client.method(method).uri("http://localhost:" + dummyPort + uriPath);
    }

    private static Http1ClientResponse getHttp1ClientResponseFromOutputStream(Http1ClientRequest request,
                                                                              String[] requestEntityParts) {
        Http1ClientResponse response = request.outputStream(it -> {
            for (String r : requestEntityParts) {
                it.write(r.getBytes(StandardCharsets.UTF_8));
            }
            it.close();
        });
        return response;
    }

    private static Proxy.Builder createHttpProxyBuilder() {
        return Proxy.builder()
                .type(Proxy.ProxyType.HTTP)
                .host(PROXY_HOST)
                .port(Integer.parseInt(PROXY_PORT));
    }

    private static Stream<Arguments> relativeUris() {
        // Request type
        boolean isOutputStream = true;
        boolean isEntity = !isOutputStream;

        return Stream.of(
                // OutputStream (chunk request)
                // Expects absolute URI
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.FALSE, isOutputStream,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, "http://" + TARGET_HOST + ":80/"),
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.FALSE, isOutputStream,
                          "https://" + TARGET_HOST + TARGET_URI_PATH, "https://" + TARGET_HOST + ":443/"),
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.TRUE, isOutputStream,
                          "http://" + TARGET_HOST + ":1111/test", TARGET_URI_PATH),
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.FALSE, isOutputStream,
                          "http://" + TARGET_HOST + ":1111/test", "http://" + TARGET_HOST + ":1111/"),
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.DEFAULT, isOutputStream,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, "http://" + TARGET_HOST + ":80/"),
                arguments(ProxyConfiguration.SYSTEM_SET_PROXY, RelativeUrisValue.FALSE, isOutputStream,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, "http://" + TARGET_HOST + ":80/"),
                // Expects relative URI
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.TRUE, isOutputStream,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.TRUE, isOutputStream,
                          "https://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.HTTP_SET_NO_PROXY_HOST, RelativeUrisValue.DEFAULT, isOutputStream,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.NO_PROXY, RelativeUrisValue.DEFAULT, isOutputStream,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.NO_PROXY, RelativeUrisValue.FALSE, isOutputStream,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.NO_PROXY, RelativeUrisValue.DEFAULT, isOutputStream,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.SYSTEM_UNSET, RelativeUrisValue.FALSE, isOutputStream,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.SYSTEM_SET_PROXY_AND_NON_PROXY_HOST, RelativeUrisValue.FALSE, isOutputStream,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                // non-OutputStream (single entity request)
                // Expects absolute URI
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.FALSE, isEntity,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, "http://" + TARGET_HOST + ":80/"),
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.FALSE, isEntity,
                          "https://" + TARGET_HOST + TARGET_URI_PATH, "https://" + TARGET_HOST + ":443/"),
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.TRUE, isEntity,
                          "http://" + TARGET_HOST + ":1111/test", TARGET_URI_PATH),
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.FALSE, isEntity,
                          "http://" + TARGET_HOST + ":1111/test", "http://" + TARGET_HOST + ":1111/"),
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.DEFAULT, isEntity,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, "http://" + TARGET_HOST + ":80/"),
                arguments(ProxyConfiguration.SYSTEM_SET_PROXY, RelativeUrisValue.FALSE, isEntity,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, "http://" + TARGET_HOST + ":80/"),
                // Expects relative URI
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.TRUE, isEntity,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.HTTP, RelativeUrisValue.TRUE, isEntity,
                          "https://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.HTTP_SET_NO_PROXY_HOST, RelativeUrisValue.DEFAULT, isEntity,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.NO_PROXY, RelativeUrisValue.DEFAULT, isEntity,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.NO_PROXY, RelativeUrisValue.FALSE, isEntity,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.NO_PROXY, RelativeUrisValue.DEFAULT, isEntity,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.SYSTEM_UNSET, RelativeUrisValue.FALSE, isEntity,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH),
                arguments(ProxyConfiguration.SYSTEM_SET_PROXY_AND_NON_PROXY_HOST, RelativeUrisValue.FALSE, isEntity,
                          "http://" + TARGET_HOST + TARGET_URI_PATH, TARGET_URI_PATH)
        );
    }

    private static Stream<Arguments> noBodyStatusResponses() {
        return Stream.of(
                arguments(Status.NO_CONTENT_204),
                arguments(Status.NOT_MODIFIED_304)
        );
    }

    private static Stream<Arguments> notModifiedResponseMetadata() {
        return Stream.of(
                arguments(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "4")),
                arguments(HeaderValues.TRANSFER_ENCODING_CHUNKED)
        );
    }

    private static Stream<Arguments> headerValues() {
        return Stream.of(
                // Valid header values
                arguments(Arrays.asList("Header Value"), true),
                arguments(Arrays.asList("HeaderValue1", "Header\u0080Value\u00ff2"), true),
                arguments(Arrays.asList("HeaderName1\u0009", "Header=Value2"), true),
                // Invalid header values
                arguments(Arrays.asList(" HeaderValue"), false),
                arguments(Arrays.asList("HeaderValue1", "Header\u007fValue"), false),
                arguments(Arrays.asList("HeaderValue1\r\n", "HeaderValue2"), false)
        );
    }

    private static Stream<Arguments> headers() {
        return Stream.of(
                // Valid headers
                arguments(HeaderValues.ACCEPT_RANGES_BYTES, true),
                arguments(HeaderValues.CONNECTION_KEEP_ALIVE, true),
                arguments(HeaderValues.CONTENT_TYPE_TEXT_PLAIN, true),
                arguments(HeaderValues.ACCEPT_TEXT, true),
                arguments(HeaderValues.CACHE_NO_CACHE, true),
                arguments(HeaderValues.TE_TRAILERS, true),
                arguments(HeaderValues.create("!#$Custom~%&\'*Header+^`|", "!Header\tValue~"), true),
                arguments(HeaderValues.create("Custom_0-9_a-z_A-Z_Header",
                                              "\u0080Header Value\u00ff"), true),
                // Invalid headers
                arguments(HeaderValues.create(VALID_HEADER_NAME, "H\u001ceaderValue1"), false),
                arguments(HeaderValues.create(VALID_HEADER_NAME,
                                              "HeaderValue1, Header\u007fValue"), false),
                arguments(HeaderValues.create(VALID_HEADER_NAME,
                                              "HeaderValue1\u001f, HeaderValue2"), false),
                arguments(HeaderValues.create("Header\u001aName", VALID_HEADER_VALUE), false),
                arguments(HeaderValues.create("Header\u000EName", VALID_HEADER_VALUE), false),
                arguments(HeaderValues.create("HeaderName\r\n", VALID_HEADER_VALUE), false),
                arguments(HeaderValues.create("HeaderName\u00FF\u0124", VALID_HEADER_VALUE), false),
                arguments(HeaderValues.create("(Header:Name)", VALID_HEADER_VALUE), false),
                arguments(HeaderValues.create("<Header?Name>", VALID_HEADER_VALUE), false),
                arguments(HeaderValues.create("{Header=Name}", VALID_HEADER_VALUE), false),
                arguments(HeaderValues.create("\"HeaderName\"", VALID_HEADER_VALUE), false),
                arguments(HeaderValues.create("[\\HeaderName]", VALID_HEADER_VALUE), false),
                arguments(HeaderValues.create("@Header,Name;", VALID_HEADER_VALUE), false)
        );
    }

    private static Stream<Arguments> responseHeaders() {
        return Stream.of(
                // Invalid header names
                arguments("Header\u001aName", VALID_HEADER_VALUE, false),
                arguments("Header\u000EName", VALID_HEADER_VALUE, false),
                arguments("HeaderName\r\n", VALID_HEADER_VALUE, false),
                arguments("(Header:Name)", VALID_HEADER_VALUE, false),
                arguments("<Header?Name>", VALID_HEADER_VALUE, false),
                arguments("{Header=Name}", VALID_HEADER_VALUE, false),
                arguments("\"HeaderName\"", VALID_HEADER_VALUE, false),
                arguments("[\\HeaderName]", VALID_HEADER_VALUE, false),
                arguments("@Header,Name;", VALID_HEADER_VALUE, false),
                // Valid header names
                arguments("!#$Custom~%&\'*Header+^`|", VALID_HEADER_VALUE, true),
                arguments("Custom_0-9_a-z_A-Z_Header", VALID_HEADER_VALUE, true),
                // Valid header values
                arguments(VALID_HEADER_NAME, "Header Value", true),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u0009, Header=Value2", true),
                arguments(VALID_HEADER_NAME, "Header\tValue", true),
                arguments(VALID_HEADER_NAME, " Header Value ", true),
                // Invalid header values
                arguments(VALID_HEADER_NAME, "H\u001ceaderValue1", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1, Header\u007fValue", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u001f, HeaderValue2", false)
        );
    }

    private static Stream<Arguments> responseHeadersForDisabledValidation() {
        return Stream.of(
                // Invalid header names
                arguments("Header\u001aName", VALID_HEADER_VALUE, false),
                arguments("Header\u000EName", VALID_HEADER_VALUE, false),
                arguments("{Header=Name}", VALID_HEADER_VALUE, false),
                arguments("\"HeaderName\"", VALID_HEADER_VALUE, false),
                arguments("[\\HeaderName]", VALID_HEADER_VALUE, false),
                arguments("@Header,Name;", VALID_HEADER_VALUE, false),
                // Valid header names
                arguments("!#$Custom~%&\'*Header+^`|", VALID_HEADER_VALUE, true),
                arguments("Custom_0-9_a-z_A-Z_Header", VALID_HEADER_VALUE, true),
                // Valid header values
                arguments(VALID_HEADER_NAME, "Header Value", true),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u0009, Header=Value2", true),
                arguments(VALID_HEADER_NAME, "Header\tValue", true),
                arguments(VALID_HEADER_NAME, " Header Value ", true),
                // Invalid header values
                arguments(VALID_HEADER_NAME, "H\u001ceaderValue1", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1, Header\u007fValue", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u001f, HeaderValue2", false)
        );
    }

    private enum RelativeUrisValue {
        TRUE, FALSE, DEFAULT
    }

    private enum ProxyConfiguration {
        UNSET, NO_PROXY, HTTP, HTTP_SET_NO_PROXY_HOST, SYSTEM_UNSET, SYSTEM_SET_PROXY, SYSTEM_SET_PROXY_AND_NON_PROXY_HOST
    }

    private static class FakeHttp1ClientConnection implements ClientConnection {
        private final DataReader clientReader;
        private final DataWriter clientWriter;
        private final DataReader serverReader;
        private final DataWriter serverWriter;
        private final boolean includeKeepAliveHeader;
        private final String rawResponse;
        private final String rawContinueResponse;
        private final Status responseStatus;
        private final boolean includeContentLength;
        private final List<Header> additionalResponseHeaders;
        private final byte[] responseEntity;
        private Throwable serverException;
        private ExecutorService webServerEmulator;
        private String prologue;
        private int releaseCount;
        private int closeCount;

        FakeHttp1ClientConnection() {
            this(true);
        }

        FakeHttp1ClientConnection(boolean includeKeepAliveHeader) {
            this(Status.OK_200, includeKeepAliveHeader, true);
        }

        FakeHttp1ClientConnection(String rawResponse) {
            this(rawResponse, null);
        }

        FakeHttp1ClientConnection(String rawResponse, String rawContinueResponse) {
            this(true, rawResponse, rawContinueResponse);
        }

        FakeHttp1ClientConnection(boolean includeKeepAliveHeader, String rawResponse, String rawContinueResponse) {
            this(Status.OK_200, includeKeepAliveHeader, true, List.of(), null, rawResponse, rawContinueResponse);
        }

        FakeHttp1ClientConnection(Status responseStatus, boolean includeContentLength) {
            this(responseStatus, true, includeContentLength);
        }

        FakeHttp1ClientConnection(Status responseStatus, boolean includeKeepAliveHeader, boolean includeContentLength) {
            this(responseStatus, includeKeepAliveHeader, includeContentLength, List.of(), null);
        }

        FakeHttp1ClientConnection(Status responseStatus,
                                  boolean includeKeepAliveHeader,
                                  boolean includeContentLength,
                                  List<Header> additionalResponseHeaders,
                                  byte[] responseEntity) {
            this(responseStatus, includeKeepAliveHeader, includeContentLength, additionalResponseHeaders, responseEntity, null, null);
        }

        private FakeHttp1ClientConnection(Status responseStatus,
                                          boolean includeKeepAliveHeader,
                                          boolean includeContentLength,
                                          List<Header> additionalResponseHeaders,
                                          byte[] responseEntity,
                                          String rawResponse,
                                          String rawContinueResponse) {
            ArrayBlockingQueue<byte[]> serverToClient = new ArrayBlockingQueue<>(1024);
            ArrayBlockingQueue<byte[]> clientToServer = new ArrayBlockingQueue<>(1024);

            this.clientReader = reader(serverToClient);
            this.clientWriter = writer(clientToServer);
            this.serverReader = reader(clientToServer);
            this.serverWriter = writer(serverToClient);
            this.includeKeepAliveHeader = includeKeepAliveHeader;
            this.rawResponse = rawResponse;
            this.rawContinueResponse = rawContinueResponse;
            this.responseStatus = responseStatus;
            this.includeContentLength = includeContentLength;
            this.additionalResponseHeaders = additionalResponseHeaders;
            this.responseEntity = responseEntity;
        }

        @Override
        public HelidonSocket helidonSocket() {
            return new FakeSocket();
        }

        @Override
        public DataReader reader() {
            return clientReader;
        }

        @Override
        public DataWriter writer() {
            return clientWriter;
        }

        @Override
        public void releaseResource() {
            releaseCount++;
        }

        @Override
        public void closeResource() {
            closeCount++;
            if (webServerEmulator != null) {
                webServerEmulator.shutdownNow();
            }
        }

        @Override
        public String channelId() {
            return helidonSocket().socketId();
        }

        @Override
        public void readTimeout(Duration readTimeout) {
            //NOOP
        }

        // This will be used for testing the element of Prologue
        String getPrologue() {
            return prologue;
        }

        int releaseCount() {
            return releaseCount;
        }

        int closeCount() {
            return closeCount;
        }

        private DataWriter writer(ArrayBlockingQueue<byte[]> queue) {
            return new DataWriter() {
                @Override
                public void write(BufferData... buffers) {
                    writeNow(buffers);
                }

                @Override
                public void write(BufferData buffer) {
                    writeNow(buffer);
                }

                @Override
                public void writeNow(BufferData... buffers) {
                    for (BufferData buffer : buffers) {
                        writeNow(buffer);
                    }
                }

                @Override
                public void writeNow(BufferData buffer) {
                    if (serverException != null) {
                        throw new IllegalStateException("Server exception", serverException);
                    }
                    if (webServerEmulator == null) {
                        webServerEmulator = Executors.newFixedThreadPool(1);
                        webServerEmulator.submit(() -> {
                            try {
                                webServerHandle();
                            } catch (Throwable t) {
                                serverException = t;
                                throw t;
                            }
                        });
                    }

                    byte[] bytes = new byte[buffer.available()];
                    buffer.read(bytes);
                    try {
                        queue.put(bytes);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException("Thread interrupted", e);
                    }
                }
            };
        }

        private DataReader reader(ArrayBlockingQueue<byte[]> queue) {
            return DataReader.create(() -> {
                if (serverException != null) {
                    throw new IllegalStateException("Server exception", serverException);
                }
                byte[] data;
                try {
                    data = queue.poll(5, TimeUnit.SECONDS);
                    if (data == null) {
                        return null;
                    }
                } catch (InterruptedException e) {
                    throw new IllegalArgumentException("Thread interrupted", e);
                }
                if (data.length == 0) {
                    return null;
                }
                return data;
            });
        }

        private void webServerHandle() {
            BufferData entity = BufferData.growing(128);

            // Read prologue
            int lineLength = serverReader.findNewLine(16384);
            if (lineLength > 0) {
                prologue = serverReader.readAsciiString(lineLength);
                serverReader.skip(2); // skip CRLF
            }

            boolean requestFailed = false;
            // Read Headers
            WritableHeaders<?> reqHeaders = null;
            try {
                reqHeaders = Http1HeadersParser.readHeaders(serverReader, 16384, false);
                for (Iterator<Header> it = reqHeaders.iterator(); it.hasNext(); ) {
                    Header header = it.next();
                    header.validate();
                }
            } catch (IllegalArgumentException e) {
                requestFailed = true;
            }

            int entitySize = 0;
            if (!requestFailed) {
                if (reqHeaders.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                    // Send 100-Continue if requested
                    if (reqHeaders.contains(HeaderValues.EXPECT_100)) {
                        String continueResponse = rawContinueResponse == null
                                ? "HTTP/1.1 100 Continue\r\n\r\n"
                                : rawContinueResponse;
                        serverWriter.write(BufferData.create(continueResponse.getBytes(StandardCharsets.UTF_8)));
                    }

                    // Assemble the entity from the chunks
                    while (true) {
                        String hex = serverReader.readLine();
                        int chunkLength = Integer.parseUnsignedInt(hex, 16);
                        if (chunkLength == 0) {
                            serverReader.readLine();
                            break;
                        }
                        BufferData chunkData = serverReader.readBuffer(chunkLength);
                        entity.write(chunkData);
                        serverReader.skip(2);
                        entitySize += chunkLength;
                    }
                } else if (reqHeaders.contains(HeaderNames.CONTENT_LENGTH)) {
                    entitySize = reqHeaders.get(HeaderNames.CONTENT_LENGTH).get(int.class);
                    if (entitySize > 0) {
                        entity.write(serverReader.getBuffer(entitySize));
                    }
                }
            }

            WritableHeaders<?> resHeaders = WritableHeaders.create();
            if (includeKeepAliveHeader) {
                resHeaders.add(HeaderValues.CONNECTION_KEEP_ALIVE);
            }

            if (reqHeaders != null) {
                // Send headers that can be validated if Expect-100-Continue, Content_Length, and Chunked request headers exist
                if (reqHeaders.contains(HeaderValues.EXPECT_100)) {
                    resHeaders.set(REQ_EXPECT_100_HEADER_NAME);
                }
                if (reqHeaders.contains(HeaderNames.CONTENT_LENGTH)) {
                    resHeaders.set(REQ_CONTENT_LENGTH_HEADER_NAME, reqHeaders.get(HeaderNames.CONTENT_LENGTH).get());
                }
                if (reqHeaders.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                    resHeaders.set(REQ_CHUNKED_HEADER);
                }
            }

            // if prologue contains "/badHeader" path, send back the entity (name and value delimited by ->) as a header
            if (getPrologue().contains(BAD_HEADER_PATH)) {
                String[] header = entity.readString(entitySize, StandardCharsets.US_ASCII).split(HEADER_NAME_VALUE_DELIMETER);
                resHeaders.add(HeaderValues.create(header[0], header[1]));
            }

            if (rawResponse != null) {
                serverWriter.write(BufferData.create(rawResponse.getBytes(StandardCharsets.US_ASCII)));
                return;
            }

            String responseMessage = !requestFailed
                    ? "HTTP/1.1 " + responseStatus.code() + " " + responseStatus.reasonPhrase() + "\r\n"
                    : "HTTP/1.1 400 Bad Request\r\n";
            serverWriter.write(BufferData.create(responseMessage.getBytes(StandardCharsets.UTF_8)));

            // Send the headers
            if (includeContentLength) {
                resHeaders.add(HeaderNames.CONTENT_LENGTH, Integer.toString(entitySize));
            }
            for (Header header : additionalResponseHeaders) {
                resHeaders.add(header);
            }
            BufferData entityBuffer = BufferData.growing(128);
            for (Header header : resHeaders) {
                header.writeHttp1Header(entityBuffer);
            }
            entityBuffer.write(Bytes.CR_BYTE);
            entityBuffer.write(Bytes.LF_BYTE);

            // Send the entity if it exist
            if (responseEntity == null) {
                serverWriter.write(entityBuffer);
            } else {
                entityBuffer.write(responseEntity);
                serverWriter.write(entityBuffer);
            }
            if (responseEntity == null && entitySize > 0) {
                serverWriter.write(entity);
            }
        }

    }

    private record CapturedHttp1Request(String prologue, List<String> headerLines) {
        boolean hasHeader(String headerName) {
            String headerPrefix = headerName + ":";
            return headerLines.stream()
                    .anyMatch(line -> line.regionMatches(true, 0, headerPrefix, 0, headerPrefix.length()));
        }
    }

    private record RawHttp1CaptureServer(ServerSocketChannel channel,
                                         CompletableFuture<CapturedHttp1Request> request) implements AutoCloseable {
        static RawHttp1CaptureServer start(UnixDomainSocketAddress address) throws IOException {
            ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(address);
            CompletableFuture<CapturedHttp1Request> request = CompletableFuture.supplyAsync(() -> {
                try (SocketChannel socket = server.accept()) {
                    CapturedHttp1Request captured = readRequest(socket);
                    writeResponse(socket);
                    return captured;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } finally {
                    try {
                        server.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
            return new RawHttp1CaptureServer(server, request);
        }

        CapturedHttp1Request awaitRequest() throws Exception {
            return request.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            try {
                channel.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static CapturedHttp1Request readRequest(SocketChannel socket) throws IOException {
            ByteArrayOutputStream rawRequest = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (socket.read(buffer) >= 0) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    rawRequest.write(buffer.get());
                }
                String rawRequestText = new String(rawRequest.toByteArray(), StandardCharsets.US_ASCII);
                if (rawRequestText.contains("\r\n\r\n")) {
                    return parse(rawRequestText);
                }
                buffer.clear();
            }
            throw new IllegalStateException("HTTP/1 request headers were not complete");
        }

        private static CapturedHttp1Request parse(String rawRequestText) {
            int headerEnd = rawRequestText.indexOf("\r\n\r\n");
            String[] headerLines = rawRequestText.substring(0, headerEnd).split("\r\n");
            return new CapturedHttp1Request(headerLines[0], Arrays.asList(headerLines).subList(1, headerLines.length));
        }

        private static void writeResponse(SocketChannel socket) throws IOException {
            ByteBuffer response = ByteBuffer.wrap(("HTTP/1.1 200 OK\r\n"
                    + "Connection: close\r\n"
                    + "Content-Length: 0\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
            while (response.hasRemaining()) {
                socket.write(response);
            }
        }
    }

    private static class FakeSocket implements HelidonSocket {
        @Override
        public void close() {

        }

        @Override
        public void idle() {

        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void write(BufferData buffer) {

        }

        @Override
        public PeerInfo remotePeer() {
            return null;
        }

        @Override
        public PeerInfo localPeer() {
            return null;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String socketId() {
            return "fake";
        }

        @Override
        public String childSocketId() {
            return "fake";
        }

        @Override
        public byte[] get() {
            return new byte[0];
        }
    }

    private static class CustomizedMediaContext implements MediaContext {
        private final MediaContext delegated = MediaContext.create();

        @Override
        public MediaContextConfig prototype() {
            return delegated.prototype();
        }

        @Override
        public <T> EntityReader<T> reader(GenericType<T> type, Headers headers) {
            return delegated.reader(type, headers);
        }

        @Override
        public <T> EntityWriter<T> writer(GenericType<T> type, Headers requestHeaders, WritableHeaders<?> responseHeaders) {
            EntityWriter<T> impl = delegated.writer(type, requestHeaders, responseHeaders);

            EntityWriter<T> realWriter = new EntityWriter<T>() {
                @Override
                public void write(GenericType<T> type,
                                  T object,
                                  OutputStream outputStream,
                                  Headers requestHeaders,
                                  WritableHeaders<?> responseHeaders) {
                    if (object instanceof String) {
                        @SuppressWarnings("unchecked") final T maxLen5 = (T) ((String) object).substring(0, 5);
                        impl.write(type, maxLen5, outputStream, requestHeaders, responseHeaders);
                    } else {
                        impl.write(type, object, outputStream, requestHeaders, responseHeaders);
                    }
                }

                @Override
                public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
                    impl.write(type, object, outputStream, headers);
                }
            };
            return realWriter;
        }

        @Override
        public <T> EntityReader<T> reader(GenericType<T> type, Headers requestHeaders, Headers responseHeaders) {
            return delegated.reader(type, requestHeaders, responseHeaders);
        }

        @Override
        public <T> EntityWriter<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
            return delegated.writer(type, requestHeaders);
        }
    }
}
