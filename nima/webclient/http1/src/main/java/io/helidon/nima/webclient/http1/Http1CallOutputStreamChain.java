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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http1HeadersParser;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.uri.UriInfo;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.ClientRequest;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;

import static java.lang.System.Logger.Level.TRACE;

class Http1CallOutputStreamChain extends Http1CallChainBase {
    private static final System.Logger LOGGER = System.getLogger(Http1CallOutputStreamChain.class.getName());
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

    private static void checkRedirectHeaders(Headers headerValues) {
        if (!headerValues.contains(Http.Header.LOCATION)) {
            throw new IllegalStateException("There is no " + Http.Header.LOCATION + " header present in the"
                                                    + " response! "
                                                    + "It is not clear where to redirect.");
        }
    }

    @Override
    WebClientServiceResponse doProceed(ClientConnection connection,
                                       WebClientServiceRequest serviceRequest,
                                       ClientRequestHeaders headers,
                                       DataWriter writer,
                                       DataReader reader,
                                       BufferData writeBuffer) {

        ClientConnectionOutputStream cos = new ClientConnectionOutputStream(connection,
                                                                            writer,
                                                                            reader,
                                                                            writeBuffer,
                                                                            headers,
                                                                            clientConfig,
                                                                            protocolConfig,
                                                                            serviceRequest,
                                                                            originalRequest(),
                                                                            whenSent);

        try {
            osHandler.handle(cos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (OutputStreamInterruptedException e) {
            Http1ClientResponseImpl response = cos.response();
            return createServiceResponse(serviceRequest,
                                         response.connection(),
                                         response.connection().reader(),
                                         response.status(),
                                         response.headers());
        }

        if (cos.interrupted()) {
            //If cos is marked as interrupted, we know that our interrupted exception has been thrown, but
            //it was intercepted by the user OutputStreamHandler and not rethrown.
            //This is a fallback mechanism to correctly handle such a situations.
            Http1ClientResponseImpl response = cos.response();
            return createServiceResponse(serviceRequest,
                                         response.connection(),
                                         response.connection().reader(),
                                         response.status(),
                                         response.headers());
        } else if (!cos.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        Http.Status responseStatus;
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
        connection.helidonSocket().log(LOGGER, TRACE, "client received status %n%s", responseStatus);
        ClientResponseHeaders responseHeaders = readHeaders(reader);
        connection.helidonSocket().log(LOGGER, TRACE, "client received headers %n%s", responseHeaders);

        if (originalRequest().followRedirects()
                && RedirectionProcessor.redirectionStatusCode(responseStatus)) {
            checkRedirectHeaders(responseHeaders);
            URI newUri = URI.create(responseHeaders.get(Http.Header.LOCATION).value());
            ClientUri redirectUri = ClientUri.create(newUri);
            if (newUri.getHost() == null) {
                UriInfo resolvedUri = cos.lastRequest.resolvedUri();
                redirectUri.scheme(resolvedUri.scheme());
                redirectUri.host(resolvedUri.host());
                redirectUri.port(resolvedUri.port());
            }
            Http1ClientRequestImpl request = new Http1ClientRequestImpl(cos.lastRequest,
                                                                        Http.Method.GET,
                                                                        redirectUri,
                                                                        cos.lastRequest.properties());
            Http1ClientResponseImpl clientResponse = RedirectionProcessor.invokeWithFollowRedirects(request,
                                                                                                    1,
                                                                                                    BufferData.EMPTY_BYTES);
            return createServiceResponse(serviceRequest,
                                         clientResponse.connection(),
                                         clientResponse.connection().reader(),
                                         clientResponse.status(),
                                         clientResponse.headers());
        }

        return createServiceResponse(serviceRequest, connection, reader, responseStatus, responseHeaders);
    }

    private static class ClientConnectionOutputStream extends OutputStream {
        private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        private static final Duration TIMEOUT_100_CONTINUE = Duration.ofSeconds(2);

        private final WebClientServiceRequest request;
        private final Http1ClientRequestImpl originalRequest;
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
        private boolean interrupted;
        private ClientConnection connection;
        private DataWriter writer;
        private DataReader reader;
        private Http1ClientRequestImpl lastRequest;
        private Http1ClientResponseImpl response;

        private ClientConnectionOutputStream(ClientConnection connection,
                                             DataWriter writer,
                                             DataReader reader,
                                             BufferData prologue,
                                             WritableHeaders<?> headers,
                                             HttpClientConfig clientConfig,
                                             Http1ClientProtocolConfig protocolConfig,
                                             WebClientServiceRequest request,
                                             Http1ClientRequestImpl originalRequest,
                                             CompletableFuture<WebClientServiceRequest> whenSent) {
            this.connection = connection;
            this.writer = writer;
            this.reader = reader;
            this.headers = headers;
            this.prologue = prologue;
            this.clientConfig = clientConfig;
            this.protocolConfig = protocolConfig;
            this.contentLength = headers.contentLength().orElse(-1);
            this.chunked = contentLength == -1 || headers.contains(Http.Headers.TRANSFER_ENCODING_CHUNKED);
            this.request = request;
            this.originalRequest = originalRequest;
            this.lastRequest = originalRequest;
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
            if (interrupted) {
                //If this OS was interrupted, it becomes NOOP.
                return;
            } else if (closed) {
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
            if (closed || interrupted) {
                return;
            }
            this.closed = true;
            if (chunked) {
                if (firstPacket != null) {
                    sendFirstChunk();
                } else if (noData) {
                    sendPrologueAndHeader();
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

        boolean interrupted() {
            return interrupted;
        }

        Http1ClientResponseImpl response() {
            return response;
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
            writeHeaders(headers, headerBuffer, protocolConfig.validateRequestHeaders());
            writer.writeNow(headerBuffer);

            whenSent.complete(request);

            if (expects100Continue) {
                try {
                    connection.readTimeout(TIMEOUT_100_CONTINUE);
                    Http.Status responseStatus = Http1StatusParser.readStatus(reader, protocolConfig.maxStatusLineLength());
                    if (redirectStatus(responseStatus, true)) {
                        if (!originalRequest.followRedirects()) {
                            throw new IllegalStateException("Expected a status of '100 Continue' but received a '"
                                                                    + responseStatus + "' instead");
                        }
                        WritableHeaders<?> headerValues = Http1HeadersParser.readHeaders(reader,
                                                                                         protocolConfig.maxHeaderSize(),
                                                                                         protocolConfig.validateHeaders());
                        // Discard any remaining data from the response
                        reader.skip(reader.available());
                        checkRedirectHeaders(headerValues);
                        redirect(responseStatus, headerValues);
                    } else {
                        // Discard any remaining data from the response
                        reader.skip(reader.available());
                    }
                } finally {
                    connection.readTimeout(originalRequest.readTimeout());
                }
            }
        }

        private boolean redirectStatus(Http.Status status, boolean sendEntity) {
            if (!RedirectionProcessor.redirectionStatusCode(status)) {
                if (status != Http.Status.CONTINUE_100 && sendEntity) {
                    throw new IllegalStateException("Expected a status of '100 Continue' but received a '"
                                                            + status + "' instead");
                }
                return false;
            }
            return true;
        }

        private void redirect(Http.Status lastStatus, WritableHeaders<?> headerValues) {
            String redirectedUri = headerValues.get(Http.Header.LOCATION).value();
            ClientUri lastUri = originalRequest.uri();
            Http.Method method;
            boolean sendEntity;
            if (lastStatus == Http.Status.TEMPORARY_REDIRECT_307
                    || lastStatus == Http.Status.PERMANENT_REDIRECT_308) {
                method = originalRequest.method();
                sendEntity = true;
            } else {
                method = Http.Method.GET;
                sendEntity = false;
            }
            for (int i = 0; i < clientConfig.maxRedirects(); i++) {
                URI newUri = URI.create(redirectedUri);
                ClientUri redirectUri = ClientUri.create(newUri);
                if (newUri.getHost() == null) {
                    redirectUri.scheme(lastUri.scheme());
                    redirectUri.host(lastUri.host());
                    redirectUri.port(lastUri.port());
                }
                lastUri = redirectUri;
                connection.releaseResource();
                Http1ClientRequestImpl clientRequest = new Http1ClientRequestImpl(originalRequest,
                                                                                  method,
                                                                                  redirectUri,
                                                                                  request.properties());
                Http1ClientResponseImpl response;
                if (sendEntity) {
                    response = (Http1ClientResponseImpl) clientRequest
                            .header(Http.HeaderValues.EXPECT_100)
                            .header(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED)
                            .readTimeout(TIMEOUT_100_CONTINUE)
                            .request();
                } else {
                    response = (Http1ClientResponseImpl) clientRequest.request();
                }
                lastRequest = clientRequest;

                connection = response.connection();
                reader = connection.reader();
                writer = connection.writer();

                if (redirectStatus(response.status(), sendEntity)) {
                    try (Http1ClientResponseImpl ignored = response) {
                        checkRedirectHeaders(response.headers());
                        if (response.status() != Http.Status.TEMPORARY_REDIRECT_307
                                && response.status() != Http.Status.PERMANENT_REDIRECT_308) {
                            method = Http.Method.GET;
                            sendEntity = false;
                        }
                        redirectedUri = response.headers().get(Http.Header.LOCATION).value();
                    }
                } else {
                    if (!sendEntity) {
                        //OS changed its state to interrupted, that means other usage of this OS will result in NOOP actions.
                        this.interrupted = true;
                        this.response = response;
                        //we are not sending anything by this OS, we need to interrupt it.
                        throw new OutputStreamInterruptedException();
                    } else {
                        reader.skip(reader.available());
                    }
                    return;
                }

            }
            throw new IllegalStateException("Maximum number of request redirections ("
                                                    + clientConfig.maxRedirects() + ") reached.");
        }

        private void sendFirstChunk() {
            sendPrologueAndHeader();
            writeChunked(firstPacket);
            firstPacket = null;
        }
    }
}
