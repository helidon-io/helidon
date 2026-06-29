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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.common.ParserHelper;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
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
    private static final Supplier<IllegalArgumentException> INVALID_SIZE_EXCEPTION_SUPPLIER =
            () -> new IllegalArgumentException("Chunk size is invalid");

    private final BufferData writeBuffer = BufferData.growing(128);
    private final HttpClientConfig clientConfig;
    private final Http1ClientProtocolConfig protocolConfig;
    private final ClientConnection connection;
    private final Http1ClientRequestImpl originalRequest;
    private final Proxy proxy;
    private final boolean keepAlive;
    private final CompletableFuture<WebClientServiceResponse> whenComplete;
    private final Duration timeout;
    private final Http1ClientImpl http1Client;
    private final Http1ConnectionListener sendListener;
    private final Http1ConnectionListener recvListener;

    private ClientConnection effectiveConnection;

    Http1CallChainBase(Http1ClientImpl http1Client,
                       Http1ClientRequestImpl clientRequest,
                       CompletableFuture<WebClientServiceResponse> whenComplete) {
        this.clientConfig = http1Client.clientConfig();
        this.protocolConfig = http1Client.protocolConfig();
        this.originalRequest = clientRequest;
        this.timeout = clientRequest.readTimeout();
        this.connection = clientRequest.connection().orElse(null);
        this.proxy = clientRequest.effectiveProxy();
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

        if (mayExposeEntity(serviceRequest.method(), responseStatus, responseHeaders)) {
            // this may be an entity (if content length is set to zero, we know there is no entity)
            builder.inputStream(inputStream(clientConfig,
                                            recvListener,
                                            connection.helidonSocket(),
                                            response,
                                            responseHeaders,
                                            reader,
                                            whenComplete));
        }

        WebClientServiceResponse serviceResponse = builder
                .connection(connection)
                .headers(responseHeaders)
                .status(responseStatus)
                .whenComplete(whenComplete)
                .serviceRequest(serviceRequest)
                .build();

        response.set(serviceResponse);
        return serviceResponse;
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

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
        ClientUri uri = serviceRequest.uri();
        ClientRequestHeaders headers = serviceRequest.headers();

        writeBuffer.clear();
        originalRequest.sanitizeRedirectHeaders(uri, headers);
        headers.setIfAbsent(HeaderValues.create(HeaderNames.HOST, uri.authority()));

        // either use the explicit connection, or obtain one (keep alive or one-off)
        effectiveConnection = connection == null ? obtainConnection(serviceRequest) : connection;
        effectiveConnection.readTimeout(this.timeout);

        DataWriter writer = effectiveConnection.writer();
        DataReader reader = effectiveConnection.reader();

        prologue(effectiveConnection, writeBuffer, serviceRequest, uri);

        return doProceed(effectiveConnection, serviceRequest, headers, writer, reader, writeBuffer);
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
                                             + uri.pathWithQueryAndFragment()
                                             + " HTTP/1.1\r\n");
        }

        if (sendListener.enabled()) {
            sendListener.prologue(effectiveConnection.helidonSocket(), HttpPrologue.create("HTTP/1.1",
                                                                                           "HTTP",
                                                                                           "1.1",
                                                                                           request.method(),
                                                                                           uri.path(),
                                                                                           uri.query(),
                                                                                           uri.fragment()));
        }
    }

    ClientResponseHeaders readHeaders(DataReader reader) {
        WritableHeaders<?> writable = Http1HeadersParser.readHeaders(reader,
                                                                     protocolConfig.maxHeaderSize(),
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

    Http1ClientRequestImpl originalRequest() {
        return originalRequest;
    }

    CompletableFuture<WebClientServiceResponse> whenComplete() {
        return whenComplete;
    }

    WebClientServiceResponse readResponse(WebClientServiceRequest serviceRequest,
                                          ClientConnection connection,
                                          DataReader reader) {
        ResponseHead responseHead = originalRequest.outputStreamRedirect()
                ? readResponseHead(connection, reader, Http1CallChainBase::isPreContinueInterimResponse)
                : readResponseHead(connection, reader);

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
            Status responseStatus;
            try {
                responseStatus = Http1StatusParser.readStatus(reader, protocolConfig.maxStatusLineLength());
            } catch (UncheckedIOException e) {
                if (closeOnReadFailure) {
                    // Normal response reads cannot reuse a connection after a timeout or close while reading status.
                    try {
                        connection.closeResource();
                    } catch (Exception ex) {
                        e.addSuppressed(ex);
                    }
                }
                throw e;
            }
            recvListener.status(connection.helidonSocket(), responseStatus);
            ClientResponseHeaders responseHeaders = readHeaders(reader);
            recvListener.headers(connection.helidonSocket(), responseHeaders);

            if (!skippedResponse.test(responseStatus)) {
                return new ResponseHead(responseStatus, responseHeaders);
            }
        }
    }

    static boolean isPreContinueInterimResponse(Status responseStatus) {
        return isInterimResponse(responseStatus)
                && responseStatus.code() != Status.CONTINUE_100.code();
    }

    private static boolean isInterimResponse(Status responseStatus) {
        return responseStatus.family() == Status.Family.INFORMATIONAL
                && responseStatus.code() != Status.SWITCHING_PROTOCOLS_101.code();
    }

    record ResponseHead(Status status, ClientResponseHeaders headers) {
    }

    Http1ConnectionListener recvListener() {
        return recvListener;
    }

    static boolean statusAllowsEntity(Status responseStatus) {
        int statusCode = responseStatus.code();
        return responseStatus.family() != Status.Family.INFORMATIONAL
                && statusCode != Status.NO_CONTENT_204.code()
                && statusCode != Status.RESET_CONTENT_205.code()
                && statusCode != Status.NOT_MODIFIED_304.code();
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

    private static boolean mayExposeEntity(Method requestMethod, Status responseStatus, ClientResponseHeaders responseHeaders) {
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
        if ((
                responseHeaders.contains(HeaderNames.UPGRADE)
                        && !responseHeaders.containsToken(HeaderValues.TRANSFER_ENCODING_CHUNKED))) {
            // this is an upgrade response and there is no entity
            return false;
        }
        // if we decide to support HTTP/1.0, we may have an entity without any headers
        // in HTTP/1.1, we should have a content encoding
        return true;
    }

    private static InputStream inputStream(HttpClientConfig clientConfig,
                                           Http1ConnectionListener recvListener,
                                           HelidonSocket helidonSocket,
                                           AtomicReference<WebClientServiceResponse> response,
                                           ClientResponseHeaders responseHeaders,
                                           DataReader reader,
                                           CompletableFuture<WebClientServiceResponse> whenComplete) {
        ContentEncodingContext encodingSupport = clientConfig.contentEncoding();

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
            inputStream = new ChunkedInputStream(helidonSocket, reader, whenComplete, response, recvListener);
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

    private ClientConnection obtainConnection(WebClientServiceRequest request) {
        var address = originalRequest.address();
        UnixDomainSocketAddress udsAddress = address.filter(a -> a instanceof UnixDomainSocketAddress)
                .map(UnixDomainSocketAddress.class::cast)
                .orElse(null);

        if (udsAddress == null) {
            return http1Client.connectionCache()
                    .connection(http1Client,
                                originalRequest,
                                request.uri(),
                                request.headers(),
                                keepAlive);
        } else {
            return http1Client.connectionCache()
                    .connection(http1Client,
                                originalRequest,
                                request.uri(),
                                request.headers(),
                                keepAlive,
                                udsAddress);
        }
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
            this.entityProcessedRunnable = () -> whenComplete.complete(response.get());
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
            this.entityProcessedRunnable = () -> whenComplete.complete(response.get());
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
        private final HelidonSocket helidonSocket;
        private final DataReader reader;
        private final Runnable entityProcessedRunnable;
        private final Http1ConnectionListener recvListener;

        private BufferData currentBuffer;
        private boolean finished;

        ChunkedInputStream(HelidonSocket helidonSocket,
                           DataReader reader,
                           CompletableFuture<WebClientServiceResponse> whenComplete,
                           AtomicReference<WebClientServiceResponse> response,
                           Http1ConnectionListener recvListener) {
            this.helidonSocket = helidonSocket;
            this.reader = reader;
            this.recvListener = recvListener;

            // we can only get the response at the time of completion, as the instance is created after this constructor
            // returns
            this.entityProcessedRunnable = () -> whenComplete.complete(response.get());
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
            if (!finished) {
                ensureBuffer();
            }
        }

        private void ensureBuffer() {
            if (currentBuffer != null && currentBuffer.available() > 0) {
                // we did not read the previous buffer fully
                return;
            }
            // chunked encoding - I will just read each chunk fully into memory, as that is how the protocol is designed
            int length;
            try {
                length = readChunkSize(reader);
            } catch (IllegalStateException e) {
                entityProcessedRunnable.run();
                throw e;
            }
            if (length == 0) {
                if (reader.startsWithNewLine()) {
                    // No trailers, skip second CRLF
                    reader.skip(2);
                }

                recvListener.data(helidonSocket, BufferData.empty());
                finished = true;
                currentBuffer = null;
                entityProcessedRunnable.run();
                return;
            }

            BufferData chunk = reader.readBuffer(length);

            recvListener.data(helidonSocket, chunk);

            reader.skip(2); // trailing CRLF after each chunk
            this.currentBuffer = chunk;
        }
    }
}
