/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;
import io.helidon.common.uri.UriInfo;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Http1HeadersParser;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import static java.lang.System.Logger.Level.TRACE;

class Http1CallOutputStreamChain extends Http1CallChainBase {
    private static final System.Logger LOGGER = System.getLogger(Http1CallOutputStreamChain.class.getName());
    private final Http1ClientImpl http1Client;
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final ClientRequest.OutputStreamHandler osHandler;

    Http1CallOutputStreamChain(Http1ClientImpl http1Client,
                               Http1ClientRequestImpl clientRequest,
                               CompletableFuture<WebClientServiceRequest> whenSent,
                               CompletableFuture<WebClientServiceResponse> whenComplete,
                               ClientRequest.OutputStreamHandler osHandler) {
        super(http1Client, clientRequest, whenComplete);
        this.http1Client = http1Client;
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

        ClientConnectionOutputStream cos = new ClientConnectionOutputStream(connection,
                                                                            writer,
                                                                            reader,
                                                                            writeBuffer,
                                                                            headers,
                                                                            http1Client.clientConfig(),
                                                                            http1Client.protocolConfig(),
                                                                            serviceRequest,
                                                                            originalRequest(),
                                                                            whenSent,
                                                                            whenComplete());

        boolean interrupted = false;
        try {
            osHandler.handle(cos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (OutputStreamInterruptedException e) {
            interrupted = true;
        }

        if (interrupted || cos.interrupted()) {
            //If cos is marked as interrupted, we know that our interrupted exception has been thrown, but
            //it was intercepted by the user OutputStreamHandler and not rethrown.
            //This is a fallback mechanism to correctly handle such a situations.
            return cos.serviceResponse();
        } else if (!cos.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        reader = cos.reader;
        connection = cos.connection;

        Status responseStatus;
        try {
            responseStatus = Http1StatusParser.readStatus(reader, http1Client.protocolConfig().maxStatusLineLength());
            if (responseStatus == Status.CONTINUE_100) {
                // skip the next empty end of line
                readHeaders(reader);
                responseStatus = Http1StatusParser.readStatus(reader, http1Client.protocolConfig().maxStatusLineLength());
            }
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
            URI newUri = URI.create(responseHeaders.get(HeaderNames.LOCATION).value());
            ClientUri redirectUri = ClientUri.create(newUri);
            if (newUri.getHost() == null) {
                UriInfo resolvedUri = cos.lastRequest.resolvedUri();
                redirectUri.scheme(resolvedUri.scheme());
                redirectUri.host(resolvedUri.host());
                redirectUri.port(resolvedUri.port());
            }
            Http1ClientRequestImpl request = new Http1ClientRequestImpl(cos.lastRequest,
                                                                        Method.GET,
                                                                        redirectUri,
                                                                        cos.lastRequest.properties());
            Http1ClientResponseImpl clientResponse = RedirectionProcessor.invokeWithFollowRedirects(request,
                                                                                                    1,
                                                                                                    BufferData.EMPTY_BYTES);
            return createServiceResponse(clientConfig(),
                                         serviceRequest,
                                         clientResponse.connection(),
                                         clientResponse.connection().reader(),
                                         clientResponse.status(),
                                         clientResponse.headers(),
                                         whenComplete());
        }

        return createServiceResponse(clientConfig(),
                                     serviceRequest,
                                     connection,
                                     reader,
                                     responseStatus,
                                     responseHeaders,
                                     whenComplete());
    }

    private static void checkRedirectHeaders(Headers headerValues) {
        if (!headerValues.contains(HeaderNames.LOCATION)) {
            throw new IllegalStateException("There is no " + HeaderNames.LOCATION + " header present in the"
                                                    + " response! "
                                                    + "It is not clear where to redirect.");
        }
    }

    private static class ClientConnectionOutputStream extends OutputStream {
        private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        private final WebClientServiceRequest request;
        private final Http1ClientRequestImpl originalRequest;
        private final CompletableFuture<WebClientServiceRequest> whenSent;
        private final CompletableFuture<WebClientServiceResponse> whenComplete;
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
        private SocketContext ctx;
        private DataWriter writer;
        private DataReader reader;
        private Http1ClientRequestImpl lastRequest;
        private Http1ClientResponseImpl response;
        private WebClientServiceResponse serviceResponse;

        private ClientConnectionOutputStream(ClientConnection connection,
                                             DataWriter writer,
                                             DataReader reader,
                                             BufferData prologue,
                                             WritableHeaders<?> headers,
                                             HttpClientConfig clientConfig,
                                             Http1ClientProtocolConfig protocolConfig,
                                             WebClientServiceRequest request,
                                             Http1ClientRequestImpl originalRequest,
                                             CompletableFuture<WebClientServiceRequest> whenSent,
                                             CompletableFuture<WebClientServiceResponse> whenComplete) {
            this.connection = connection;
            this.ctx = connection.helidonSocket();
            this.writer = writer;
            this.reader = reader;
            this.headers = headers;
            this.prologue = prologue;
            this.clientConfig = clientConfig;
            this.protocolConfig = protocolConfig;
            this.contentLength = headers.contentLength().orElse(-1);
            this.chunked = contentLength == -1 || headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            this.request = request;
            this.originalRequest = originalRequest;
            this.lastRequest = originalRequest;
            this.whenSent = whenSent;
            this.whenComplete = whenComplete;
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

            if (data.available() < 0) {
                throw new IllegalStateException("Buffer returned negative available: "
                                                        + data
                                                        + ", received: off=" + off + ", len=" + len + ", b.length=" + b.length );
            }

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
                BufferData terminating = BufferData.create(TERMINATING_CHUNK);
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    ctx.log(LOGGER, System.Logger.Level.TRACE, "send data%n%s", terminating.debugDataHex());
                }
                writer.write(terminating);
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
            super.close();
        }

        WebClientServiceResponse serviceResponse() {
            if (serviceResponse != null) {
                return serviceResponse;
            }

            return createServiceResponse(clientConfig,
                                         request,
                                         response.connection(),
                                         response.connection().reader(),
                                         response.status(),
                                         response.headers(),
                                         whenComplete);
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

            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                ctx.log(LOGGER, System.Logger.Level.TRACE, "send data:%n%s", toWrite.debugDataHex());
            }
            writer.writeNow(toWrite);
        }

        private void writeContent(BufferData buffer) throws IOException {
            bytesWritten += buffer.available();
            if (contentLength != -1 && bytesWritten > contentLength) {
                throw new IOException("Content length was set to " + contentLength
                                              + ", but you are writing additional " + (bytesWritten - contentLength) + " "
                                              + "bytes");
            }
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                ctx.log(LOGGER, System.Logger.Level.TRACE, "send data:%n%s", buffer.debugDataHex());
            }
            writer.writeNow(buffer);
        }

        private void sendPrologueAndHeader() {
            boolean expects100Continue = clientConfig.sendExpectContinue() && !noData;
            if (expects100Continue) {
                headers.add(HeaderValues.EXPECT_100);
            }

            if (chunked) {
                // Add chunked encoding, if there is no other transfer-encoding headers
                if (!headers.contains(HeaderNames.TRANSFER_ENCODING)) {
                    headers.set(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                } else {
                    // Add chunked encoding, if it's not part of existing transfer-encoding headers
                    if (!headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                    }
                }
                headers.remove(HeaderNames.CONTENT_LENGTH);
            }

            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                ctx.log(LOGGER, System.Logger.Level.TRACE, "send prologue: %n%s", prologue.debugDataHex());
            }
            writer.writeNow(prologue);

            BufferData headerBuffer = BufferData.growing(128);
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                ctx.log(LOGGER, System.Logger.Level.TRACE, "send headers:%n%s", headers);
            }
            writeHeaders(headers, headerBuffer, protocolConfig.validateRequestHeaders());
            writer.writeNow(headerBuffer);

            whenSent.complete(request);

            if (expects100Continue) {
                Status responseStatus;

                try {
                    connection.readTimeout(originalRequest.readContinueTimeout());
                    responseStatus = Http1StatusParser.readStatus(reader, protocolConfig.maxStatusLineLength());
                    connection.helidonSocket().log(LOGGER, TRACE, "recv status: %n%s", responseStatus);
                } catch (UncheckedIOException ignored) {
                    // we assume this is a timeout exception, if the socket got closed, next read will throw appropriate exception
                    // we treat this as receiving 100-Continue
                    responseStatus = null;
                } finally {
                    connection.readTimeout(originalRequest.readTimeout());
                }
                if (responseStatus == Status.CONTINUE_100) {
                    // there is the status and (usually) empty headers. We ignore such headers
                    Http1HeadersParser.readHeaders(reader,
                                                   protocolConfig.maxHeaderSize(),
                                                   protocolConfig.validateResponseHeaders());
                }
                if (responseStatus == null) {
                    responseStatus = Status.CONTINUE_100;
                }

                if (responseStatus != Status.CONTINUE_100) {
                    WritableHeaders<?> responseHeaders = Http1HeadersParser.readHeaders(reader,
                                                                                        protocolConfig.maxHeaderSize(),
                                                                                        protocolConfig.validateResponseHeaders());
                    connection.helidonSocket().log(LOGGER, TRACE, "client received headers %n%s", responseHeaders);

                    if (RedirectionProcessor.redirectionStatusCode(responseStatus) && originalRequest.followRedirects()) {
                        // redirect as needed
                        // Discard any remaining data from the response
                        reader.skip(reader.available());
                        checkRedirectHeaders(responseHeaders);
                        redirect(responseStatus, responseHeaders);
                    } else {
                        //OS changed its state to interrupted, that means other usage of this OS will result in NOOP actions.
                        this.interrupted = true;
                        this.serviceResponse = createServiceResponse(clientConfig,
                                                                     request,
                                                                     connection,
                                                                     reader,
                                                                     responseStatus,
                                                                     ClientResponseHeaders.create(responseHeaders),
                                                                     whenComplete);
                        //we are not sending anything by this OS, we need to interrupt it.
                        throw new OutputStreamInterruptedException();
                    }
                }
            }
        }

        private void redirect(Status lastStatus, WritableHeaders<?> headerValues) {
            String redirectedUri = headerValues.get(HeaderNames.LOCATION).value();
            ClientUri lastUri = originalRequest.uri();
            Method method;
            boolean sendEntity;
            if (lastStatus == Status.TEMPORARY_REDIRECT_307
                    || lastStatus == Status.PERMANENT_REDIRECT_308) {
                method = originalRequest.method();
                sendEntity = true;
            } else {
                method = Method.GET;
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
                            .header(HeaderValues.EXPECT_100)
                            .header(HeaderValues.TRANSFER_ENCODING_CHUNKED)
                            .readTimeout(originalRequest.readContinueTimeout())
                            .request();
                    response.connection().readTimeout(originalRequest.readTimeout());
                } else {
                    response = (Http1ClientResponseImpl) clientRequest.request();
                }
                lastRequest = clientRequest;

                connection = response.connection();
                ctx = connection.helidonSocket();
                reader = connection.reader();
                writer = connection.writer();

                if (RedirectionProcessor.redirectionStatusCode(response.status())) {
                    try (response) {
                        checkRedirectHeaders(response.headers());
                        if (response.status() != Status.TEMPORARY_REDIRECT_307
                                && response.status() != Status.PERMANENT_REDIRECT_308) {
                            method = Method.GET;
                            sendEntity = false;
                        }
                        redirectedUri = response.headers().get(HeaderNames.LOCATION).value();
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
