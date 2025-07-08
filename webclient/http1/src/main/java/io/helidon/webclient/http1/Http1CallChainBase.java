/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.ParserHelper;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Http1HeadersParser;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

import static java.lang.System.Logger.Level.TRACE;
import static java.nio.charset.StandardCharsets.US_ASCII;

abstract class Http1CallChainBase implements WebClientService.Chain {
    private static final String CLASS_NAME = Http1CallChainBase.class.getName();
    /*
    Specify more fine-grained log levels, to allow printing only what needed
     */
    protected static final System.Logger LOGGER_REQ_ENTITY = System.getLogger(CLASS_NAME + ".req.entity");
    protected static final System.Logger LOGGER_REQ_PROLOGUE = System.getLogger(CLASS_NAME + ".req.prologue");
    protected static final System.Logger LOGGER_REQ_HEADERS = System.getLogger(CLASS_NAME + ".req.headers");
    protected static final System.Logger LOGGER_RES_STATUS = System.getLogger(CLASS_NAME + ".res.status");
    protected static final System.Logger LOGGER_RES_HEADERS = System.getLogger(CLASS_NAME + ".res.headers");
    private static final System.Logger LOGGER_RES_ENTITY = System.getLogger(CLASS_NAME + ".res.entity");

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
    private ClientConnection effectiveConnection;

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
    }

    static void writeHeaders(ClientConnection connection, Headers headers, BufferData bufferData, boolean validate) {
        for (Header header : headers) {
            if (validate) {
                header.validate();
            }
            header.writeHttp1Header(bufferData);
        }
        bufferData.write(Bytes.CR_BYTE);
        bufferData.write(Bytes.LF_BYTE);

        connection.helidonSocket().log(LOGGER_REQ_HEADERS, TRACE, "client sent headers %n%s", headers);
    }

    static WebClientServiceResponse createServiceResponse(HttpClientConfig clientConfig,
                                                          WebClientServiceRequest serviceRequest,
                                                          ClientConnection connection,
                                                          DataReader reader,
                                                          Status responseStatus,
                                                          ClientResponseHeaders responseHeaders,
                                                          CompletableFuture<WebClientServiceResponse> whenComplete) {
        WebClientServiceResponse.Builder builder = WebClientServiceResponse.builder();
        AtomicReference<WebClientServiceResponse> response = new AtomicReference<>();

        if (mayHaveEntity(responseStatus, responseHeaders)) {
            // this may be an entity (if content length is set to zero, we know there is no entity)
            builder.inputStream(inputStream(clientConfig,
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

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
        // either use the explicit connection, or obtain one (keep alive or one-off)
        effectiveConnection = connection == null ? obtainConnection(serviceRequest, timeout) : connection;
        effectiveConnection.readTimeout(this.timeout);

        DataWriter writer = effectiveConnection.writer();
        DataReader reader = effectiveConnection.reader();
        ClientUri uri = serviceRequest.uri();
        ClientRequestHeaders headers = serviceRequest.headers();

        writeBuffer.clear();
        prologue(effectiveConnection, writeBuffer, serviceRequest, uri);
        headers.setIfAbsent(HeaderValues.create(HeaderNames.HOST, uri.authority()));

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
            InetSocketAddress uriAddress = new InetSocketAddress(uri.host(), uri.port());
            String requestUri = proxy == Proxy.noProxy()
                    || (proxy.type() == Proxy.ProxyType.HTTP && proxy.isNoHosts(uriAddress))
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

        if (LOGGER_REQ_PROLOGUE.isLoggable(TRACE)) {
            effectiveConnection.helidonSocket().log(LOGGER_REQ_PROLOGUE,
                                           TRACE,
                                           "client sent prologue %n%s",
                                           nonEntityData.debugDataHex());
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

    protected WebClientServiceResponse readResponse(WebClientServiceRequest serviceRequest,
                                                    ClientConnection connection,
                                                    DataReader reader) {
        Status responseStatus;
        try {
            responseStatus = Http1StatusParser.readStatus(reader, protocolConfig.maxStatusLineLength());
        } catch (UncheckedIOException e) {
            // if we get a timeout or connection close, we must close the resource (as otherwise we may receive
            // data of this request on the next use of this connection
            try {
                connection.closeResource();
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
        connection.helidonSocket().log(LOGGER_RES_STATUS, TRACE, "client received status %n%s", responseStatus);
        ClientResponseHeaders responseHeaders = readHeaders(reader);
        connection.helidonSocket().log(LOGGER_RES_HEADERS, TRACE, "client received headers %n%s", responseHeaders);

        return createServiceResponse(clientConfig,
                                     serviceRequest,
                                     connection,
                                     reader,
                                     responseStatus,
                                     responseHeaders,
                                     whenComplete);
    }

    private static InputStream inputStream(HttpClientConfig clientConfig,
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
                throw new IllegalStateException("Unsupported content encoding: \n"
                                                        + BufferData.create(contentEncoding.getBytes(StandardCharsets.UTF_8))
                        .debugDataHex());
            }
        } else {
            decoder = ContentDecoder.NO_OP;
        }
        if (responseHeaders.contains(HeaderNames.CONTENT_LENGTH)) {
            long length = responseHeaders.contentLength().getAsLong();
            return decoder.apply(new ContentLengthInputStream(helidonSocket, reader, whenComplete, response, length));
        } else if (responseHeaders.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            return new ChunkedInputStream(helidonSocket, reader, whenComplete, response);
        } else {
            // we assume the rest of the connection is entity (valid for HTTP/1.0, HTTP CONNECT method etc.
            return new EverythingInputStream(helidonSocket, reader, whenComplete, response);
        }
    }

    private static boolean mayHaveEntity(Status responseStatus, ClientResponseHeaders responseHeaders) {
        if (responseHeaders.contains(HeaderValues.CONTENT_LENGTH_ZERO)) {
            return false;
        }
        // Why is NOT_MODIFIED_304 not added here too?
        if (responseStatus.code() == Status.NO_CONTENT_204.code()) {
            return false;
        }
        if ((
                responseHeaders.contains(HeaderNames.UPGRADE)
                        && !responseHeaders.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED))) {
            // this is an upgrade response and there is no entity
            return false;
        }
        // if we decide to support HTTP/1.0, we may have an entity without any headers
        // in HTTP/1.1, we should have a content encoding
        return true;
    }

    private ClientConnection obtainConnection(WebClientServiceRequest request, Duration requestReadTimeout) {
        return http1Client.connectionCache()
                .connection(http1Client,
                            requestReadTimeout,
                            tls,
                            proxy,
                            request.uri(),
                            request.headers(),
                            keepAlive);
    }

    static class ContentLengthInputStream extends InputStream {
        private final DataReader reader;
        private final Runnable entityProcessedRunnable;
        private final HelidonSocket socket;

        private BufferData currentBuffer;
        private boolean finished;
        private long remainingLength;

        ContentLengthInputStream(HelidonSocket socket,
                                 DataReader reader,
                                 CompletableFuture<WebClientServiceResponse> whenComplete,
                                 AtomicReference<WebClientServiceResponse> response,
                                 long length) {
            this.socket = socket;
            this.reader = reader;
            this.remainingLength = length;
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
                if (LOGGER_RES_ENTITY.isLoggable(TRACE)) {
                    socket.log(LOGGER_RES_ENTITY,
                               TRACE,
                               "client read entity buffer %n%s",
                               currentBuffer.debugDataHex(true));
                }
            }
        }
    }

    static class EverythingInputStream extends InputStream {
        private final HelidonSocket helidonSocket;
        private final DataReader reader;
        private final Runnable entityProcessedRunnable;

        private BufferData currentBuffer;
        private boolean finished;

        EverythingInputStream(HelidonSocket helidonSocket,
                              DataReader reader,
                              CompletableFuture<WebClientServiceResponse> whenComplete,
                              AtomicReference<WebClientServiceResponse> response) {
            this.helidonSocket = helidonSocket;
            this.reader = reader;
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
                if (LOGGER_RES_ENTITY.isLoggable(TRACE)) {
                    helidonSocket.log(LOGGER_RES_ENTITY,
                                      TRACE,
                                      "client read entity buffer %n%s",
                                      currentBuffer.debugDataHex(true));
                }
            }
        }
    }

    static class ChunkedInputStream extends InputStream {
        private final HelidonSocket helidonSocket;
        private final DataReader reader;
        private final Runnable entityProcessedRunnable;

        private BufferData currentBuffer;
        private boolean finished;

        ChunkedInputStream(HelidonSocket helidonSocket,
                           DataReader reader,
                           CompletableFuture<WebClientServiceResponse> whenComplete,
                           AtomicReference<WebClientServiceResponse> response) {
            this.helidonSocket = helidonSocket;
            this.reader = reader;
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

        private void ensureBuffer() {
            if (currentBuffer != null && currentBuffer.available() > 0) {
                // we did not read the previous buffer fully
                return;
            }
            // chunked encoding - I will just read each chunk fully into memory, as that is how the protocol is designed
            int endOfChunkSize = reader.findNewLine(256);
            if (endOfChunkSize == 256) {
                entityProcessedRunnable.run();
                throw new IllegalStateException("Cannot read chunked entity, end of line not found within 256 bytes:\n"
                                                        + reader.readBuffer(Math.min(reader.available(), 256)));
            }
            String hex = reader.readAsciiString(endOfChunkSize);
            reader.skip(2); // CRLF
            int length;
            try {
                length = ParserHelper.parseNonNegative(hex, 16, INVALID_SIZE_EXCEPTION_SUPPLIER);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Chunk size is not a number:\n"
                                                           + BufferData.create(hex.getBytes(US_ASCII)).debugDataHex());
            }
            if (length == 0) {
                if (reader.startsWithNewLine()) {
                    // No trailers, skip second CRLF
                    reader.skip(2);
                }
                helidonSocket.log(LOGGER_RES_ENTITY, TRACE, "read last (empty) chunk");
                finished = true;
                currentBuffer = null;
                entityProcessedRunnable.run();
                return;
            }

            BufferData chunk = reader.readBuffer(length);

            if (LOGGER_RES_ENTITY.isLoggable(TRACE)) {
                helidonSocket.log(LOGGER_RES_ENTITY, TRACE, "client read chunk\n%s", chunk.debugDataHex(true));
            }

            reader.skip(2); // trailing CRLF after each chunk
            this.currentBuffer = chunk;
        }
    }
}
