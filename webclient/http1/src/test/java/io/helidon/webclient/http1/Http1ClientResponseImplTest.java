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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import io.helidon.common.Size;
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1ClientResponseImplTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void interleavedEntityReadersKeepIndependentStagedBytes(boolean repeatedEntity) throws IOException {
        byte[] content = ("a".repeat(512)
                + "b".repeat(512)
                + "c".repeat(512)
                + "d".repeat(512)
                + "e".repeat(512)).getBytes(StandardCharsets.UTF_8);
        var connection = new TestConnection();
        var response = response(contentLengthHeaders(content.length), new ByteArrayInputStream(content), connection);
        var firstEntity = response.entity();
        var secondEntity = repeatedEntity ? response.entity() : firstEntity.copy(() -> { });
        InputStream first = firstEntity.inputStream();
        InputStream second = secondEntity.inputStream();
        byte[] target = new byte[512];

        assertThat(first.read(), is((int) 'a'));
        assertThat(second.read(target), is(target.length));
        assertThat(target, is("b".repeat(512).getBytes(StandardCharsets.UTF_8)));
        assertThat(second.read(target), is(target.length));
        assertThat(target, is("c".repeat(512).getBytes(StandardCharsets.UTF_8)));
        assertThat(first.readNBytes(511), is("a".repeat(511).getBytes(StandardCharsets.UTF_8)));

        // The first reader refills while the second still retains its consumed old cursor.
        assertThat(first.read(), is((int) 'd'));
        assertThat(second.read(), is((int) 'e'));
        assertThat(first.readNBytes(511), is("d".repeat(511).getBytes(StandardCharsets.UTF_8)));
        assertThat(second.readNBytes(511), is("e".repeat(511).getBytes(StandardCharsets.UTF_8)));
        assertThat(first.read(), is(-1));
        response.close();
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void preservesOutstandingStagingWhenAnotherReaderCompletesResponse() throws IOException {
        byte[] content = ("a".repeat(512) + "b".repeat(512)).getBytes(StandardCharsets.UTF_8);
        var connection = new TestConnection();
        var response = response(contentLengthHeaders(content.length), new ByteArrayInputStream(content), connection);
        InputStream first = response.entity().inputStream();
        InputStream second = response.entity().inputStream();

        assertThat(first.read(), is((int) 'a'));
        assertThat(second.readNBytes(512), is("b".repeat(512).getBytes(StandardCharsets.UTF_8)));
        assertThat(second.read(), is(-1));
        assertThat(connection.releaseCount(), is(1));
        assertThat(first.readNBytes(511), is("a".repeat(511).getBytes(StandardCharsets.UTF_8)));
        assertThat(assertThrows(IllegalStateException.class, first::read).getMessage(), is("Response is closed"));
        response.close();
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void boundsStagingReadsAcrossSizeChangesPartialReadsAndZeroProgress() throws IOException {
        byte[] content = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+-".getBytes(StandardCharsets.UTF_8);
        var requestedLengths = new ArrayList<Integer>();
        var source = new ByteArrayInputStream(content) {
            private boolean zeroReturned;

            @Override
            public int read(byte[] bytes, int offset, int length) {
                requestedLengths.add(length);
                if (!zeroReturned) {
                    zeroReturned = true;
                    return 0;
                }
                return super.read(bytes, offset, Math.min(length, 7));
            }
        };
        var connection = new TestConnection();
        var response = response(contentLengthHeaders(content.length), source, connection);
        InputStream stream = response.inputStream();
        byte[] target = new byte[65536];

        assertThat(stream.read(target, 0, 0), is(0));
        assertThat(requestedLengths, is(List.of()));
        assertThat(stream.read(target, 0, 64), is(0));
        int position = 0;
        for (int length : new int[] {1, 64, 512, 8192, 65536, 64}) {
            int count = Math.min(length, 7);
            assertThat(stream.read(target, 0, length), is(count));
            assertThat(Arrays.copyOf(target, count), is(Arrays.copyOfRange(content, position, position + count)));
            position += count;
        }
        stream.close();
        response.close();

        assertThat(requestedLengths, is(List.of(64, 1, 64, 512, 8192, 8192, 64, 1)));
        assertThat(connection.closeCount(), is(1));
        assertThat(connection.releaseCount(), is(0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void keepsCloseProbeAtOneByteAfterAnEightKiBLease(int remaining) throws IOException {
        byte[] content = new byte[8192 + remaining];
        Arrays.fill(content, (byte) 'x');
        var requestedLengths = new ArrayList<Integer>();
        var source = new ByteArrayInputStream(content) {
            @Override
            public int read(byte[] bytes, int offset, int length) {
                requestedLengths.add(length);
                return super.read(bytes, offset, length);
            }
        };
        var connection = new TestConnection();
        var response = response(contentLengthHeaders(content.length), source, connection);
        InputStream stream = response.inputStream();

        assertThat(stream.read(new byte[65536]), is(8192));
        stream.close();
        response.close();

        assertThat(requestedLengths, is(List.of(8192, 1)));
        assertThat(connection.releaseCount(), is(remaining == 0 ? 1 : 0));
        assertThat(connection.closeCount(), is(remaining == 0 ? 0 : 1));
        if (remaining == 1) {
            assertThat(stream.read(), is((int) 'x'));
            assertThrows(IllegalStateException.class, stream::read);
        }
    }

    @Test
    void stagedReadFailurePreservesOutstandingBytesAndCleanupFailures() throws IOException {
        var readFailure = new IOException("read failed");
        var decoderCloseFailure = new IOException("decoder close failed");
        var connectionCloseFailure = new IllegalStateException("connection close failed");
        var decoderCloses = new AtomicInteger();
        var source = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public int read(byte[] bytes, int offset, int length) {
                if (available() == 0) {
                    throw new UncheckedIOException(readFailure);
                }
                return super.read(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                decoderCloses.incrementAndGet();
                throw decoderCloseFailure;
            }
        };
        var connection = new TestConnection(DataReader.create(() -> null), connectionCloseFailure);
        var response = response(contentLengthHeaders(), source, connection);
        InputStream first = response.entity().inputStream();
        InputStream second = response.entity().inputStream();

        assertThat(first.read(), is((int) 'd'));
        var failure = assertThrows(UncheckedIOException.class, second::read);

        assertThat(failure.getCause(), sameInstance(readFailure));
        assertThat(failure.getSuppressed().length, is(2));
        assertThat(failure.getSuppressed()[0], sameInstance(connectionCloseFailure));
        assertThat(failure.getSuppressed()[1], sameInstance(decoderCloseFailure));
        assertThat(first.readNBytes(3), is("ata".getBytes(StandardCharsets.UTF_8)));
        assertThat(assertThrows(IllegalStateException.class, first::read).getCause(), sameInstance(failure));
        response.close();
        assertThat(decoderCloses.get(), is(1));
        assertThat(connection.closeCount(), is(1));
        assertThat(connection.releaseCount(), is(0));
    }

    @Test
    void exactChunkedReadThenCloseConsumesAdvertisedTrailers() throws IOException {
        var connection = new TestConnection(dataReader("4\r\ndata\r\n0\r\nX-Result: done\r\n\r\n"));
        var source = new Http1CallChainBase.ChunkedInputStream(connection,
                                                             connection.reader(),
                                                             new CompletableFuture<>(),
                                                             new AtomicReference<>(),
                                                             true,
                                                             new Http1ConnectionListener() { },
                                                             null);
        var headers = WritableHeaders.create();
        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        headers.add(HeaderNames.TRAILER, "X-Result");
        var response = response(ClientResponseHeaders.create(headers), source, connection);

        try (InputStream stream = response.inputStream()) {
            assertThat(stream.readNBytes(4), is("data".getBytes(StandardCharsets.UTF_8)));
            assertThat(connection.releaseCount(), is(0));
        }

        assertThat(response.trailers().get(HeaderNames.create("X-Result")).get(), is("done"));
        assertThat(connection.reader().available(), is(0));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void trailerFailurePreservesOutstandingStagingAndRetiresConnection() throws IOException {
        var connection = new TestConnection(dataReader("X-Result: done\r\n\r\nunexpected"));
        var decoderCloses = new AtomicInteger();
        var source = new ByteArrayInputStream("a".repeat(512).getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                decoderCloses.incrementAndGet();
            }
        };
        var headers = WritableHeaders.create();
        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        headers.add(HeaderNames.TRAILER, "X-Result");
        var response = response(ClientResponseHeaders.create(headers), source, connection);
        InputStream first = response.entity().inputStream();
        InputStream second = response.entity().inputStream();

        assertThat(first.read(), is((int) 'a'));
        var failure = assertThrows(IllegalStateException.class, second::read);

        assertThat(failure.getMessage(), is("Unexpected data after response trailers"));
        assertThat(first.readNBytes(511), is("a".repeat(511).getBytes(StandardCharsets.UTF_8)));
        assertThat(assertThrows(IllegalStateException.class, first::read).getCause(), sameInstance(failure));
        response.close();
        assertThat(decoderCloses.get(), is(1));
        assertThat(connection.closeCount(), is(1));
        assertThat(connection.releaseCount(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void decodedBufferLimitStillCountsAcrossStagingLeases(boolean gzip) throws IOException {
        var decoderCloses = new AtomicInteger();
        InputStream decoder = decoder(gzip, false, decoderCloses::incrementAndGet, "a".repeat(16384));
        var connection = new TestConnection();
        var protocolConfig = Http1ClientProtocolConfig.builder()
                .maxBufferedEntitySize(Size.create(8192))
                .build();
        var response = response(contentLengthHeaders(), decoder, connection, protocolConfig);

        var failure = assertThrows(IllegalStateException.class, () -> response.entity().buffer());

        assertThat(failure.getMessage(), is("Maximum buffered entity length exceeded"));
        assertThat(decoderCloses.get(), is(1));
        assertThat(assertThrows(IOException.class, decoder::read).getMessage(), is("Stream closed"));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closesDecoderAfterByteArrayConversion(boolean gzip) throws IOException {
        InputStream decoder = decoder(gzip, false, () -> { });
        TestConnection connection = new TestConnection();
        Http1ClientResponseImpl response = response(contentLengthHeaders(), decoder, connection);

        assertThat(response.entity().as(byte[].class), is("data".getBytes(StandardCharsets.UTF_8)));

        assertThat(assertThrows(IOException.class, decoder::read).getMessage(), is("Stream closed"));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closesDecoderAfterByteArrayConversionFails(boolean gzip) throws IOException {
        TestConnection connection = new TestConnection();
        var transportClosesAtDecoderClose = new AtomicInteger(-1);
        InputStream decoder = decoder(gzip, true, () -> transportClosesAtDecoderClose.set(connection.closeCount()));
        Http1ClientResponseImpl response = response(contentLengthHeaders(), decoder, connection);

        assertThrows(UncheckedIOException.class, () -> response.entity().as(byte[].class));

        assertThat(assertThrows(IOException.class, decoder::read).getMessage(), is("Stream closed"));
        assertThat(transportClosesAtDecoderClose.get(), is(1));
        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void keepsLazyDecoderOpenAfterPartialRead(boolean gzip) throws IOException {
        InputStream decoder = decoder(gzip, false, () -> { });
        TestConnection connection = new TestConnection();
        Http1ClientResponseImpl response = response(contentLengthHeaders(), decoder, connection);

        InputStream entityStream = response.entity().inputStream();
        assertThat(entityStream.read(), is((int) 'd'));
        assertThat(entityStream.read(), is((int) 'a'));
        assertThat(connection.releaseCount(), is(0));
        decoder.close();
        response.close();
    }

    @Test
    void preservesReadFailureAndClosesTransportWhenDecoderCloseFails() {
        var readFailure = new IOException("read failed");
        var closeFailure = new IOException("close failed");
        var reads = new AtomicInteger();
        var connection = new TestConnection();
        var response = response(contentLengthHeaders(), new InputStream() {
            @Override
            public int read() throws IOException {
                reads.incrementAndGet();
                throw readFailure;
            }

            @Override
            public void close() throws IOException {
                throw closeFailure;
            }
        }, connection);

        var failure = assertThrows(UncheckedIOException.class, () -> response.entity().as(byte[].class));

        assertThat(failure.getCause(), sameInstance(readFailure));
        assertThat(failure.getSuppressed().length, is(1));
        assertThat(failure.getSuppressed()[0], sameInstance(closeFailure));
        assertThat(reads.get(), is(1));
        assertThat(connection.closeCount(), is(1));
        assertThat(connection.releaseCount(), is(0));
    }

    @Test
    void propagatesReadErrorWithoutFailureCleanup() {
        var readError = new OutOfMemoryError("read failed");
        var decoderCloses = new AtomicInteger();
        var connection = new TestConnection();
        var response = response(contentLengthHeaders(), new InputStream() {
            @Override
            public int read() {
                throw readError;
            }

            @Override
            public void close() {
                decoderCloses.incrementAndGet();
            }
        }, connection);

        var failure = assertThrows(OutOfMemoryError.class, () -> response.entity().inputStream().read());

        assertThat(failure, sameInstance(readError));
        assertThat(decoderCloses.get(), is(0));
        assertThat(connection.closeCount(), is(0));
        assertThat(connection.releaseCount(), is(0));
        response.close();
    }

    @Test
    void propagatesDecoderCloseErrorDuringFailureCleanup() {
        var closeError = new OutOfMemoryError("close failed");
        var connection = new TestConnection();
        var response = response(contentLengthHeaders(), new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("read failed");
            }

            @Override
            public void close() {
                throw closeError;
            }
        }, connection);

        var failure = assertThrows(OutOfMemoryError.class, () -> response.entity().inputStream().read());

        assertThat(failure, sameInstance(closeError));
        assertThat(connection.closeCount(), is(1));
        assertThat(connection.releaseCount(), is(0));
    }

    @Test
    void chunkedMustBeFinalTransferCoding() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.create(HeaderNames.TRANSFER_ENCODING, "chunked, gzip"));
        assertThat(Http1CallChainBase.isChunkedFinalTransferCoding(headers), is(false));

        headers = WritableHeaders.create();
        headers.add(HeaderValues.create(HeaderNames.TRANSFER_ENCODING, "gzip, chunked"));
        assertThat(Http1CallChainBase.isChunkedFinalTransferCoding(headers), is(true));
    }

    @Test
    void doesNotCreateEntityStreamForHeadResponseWithoutFraming() {
        WebClientServiceResponse response = serviceResponse(Method.HEAD,
                                                            Status.OK_200,
                                                            ClientResponseHeaders.create(WritableHeaders.create()));

        assertThat(response.inputStream().isEmpty(), is(true));
    }

    @Test
    void doesNotCreateEntityStreamForHeadResponseWithContentLength() {
        WebClientServiceResponse response = serviceResponse(Method.HEAD,
                                                            Status.OK_200,
                                                            contentLengthHeaders());

        assertThat(response.inputStream().isEmpty(), is(true));
    }

    @Test
    void doesNotCreateEntityStreamForNotModifiedWithoutFraming() {
        WebClientServiceResponse response = serviceResponse(Method.GET,
                                                            Status.NOT_MODIFIED_304,
                                                            ClientResponseHeaders.create(WritableHeaders.create()));

        assertThat(response.inputStream().isEmpty(), is(true));
    }

    @Test
    void doesNotCreateEntityStreamForResetContentWithoutFraming() {
        WebClientServiceResponse response = serviceResponse(Method.GET,
                                                            Status.RESET_CONTENT_205,
                                                            ClientResponseHeaders.create(WritableHeaders.create()));

        assertThat(response.inputStream().isEmpty(), is(true));
    }

    @Test
    void createsEntityStreamForUnframedGetResponse() {
        WebClientServiceResponse response = serviceResponse(Method.GET,
                                                            Status.OK_200,
                                                            ClientResponseHeaders.create(WritableHeaders.create()));

        assertThat(response.inputStream().isPresent(), is(true));
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
                Status.NO_CONTENT_204,
                responseHeaders,
                new CompletableFuture<>(),
                null);
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
                                                                       null);
        InputStream tunnelStream = response.inputStream();

        byte[] actual = new byte[tunnelData.length()];
        assertThat(tunnelStream.read(actual), is(actual.length));
        assertThat(new String(actual, StandardCharsets.US_ASCII), is(tunnelData));
        response.close();
        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void closesCloseDelimitedResponseAfterEntityRead() {
        TestConnection connection = new TestConnection();
        Http1ClientResponseImpl response = response(ClientResponseHeaders.create(WritableHeaders.create()),
                                                    inputStream("data"),
                                                    connection);

        assertThat(response.entity().as(String.class), is("data"));

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void closesUnreadCloseDelimitedResponse() {
        TestConnection connection = new TestConnection();
        Http1ClientResponseImpl response = response(ClientResponseHeaders.create(WritableHeaders.create()),
                                                    inputStream("data"),
                                                    connection);

        response.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void releasesNoEntityResponseWithContentLength() {
        TestConnection connection = new TestConnection();
        Http1ClientResponseImpl response = response(contentLengthHeaders(),
                                                    null,
                                                    connection);

        response.close();

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void closesNoEntityResponseWithUnexpectedBufferedData() {
        TestConnection connection = new TestConnection(dataReader("head"));
        Http1ClientResponseImpl response = response(ClientResponseHeaders.create(WritableHeaders.create()),
                                                    null,
                                                    connection);

        response.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void releasesLengthDelimitedResponseAfterEntityRead() {
        TestConnection connection = new TestConnection();
        Http1ClientResponseImpl response = response(contentLengthHeaders(),
                                                    inputStream("data"),
                                                    connection);

        assertThat(response.entity().as(String.class), is("data"));

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
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
        TestConnection connection = new TestConnection();
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
                                                                           false,
                                                                           new Http1ConnectionListener() {
                                                                           }, null);

        inputStream.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void completesResponseWhenPrematureChunkedCloseFails() {
        IllegalStateException closeFailure = new IllegalStateException("Connection close failed");
        TestConnection connection = new TestConnection(dataReader("2\r\nOK\r\n"), closeFailure);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        InputStream inputStream = new Http1CallChainBase.ChunkedInputStream(connection,
                                                                           connection.reader(),
                                                                           whenComplete,
                                                                           new AtomicReference<>(),
                                                                           false,
                                                                           new Http1ConnectionListener() {
                                                                           }, null);

        IllegalStateException actualFailure = assertThrows(IllegalStateException.class, inputStream::close);

        assertThat(actualFailure, sameInstance(closeFailure));
        assertThat(whenComplete.isDone(), is(true));
    }

    private static InputStream decoder(boolean gzip, boolean truncated, Runnable whenSourceCloses) throws IOException {
        return decoder(gzip, truncated, whenSourceCloses, "data");
    }

    private static InputStream decoder(boolean gzip, boolean truncated, Runnable whenSourceCloses, String content)
            throws IOException {
        var encoded = new ByteArrayOutputStream();
        try (var encoder = gzip ? new GZIPOutputStream(encoded) : new DeflaterOutputStream(encoded)) {
            encoder.write(content.getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes = encoded.toByteArray();
        var source = new ByteArrayInputStream(bytes, 0, truncated ? bytes.length - 3 : bytes.length) {
            @Override
            public void close() {
                whenSourceCloses.run();
            }
        };
        return gzip ? new GZIPInputStream(source) : new InflaterInputStream(source);
    }

    private static WebClientServiceResponse serviceResponse(Method method,
                                                            Status status,
                                                            ClientResponseHeaders headers) {
        return Http1CallChainBase.createServiceResponse(new Http1ClientImpl(null,
                                                                            Http1ClientConfig.builder().buildPrototype()),
                                                        new TestServiceRequest(method),
                                                        new TestConnection(),
                                                        status,
                                                        headers,
                                                        new CompletableFuture<>(),
                                                        null);
    }

    private static Http1ClientResponseImpl response(ClientResponseHeaders headers,
                                                    InputStream inputStream,
                                                    TestConnection connection) {
        return response(headers, inputStream, connection, Http1ClientProtocolConfig.create());
    }

    private static Http1ClientResponseImpl response(ClientResponseHeaders headers,
                                                    InputStream inputStream,
                                                    TestConnection connection,
                                                    Http1ClientProtocolConfig protocolConfig) {
        return new Http1ClientResponseImpl(HttpClientConfig.builder().build(),
                                           protocolConfig,
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

    private static ClientResponseHeaders contentLengthHeaders() {
        return contentLengthHeaders(4);
    }

    private static ClientResponseHeaders contentLengthHeaders(int length) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.CONTENT_LENGTH, Integer.toString(length));
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
