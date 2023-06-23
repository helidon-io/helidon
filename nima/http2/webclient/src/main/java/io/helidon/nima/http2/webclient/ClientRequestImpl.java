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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.Version;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.ClientRequest;
import io.helidon.nima.webclient.UriHelper;

class ClientRequestImpl implements Http2ClientRequest {
    static final HeaderValue USER_AGENT_HEADER = Header.create(Header.USER_AGENT, "Helidon Nima " + Version.VERSION);
    //todo Gracefully close connections in channel cache
    private static final Map<ConnectionKey, Http2ClientConnectionHandler> CHANNEL_CACHE = new ConcurrentHashMap<>();
    private final Http2ClientImpl client;
    private final ExecutorService executor;
    private final Http.Method method;
    private final UriHelper uri;
    private final UriQueryWriteable query;
    private final int initialWindowSize;
    private final int maxFrameSize;
    private final long maxHeaderListSize;
    private final int connectionPrefetch;
    private final Map<String, String> properties;

    private WritableHeaders<?> explicitHeaders;
    private Tls tls;
    private int priority;
    private boolean priorKnowledge;
    private boolean followRedirects;
    private int requestPrefetch = 0;
    private int maxRedirects;
    private ClientConnection explicitConnection;
    private Duration flowControlTimeout = Duration.ofMillis(100);
    private Duration timeout = Duration.ofSeconds(10);
    private UriFragment fragment = UriFragment.empty();

    ClientRequestImpl(Http2ClientImpl client,
                      ExecutorService executor,
                      Http.Method method,
                      UriHelper helper,
                      Tls tls,
                      UriQueryWriteable query) {
        this.client = client;
        this.executor = executor;
        this.method = method;
        this.uri = helper;
        this.priorKnowledge = client.priorKnowledge();
        this.initialWindowSize = client.initialWindowSize();
        this.maxFrameSize = client.maxFrameSize();
        this.maxHeaderListSize = client.maxHeaderListSize();
        this.connectionPrefetch = client.prefetch();
        this.properties = client.properties();
        this.tls = tls == null || !tls.enabled() ? null : tls;
        this.query = query;
        this.followRedirects = client.followRedirects();
        this.maxRedirects = client.maxRedirects();
        this.explicitHeaders = WritableHeaders.create(client.defaultHeaders());
    }

    @Override
    public Http2ClientRequest tls(Tls tls) {
        this.tls = tls == null || !tls.enabled() ? null : tls;
        return this;
    }

    @Override
    public Http2ClientRequest uri(URI uri) {
        this.uri.resolve(uri, query);
        return this;
    }

    @Override
    public Http2ClientRequest header(HeaderValue header) {
        this.explicitHeaders.add(header);
        return this;
    }

    @Override
    public Http2ClientRequest headers(Headers headers) {
        for (HeaderValue header : headers) {
            this.explicitHeaders.add(header);
        }
        return this;
    }

    @Override
    public Http2ClientRequest headers(Function<ClientRequestHeaders, WritableHeaders<?>> headersConsumer) {
        this.explicitHeaders = headersConsumer.apply(ClientRequestHeaders.create(explicitHeaders));
        return this;
    }

    @Override
    public Http2ClientRequest pathParam(String name, String value) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Http2ClientRequest queryParam(String name, String... values) {
        query.set(name, values);
        return this;
    }

    @Override
    public Http2ClientResponse request() {
        return submit(BufferData.EMPTY_BYTES);
    }

    @Override
    public ClientRequestHeaders headers() {
        return ClientRequestHeaders.create(explicitHeaders);
    }

    @Override
    public Http2ClientResponse submit(Object entity) {
        // todo validate request ok

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

    @Override
    public URI resolvedUri() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Http2ClientRequest connection(ClientConnection connection) {
        this.explicitConnection = connection;
        return this;
    }

    @Override
    public Http2ClientRequest skipUriEncoding() {
        this.uri.skipUriEncoding(true);
        return this;
    }

    @Override
    public Http2ClientRequest property(String propertyName, String propertyValue) {
        properties.put(propertyName, propertyValue);
        return this;
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
    public Http2ClientRequest fragment(String fragment) {
        this.fragment = UriFragment.create(fragment);
        return this;
    }

    @Override
    public Http2ClientRequest followRedirects(boolean followRedirects) {
        //        this.followRedirects = followRedirects;
        //        return this;
        throw new UnsupportedOperationException("Not supported in HTTP2 yet");
    }

    @Override
    public Http2ClientRequest maxRedirects(int maxRedirects) {
        //        this.maxRedirects = maxRedirects;
        //        return this;
        throw new UnsupportedOperationException("Not supported in HTTP2 yet");
    }

    UriHelper uriHelper() {
        return uri;
    }

    private Http2ClientResponse readResponse(Http2ClientStream stream) {
        Http2Headers headers = stream.readHeaders();

        return new ClientResponseImpl(headers, stream);
    }

    private byte[] entityBytes(Object entity) {
        if (entity instanceof byte[]) {
            return (byte[]) entity;
        }
        if (entity instanceof String) {
            return ((String) entity).getBytes(StandardCharsets.UTF_8);
        }
        // todo entity handlers
        throw new IllegalArgumentException("Only string and byte array supported now");
    }

    private Http2Headers prepareHeaders(WritableHeaders<?> headers) {
        Http2Headers http2Headers = Http2Headers.create(headers);
        http2Headers.method(this.method);
        http2Headers.authority(this.uri.authority());
        http2Headers.scheme(this.uri.scheme());
        http2Headers.path(this.uri.pathWithQueryAndFragment(query, fragment));

        headers.remove(Header.HOST, LogHeaderConsumer.INSTANCE);
        headers.remove(Header.TRANSFER_ENCODING, LogHeaderConsumer.INSTANCE);
        headers.remove(Header.CONNECTION, LogHeaderConsumer.INSTANCE);
        return http2Headers;
    }

    private Http2ClientStream reserveStream() {
        if (explicitConnection == null) {
            return newStream(uri);
        } else {
            throw new UnsupportedOperationException("Explicit connection not (yet) supported for HTTP/2 client");
        }
    }

    private Http2ClientStream newStream(UriHelper uri) {
        try {
            ConnectionKey connectionKey = new ConnectionKey(method,
                                                            uri.scheme(),
                                                            uri.host(),
                                                            uri.port(),
                                                            priorKnowledge,
                                                            tls,
                                                            client.dnsResolver(),
                                                            client.dnsAddressLookup());

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
            return newStream(UriHelper.create(URI.create(e.redirectUri()), UriQueryWriteable.create()));
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
