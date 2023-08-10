/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient.http1;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.ClientRequest;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;

class Http1CallOutputStreamChain extends Http1CallChainBase {
    private final HttpClientConfig clientConfig;
    private final Http1ClientProtocolConfig protocolConfig;
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final ClientRequest.OutputStreamHandler osHandler;

    Http1CallOutputStreamChain(WebClient webClient,
                               Http1ClientRequestImpl clientRequest,
                               HttpClientConfig clientConfig,
                               Http1ClientProtocolConfig protocolConfig,
                               CompletableFuture<WebClientServiceRequest> whenSent,
                               CompletableFuture<WebClientServiceResponse> whenComplete,
                               ClientRequest.OutputStreamHandler osHandler) {
        super(webClient,
              clientConfig,
              protocolConfig,
              clientRequest,
              whenComplete);
        this.clientConfig = clientConfig;
        this.protocolConfig = protocolConfig;
        this.whenSent = whenSent;
        this.osHandler = osHandler;
    }

    @Override
    WebClientServiceResponse doProceed(ClientConnection connection,
                                       WebClientServiceRequest serviceRequest,
                                       ClientRequestHeaders headers,
                                       DataWriter writer,
                                       DataReader reader,
                                       BufferData writeBuffer) {

        ClientConnectionOutputStream cos = new ClientConnectionOutputStream(writer,
                                                                            reader,
                                                                            writeBuffer,
                                                                            headers,
                                                                            clientConfig,
                                                                            protocolConfig,
                                                                            serviceRequest,
                                                                            whenSent);

        try {
            osHandler.handle(cos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!cos.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        return readResponse(serviceRequest, connection, reader);
    }

    private static class ClientConnectionOutputStream extends OutputStream {
        private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        private final DataWriter writer;
        private final DataReader reader;
        private final WebClientServiceRequest request;
        private final CompletableFuture<WebClientServiceRequest> whenSent;
        private final HttpClientConfig clientConfig;
        private final Http1ClientProtocolConfig protocolConfig;
        private final WritableHeaders<?> headers;
        private final BufferData prologue;

        private boolean chunked;
        private BufferData firstPacket;
        private long bytesWritten;
        private long contentLength;
        private boolean noData = true;
        private boolean closed;

        private ClientConnectionOutputStream(DataWriter writer,
                                             DataReader reader,
                                             BufferData prologue,
                                             WritableHeaders<?> headers,
                                             HttpClientConfig clientConfig,
                                             Http1ClientProtocolConfig protocolConfig,
                                             WebClientServiceRequest request,
                                             CompletableFuture<WebClientServiceRequest> whenSent) {
            this.writer = writer;
            this.reader = reader;
            this.headers = headers;
            this.prologue = prologue;
            this.clientConfig = clientConfig;
            this.protocolConfig = protocolConfig;
            this.contentLength = headers.contentLength().orElse(-1);
            this.chunked = contentLength == -1 || headers.contains(Http.Headers.TRANSFER_ENCODING_CHUNKED);
            this.request = request;
            this.whenSent = whenSent;
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

            BufferData data = BufferData.create(b, off, len);

            if (!chunked) {
                if (firstPacket == null) {
                    firstPacket = data;
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
                writeChunked(data);
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
                }
                writer.write(BufferData.create(TERMINATING_CHUNK));
            } else {
                headers.remove(Http.HeaderNames.TRANSFER_ENCODING);
                if (noData) {
                    headers.set(Http.Headers.CONTENT_LENGTH_ZERO);
                    contentLength = 0;
                }
                if (noData || firstPacket != null) {
                    sendPrologueAndHeader();
                }
                if (firstPacket != null) {
                    writeContent(firstPacket);
                }
            }
            super.close();
        }

        boolean closed() {
            return closed;
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

            writer.writeNow(toWrite);
        }

        private void writeContent(BufferData buffer) throws IOException {
            bytesWritten += buffer.available();
            if (contentLength != -1 && bytesWritten > contentLength) {
                throw new IOException("Content length was set to " + contentLength
                                              + ", but you are writing additional " + (bytesWritten - contentLength) + " "
                                              + "bytes");
            }

            writer.writeNow(buffer);
        }

        private void sendPrologueAndHeader() {
            boolean expects100Continue = clientConfig.sendExpectContinue() && !noData;
            if (expects100Continue) {
                headers.add(Http.Headers.EXPECT_100);
            }

            if (chunked) {
                // Add chunked encoding, if there is no other transfer-encoding headers
                if (!headers.contains(Http.HeaderNames.TRANSFER_ENCODING)) {
                    headers.set(Http.Headers.TRANSFER_ENCODING_CHUNKED);
                } else {
                    // Add chunked encoding, if it's not part of existing transfer-encoding headers
                    if (!headers.contains(Http.Headers.TRANSFER_ENCODING_CHUNKED)) {
                        headers.add(Http.Headers.TRANSFER_ENCODING_CHUNKED);
                    }
                }
                headers.remove(Http.HeaderNames.CONTENT_LENGTH);
            }

            writer.writeNow(prologue);

            BufferData headerBuffer = BufferData.growing(128);
            writeHeaders(headers, headerBuffer, protocolConfig.validateHeaders());
            writer.writeNow(headerBuffer);

            whenSent.complete(request);

            if (expects100Continue) {
                Http.Status responseStatus = Http1StatusParser.readStatus(reader, protocolConfig.maxStatusLineLength());
                if (responseStatus != Http.Status.CONTINUE_100) {
                    throw new IllegalStateException("Expected a status of '100 Continue' but received a '"
                                                            + responseStatus + "' instead");
                }
                // Discard any remaining data from the response
                reader.skip(reader.available());
            }
        }

        private void sendFirstChunk() {
            sendPrologueAndHeader();
            writeChunked(firstPacket);
            firstPacket = null;
        }
    }
}
