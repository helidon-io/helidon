/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http2.webclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.webclient.api.ClientRequest;
import io.helidon.nima.webclient.api.ClientRequestBase;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.ConnectionKey;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;
import io.helidon.nima.webclient.spi.WebClientService;

class ClientRequestImpl extends ClientRequestBase<Http2ClientRequest, Http2ClientResponse> implements Http2ClientRequest {

    private final Http2ClientProtocolConfig protocolConfig;
    private final ExecutorService executor;
    private final int initialWindowSize;
    private final int maxFrameSize;
    private final long maxHeaderListSize;
    private final int connectionPrefetch;

    private int priority;
    private boolean priorKnowledge;
    private int requestPrefetch = 0;
    private Duration flowControlTimeout = Duration.ofMillis(100);
    private Duration timeout = Duration.ofSeconds(10);

    ClientRequestImpl(WebClient webClient,
                      HttpClientConfig clientConfig,
                      Http2ClientProtocolConfig protocolConfig,
                      Http.Method method,
                      ClientUri clientUri,
                      UriQueryWriteable query) {
        super(clientConfig, Http2Client.PROTOCOL_ID, method, clientUri, query, clientConfig.properties());
        this.protocolConfig = protocolConfig;
        this.executor = clientConfig.executor();

        this.priorKnowledge = protocolConfig.priorKnowledge();
        this.initialWindowSize = protocolConfig.initialWindowSize();
        this.maxFrameSize = protocolConfig.maxFrameSize();
        this.maxHeaderListSize = protocolConfig.maxHeaderListSize();
        this.connectionPrefetch = protocolConfig.prefetch();
    }

    @Override
    public Http2ClientRequest priority(int priority) {
        if (priority < 1 || priority > 256) {
            throw new IllegalArgumentException("Priority must be between 1 and 256 (inclusive)");
        }
        this.priority = priority;
        return this;
    }

    @Override
    public Http2ClientRequest priorKnowledge(boolean priorKnowledge) {
        this.priorKnowledge = priorKnowledge;
        return this;
    }

    @Override
    public Http2ClientRequest requestPrefetch(int requestPrefetch) {
        this.requestPrefetch = requestPrefetch;
        return this;
    }

    @Override
    public Http2ClientRequest timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public Http2ClientRequest flowControlTimeout(Duration timeout) {
        this.flowControlTimeout = timeout;
        return this;
    }

    @Override
    public Http2ClientResponse doSubmit(Object entity) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        WebClientService.Chain httpCall = new Http2CallEntityChain(webClient,
                                                                  this,
                                                                  protocolConfig,
                                                                  connection().orElse(null),
                                                                  whenSent,
                                                                  whenComplete,
                                                                  entity);

        return invokeWithServices(httpCall, whenSent, whenComplete);
        WritableHeaders<?> headers = WritableHeaders.create(explicitHeaders);

        Http2ClientStream stream = reserveStream();

        byte[] entityBytes;
        if (entity == BufferData.EMPTY_BYTES) {
            entityBytes = BufferData.EMPTY_BYTES;
        } else {
            entityBytes = entityBytes(entity);
        }
        headers.set(Header.create(Header.CONTENT_LENGTH, entityBytes.length));
        headers.setIfAbsent(USER_AGENT_HEADER);

        Http2Headers http2Headers = prepareHeaders(headers);
        stream.write(http2Headers, entityBytes.length == 0);

        stream.flowControl().inbound().incrementWindowSize(requestPrefetch);

        if (entityBytes.length != 0) {
            stream.writeData(BufferData.create(entityBytes), true);
        }

        return readResponse(stream);
    }

    @Override
    public Http2ClientResponse outputStream(ClientRequest.OutputStreamHandler streamHandler) {
        // todo validate request ok

        WritableHeaders<?> headers = WritableHeaders.create(explicitHeaders);

        Http2ClientStream stream = reserveStream();

        Http2Headers http2Headers = prepareHeaders(headers);

        stream.write(http2Headers, false);

        Http2ClientStream.ClientOutputStream outputStream;
        try {
            outputStream = stream.outputStream();
            streamHandler.handle(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!outputStream.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        return readResponse(stream);
    }

    private Http2ClientResponse readResponse(Http2ClientStream stream) {
        Http2Headers headers = stream.readHeaders();

        return new ClientResponseImpl(headers, stream, clientUri);
    }

    private byte[] entityBytes(Object entity) {
        if (entity instanceof byte[] bytes) {
            return bytes;
        }

        GenericType<Object> genericType = GenericType.create(entity);
        EntityWriter<Object> writer = mediaContext.writer(genericType, explicitHeaders);

        // This uses an in-memory buffer, which would cause damage for writing big objects (such as Path)
        // we have a follow-up issue to make sure this is fixed
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writer.write(genericType, entity, bos, explicitHeaders);
        return bos.toByteArray();
    }

    private Http2Headers prepareHeaders(WritableHeaders<?> headers) {
        Http2Headers http2Headers = Http2Headers.create(headers);
        http2Headers.method(this.method);
        http2Headers.authority(this.clientUri.authority());
        http2Headers.scheme(this.clientUri.scheme());
        http2Headers.path(this.clientUri.pathWithQueryAndFragment(query, fragment));

        headers.remove(Header.HOST, LogHeaderConsumer.INSTANCE);
        headers.remove(Header.TRANSFER_ENCODING, LogHeaderConsumer.INSTANCE);
        headers.remove(Header.CONNECTION, LogHeaderConsumer.INSTANCE);
        return http2Headers;
    }

    private Http2ClientStream reserveStream() {
        if (explicitConnection == null) {
            return newStream(clientUri);
        } else {
            throw new UnsupportedOperationException("Explicit connection not (yet) supported for HTTP/2 client");
        }
    }

    private Http2ClientStream newStream(ClientUri uri) {
        try {
            ConnectionKey connectionKey = new ConnectionKey(method,
                                                            uri.scheme(),
                                                            uri.host(),
                                                            uri.port(),
                                                            priorKnowledge,
                                                            tls,
                                                            clientConfig.dnsResolver(),
                                                            clientConfig.dnsAddressLookup());

            // this statement locks all threads - must not do anything complicated (just create a new instance)
            return CHANNEL_CACHE.computeIfAbsent(connectionKey,
                                                 key -> new Http2ClientConnectionHandler(executor,
                                                                                         SocketOptions.builder().build(),
                                                                                         uri.path(),
                                                                                         key))
                    // this statement may block a single connection key
                    .newStream(new ConnectionContext(priority,
                                                     priorKnowledge,
                                                     initialWindowSize,
                                                     maxFrameSize,
                                                     maxHeaderListSize,
                                                     connectionPrefetch,
                                                     requestPrefetch,
                                                     flowControlTimeout,
                                                     timeout));
        } catch (UpgradeRedirectException e) {
            return newStream(ClientUri.create(URI.create(e.redirectUri()), UriQueryWriteable.create()));
        }
    }

    private static final class LogHeaderConsumer implements Consumer<HeaderValue> {
        private static final System.Logger LOGGER = System.getLogger(LogHeaderConsumer.class.getName());
        private static final LogHeaderConsumer INSTANCE = new LogHeaderConsumer();

        @Override
        public void accept(HeaderValue httpHeader) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           "HTTP/2 request contains wrong header, removing: " + httpHeader);
            }
        }
    }
}
