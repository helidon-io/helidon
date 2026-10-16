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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.common.ParserHelper;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriFragment;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Http1HeadersParser;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.http1.Http1ConnectionListener;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

abstract class Http1CallChainBase implements WebClientService.Chain {
    private static final int MAX_SKIPPED_INFORMATIONAL_RESPONSES = 32;
    private static final Supplier<IllegalArgumentException> INVALID_SIZE_EXCEPTION_SUPPLIER =
            () -> new IllegalArgumentException("Chunk size is invalid");

    private final BufferData writeBuffer = BufferData.growing(128);
    private final HttpClientConfig clientConfig;
    private final Http1ClientProtocolConfig protocolConfig;
    private final ClientConnection connection;
    private final Http1ClientRequestImpl originalRequest;
    private final Tls tls;
    private final Proxy proxy;
    private final boolean keepAlive;
    private final CompletableFuture<WebClientServiceResponse> whenComplete;
    private final Duration timeout;
    private final Http1ClientImpl http1Client;
    private final Http1ConnectionListener sendListener;
    private final Http1ConnectionListener recvListener;

    private ClientConnection effectiveConnection;
    private WebClientServiceResponse transportResponse;
    private int skippedInformationalResponses;

    Http1CallChainBase(Http1ClientImpl http1Client,
                       Http1ClientRequestImpl clientRequest,
                       CompletableFuture<WebClientServiceResponse> whenComplete) {
        this.clientConfig = http1Client.clientConfig();
        this.protocolConfig = http1Client.protocolConfig();
        this.originalRequest = clientRequest;
        this.timeout = clientRequest.readTimeout();
        this.connection = clientRequest.connection().orElse(null);
        this.tls = clientRequest.tls();
        this.proxy = clientRequest.proxy();
        this.keepAlive = clientRequest.keepAlive();
        this.http1Client = clientRequest.http1Client();
        this.whenComplete = whenComplete;
        this.sendListener = http1Client.sendListener();
        this.recvListener = http1Client.recvListener();
    }

    static WebClientServiceResponse createServiceResponse(Http1ClientImpl http1Client,
                                                          WebClientServiceRequest serviceRequest,
                                                          ClientConnection connection,
                                                          DataReader reader,
                                                          Status responseStatus,
                                                          ClientResponseHeaders responseHeaders,
                                                          CompletableFuture<WebClientServiceResponse> whenComplete) {
        HttpClientConfig clientConfig = http1Client.clientConfig();
        Http1ConnectionListener recvListener = http1Client.recvListener();
        WebClientServiceResponse.Builder builder = WebClientServiceResponse.builder();
        AtomicReference<WebClientServiceResponse> response = new AtomicReference<>();
        boolean successfulConnect = isSuccessfulConnect(serviceRequest.method(), responseStatus);
        boolean exposeEntity = mayExposeEntity(serviceRequest.method(), responseStatus, responseHeaders);
        boolean exposesChunkedEntity = !successfulConnect
                && exposeEntity
                && isChunkedFinalTransferCoding(responseHeaders);
        ClientConnection responseConnection = exposesChunkedEntity
                ? new SingleReleaseClientConnection(connection)
                : connection;

        if (exposeEntity) {
            // this may be an entity (if content length is set to zero, we know there is no entity)
            try {
                InputStream inputStream = successfulConnect
                        ? new EverythingInputStream(responseConnection.helidonSocket(),
                                                    reader,
                                                    whenComplete,
                                                    response,
                                                    recvListener)
                        : inputStream(clientConfig,
                                      recvListener,
                                      responseConnection,
                                      response,
                                      responseHeaders,
                                      reader,
                                      whenComplete);
                builder.inputStream(inputStream);
            } catch (RuntimeException | Error e) {
                whenComplete.completeExceptionally(e);
                throw e;
            }
        }

        WebClientServiceResponse serviceResponse = builder
                .connection(responseConnection)
                .headers(responseHeaders)
                .status(responseStatus)
                .whenComplete(whenComplete)
                .serviceRequest(serviceRequest)
                .build();

        response.set(serviceResponse);
        return serviceResponse;
    }

    static boolean isChunkedEntityDiscarded(InputStream inputStream) {
        return inputStream instanceof CloseOnceInputStream closeOnce && closeOnce.chunkedInputStream.discarded;
    }

    static void writeHeaders(ClientConnection connection,
                             Headers headers,
                             BufferData bufferData,
                             boolean validate,
                             Http1ConnectionListener sendListener) {
        for (Header header : headers) {
            if (validate) {
                header.validate();
            }
            header.writeHttp1Header(bufferData);
        }
        bufferData.write(Bytes.CR_BYTE);
        bufferData.write(Bytes.LF_BYTE);

        sendListener.headers(connection.helidonSocket(), headers);
    }

    static boolean isPreContinueInterimResponse(Status responseStatus) {
        return isInterimResponse(responseStatus)
                && responseStatus.code() != Status.CONTINUE_100.code();
    }

    static boolean statusAllowsEntity(Status responseStatus) {
        int statusCode = responseStatus.code();
        return responseStatus.family() != Status.Family.INFORMATIONAL
                && statusCode != Status.NO_CONTENT_204.code()
                && statusCode != Status.RESET_CONTENT_205.code()
                && statusCode != Status.NOT_MODIFIED_304.code();
    }

    static boolean isSuccessfulConnect(Method requestMethod, Status responseStatus) {
        return requestMethod == Method.CONNECT && responseStatus.family() == Status.Family.SUCCESSFUL;
    }

    static boolean isChunkedFinalTransferCoding(Headers headers) {
        if (!headers.contains(HeaderNames.TRANSFER_ENCODING)) {
            return false;
        }
        List<String> values = headers.get(HeaderNames.TRANSFER_ENCODING).allValues();
        String lastValue = values.get(values.size() - 1);
        int lastSeparator = lastValue.lastIndexOf(',');
        String finalCoding = lastValue.substring(lastSeparator + 1).trim();
        return HeaderValues.TRANSFER_ENCODING_CHUNKED.get().equalsIgnoreCase(finalCoding);
    }

    static int readChunkSize(DataReader reader) {
        int endOfChunkSize = reader.findNewLine(256);
        if (endOfChunkSize == 256) {
            throw new IllegalStateException("Cannot read chunked entity, end of line not found within 256 bytes");
        }
        String chunkSizeLine = reader.readAsciiString(endOfChunkSize);
        reader.skip(2); // CRLF
        return parseChunkSizeLine(chunkSizeLine);
    }

    static int parseChunkSizeLine(String chunkSizeLine) {
        int extension = chunkSizeLine.indexOf(';');
        String hex = (extension == -1 ? chunkSizeLine : chunkSizeLine.substring(0, extension)).strip();
        try {
            return ParserHelper.parseNonNegative(hex, 16, INVALID_SIZE_EXCEPTION_SUPPLIER);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Chunk size is not a number");
        }
    }

    static boolean consumeBufferedFinalChunk(DataReader reader) {
        int available = reader.available();
        if (available < 5) {
            return false;
        }
        BufferData bufferData = reader.getBuffer(available);
        int endOfChunkSize = bufferData.indexOf(Bytes.CR_BYTE);
        if (endOfChunkSize == -1 || endOfChunkSize >= 256 || endOfChunkSize + 4 != available) {
            return false;
        }
        if (bufferData.get(endOfChunkSize + 1) != Bytes.LF_BYTE
                || bufferData.get(endOfChunkSize + 2) != Bytes.CR_BYTE
                || bufferData.get(endOfChunkSize + 3) != Bytes.LF_BYTE) {
            return false;
        }
        try {
            if (parseChunkSizeLine(bufferData.readString(endOfChunkSize, StandardCharsets.US_ASCII)) != 0) {
                return false;
            }
        } catch (RuntimeException ignored1) {
            return false;
        }
        reader.skip(available);
        return true;
    }

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
        skippedInformationalResponses = 0;
        // either use the explicit connection, or obtain one (keep alive or one-off)
        effectiveConnection = connection == null ? obtainConnection(serviceRequest) : connection;
        try {
            effectiveConnection.readTimeout(this.timeout);

            DataWriter writer = effectiveConnection.writer();
            DataReader reader = effectiveConnection.reader();
            ClientUri uri = serviceRequest.uri();
            ClientRequestHeaders headers = serviceRequest.headers();

            writeBuffer.clear();
            originalRequest.sanitizeRedirectHeaders(uri, headers);
            prologue(effectiveConnection, writeBuffer, serviceRequest, uri);
            headers.setIfAbsent(HeaderValues.create(HeaderNames.HOST, uri.authority()));

            WebClientServiceResponse response = doProceed(effectiveConnection,
                                                          serviceRequest,
                                                          headers,
                                                          writer,
                                                          reader,
                                                          writeBuffer);
            // Preserve the original framing and body ownership before client services replace response headers or content.
            transportResponse = response;
            return response;
        } catch (RuntimeException | Error e) {
            if (connection == null) {
                try {
                    effectiveConnection.closeResource();
                } catch (Throwable closeFailure) {
                    e.addSuppressed(closeFailure);
                }
            }
            throw e;
        }
    }

    abstract WebClientServiceResponse doProceed(ClientConnection connection,
                                                WebClientServiceRequest request,
                                                ClientRequestHeaders headers,
                                                DataWriter writer,
                                                DataReader reader,
                                                BufferData writeBuffer);

    void prologue(ClientConnection effectiveConnection,
                  BufferData nonEntityData,
                  WebClientServiceRequest request,
                  ClientUri uri) {
        if (request.method() == Method.CONNECT) {
            // When CONNECT, the first line contains the remote host:port, in the same way as the HOST header.
            nonEntityData.writeAscii(request.method().text()
                                             + " "
                                             + request.headers().get(HeaderNames.HOST).get()
                                             + " HTTP/1.1\r\n");
        } else {
            String requestTarget = requestTarget(uri);
            // When proxy is set, ensure that the request uses absolute URI because of Section 5.1.2 Request-URI in
            // https://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html which states: "The absoluteURI form is REQUIRED when the
            // request is being made to a proxy."
            String absoluteUri = uri.scheme() + "://" + uri.host() + ":" + uri.port();
            String requestUri = proxy == Proxy.noProxy()
                    || (
                    proxy.type() == Proxy.ProxyType.HTTP
                            && proxy.isNoHosts(new InetSocketAddress(uri.host(), uri.port())))
                    || (proxy.type() == Proxy.ProxyType.SYSTEM && !proxy.isUsingSystemProxy(absoluteUri))
                    || clientConfig.relativeUris()
                    ? "" // don't set host details, so it becomes relative URI
                    : absoluteUri;
            nonEntityData.writeAscii(request.method().text()
                                             + " "
                                             + requestUri
                                             + requestTarget
                                             + " HTTP/1.1\r\n");
        }

        if (sendListener.enabled()) {
            sendListener.prologue(effectiveConnection.helidonSocket(), HttpPrologue.create("HTTP/1.1",
                                                                                           "HTTP",
                                                                                           "1.1",
                                                                                           request.method(),
                                                                                           uri.path(),
                                                                                           uri.query(),
                                                                                           UriFragment.empty()));
        }
    }

    ClientResponseHeaders readHeaders(DataReader reader) {
        WritableHeaders<?> writable = Http1HeadersParser.readHeaders(reader,
                                                                     Http1ClientConfigSupport.maxHeadersSize(protocolConfig),
                                                                     protocolConfig.validateResponseHeaders());
        return ClientResponseHeaders.create(writable, clientConfig.mediaTypeParserMode());
    }

    HttpClientConfig clientConfig() {
        return clientConfig;
    }

    Http1ClientProtocolConfig protocolConfig() {
        return protocolConfig;
    }

    ClientConnection connection() {
        return effectiveConnection;
    }

    WebClientServiceResponse transportResponse() {
        return transportResponse;
    }

    Http1ClientRequestImpl originalRequest() {
        return originalRequest;
    }

    CompletableFuture<WebClientServiceResponse> whenComplete() {
        return whenComplete;
    }

    WebClientServiceResponse readResponse(WebClientServiceRequest serviceRequest,
                                          ClientConnection connection,
                                          DataReader reader) {
        ResponseHead responseHead = readResponseHead(connection, reader);

        return createServiceResponse(http1Client,
                                     serviceRequest,
                                     connection,
                                     reader,
                                     responseHead.status(),
                                     responseHead.headers(),
                                     whenComplete);
    }

    Http1ConnectionListener sendListener() {
        return sendListener;
    }

    Http1ConnectionListener recvListener() {
        return recvListener;
    }

    ResponseHead readResponseHead(ClientConnection connection, DataReader reader) {
        return readResponseHead(connection, reader, Http1CallChainBase::isInterimResponse);
    }

    ResponseHead readResponseHead(ClientConnection connection, DataReader reader, Predicate<Status> skippedResponse) {
        return readResponseHead(connection, reader, skippedResponse, true);
    }

    ResponseHead readResponseHead(ClientConnection connection,
                                  DataReader reader,
                                  Predicate<Status> skippedResponse,
                                  boolean closeOnReadFailure) {
        while (true) {
            Status responseStatus = readResponseStatus(connection, reader, closeOnReadFailure);
            ClientResponseHeaders responseHeaders = readResponseHeaders(connection, reader);

            if (!skippedResponse.test(responseStatus)) {
                return new ResponseHead(responseStatus, responseHeaders);
            }
            countSkippedInformationalResponse(connection);
        }
    }

    void countSkippedInformationalResponse(ClientConnection connection) {
        if (skippedInformationalResponses == MAX_SKIPPED_INFORMATIONAL_RESPONSES) {
            IllegalStateException failure = new IllegalStateException("Too many HTTP informational responses");
            try {
                connection.closeResource();
            } catch (Exception e) {
                failure.addSuppressed(e);
            }
            throw failure;
        }
        skippedInformationalResponses++;
    }

    Status readResponseStatus(ClientConnection connection, DataReader reader, boolean closeOnReadFailure) {
        Status responseStatus;
        try {
            responseStatus = Http1StatusParser.readStatus(reader, protocolConfig.maxStatusLineLength());
        } catch (RuntimeException e) {
            if (closeOnReadFailure || !(e instanceof UncheckedIOException)) {
                // A connection cannot be reused after a malformed status or a normal response read failure.
                try {
                    connection.closeResource();
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }
            }
            throw e;
        }
        recvListener.status(connection.helidonSocket(), responseStatus);
        return responseStatus;
    }

    ClientResponseHeaders readResponseHeaders(ClientConnection connection, DataReader reader) {
        ClientResponseHeaders responseHeaders = readHeaders(reader);
        recvListener.headers(connection.helidonSocket(), responseHeaders);
        return responseHeaders;
    }

    private static boolean isInterimResponse(Status responseStatus) {
        return responseStatus.family() == Status.Family.INFORMATIONAL
                && responseStatus.code() != Status.SWITCHING_PROTOCOLS_101.code();
    }

    private static boolean mayExposeEntity(Method requestMethod, Status responseStatus, ClientResponseHeaders responseHeaders) {
        if (isSuccessfulConnect(requestMethod, responseStatus)) {
            return true;
        }
        if (requestMethod == Method.HEAD) {
            return false;
        }
        if (!statusAllowsEntity(responseStatus)) {
            return false;
        }
        if (responseHeaders.contains(HeaderNames.TRANSFER_ENCODING)) {
            return true;
        }
        if (responseHeaders.contains(HeaderValues.CONTENT_LENGTH_ZERO)) {
            return false;
        }
        // if we decide to support HTTP/1.0, we may have an entity without any headers
        // in HTTP/1.1, we should have a content encoding
        return true;
    }

    private static InputStream inputStream(HttpClientConfig clientConfig,
                                           Http1ConnectionListener recvListener,
                                           ClientConnection connection,
                                           AtomicReference<WebClientServiceResponse> response,
                                           ClientResponseHeaders responseHeaders,
                                           DataReader reader,
                                           CompletableFuture<WebClientServiceResponse> whenComplete) {
        ContentEncodingContext encodingSupport = clientConfig.contentEncoding();
        HelidonSocket helidonSocket = connection.helidonSocket();

        ContentDecoder decoder;

        if (encodingSupport.contentDecodingEnabled() && responseHeaders.contains(HeaderNames.CONTENT_ENCODING)) {
            String contentEncoding = responseHeaders.get(HeaderNames.CONTENT_ENCODING).get();
            if (encodingSupport.contentDecodingSupported(contentEncoding)) {
                decoder = encodingSupport.decoder(contentEncoding);
            } else {
                decoder = ContentDecoder.NO_OP;
            }
        } else {
            decoder = ContentDecoder.NO_OP;
        }
        InputStream inputStream;
        if (isChunkedFinalTransferCoding(responseHeaders)) {
            var chunkedInputStream = new ChunkedInputStream(connection,
                                                          reader,
                                                          whenComplete,
                                                          response,
                                                          recvListener);
            return new CloseOnceInputStream(decoder.apply(chunkedInputStream), chunkedInputStream);
        } else if (!responseHeaders.contains(HeaderNames.TRANSFER_ENCODING)
                && responseHeaders.contains(HeaderNames.CONTENT_LENGTH)) {
            long length = responseHeaders.contentLength().getAsLong();
            inputStream = new ContentLengthInputStream(helidonSocket,
                                                       reader,
                                                       whenComplete,
                                                       response,
                                                       length,
                                                       recvListener);
        } else {
            // we assume the rest of the connection is entity (valid for HTTP/1.0, HTTP CONNECT method etc.
            inputStream = new EverythingInputStream(helidonSocket, reader, whenComplete, response, recvListener);
        }
        return decoder.apply(inputStream);
    }

    private static void completeResponse(CompletableFuture<WebClientServiceResponse> whenComplete,
                                         AtomicReference<WebClientServiceResponse> response) {
        WebClientServiceResponse serviceResponse = response.get();
        if (serviceResponse != null) {
            whenComplete.complete(serviceResponse);
        }
    }

    private static String requestTarget(ClientUri uri) {
        String requestTarget = uri.pathWithQueryAndFragment();
        var fragment = uri.fragment();
        if (!fragment.hasValue()) {
            return requestTarget;
        }
        String rawFragment = fragment.rawValue();
        int fragmentLength = requestTarget.endsWith(rawFragment) ? rawFragment.length() : fragment.value().length();
        return requestTarget.substring(0, requestTarget.length() - fragmentLength - 1);
    }

    private ClientConnection obtainConnection(WebClientServiceRequest request) {
        var address = originalRequest.address();
        UnixDomainSocketAddress udsAddress = address.filter(a -> a instanceof UnixDomainSocketAddress)
                .map(UnixDomainSocketAddress.class::cast)
                .orElse(null);

        if (udsAddress == null) {
            return http1Client.connectionCache()
                    .connection(http1Client,
                                tls,
                                proxy,
                                request.uri(),
                                request.headers(),
                                keepAlive);
        } else {
            return http1Client.connectionCache()
                    .connection(http1Client,
                                tls,
                                request.uri(),
                                request.headers(),
                                keepAlive,
                                udsAddress);
        }
    }

    record ResponseHead(Status status, ClientResponseHeaders headers) {
    }

    static class ContentLengthInputStream extends InputStream {
        private final DataReader reader;
        private final Runnable entityProcessedRunnable;
        private final HelidonSocket socket;
        private final Http1ConnectionListener recvListener;

        private BufferData currentBuffer;
        private boolean finished;
        private long remainingLength;

        ContentLengthInputStream(HelidonSocket socket,
                                 DataReader reader,
                                 CompletableFuture<WebClientServiceResponse> whenComplete,
                                 AtomicReference<WebClientServiceResponse> response,
                                 long length,
                                 Http1ConnectionListener recvListener) {
            this.socket = socket;
            this.reader = reader;
            this.remainingLength = length;
            this.recvListener = recvListener;

            // we can only get the response at the time of completion, as the instance is created after this constructor
            // returns
            this.entityProcessedRunnable = () -> completeResponse(whenComplete, response);
        }

        @Override
        public int read() {
            if (finished) {
                return -1;
            }
            int maxRemaining = maxRemaining(512);
            ensureBuffer(maxRemaining);
            if (finished || currentBuffer == null) {
                return -1;
            }
            int read = currentBuffer.read();
            if (read != -1) {
                remainingLength--;
            }
            return read;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) {
                return 0;
            }
            if (finished) {
                return -1;
            }
            int maxRemaining = maxRemaining(len);
            ensureBuffer(maxRemaining);
            if (finished || currentBuffer == null) {
                return -1;
            }
            int read = currentBuffer.read(b, off, len);
            remainingLength -= read;
            return read;
        }

        private int maxRemaining(int estimate) {
            return Integer.min(estimate, (int) Long.min(Integer.MAX_VALUE, remainingLength));
        }

        private void ensureBuffer(int estimate) {
            if (remainingLength == 0) {
                entityProcessedRunnable.run();
                // we have fully read the entity
                finished = true;
                currentBuffer = null;
                return;
            }

            if (currentBuffer != null && !currentBuffer.consumed()) {
                return;
            }

            reader.ensureAvailable();
            int toRead = Math.min(reader.available(), estimate);

            // read between 0 and available bytes (or estimate, which is the number of requested bytes)
            currentBuffer = reader.readBuffer(toRead);
            if (currentBuffer == null || currentBuffer == BufferData.empty()) {
                entityProcessedRunnable.run();
                finished = true;
            } else {
                recvListener.data(socket, currentBuffer);
            }
        }
    }

    static class EverythingInputStream extends InputStream {
        private final HelidonSocket helidonSocket;
        private final DataReader reader;
        private final Http1ConnectionListener recvListener;
        private final Runnable entityProcessedRunnable;

        private BufferData currentBuffer;
        private boolean finished;

        EverythingInputStream(HelidonSocket helidonSocket,
                              DataReader reader,
                              CompletableFuture<WebClientServiceResponse> whenComplete,
                              AtomicReference<WebClientServiceResponse> response,
                              Http1ConnectionListener recvListener) {
            this.helidonSocket = helidonSocket;
            this.reader = reader;
            this.recvListener = recvListener;

            // we can only get the response at the time of completion, as the instance is created after this constructor
            // returns
            this.entityProcessedRunnable = () -> completeResponse(whenComplete, response);
        }

        @Override
        public int read() {
            if (finished) {
                return -1;
            }
            ensureBuffer(512);
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) {
                return 0;
            }
            if (finished) {
                return -1;
            }
            ensureBuffer(len);
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read(b, off, len);
        }

        private void ensureBuffer(int estimate) {
            if (currentBuffer != null && currentBuffer.available() > 0) {
                // we did not read the previous buffer fully
                return;
            }

            reader.ensureAvailable();
            int toRead = Math.min(reader.available(), estimate);

            // read between 0 and available bytes (or estimate, which is the number of requested bytes)
            currentBuffer = reader.readBuffer(toRead);
            if (currentBuffer == null || currentBuffer == BufferData.empty()) {
                entityProcessedRunnable.run();
                finished = true;
            } else {
                recvListener.data(helidonSocket, currentBuffer);
            }
        }
    }

    static class ChunkedInputStream extends InputStream {
        private final ClientConnection connection;
        private final HelidonSocket helidonSocket;
        private final DataReader reader;
        private final Runnable entityProcessedRunnable;
        private final Http1ConnectionListener recvListener;
        private final CompletableFuture<WebClientServiceResponse> whenComplete;
        private final AtomicReference<WebClientServiceResponse> response;

        private BufferData currentBuffer;
        private RuntimeException failure;
        private int remainingChunkLength;
        private boolean chunkTerminatorPending;
        private boolean discarded;
        private boolean finished;

        ChunkedInputStream(ClientConnection connection,
                           DataReader reader,
                           CompletableFuture<WebClientServiceResponse> whenComplete,
                           AtomicReference<WebClientServiceResponse> response,
                           Http1ConnectionListener recvListener) {
            this.connection = connection;
            this.helidonSocket = connection.helidonSocket();
            this.reader = reader;
            this.recvListener = recvListener;
            this.whenComplete = whenComplete;
            this.response = response;

            // we can only get the response at the time of completion, as the instance is created after this constructor
            // returns
            this.entityProcessedRunnable = () -> completeResponse(whenComplete, response);
        }

        @Override
        public int read() {
            if (finished) {
                return -1;
            }
            ensureBuffer();
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) {
                return 0;
            }
            if (finished) {
                return -1;
            }
            ensureBuffer();
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (!finished && failure == null) {
                if (currentBuffer != null
                        && currentBuffer.consumed()
                        && remainingChunkLength == 0) {
                    consumeExpectedFinalChunk();
                    finish();
                    return;
                }
                finished = true;
                discarded = true;
                currentBuffer = null;
                try {
                    connection.closeResource();
                } catch (RuntimeException | Error e) {
                    try {
                        entityProcessedRunnable.run();
                    } catch (RuntimeException | Error suppressed) {
                        e.addSuppressed(suppressed);
                    }
                    throw e;
                }
                entityProcessedRunnable.run();
            }
        }

        private void consumeExpectedFinalChunk() {
            try {
                if (chunkTerminatorPending) {
                    consumeChunkTerminator();
                    chunkTerminatorPending = false;
                }
                if (readChunkSize() != 0) {
                    throw new IllegalStateException("HTTP content decoder completed before the final response chunk");
                }
            } catch (RuntimeException e) {
                throw fail(new IllegalStateException("HTTP response chunked transfer coding is incomplete", e));
            }
        }

        private void ensureBuffer() {
            if (currentBuffer != null && currentBuffer.available() > 0) {
                // we did not read the previous buffer fully
                return;
            }
            if (failure != null) {
                throw new IllegalStateException(failure.getMessage());
            }
            if (chunkTerminatorPending) {
                try {
                    consumeChunkTerminator();
                } catch (RuntimeException e) {
                    throw fail(e);
                }
                chunkTerminatorPending = false;
            }
            if (remainingChunkLength == 0) {
                try {
                    remainingChunkLength = readChunkSize();
                } catch (RuntimeException e) {
                    throw fail(e);
                }
                if (remainingChunkLength == 0) {
                    // The response lifecycle consumes the complete trailer section before releasing the connection.
                    finish();
                    return;
                }
            }

            reader.ensureAvailable();
            // Retain the available window so small application reads do not create a buffer per read.
            int toRead = Math.min(remainingChunkLength, reader.available());
            BufferData chunk = reader.readBuffer(toRead);
            remainingChunkLength -= toRead;

            recvListener.data(helidonSocket, chunk);

            if (remainingChunkLength == 0) {
                chunkTerminatorPending = true;
                if (reader.available() >= 2 || (reader.available() == 1 && reader.lookup() != Bytes.CR_BYTE)) {
                    try {
                        consumeChunkTerminator();
                        chunkTerminatorPending = false;
                    } catch (RuntimeException e) {
                        throw fail(e);
                    }
                }
            }
            this.currentBuffer = chunk;
        }

        private int readChunkSize() {
            return Http1CallChainBase.readChunkSize(reader);
        }

        private void consumeChunkTerminator() {
            if (reader.read() != Bytes.CR_BYTE || reader.read() != Bytes.LF_BYTE) {
                throw new IllegalStateException("HTTP response chunk data is not followed by CRLF");
            }
        }

        private void finish() {
            recvListener.data(helidonSocket, BufferData.empty());
            finished = true;
            currentBuffer = null;
            entityProcessedRunnable.run();
        }

        private RuntimeException fail(RuntimeException failure) {
            this.failure = failure;
            try {
                connection.closeResource();
            } catch (RuntimeException | Error e) {
                failure.addSuppressed(e);
            }
            try {
                WebClientServiceResponse serviceResponse = response.get();
                if (serviceResponse == null) {
                    whenComplete.completeExceptionally(failure);
                } else {
                    whenComplete.complete(serviceResponse);
                }
            } catch (RuntimeException | Error e) {
                failure.addSuppressed(e);
            }
            return failure;
        }
    }

    private static final class CloseOnceInputStream extends FilterInputStream {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ChunkedInputStream chunkedInputStream;

        private CloseOnceInputStream(InputStream inputStream, ChunkedInputStream chunkedInputStream) {
            super(inputStream);
            this.chunkedInputStream = chunkedInputStream;
        }

        @Override
        public void close() throws IOException {
            if (closed.compareAndSet(false, true)) {
                super.close();
            }
        }
    }

    private static final class SingleReleaseClientConnection implements ClientConnection {
        private final ClientConnection delegate;
        private final AtomicBoolean released = new AtomicBoolean();

        private SingleReleaseClientConnection(ClientConnection delegate) {
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
        public boolean allowExpectContinue() {
            return delegate.allowExpectContinue();
        }

        @Override
        public void allowExpectContinue(boolean allowExpectContinue) {
            delegate.allowExpectContinue(allowExpectContinue);
        }

        @Override
        public boolean isConnected() {
            return delegate.isConnected();
        }

        @Override
        public ClientConnection connect() {
            delegate.connect();
            return this;
        }

        @Override
        public void releaseResource() {
            if (released.compareAndSet(false, true)) {
                delegate.releaseResource();
            }
        }

        @Override
        public void closeResource() {
            if (released.compareAndSet(false, true)) {
                delegate.closeResource();
            }
        }
    }
}
