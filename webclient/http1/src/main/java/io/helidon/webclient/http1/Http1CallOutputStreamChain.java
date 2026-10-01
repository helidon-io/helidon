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
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.context.Contexts;
import io.helidon.common.socket.SocketContext;
import io.helidon.common.uri.UriFragment;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http1.Http1ConnectionListener;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http1CallOutputStreamChain extends Http1CallChainBase {
    private final Http1ClientImpl http1Client;
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final ClientRequest.OutputStreamHandler osHandler;
    private final boolean allowEntity;
    private final Http1EntityWriter entityWriter;

    private boolean hasEntity;
    private boolean handlerInvoked;

    Http1CallOutputStreamChain(Http1ClientImpl http1Client,
                               Http1ClientRequestImpl clientRequest,
                               CompletableFuture<WebClientServiceRequest> whenSent,
                               CompletableFuture<WebClientServiceResponse> whenComplete,
                               ClientRequest.OutputStreamHandler osHandler,
                               boolean allowEntity,
                               Http1EntityWriter entityWriter) {
        super(http1Client, clientRequest, whenComplete);
        this.http1Client = http1Client;
        this.whenSent = whenSent;
        this.osHandler = osHandler;
        this.allowEntity = allowEntity;
        this.entityWriter = entityWriter;
    }

    @Override
    WebClientServiceResponse doProceed(ClientConnection connection,
                                       WebClientServiceRequest serviceRequest,
                                       ClientRequestHeaders headers,
                                       DataWriter writer,
                                       DataReader reader,
                                       BufferData writeBuffer) {

        handlerInvoked = false;
        hasEntity = false;
        boolean expectAllowed = connection.allowExpectContinue()
                && originalRequest().sendExpectContinue().orElse(http1Client.clientConfig().sendExpectContinue());
        if (entityWriter != null && !expectAllowed && entityWriter.canWriteInline()) {
            handlerInvoked = true;
            ClientConnectionOutputStream output = writeInline(connection, serviceRequest, headers, writer, writeBuffer);
            return completeResponse(connection, serviceRequest, reader, output);
        }

        boolean legacyEntity = false;
        if (entityWriter != null) {
            try {
                legacyEntity = entityWriter.prepare(headers, Contexts.context().orElse(serviceRequest.context()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            // Header preparation must not reintroduce credentials removed for a redirected origin.
            originalRequest().sanitizeRedirectHeaders(serviceRequest.uri(), headers);
        }
        ClientConnectionOutputStream cos = new ClientConnectionOutputStream(this,
                                                                            connection,
                                                                            writer,
                                                                            writeBuffer,
                                                                            headers,
                                                                            http1Client,
                                                                            serviceRequest,
                                                                            whenSent,
                                                                            allowEntity);

        hasEntity = legacyEntity || headers.contentLength().orElse(0) > 0;
        boolean expects100Continue = expectAllowed
                && (entityWriter == null || legacyEntity)
                && headers.contentLength().orElse(-1) != 0;
        if (expects100Continue) {
            // Explicit handlers start after negotiation; a legacy writer stays suspended at its first output.
            headers.set(HeaderValues.EXPECT_100);
            cos.sendPrologueAndHeader();
            ResponseHead responseHead = awaitContinue(connection, reader, writer);
            if (responseHead != null && responseHead.status().code() != Status.CONTINUE_100.code()) {
                captureProtocolResponse(connection, responseHead.status(), responseHead.headers());
                return createServiceResponse(http1Client,
                                             serviceRequest,
                                             Http1ClientResponseImpl.closeOnRelease(connection),
                                             reader,
                                             responseHead.status(),
                                             responseHead.headers(),
                                             whenComplete());
            }
        }

        handlerInvoked = true;
        try {
            if (entityWriter == null) {
                osHandler.handle(cos);
            } else {
                entityWriter.writeTo(cos);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return completeResponse(connection, serviceRequest, reader, cos);
    }

    boolean hasEntity() {
        return hasEntity;
    }

    boolean handlerInvoked() {
        return handlerInvoked;
    }

    private ClientConnectionOutputStream writeInline(ClientConnection connection,
                                                      WebClientServiceRequest serviceRequest,
                                                      ClientRequestHeaders headers,
                                                      DataWriter writer,
                                                      BufferData writeBuffer) {
        InlineEntityOutputStream output = new InlineEntityOutputStream(() -> {
            // Legacy writers prepare headers during write, before their first output or empty close.
            originalRequest().sanitizeRedirectHeaders(serviceRequest.uri(), headers);
            return new ClientConnectionOutputStream(this,
                                                     connection,
                                                     writer,
                                                     writeBuffer,
                                                     headers,
                                                     http1Client,
                                                     serviceRequest,
                                                     whenSent,
                                                     allowEntity);
        });
        entityWriter.writeInline(output, headers);
        if (output.delegate == null) {
            throw new IllegalStateException("Output stream was not closed in entity writer");
        }
        return output.delegate;
    }

    private WebClientServiceResponse completeResponse(ClientConnection connection,
                                                       WebClientServiceRequest serviceRequest,
                                                       DataReader reader,
                                                       ClientConnectionOutputStream output) {
        hasEntity |= output.hasEntity();
        if (!output.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        ResponseHead responseHead = readResponseHead(connection, reader);
        Status responseStatus = responseHead.status();
        ClientResponseHeaders responseHeaders = responseHead.headers();
        captureProtocolResponse(connection, responseStatus, responseHeaders);

        return createServiceResponse(http1Client,
                                     serviceRequest,
                                     connection,
                                     reader,
                                     responseStatus,
                                     responseHeaders,
                                     whenComplete());
    }

    private ResponseHead awaitContinue(ClientConnection connection, DataReader reader, DataWriter writer) {
        try {
            writer.flush();
            connection.readTimeout(originalRequest().readContinueTimeout());
            while (true) {
                boolean statusRead = false;
                try {
                    Status responseStatus = readResponseStatus(connection, reader, false);
                    statusRead = true;
                    ClientResponseHeaders responseHeaders = readResponseHeaders(connection, reader);
                    if (!isPreContinueInterimResponse(responseStatus)) {
                        return new ResponseHead(responseStatus, responseHeaders);
                    }
                } catch (UncheckedIOException e) {
                    if (!statusRead && e.getCause() instanceof SocketTimeoutException) {
                        connection.allowExpectContinue(false);
                        return null;
                    }
                    throw e;
                }
            }
        } catch (UncheckedIOException e) {
            try {
                connection.closeResource();
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw e;
        } finally {
            if (connection.isConnected()) {
                connection.readTimeout(originalRequest().readTimeout());
            }
        }
    }

    private static final class InlineEntityOutputStream extends OutputStream {
        private final Supplier<ClientConnectionOutputStream> factory;

        private ClientConnectionOutputStream delegate;

        private InlineEntityOutputStream(Supplier<ClientConnectionOutputStream> factory) {
            this.factory = factory;
        }

        @Override
        public void write(int value) throws IOException {
            stream().write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length != 0 || delegate != null) {
                stream().write(bytes, offset, length);
            }
        }

        @Override
        public void flush() throws IOException {
            if (delegate != null) {
                delegate.flush();
            }
        }

        @Override
        public void close() throws IOException {
            stream().close();
        }

        private ClientConnectionOutputStream stream() {
            if (delegate == null) {
                delegate = factory.get();
            }
            return delegate;
        }
    }

    private static class ClientConnectionOutputStream extends OutputStream {
        private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        private final WebClientServiceRequest request;
        private final CompletableFuture<WebClientServiceRequest> whenSent;
        private final Http1CallOutputStreamChain callChain;
        private final Http1ConnectionListener sendListener;
        private final Http1ClientProtocolConfig protocolConfig;
        private final WritableHeaders<?> headers;
        private final BufferData prologue;
        private final ClientConnection connection;
        private final SocketContext ctx;
        private final DataWriter writer;
        private final boolean allowEntity;

        private boolean chunked;
        private BufferData firstPacket;
        private long bytesWritten;
        private long contentLength;
        private boolean hasEntity;
        private boolean noData = true;
        private boolean closed;
        private boolean headersSent;

        private ClientConnectionOutputStream(Http1CallOutputStreamChain callChain,
                                             ClientConnection connection,
                                             DataWriter writer,
                                             BufferData prologue,
                                             WritableHeaders<?> headers,
                                             Http1ClientImpl http1Client,
                                             WebClientServiceRequest request,
                                             CompletableFuture<WebClientServiceRequest> whenSent,
                                             boolean allowEntity) {
            this.callChain = callChain;
            this.connection = connection;
            this.ctx = connection.helidonSocket();
            this.writer = writer;
            this.headers = headers;
            this.prologue = prologue;
            this.protocolConfig = http1Client.protocolConfig();
            this.contentLength = headers.contentLength().orElse(-1);
            this.chunked = contentLength == -1 || headers.containsToken(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            this.request = request;
            this.whenSent = whenSent;
            this.sendListener = http1Client.sendListener();
            this.allowEntity = allowEntity;
        }

        @Override
        public void write(int b) throws IOException {
            // this method should not be called, as we are wrapped with a buffered stream
            byte[] data = {(byte) b};
            write(data, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Output stream already closed");
            }
            if (len == 0) {
                return;
            }
            if (len > 0) {
                if (!allowEntity) {
                    throw new IllegalStateException("Cross-origin redirect with request entity is disabled.");
                }
                hasEntity = true;
            }

            // if not chunked and length known, write directly checking length at close
            if (!chunked && contentLength > 0) {
                sendPrologueAndHeader();
                noData = false;
                writeContent(BufferData.create(b, off, len));
                return;
            }

            // if length not known, try to optimize for single write
            if (!chunked) {
                if (firstPacket == null) {
                    BufferData first = BufferData.create(len - off);
                    first.write(b, off, len);    // copies byte buffer
                    firstPacket = first;
                } else {
                    chunked = true;
                    sendFirstChunk();
                }
                noData = false;
            }

            if (chunked) {
                if (noData) {
                    noData = false;
                    sendPrologueAndHeader();
                }
                writeChunked(BufferData.create(b, off, len));
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            this.closed = true;
            if (chunked) {
                if (firstPacket != null) {
                    sendFirstChunk();
                } else if (noData) {
                    sendPrologueAndHeader();
                }
                BufferData terminating = BufferData.create(TERMINATING_CHUNK);

                sendListener.data(ctx, terminating);

                writer.write(terminating);
            } else if (contentLength > 0) {
                if (contentLength != bytesWritten) {
                    throw new IOException("Content length is set to " + contentLength
                                                  + ", but the number of bytes written was " + bytesWritten);
                }
            } else {
                headers.remove(HeaderNames.TRANSFER_ENCODING);
                if (noData) {
                    headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
                    contentLength = 0;
                }
                if (noData || firstPacket != null) {
                    sendPrologueAndHeader();
                }
                if (firstPacket != null) {
                    writeContent(firstPacket);
                }
            }
            writer.close();
            super.close();
        }

        boolean closed() {
            return closed;
        }

        boolean hasEntity() {
            return hasEntity;
        }

        private void writeChunked(BufferData buffer) {
            int available = buffer.available();
            byte[] hex = Integer.toHexString(available).getBytes(StandardCharsets.UTF_8);

            BufferData toWrite = BufferData.create(available + hex.length + 4); // \r\n after size, another after chunk
            toWrite.write(hex);
            toWrite.write(Bytes.CR_BYTE);
            toWrite.write(Bytes.LF_BYTE);
            toWrite.write(buffer);
            toWrite.write(Bytes.CR_BYTE);
            toWrite.write(Bytes.LF_BYTE);

            sendListener.data(ctx, toWrite);
            writer.write(toWrite);
        }

        private void writeContent(BufferData buffer) throws IOException {
            bytesWritten += buffer.available();
            if (contentLength != -1 && bytesWritten > contentLength) {
                throw new IOException("Content length was set to " + contentLength
                                              + ", but you are writing additional " + (bytesWritten - contentLength) + " "
                                              + "bytes");
            }

            sendListener.data(ctx, buffer);
            writer.write(buffer);
        }

        private void sendPrologueAndHeader() {
            if (headersSent) {
                return;
            }

            if (chunked) {
                // Add chunked encoding, if there is no other transfer-encoding headers
                if (!headers.contains(HeaderNames.TRANSFER_ENCODING)) {
                    headers.set(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                } else {
                    // Add chunked encoding, if it's not part of existing transfer-encoding headers
                    if (!headers.containsToken(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                    }
                }
                headers.remove(HeaderNames.CONTENT_LENGTH);
            }

            // write prologue and headers in single buffer to avoid multiple TLS records,
            // which in turn can result in a delay when TCP_NO_DELAY is false
            BufferData buffer = BufferData.growing(512);
            buffer.write(prologue);

            if (sendListener.enabled()) {
                var uri = request.uri();
                sendListener.prologue(ctx, HttpPrologue.create("HTTP/1.1",
                                                               "HTTP",
                                                               "1.1",
                                                               request.method(),
                                                               uri.path(),
                                                               uri.query(),
                                                               UriFragment.empty()));
            }

            writeHeaders(connection,
                         headers,
                         buffer,
                         callChain.forwardProxy(),
                         protocolConfig.validateRequestHeaders(),
                         sendListener);
            writer.write(buffer);

            headersSent = true;
            whenSent.complete(request);
        }

        private void sendFirstChunk() {
            sendPrologueAndHeader();
            writeChunked(firstPacket);
            firstPacket = null;
        }
    }
}
