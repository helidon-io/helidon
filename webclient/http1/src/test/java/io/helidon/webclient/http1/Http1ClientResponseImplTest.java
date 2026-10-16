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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.context.Context;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http1.Http1ConnectionListener;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1ClientResponseImplTest {

    static WebClientServiceResponse serviceResponse(Method method,
                                                            Status status,
                                                            ClientResponseHeaders headers) {
        return Http1CallChainBase.createServiceResponse(new Http1ClientImpl(null,
                                                                            Http1ClientConfig.builder().buildPrototype()),
                                                        new TestServiceRequest(method),
                                                        new TestConnection(),
                                                        DataReader.create(() -> null),
                                                        status,
                                                        headers,
                                                        new CompletableFuture<>());
    }

    @Test
    void successfulConnectIgnoresFramingHeadersAndClosesTunnel() throws IOException {
        String tunnelData = "4\r\ndata\r\n0\r\n\r\n";
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        headers.add(HeaderValues.CONTENT_LENGTH_ZERO);
        ClientResponseHeaders responseHeaders = ClientResponseHeaders.create(headers);
        TestServiceRequest serviceRequest = new TestServiceRequest(Method.CONNECT);
        TestConnection connection = new TestConnection(dataReader(tunnelData));
        WebClientServiceResponse serviceResponse = Http1CallChainBase.createServiceResponse(
                new Http1ClientImpl(null, Http1ClientConfig.builder().buildPrototype()),
                serviceRequest,
                connection,
                connection.reader(),
                Status.NO_CONTENT_204,
                responseHeaders,
                new CompletableFuture<>());
        InputStream serviceStream = serviceResponse.inputStream().orElseThrow();
        Http1ClientResponseImpl response = new Http1ClientResponseImpl(HttpClientConfig.builder().build(),
                                                                       Http1ClientProtocolConfig.create(),
                                                                       Status.NO_CONTENT_204,
                                                                       Method.CONNECT,
                                                                       serviceRequest.headers(),
                                                                       responseHeaders,
                                                                       connection,
                                                                       serviceStream,
                                                                       MediaContext.create(),
                                                                       ClientUri.create(URI.create("http://localhost/test")),
                                                                       new CompletableFuture<>(),
                                                                       serviceResponse);
        InputStream tunnelStream = response.inputStream();

        byte[] actual = new byte[tunnelData.length()];
        assertThat(tunnelStream.read(actual), is(actual.length));
        assertThat(new String(actual, StandardCharsets.US_ASCII), is(tunnelData));
        response.close();
        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void closesChunkedResponseDuringEntityCleanup() throws Exception {
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch continueClose = new CountDownLatch(1);
        InputStream inputStream = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public void close() throws IOException {
                closeStarted.countDown();
                try {
                    if (!continueClose.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to continue entity cleanup");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Entity cleanup interrupted", e);
                }
            }
        };
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        TestConnection connection = new TestConnection(dataReader("\r\n"));
        Http1ClientResponseImpl response = response(ClientResponseHeaders.create(headers), inputStream, connection);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Integer> entityRead = executor.submit(() -> response.entity().inputStream().read());
            assertThat(closeStarted.await(5, TimeUnit.SECONDS), is(true));

            response.close();

            assertThat(connection.releaseCount(), is(0));
            assertThat(connection.closeCount(), is(1));
            continueClose.countDown();
            assertThat(entityRead.get(5, TimeUnit.SECONDS), is(-1));
        } finally {
            continueClose.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void closesConnectionWhenChunkedStreamClosesBeforeFinalChunk() throws IOException {
        TestConnection connection = new TestConnection(dataReader("2\r\nOK\r\n"));
        InputStream inputStream = new Http1CallChainBase.ChunkedInputStream(connection,
                                                                           connection.reader(),
                                                                           new CompletableFuture<>(),
                                                                           new AtomicReference<>(),
                                                                           new Http1ConnectionListener() {
                                                                           });

        inputStream.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void completesResponseWhenPrematureChunkedCloseFails() {
        IllegalStateException closeFailure = new IllegalStateException("Connection close failed");
        TestConnection connection = new TestConnection(dataReader("2\r\nOK\r\n"), closeFailure);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        WebClientServiceResponse serviceResponse = serviceResponse(Method.HEAD, Status.OK_200, contentLengthHeaders());
        InputStream inputStream = new Http1CallChainBase.ChunkedInputStream(connection,
                                                                           connection.reader(),
                                                                           whenComplete,
                                                                           new AtomicReference<>(serviceResponse),
                                                                           new Http1ConnectionListener() {
                                                                           });

        IllegalStateException actualFailure = assertThrows(IllegalStateException.class, inputStream::close);

        assertThat(actualFailure, sameInstance(closeFailure));
        assertThat(whenComplete.isDone(), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"content-length", "close-delimited", "chunked"})
    void zeroLengthReadDoesNotReadOrCompleteResponse(String framing) throws IOException {
        DataReader reader = DataReader.create(() -> {
            throw new AssertionError("Zero-length read requested network data");
        });
        var whenComplete = new CompletableFuture<WebClientServiceResponse>();
        InputStream stream = responseStream(framing, new TestConnection(reader), whenComplete);

        assertThat(stream.read(new byte[1], 1, 0), is(0));
        assertThat(whenComplete.isDone(), is(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"content-length", "chunked"})
    void zeroLengthReadReturnsZeroAfterEndOfResponse(String framing) throws IOException {
        String data = framing.equals("chunked") ? "1\r\na\r\n0\r\n\r\n" : "a";
        var whenComplete = new CompletableFuture<WebClientServiceResponse>();
        InputStream stream = responseStream(framing, new TestConnection(dataReader(data)), whenComplete);

        assertThat(new String(stream.readAllBytes(), StandardCharsets.US_ASCII), is("a"));
        assertThat(whenComplete.isDone(), is(true));
        assertThat(stream.read(new byte[0], 0, 0), is(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"content-length", "close-delimited", "chunked"})
    void zeroLengthReadValidatesArgumentsBeforeReading(String framing) {
        DataReader reader = DataReader.create(() -> {
            throw new AssertionError("Invalid read requested network data");
        });
        InputStream stream = responseStream(framing, new TestConnection(reader), new CompletableFuture<>());

        assertThrows(NullPointerException.class, () -> stream.read(null, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[1], -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[1], 2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[1], 0, -1));
    }

    @Test
    void rejectsFirstChunkDelimiterByteWithoutReadingAgain() {
        AtomicInteger reads = new AtomicInteger();
        DataReader reader = DataReader.create(() -> {
            if (reads.getAndIncrement() == 0) {
                return "1\r\naX".getBytes(StandardCharsets.US_ASCII);
            }
            throw new IllegalStateException("Unexpected network read after invalid first delimiter byte");
        });
        TestConnection connection = new TestConnection(reader);
        var whenComplete = new CompletableFuture<WebClientServiceResponse>();
        InputStream stream = responseStream("chunked", connection, whenComplete);

        IllegalStateException failure = assertThrows(IllegalStateException.class, stream::read);

        assertThat(failure.getMessage(), containsString("chunk data is not followed by CRLF"));
        assertThat(reads.get(), is(1));
        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
        assertThat(whenComplete.isDone(), is(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consumesChunkedTrailersBeforeReleasingConnection(boolean advertised) {
        TestConnection connection = new TestConnection(dataReader("2\r\nOK\r\n0\r\nX-Test: value\r\n\r\n"));
        Http1ClientResponseImpl response = chunkedResponse(connection, advertised, Http1ClientProtocolConfig.create());

        assertThat(response.entity().as(String.class), is("OK"));

        assertThat(response.trailers(), hasHeader(HeaderNames.create("X-Test"), "value"));
        assertThat(connection.reader().available(), is(0));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
        response.close();
        assertThat(connection.releaseCount(), is(1));
    }

    @Test
    void returnsEmptyUnadvertisedTrailersBeforeReadingEntity() {
        AtomicInteger reads = new AtomicInteger();
        DataReader reader = DataReader.create(() -> {
            if (reads.getAndIncrement() != 0) {
                throw new AssertionError("Unexpected read beyond complete chunked response");
            }
            return "2\r\nOK\r\n0\r\nX-Test: value\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        });
        TestConnection connection = new TestConnection(reader);
        Http1ClientResponseImpl response = chunkedResponse(connection, false, Http1ClientProtocolConfig.create());

        assertThat(response.trailers().size(), is(0));
        var entity = response.entity();
        assertThat(response.trailers().size(), is(0));
        assertThat(reads.get(), is(0));
        assertThat(connection.releaseCount(), is(0));
        assertThat(entity.as(String.class), is("OK"));

        assertThat(response.trailers(), hasHeader(HeaderNames.create("X-Test"), "value"));
        assertThat(reads.get(), is(1));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0\r\n\r\n", "0;extension=value\r\n\r\n"})
    void releasesUntouchedChunkedResponseWithBufferedEmptyFraming(String framing) {
        TestConnection connection = new TestConnection(bufferedReaderWithoutFurtherReads(framing));
        Http1ClientResponseImpl response = chunkedResponse(connection, false, Http1ClientProtocolConfig.create());

        response.close();

        assertThat(connection.reader().available(), is(0));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
        assertThat(response.trailers().size(), is(0));
        response.close();
        assertThat(connection.releaseCount(), is(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0\r\n", "0\r\nX-Test: value\r\n\r\n", "0\r\n\r\nstale",
            "1\r\nX\r\n0\r\n\r\n", "1\r\n0\r\n\r\n"})
    void closesUntouchedChunkedResponseWhenBufferedFramingCannotBeDrained(String framing) {
        TestConnection connection = new TestConnection(bufferedReaderWithoutFurtherReads(framing));
        Http1ClientResponseImpl response = chunkedResponse(connection, false, Http1ClientProtocolConfig.create());

        response.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void closesPartiallyReadChunkedResponseWithBufferedFinalChunk() throws IOException {
        TestConnection connection = new TestConnection(bufferedReaderWithoutFurtherReads("2\r\nOK\r\n0\r\n\r\n"));
        Http1ClientResponseImpl response = chunkedResponse(connection, false, Http1ClientProtocolConfig.create());

        assertThat(response.inputStream().read(), is((int) 'O'));
        response.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void retainsConnectionUntilCompleteUnadvertisedTrailerSectionArrives() {
        String[] fragments = {"2\r\nOK\r\n0\r\n", "X-Test: val", "ue\r\n", "\r\n"};
        AtomicInteger reads = new AtomicInteger();
        var currentConnection = new AtomicReference<TestConnection>();
        DataReader reader = DataReader.create(() -> {
            assertThat("Connection released before complete trailers arrived",
                       currentConnection.get().releaseCount(), is(0));
            assertThat("Connection closed while receiving valid trailers", currentConnection.get().closeCount(), is(0));
            int index = reads.getAndIncrement();
            return index < fragments.length ? fragments[index].getBytes(StandardCharsets.US_ASCII) : null;
        });
        TestConnection connection = new TestConnection(reader);
        currentConnection.set(connection);
        Http1ClientResponseImpl response = chunkedResponse(connection, false, Http1ClientProtocolConfig.create());

        assertThat(response.entity().as(String.class), is("OK"));

        assertThat(response.trailers(), hasHeader(HeaderNames.create("X-Test"), "value"));
        assertThat(reads.get(), is(fragments.length));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consumesTrailersWhenClosingAfterExactPayloadRead(boolean advertised) throws IOException {
        TestConnection connection = new TestConnection(dataReader("2\r\nOK\r\n0\r\nX-Test: value\r\n\r\n"));
        Http1ClientResponseImpl response = chunkedResponse(connection, advertised, Http1ClientProtocolConfig.create());

        try (InputStream stream = response.inputStream()) {
            byte[] bytes = new byte[2];
            assertThat(stream.read(bytes), is(2));
            assertThat(new String(bytes, StandardCharsets.US_ASCII), is("OK"));
            assertThat(connection.releaseCount(), is(0));
        }

        assertThat(response.trailers(), hasHeader(HeaderNames.create("X-Test"), "value"));
        assertThat(connection.reader().available(), is(0));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closesConnectionWhenTrailersExceedHeaderLimit(boolean advertised) {
        TestConnection connection = new TestConnection(dataReader("2\r\nOK\r\n0\r\nX-Test: "
                                                                        + "a".repeat(64) + "\r\n\r\n"));
        Http1ClientResponseImpl response = chunkedResponse(connection,
                                                           advertised,
                                                           Http1ClientProtocolConfig.builder().maxHeadersSize(32).build());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> response.entity().as(String.class));
        assertThat(failure.getCause().getMessage(), is("Header size exceeded"));
        response.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void closesConnectionWhenUnadvertisedTrailersAreIncomplete() {
        TestConnection connection = new TestConnection(dataReader("2\r\nOK\r\n0\r\nX-Test: value\r\n"));
        Http1ClientResponseImpl response = chunkedResponse(connection, false, Http1ClientProtocolConfig.create());

        assertThrows(IllegalStateException.class, () -> response.entity().as(String.class));
        response.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void closesConnectionAfterUnadvertisedTrailersFollowedByData() {
        TestConnection connection = new TestConnection(dataReader("2\r\nOK\r\n0\r\nX-Test: value\r\n\r\nstale"));
        Http1ClientResponseImpl response = chunkedResponse(connection, false, Http1ClientProtocolConfig.create());

        assertThat(response.entity().as(String.class), is("OK"));
        assertThat(response.trailers(), hasHeader(HeaderNames.create("X-Test"), "value"));
        response.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readsResetContentTrailersWithoutExposingEntity(boolean advertised) {
        TestConnection connection = new TestConnection(dataReader("0\r\nX-Test: value\r\n\r\n"));
        Http1ClientResponseImpl response = resetContentResponse(connection, advertised);

        assertThat(response.entity().hasEntity(), is(false));
        assertThat(response.trailers(), hasHeader(HeaderNames.create("X-Test"), "value"));
        response.close();

        assertThat(connection.reader().available(), is(0));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void emptyResetContentTrailersRemainReadableAfterConnectionRelease() {
        AtomicInteger reads = new AtomicInteger();
        DataReader reader = DataReader.create(() -> {
            if (reads.getAndIncrement() == 0) {
                return "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
            }
            throw new AssertionError("Trailer access read from a released connection");
        });
        reader.pullData();
        TestConnection connection = new TestConnection(reader);
        Http1ClientResponseImpl response = resetContentResponse(connection, false);

        assertThat(response.entity().hasEntity(), is(false));
        response.close();

        assertThat(connection.releaseCount(), is(1));
        assertThat(response.trailers().size(), is(0));
        assertThat(reads.get(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void closesResetContentConnectionAfterUnadvertisedFramingFailure() {
        TestConnection connection = new TestConnection(dataReader("1\r\n0\r\n\r\n"));
        Http1ClientResponseImpl response = resetContentResponse(connection, false);

        assertThat(response.entity().hasEntity(), is(false));
        assertThrows(IllegalStateException.class, response::trailers);
        response.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    private static Http1ClientResponseImpl resetContentResponse(TestConnection connection, boolean advertised) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        if (advertised) {
            headers.add(HeaderNames.TRAILER, "X-Test");
        }
        return new Http1ClientResponseImpl(HttpClientConfig.builder().build(),
                                           Http1ClientProtocolConfig.create(),
                                           Status.RESET_CONTENT_205,
                                           Method.GET,
                                           ClientRequestHeaders.create(WritableHeaders.create()),
                                           ClientResponseHeaders.create(headers),
                                           connection,
                                           null,
                                           MediaContext.create(),
                                           ClientUri.create(URI.create("http://localhost/test")),
                                           new CompletableFuture<>(),
                                           null);
    }

    private static InputStream responseStream(String framing,
                                              TestConnection connection,
                                              CompletableFuture<WebClientServiceResponse> whenComplete) {
        var listener = new Http1ConnectionListener() {
        };
        var response = new AtomicReference<>(serviceResponse(Method.HEAD, Status.OK_200, contentLengthHeaders()));
        return switch (framing) {
            case "content-length" -> new Http1CallChainBase.ContentLengthInputStream(connection.helidonSocket(),
                                                                                     connection.reader(),
                                                                                     whenComplete,
                                                                                     response,
                                                                                     1,
                                                                                     listener);
            case "close-delimited" -> new Http1CallChainBase.EverythingInputStream(connection.helidonSocket(),
                                                                                  connection.reader(),
                                                                                  whenComplete,
                                                                                  response,
                                                                                  listener);
            case "chunked" -> new Http1CallChainBase.ChunkedInputStream(connection,
                                                                       connection.reader(),
                                                                       whenComplete,
                                                                       response,
                                                                       listener);
            default -> throw new IllegalArgumentException("Unexpected framing: " + framing);
        };
    }

    private static Http1ClientResponseImpl chunkedResponse(TestConnection connection,
                                                          boolean advertised,
                                                          Http1ClientProtocolConfig protocolConfig) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        if (advertised) {
            headers.add(HeaderNames.TRAILER, "X-Test");
        }
        ClientResponseHeaders responseHeaders = ClientResponseHeaders.create(headers);
        TestServiceRequest serviceRequest = new TestServiceRequest(Method.GET);
        WebClientServiceResponse serviceResponse = Http1CallChainBase.createServiceResponse(
                new Http1ClientImpl(null, Http1ClientConfig.builder().buildPrototype()),
                serviceRequest,
                connection,
                connection.reader(),
                Status.OK_200,
                responseHeaders,
                new CompletableFuture<>());
        return new Http1ClientResponseImpl(HttpClientConfig.builder().build(),
                                           protocolConfig,
                                           Status.OK_200,
                                           Method.GET,
                                           serviceRequest.headers(),
                                           responseHeaders,
                                           (ClientConnection) serviceResponse.connection(),
                                           serviceResponse.inputStream().orElseThrow(),
                                           MediaContext.create(),
                                           ClientUri.create(URI.create("http://localhost/test")),
                                           new CompletableFuture<>(),
                                           serviceResponse);
    }

    private static Http1ClientResponseImpl response(ClientResponseHeaders headers,
                                                    InputStream inputStream,
                                                    TestConnection connection) {
        return new Http1ClientResponseImpl(HttpClientConfig.builder().build(),
                                           Http1ClientProtocolConfig.create(),
                                           Status.OK_200,
                                           Method.GET,
                                           ClientRequestHeaders.create(WritableHeaders.create()),
                                           headers,
                                           connection,
                                           inputStream,
                                           MediaContext.create(),
                                           ClientUri.create(URI.create("http://localhost/test")),
                                           new CompletableFuture<>(),
                                           null);
    }

    private static InputStream inputStream(String entity) {
        return new ByteArrayInputStream(entity.getBytes(StandardCharsets.UTF_8));
    }

    private static DataReader dataReader(String entity) {
        AtomicReference<byte[]> bytes = new AtomicReference<>(entity.getBytes(StandardCharsets.UTF_8));
        DataReader reader = DataReader.create(() -> bytes.getAndSet(null));
        reader.pullData();
        return reader;
    }

    private static DataReader bufferedReaderWithoutFurtherReads(String data) {
        var bytes = new AtomicReference<>(data.getBytes(StandardCharsets.US_ASCII));
        DataReader reader = DataReader.create(() -> {
            byte[] result = bytes.getAndSet(null);
            if (result == null) {
                throw new AssertionError("Unexpected network read while closing a response or reading consumed trailers");
            }
            return result;
        });
        reader.pullData();
        return reader;
    }

    private static ClientResponseHeaders contentLengthHeaders() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.CONTENT_LENGTH, "4");
        return ClientResponseHeaders.create(headers);
    }

    private static final class TestServiceRequest implements WebClientServiceRequest {
        private final Method method;
        private final ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        private final Context context = Context.create();
        private final Map<String, String> properties = new HashMap<>();
        private final CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        private final CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        private String requestId = "test";

        private TestServiceRequest(Method method) {
            this.method = method;
        }

        @Override
        public ClientUri uri() {
            return ClientUri.create(URI.create("http://localhost/test"));
        }

        @Override
        public Method method() {
            return method;
        }

        @Override
        public String protocolId() {
            return "http/1.1";
        }

        @Override
        public ClientRequestHeaders headers() {
            return headers;
        }

        @Override
        public Context context() {
            return context;
        }

        @Override
        public String requestId() {
            return requestId;
        }

        @Override
        public void requestId(String requestId) {
            this.requestId = requestId;
        }

        @Override
        public CompletionStage<WebClientServiceRequest> whenSent() {
            return whenSent;
        }

        @Override
        public CompletionStage<WebClientServiceResponse> whenComplete() {
            return whenComplete;
        }

        @Override
        public Map<String, String> properties() {
            return properties;
        }
    }

    private static final class TestConnection implements ClientConnection {
        private final AtomicInteger releaseCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();
        private final DataReader reader;
        private final RuntimeException closeFailure;

        private TestConnection() {
            this(DataReader.create(() -> null), null);
        }

        private TestConnection(DataReader reader) {
            this(reader, null);
        }

        private TestConnection(DataReader reader, RuntimeException closeFailure) {
            this.reader = reader;
            this.closeFailure = closeFailure;
        }

        @Override
        public DataReader reader() {
            return reader;
        }

        @Override
        public DataWriter writer() {
            return new NoopDataWriter();
        }

        @Override
        public String channelId() {
            return "test";
        }

        @Override
        public HelidonSocket helidonSocket() {
            return new TestSocket();
        }

        @Override
        public void readTimeout(Duration readTimeout) {
        }

        @Override
        public void releaseResource() {
            releaseCount.incrementAndGet();
        }

        @Override
        public void closeResource() {
            closeCount.incrementAndGet();
            if (closeFailure != null) {
                throw closeFailure;
            }
        }

        private int releaseCount() {
            return releaseCount.get();
        }

        private int closeCount() {
            return closeCount.get();
        }
    }

    private static final class NoopDataWriter implements DataWriter {
        @Override
        public void write(BufferData... buffers) {
        }

        @Override
        public void write(BufferData buffer) {
        }

        @Override
        public void writeNow(BufferData... buffers) {
        }

        @Override
        public void writeNow(BufferData buffer) {
        }
    }

    private static final class TestSocket implements HelidonSocket {
        @Override
        public int read(BufferData buffer) {
            return -1;
        }

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
            return "test";
        }

        @Override
        public String childSocketId() {
            return "test";
        }

        @Override
        public byte[] get() {
            return new byte[0];
        }
    }
}
