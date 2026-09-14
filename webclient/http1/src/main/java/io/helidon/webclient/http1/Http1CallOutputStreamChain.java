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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;
import io.helidon.common.uri.UriFragment;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http1.Http1ConnectionListener;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientRequestOrigin;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.RedirectSecurityState;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.api.WebClientTransportObserverSupport;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.ERROR;

class Http1CallOutputStreamChain extends Http1CallChainBase {
    private final Http1ClientImpl http1Client;
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final ClientRequest.OutputStreamHandler osHandler;
    private boolean requestEntitySent;
    private int followedRedirects;
    private boolean closeConnectionOnResponseClose;

    Http1CallOutputStreamChain(Http1ClientImpl http1Client,
                               Http1ClientRequestImpl clientRequest,
                               CompletableFuture<WebClientServiceRequest> whenSent,
                               CompletableFuture<WebClientServiceResponse> whenComplete,
                               ClientRequest.OutputStreamHandler osHandler,
                               int followedRedirects) {
        super(http1Client, clientRequest, whenComplete);
        this.http1Client = http1Client;
        this.whenSent = whenSent;
        this.osHandler = osHandler;
        this.followedRedirects = followedRedirects;
    }

    @Override
    WebClientServiceResponse doProceed(ClientConnection connection,
                                       WebClientServiceRequest serviceRequest,
                                       ClientRequestHeaders headers,
                                       DataWriter writer,
                                       DataReader reader,
                                       BufferData writeBuffer) {

        ClientConnectionOutputStream cos = new ClientConnectionOutputStream(this,
                                                                            connection,
                                                                            writer,
                                                                            reader,
                                                                            writeBuffer,
                                                                            headers,
                                                                            http1Client,
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
        } finally {
            responseConnection(cos.connection);
        }
        requestEntitySent = cos.hasEntity();
        followedRedirects = cos.numberOfRedirects();

        if (interrupted || cos.interrupted()) {
            //If cos is marked as interrupted, we know that our interrupted exception has been thrown, but
            //it was intercepted by the user OutputStreamHandler and not rethrown.
            //This is a fallback mechanism to correctly handle such a situations.
            if (cos.redirectFailure != null) {
                throw cos.redirectFailure;
            }
            whenSent.complete(cos.lastServiceRequest());
            return cos.serviceResponse();
        } else if (!cos.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        reader = cos.reader;
        connection = cos.connection;

        ResponseHead responseHead = readResponseHead(connection, reader);
        Status responseStatus = responseHead.status();
        ClientResponseHeaders responseHeaders = responseHead.headers();
        captureProtocolResponse(connection, responseStatus, responseHeaders);

        return createServiceResponseWithTrailers(http1Client,
                                                 cos.lastServiceRequest(),
                                                 connection,
                                                 reader,
                                                 responseStatus,
                                                 responseHeaders,
                                                 whenComplete());
    }

    boolean requestEntitySent() {
        return requestEntitySent;
    }

    int followedRedirects() {
        return followedRedirects;
    }

    boolean closeConnectionOnResponseClose() {
        return closeConnectionOnResponseClose;
    }

    private void closeConnectionOnResponseClose(boolean closeConnectionOnResponseClose) {
        this.closeConnectionOnResponseClose = closeConnectionOnResponseClose;
    }

    private WebClientServiceResponse redirectedServiceResponse(Http1ClientResponseImpl response,
                                                                Http1ClientRequestImpl redirectedRequest) {
        originalRequest().redirectSecurityState(redirectedRequest.redirectSecurityState());
        WebClientServiceResponse redirected = response.serviceResponse();
        whenComplete().whenComplete((_, failure) -> {
            if (failure == null) {
                redirected.whenComplete().complete(redirected);
            } else {
                redirected.whenComplete().completeExceptionally(failure);
            }
        });
        return WebClientServiceResponse.builder(redirected)
                .whenComplete(whenComplete())
                .build();
    }

    private static void checkRedirectHeaders(Headers headerValues) {
        if (!headerValues.contains(HeaderNames.LOCATION)) {
            throw new IllegalStateException("There is no " + HeaderNames.LOCATION + " header present in the"
                                                    + " response! "
                                                    + "It is not clear where to redirect.");
        }
    }

    private static ClientUri responseCookieUri(RedirectSecurityState securityState, ClientUri endpointUri) {
        return securityState.lastEffectiveOrigin()
                .map(origin -> origin.apply(endpointUri))
                .orElseGet(() -> ClientRequestOrigin.create(endpointUri).apply(endpointUri));
    }

    private static class ClientConnectionOutputStream extends OutputStream {
        private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        private final WebClientServiceRequest request;
        private final Http1ClientRequestImpl originalRequest;
        private final CompletableFuture<WebClientServiceRequest> whenSent;
        private final CompletableFuture<WebClientServiceResponse> whenComplete;
        private final Http1CallOutputStreamChain callChain;
        private final Http1ClientImpl http1Client;
        private final Http1ConnectionListener sendListener;
        private final HttpClientConfig clientConfig;
        private final Http1ClientProtocolConfig protocolConfig;
        private final WritableHeaders<?> headers;
        private final BufferData prologue;

        private boolean chunked;
        private BufferData firstPacket;
        private long bytesWritten;
        private long contentLength;
        private boolean hasEntity;
        private int numberOfRedirects;
        private boolean noData = true;
        private boolean headersSent;
        private boolean closed;
        private boolean interrupted;
        private ClientConnection connection;
        private SocketContext ctx;
        private DataWriter writer;
        private DataReader reader;
        private Http1ClientRequestImpl lastRequest;
        private WebClientServiceRequest lastServiceRequest;
        private ClientUri lastEndpointUri;
        private Http1ClientResponseImpl response;
        private WebClientServiceResponse serviceResponse;
        private ByteArrayOutputStream redirectedEntity;
        private Http1ClientRequestImpl redirectedRequest;
        private RuntimeException redirectFailure;

        private ClientConnectionOutputStream(Http1CallOutputStreamChain callChain,
                                             ClientConnection connection,
                                             DataWriter writer,
                                             DataReader reader,
                                             BufferData prologue,
                                             WritableHeaders<?> headers,
                                             Http1ClientImpl http1Client,
                                             WebClientServiceRequest request,
                                             Http1ClientRequestImpl originalRequest,
                                             CompletableFuture<WebClientServiceRequest> whenSent,
                                             CompletableFuture<WebClientServiceResponse> whenComplete) {
            this.callChain = callChain;
            this.connection = connection;
            this.ctx = connection.helidonSocket();
            this.writer = writer;
            this.reader = reader;
            this.headers = headers;
            this.prologue = prologue;
            this.http1Client = http1Client;
            this.clientConfig = http1Client.clientConfig();
            this.protocolConfig = http1Client.protocolConfig();
            this.contentLength = headers.contentLength().orElse(-1);
            this.chunked = contentLength == -1 || headers.containsToken(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            this.request = request;
            this.originalRequest = originalRequest;
            this.lastRequest = originalRequest;
            this.lastServiceRequest = request;
            this.lastEndpointUri = ClientUri.create(request.uri().toUri());
            this.numberOfRedirects = callChain.followedRedirects;
            this.whenSent = whenSent;
            this.whenComplete = whenComplete;
            this.sendListener = http1Client.sendListener();
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
            if (len == 0) {
                return;
            }
            hasEntity = true;

            // if not chunked and length known, write directly checking length at close
            if (!chunked && contentLength > 0) {
                if (!headersSent) {
                    sendPrologueAndHeader();
                    noData = false;
                }
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
            if (closed || interrupted) {
                return;
            }
            this.closed = true;
            if (redirectedEntity != null) {
                try {
                    if (contentLength > 0 && contentLength != bytesWritten) {
                        throw new IOException("Content length is set to " + contentLength
                                                      + ", but the number of bytes written was " + bytesWritten);
                    }
                    redirectedRequest.followRedirects(true);
                    redirectedRequest.deferResponseCookies();
                    byte[] entity = redirectedEntity.toByteArray();
                    response = redirectedRequest.replayBufferedEntity(entity, numberOfRedirects);
                    lastServiceRequest = response.serviceRequest();
                    lastEndpointUri = response.lastEndpointUri();
                    connection = response.connection();
                    if (response.closesConnectionOnClose()) {
                        callChain.closeConnectionOnResponseClose(true);
                    }
                    whenSent.complete(lastServiceRequest);
                    interrupted = true;
                    super.close();
                    return;
                } catch (IOException e) {
                    redirectFailure = new UncheckedIOException(e);
                    interrupted = true;
                    throw e;
                } catch (RuntimeException e) {
                    redirectFailure = e;
                    interrupted = true;
                    throw e;
                }
            }
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
            whenSent.complete(lastServiceRequest);
            super.close();
        }

        WebClientServiceResponse serviceResponse() {
            if (serviceResponse != null) {
                return serviceResponse;
            }

            return callChain.redirectedServiceResponse(response,
                                                       redirectedRequest == null ? lastRequest : redirectedRequest);
        }

        boolean closed() {
            return closed;
        }

        boolean interrupted() {
            return interrupted;
        }

        boolean hasEntity() {
            return hasEntity;
        }

        int numberOfRedirects() {
            return numberOfRedirects;
        }

        WebClientServiceRequest lastServiceRequest() {
            return lastServiceRequest;
        }

        Http1ClientResponseImpl response() {
            return response;
        }

        private void writeChunked(BufferData buffer) throws IOException {
            if (redirectedEntity != null) {
                writeRedirected(buffer);
                return;
            }
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

            if (redirectedEntity != null) {
                writeRedirected(buffer);
                return;
            }

            sendListener.data(ctx, buffer);
            writer.write(buffer);
        }

        private void writeRedirected(BufferData buffer) throws IOException {
            int available = buffer.available();
            if ((long) redirectedEntity.size() + available > clientConfig.maxInMemoryEntity()) {
                throw new IOException("Redirected output-stream request entity exceeds the configured in-memory limit of "
                                              + clientConfig.maxInMemoryEntity() + " bytes");
            }
            redirectedEntity.writeBytes(buffer.readBytes());
        }

        private void sendPrologueAndHeader() {
            // setting for expect 100 header, can be overridden for each request
            boolean expects100Continue = connection.allowExpectContinue()
                    && !noData
                    && originalRequest.sendExpectContinue().orElse(clientConfig.sendExpectContinue());

            if (expects100Continue) {
                headers.add(HeaderValues.EXPECT_100);
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

            if (expects100Continue) {
                ResponseHead responseHead = null;

                try {
                    writer.flush();     // flush before a read
                    connection.readTimeout(originalRequest.readContinueTimeout());
                    while (responseHead == null) {
                        boolean statusRead = false;
                        try {
                            Status responseStatus = callChain.readResponseStatus(connection, reader, false);
                            statusRead = true;
                            ClientResponseHeaders responseHeaders = callChain.readResponseHeaders(connection, reader);

                            if (!Http1CallChainBase.isPreContinueInterimResponse(responseStatus)) {
                                responseHead = new ResponseHead(responseStatus, responseHeaders);
                            }
                        } catch (UncheckedIOException e) {
                            if (!statusRead && e.getCause() instanceof SocketTimeoutException) {
                                connection.allowExpectContinue(false);
                                break;
                            }
                            throw e;
                        }
                    }
                } catch (UncheckedIOException e) {
                    try {
                        WebClientTransportObserverSupport.close(connection, ERROR);
                    } catch (RuntimeException | Error ex) {
                        e.addSuppressed(ex);
                    }
                    throw e;
                } finally {
                    if (connection.isConnected()) {
                        connection.readTimeout(originalRequest.readTimeout());
                    }
                }

                Status responseStatus = responseHead == null ? Status.CONTINUE_100 : responseHead.status();

                if (responseStatus.code() != Status.CONTINUE_100.code()) {
                    ClientResponseHeaders responseHeaders = responseHead.headers();
                    callChain.captureProtocolResponse(connection, responseStatus, responseHeaders);

                    if (RedirectionProcessor.redirectionStatusCode(responseStatus) && originalRequest.followRedirects()) {
                        callChain.publishProtocolResponse();
                        // redirect as needed
                        // Discard any remaining data from the response
                        reader.skip(reader.available());
                        checkRedirectHeaders(responseHeaders);
                        originalRequest.recordResponseCookies(responseCookieUri(originalRequest.redirectSecurityState(),
                                                                                lastEndpointUri),
                                                              responseHeaders);
                        redirect(responseStatus, responseHeaders);
                    } else {
                        //OS changed its state to interrupted, that means other usage of this OS will result in NOOP actions.
                        this.interrupted = true;
                        callChain.closeConnectionOnResponseClose(true);
                        this.serviceResponse = callChain.createServiceResponseWithTrailers(http1Client,
                                                                                           request,
                                                                                           connection,
                                                                                           reader,
                                                                                           responseStatus,
                                                                                           responseHeaders,
                                                                                           whenComplete);
                        //we are not sending anything by this OS, we need to interrupt it.
                        throw new OutputStreamInterruptedException();
                    }
                }
            }
        }

        private void redirect(Status lastStatus, Headers headerValues) {
            String redirectedUri = headerValues.get(HeaderNames.LOCATION).get();
            ClientUri sourceUri = lastEndpointUri;
            Method method = RedirectionProcessor.keepsMethodAndEntity(lastRequest.method(), lastStatus)
                    ? originalRequest.method()
                    : Method.GET;
            boolean sendEntity = RedirectionProcessor.keepsMethodAndEntity(lastRequest.method(), lastStatus);
            connection.closeResource();
            while (numberOfRedirects < originalRequest.maxRedirects()) {
                numberOfRedirects++;
                ClientUri redirectUri = originalRequest.resolveRedirectUri(sourceUri, redirectedUri);
                Http1ClientRequestImpl clientRequest = new Http1ClientRequestImpl(lastRequest,
                                                                                  method,
                                                                                  redirectUri,
                                                                                  lastRequest.properties(),
                                                                                  sourceUri,
                                                                                  sendEntity,
                                                                                  sendEntity);
                clientRequest.followRedirects(false);
                Http1ClientResponseImpl response;
                if (sendEntity && !clientConfig.services().isEmpty()) {
                    clientRequest.redirectedWhenSent(whenSent);
                    redirectedRequest = clientRequest;
                    redirectedEntity = new ByteArrayOutputStream();
                    lastRequest = clientRequest;
                    return;
                }
                clientRequest.deferResponseCookies();
                if (sendEntity) {
                    clientRequest.outputStreamRedirect(true)
                            .header(HeaderValues.EXPECT_100)
                            .header(HeaderValues.TRANSFER_ENCODING_CHUNKED)
                            .readTimeout(originalRequest.readContinueTimeout());
                    response = clientRequest.redirectProbe();
                    if (response.connection() != null) {
                        response.connection().readTimeout(originalRequest.readTimeout());
                    }
                } else {
                    response = clientRequest.redirectProbe();
                }
                lastRequest = clientRequest;
                lastServiceRequest = response.serviceRequest();
                lastEndpointUri = response.lastEndpointUri();

                connection = response.connection();
                if (connection != null) {
                    callChain.responseConnection(connection);
                    ctx = connection.helidonSocket();
                    reader = connection.reader();
                    writer = connection.writer();
                }

                if (response.status() == Status.CONTINUE_100) {
                    response.completeWithoutClosingConnection();
                    return;
                } else if (RedirectionProcessor.redirectionStatusCode(response.status())) {
                    try {
                        checkRedirectHeaders(response.headers());
                        ClientUri endpointUri = lastEndpointUri;
                        clientRequest.recordResponseCookies(responseCookieUri(clientRequest.redirectSecurityState(),
                                                                              endpointUri),
                                                            response.headers());
                        if (!RedirectionProcessor.keepsMethodAndEntity(lastRequest.method(), response.status())) {
                            method = Method.GET;
                            sendEntity = false;
                        }
                        sourceUri = endpointUri;
                        redirectedUri = response.headers().get(HeaderNames.LOCATION).get();
                    } finally {
                        Throwable closeFailure = null;
                        try {
                            if (response.connection() != null) {
                                response.connection().closeResource();
                            }
                        } catch (RuntimeException | Error failure) {
                            closeFailure = failure;
                            throw failure;
                        } finally {
                            if (closeFailure != null) {
                                response.serviceResponse().whenComplete().completeExceptionally(closeFailure);
                            }
                            try {
                                response.completeWithoutClosingConnection();
                            } catch (RuntimeException | Error completionFailure) {
                                if (closeFailure == null) {
                                    throw completionFailure;
                                }
                                if (closeFailure != completionFailure) {
                                    closeFailure.addSuppressed(completionFailure);
                                }
                            }
                        }
                    }
                } else {
                    //OS changed its state to interrupted, that means other usage of this OS will result in NOOP actions.
                    this.interrupted = true;
                    callChain.closeConnectionOnResponseClose(true);
                    this.response = response;
                    //we are not sending anything by this OS, we need to interrupt it.
                    throw new OutputStreamInterruptedException();
                }
            }
            throw RedirectionProcessor.maxRedirectsReached(originalRequest.maxRedirects());
        }

        private void sendFirstChunk() throws IOException {
            sendPrologueAndHeader();
            writeChunked(firstPacket);
            firstPacket = null;
        }
    }
}
