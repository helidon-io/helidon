/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Logger;

import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpRequest;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.webclient.spi.WebClientService;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import io.netty.util.AttributeKey;

/**
 * Implementation of {@link WebClientRequestBuilder}.
 */
class WebClientRequestBuilderImpl implements WebClientRequestBuilder {

    private static final Logger LOGGER = Logger.getLogger(WebClientRequestBuilderImpl.class.getName());

    private static final Map<ConnectionIdent, Set<ChannelRecord>> CHANNEL_CACHE = new ConcurrentHashMap<>();
    static final AttributeKey<WebClientRequestImpl> REQUEST = AttributeKey.valueOf("request");
    static final AttributeKey<CompletableFuture<WebClientServiceResponse>> RECEIVED = AttributeKey.valueOf("received");
    static final AttributeKey<CompletableFuture<WebClientServiceResponse>> COMPLETED = AttributeKey.valueOf("completed");
    static final AttributeKey<CompletableFuture<WebClientResponse>> RESULT = AttributeKey.valueOf("result");
    static final AttributeKey<AtomicBoolean> IN_USE = AttributeKey.valueOf("inUse");
    static final AttributeKey<AtomicBoolean> RETURN = AttributeKey.valueOf("finished");
    static final AttributeKey<AtomicBoolean> RESPONSE_RECEIVED = AttributeKey.valueOf("responseReceived");
    static final AttributeKey<WebClientResponse> RESPONSE = AttributeKey.valueOf("response");
    static final AttributeKey<ConnectionIdent> CONNECTION_IDENT = AttributeKey.valueOf("connectionIdent");
    static final AttributeKey<Long> REQUEST_ID = AttributeKey.valueOf("requestID");

    private static final AtomicLong REQUEST_NUMBER = new AtomicLong(0);
    private static final String DEFAULT_TRANSPORT_PROTOCOL = "http";
    private static final Map<String, Integer> DEFAULT_SUPPORTED_PROTOCOLS = new HashMap<>();

    static {
        DEFAULT_SUPPORTED_PROTOCOLS.put(DEFAULT_TRANSPORT_PROTOCOL, 80);
        DEFAULT_SUPPORTED_PROTOCOLS.put("https", 443);
    }

    private final Map<String, String> properties;
    private final LazyValue<NioEventLoopGroup> eventGroup;
    private final WebClientConfiguration configuration;
    private final Http.RequestMethod method;
    private final WebClientRequestHeaders headers;
    private final WebClientQueryParams queryParams;
    private final MessageBodyReaderContext readerContext;
    private final MessageBodyWriterContext writerContext;

    private URI uri;
    private URI finalUri;
    private Http.Version httpVersion;
    private Context context;
    private Proxy proxy;
    private String fragment;
    private boolean followRedirects;
    private boolean skipUriEncoding;
    private int redirectionCount;
    private RequestConfiguration requestConfiguration;
    private HttpRequest.Path path;
    private List<WebClientService> services;
    private Duration readTimeout;
    private Duration connectTimeout;
    private boolean keepAlive;
    private Long requestId;
    private boolean allowChunkedEncoding;

    private WebClientRequestBuilderImpl(LazyValue<NioEventLoopGroup> eventGroup,
                                        WebClientConfiguration configuration,
                                        Http.RequestMethod method) {
        this.properties = new HashMap<>();
        this.eventGroup = eventGroup;
        this.configuration = configuration;
        this.method = method;
        this.uri = configuration.uri();
        this.skipUriEncoding = false;
        this.allowChunkedEncoding = true;
        this.path = ClientPath.create(null, "", new HashMap<>());
        //Default headers added to the current headers of the request
        this.headers = new WebClientRequestHeadersImpl(this.configuration.headers());
        this.queryParams = new WebClientQueryParams();
        this.httpVersion = Http.Version.V1_1;
        this.redirectionCount = 0;
        this.services = configuration.clientServices();
        this.readerContext = MessageBodyReaderContext.create(configuration.readerContext());
        this.writerContext = MessageBodyWriterContext.create(configuration.writerContext(), headers);
        this.requestId = null;
        Context.Builder contextBuilder = Context.builder().id("webclient-" + requestId);
        configuration.context().ifPresentOrElse(contextBuilder::parent,
                                                () -> Contexts.context().ifPresent(contextBuilder::parent));
        this.context = contextBuilder.build();
        this.followRedirects = configuration.followRedirects();
        this.readTimeout = configuration.readTimout();
        this.connectTimeout = configuration.connectTimeout();
        this.proxy = configuration.proxy().orElse(Proxy.noProxy());
        this.keepAlive = configuration.keepAlive();
    }

    public static WebClientRequestBuilder create(LazyValue<NioEventLoopGroup> eventGroup,
                                                 WebClientConfiguration configuration,
                                                 Http.RequestMethod method) {
        return new WebClientRequestBuilderImpl(eventGroup, configuration, method);
    }

    /**
     * Creates new instance of {@link WebClientRequestBuilder} based on previous request.
     *
     * @param clientRequest previous request
     * @return client request builder
     */
    static WebClientRequestBuilder create(WebClientRequestImpl clientRequest) {
        WebClientRequestBuilderImpl builder = new WebClientRequestBuilderImpl(NettyClient.eventGroup(),
                                                                              clientRequest.configuration(),
                                                                              Http.Method.GET);
        builder.headers(clientRequest.headers());
        builder.queryParams(clientRequest.queryParams());
        builder.uri = clientRequest.uri();
        builder.httpVersion = clientRequest.version();
        builder.proxy = clientRequest.proxy();
        builder.fragment = clientRequest.fragment();
        builder.redirectionCount = clientRequest.redirectionCount() + 1;
        int maxRedirects = builder.configuration.maxRedirects();
        if (builder.redirectionCount > maxRedirects) {
            throw new WebClientException("Max number of redirects extended! (" + maxRedirects + ")");
        }
        return builder;
    }

    private static ChannelFuture obtainChannelFuture(RequestConfiguration configuration,
                                                     Bootstrap bootstrap) {
        ConnectionIdent connectionIdent = new ConnectionIdent(configuration);
        Set<ChannelRecord> channels = CHANNEL_CACHE.computeIfAbsent(connectionIdent,
                                                                    s -> Collections.synchronizedSet(new HashSet<>()));
        synchronized (channels) {
            for (ChannelRecord channelRecord : channels) {
                Channel channel = channelRecord.channel;
                if (channel.isOpen() && channel.attr(IN_USE).get().compareAndSet(false, true)) {
                    LOGGER.finest(() -> "Reusing -> " + channel.hashCode());
                    LOGGER.finest(() -> "Setting in use -> true");
                    return channelRecord.channelFuture;
                }
                LOGGER.finest(() -> "Not accepted -> " + channel.hashCode());
                LOGGER.finest(() -> "Open -> " + channel.isOpen());
                LOGGER.finest(() -> "In use -> " + channel.attr(IN_USE).get());
            }
            LOGGER.finest(() -> "New connection to -> " + connectionIdent);
            URI uri = connectionIdent.base;
            ChannelFuture connect = bootstrap.connect(uri.getHost(), uri.getPort());
            Channel channel = connect.channel();
            channel.attr(IN_USE).set(new AtomicBoolean(true));
            channel.attr(RETURN).set(new AtomicBoolean(false));
            channel.attr(CONNECTION_IDENT).set(connectionIdent);
            channels.add(new ChannelRecord(connect));
            return connect;
        }
    }

    static void removeChannelFromCache(ConnectionIdent key, Channel channel) {
        LOGGER.finest(() -> "Removing from channel cache.");
        LOGGER.finest(() -> "Connection ident -> " + key);
        LOGGER.finest(() -> "Channel -> " + channel.hashCode());
        CHANNEL_CACHE.get(key).remove(new ChannelRecord(channel));
    }

    @Override
    public WebClientRequestBuilder uri(String uri) {
        return uri(URI.create(uri));
    }

    @Override
    public WebClientRequestBuilder uri(URL url) {
        try {
            return uri(url.toURI());
        } catch (URISyntaxException e) {
            throw new WebClientException("Failed to create URI from URL", e);
        }
    }

    @Override
    public WebClientRequestBuilder uri(URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    public WebClientRequestBuilder skipUriEncoding() {
        this.skipUriEncoding = true;
        this.queryParams.skipEncoding();
        return this;
    }

    @Override
    public WebClientRequestBuilder followRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    @Override
    public WebClientRequestBuilder property(String propertyName, String propertyValue) {
        properties.put(propertyName, propertyValue);
        return this;
    }

    @Override
    public WebClientRequestBuilder context(Context context) {
        this.context = context;
        return this;
    }

    @Override
    public WebClientRequestHeaders headers() {
        return headers;
    }

    @Override
    public WebClientRequestBuilder queryParam(String name, String... values) {
        queryParams.add(name, values);
        return this;
    }

    @Override
    public WebClientRequestBuilder proxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    @Override
    public WebClientRequestBuilder headers(Headers headers) {
        this.headers.clear();
        this.headers.putAll(headers);
        return this;
    }

    @Override
    public WebClientRequestBuilder headers(Function<WebClientRequestHeaders, Headers> headers) {
        Headers newHeaders = headers.apply(this.headers);
        if (!newHeaders.equals(this.headers)) {
            headers(newHeaders);
        }
        return this;
    }

    @Override
    public WebClientRequestBuilder queryParams(Parameters queryParams) {
        Objects.requireNonNull(queryParams);
        queryParams.toMap().forEach((name, params) -> queryParam(name, params.toArray(new String[0])));
        return this;
    }

    @Override
    public WebClientRequestBuilder httpVersion(Http.Version httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    @Override
    public WebClientRequestBuilder connectTimeout(long amount, TimeUnit unit) {
        this.connectTimeout = Duration.of(amount, unit.toChronoUnit());
        return this;
    }

    @Override
    public WebClientRequestBuilder readTimeout(long amount, TimeUnit unit) {
        this.readTimeout = Duration.of(amount, unit.toChronoUnit());
        return this;
    }

    @Override
    public WebClientRequestBuilder fragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    @Override
    public WebClientRequestBuilder path(HttpRequest.Path path) {
        this.path = path;
        return this;
    }

    @Override
    public WebClientRequestBuilder path(String path) {
        this.path = ClientPath.create(null, path, new HashMap<>());
        return this;
    }

    @Override
    public WebClientRequestBuilder contentType(MediaType contentType) {
        this.headers.contentType(contentType);
        this.writerContext.contentType(contentType);
        return this;
    }

    @Override
    public WebClientRequestBuilder accept(MediaType... mediaTypes) {
        Arrays.stream(mediaTypes).forEach(headers::addAccept);
        return this;
    }

    @Override
    public WebClientRequestBuilder keepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    @Override
    public WebClientRequestBuilder requestId(long requestId) {
        this.requestId = requestId;
        return this;
    }

    @Override
    public WebClientRequestBuilder allowChunkedEncoding(boolean allowChunkedEncoding) {
        this.allowChunkedEncoding = allowChunkedEncoding;
        return this;
    }

    @Override
    public <T> Single<T> request(Class<T> responseType) {
        return request(GenericType.create(responseType));
    }

    @Override
    public <T> Single<T> request(GenericType<T> responseType) {
        return Contexts.runInContext(context, () -> invokeWithEntity(Single.empty(), responseType));
    }

    @Override
    public Single<WebClientResponse> request() {
        return Contexts.runInContext(context, () -> invoke(Single.empty()));
    }

    @Override
    public Single<WebClientResponse> submit() {
        return request();
    }

    @Override
    public <T> Single<T> submit(Flow.Publisher<DataChunk> requestEntity, Class<T> responseType) {
        return Contexts.runInContext(context, () -> invokeWithEntity(requestEntity, GenericType.create(responseType)));
    }

    @Override
    public <T> Single<T> submit(Object requestEntity, Class<T> responseType) {
        GenericType<T> responseGenericType = GenericType.create(responseType);
        Flow.Publisher<DataChunk> dataChunkPublisher = writerContext.marshall(
                Single.just(requestEntity), GenericType.create(requestEntity));
        return Contexts.runInContext(context, () -> invokeWithEntity(dataChunkPublisher, responseGenericType));
    }

    @Override
    public Single<WebClientResponse> submit(Flow.Publisher<DataChunk> requestEntity) {
        return Contexts.runInContext(context, () -> invoke(requestEntity));
    }

    @Override
    public Single<WebClientResponse> submit(Object requestEntity) {
        Flow.Publisher<DataChunk> dataChunkPublisher = writerContext.marshall(
                Single.just(requestEntity), GenericType.create(requestEntity));
        return submit(dataChunkPublisher);
    }

    @Override
    public Single<WebClientResponse> submit(Function<MessageBodyWriterContext, Flow.Publisher<DataChunk>> function) {
        return submit(function.apply(writerContext));
    }

    @Override
    public MessageBodyReaderContext readerContext() {
        return readerContext;
    }

    @Override
    public MessageBodyWriterContext writerContext() {
        return writerContext;
    }

    long requestId() {
        return requestId;
    }

    Http.RequestMethod method() {
        return method;
    }

    Http.Version httpVersion() {
        return httpVersion;
    }

    URI uri() {
        return finalUri;
    }

    Parameters queryParams() {
        return queryParams;
    }

    String query() {
        return finalUri.getRawQuery() == null ? "" : finalUri.getRawQuery();
    }

    String queryFromParams() {
        StringBuilder queries = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : queryParams.pickCorrectParameters().toMap().entrySet()) {
            for (String value : entry.getValue()) {
                if (queries.length() > 0) {
                    queries.append("&");
                }
                if (entry.getKey().isEmpty()) {
                    queries.append(value);
                } else {
                    queries.append(entry.getKey()).append("=").append(value);
                }
            }
        }
        return queries.toString();
    }

    String fragment() {
        return fragment;
    }

    HttpRequest.Path path() {
        return path;
    }

    RequestConfiguration requestConfiguration() {
        return requestConfiguration;
    }

    Map<String, String> properties() {
        return properties;
    }

    Proxy proxy() {
        return proxy;
    }

    int redirectionCount() {
        return redirectionCount;
    }

    Context context() {
        return context;
    }

    private <T> Single<T> invokeWithEntity(Flow.Publisher<DataChunk> requestEntity, GenericType<T> responseType) {
        return invoke(requestEntity)
                .map(this::getContentFromClientResponse)
                .flatMapSingle(content -> content.as(responseType));
    }

    private Single<WebClientResponse> invoke(Flow.Publisher<DataChunk> requestEntity) {
        finalUri = prepareFinalURI();
        if (requestId == null) {
            requestId = REQUEST_NUMBER.incrementAndGet();
        }
        //        LOGGER.finest(() -> "(client reqID: " + requestId + ") Request final URI: " + uri);
        CompletableFuture<WebClientServiceRequest> sent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> responseReceived = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> complete = new CompletableFuture<>();
        Single<WebClientServiceRequest> singleSent = Single.create(sent);
        Single<WebClientServiceResponse> singleResponseReceived = Single.create(responseReceived);
        Single<WebClientServiceResponse> singleComplete = Single.create(complete);
        WebClientServiceRequest completedRequest = new WebClientServiceRequestImpl(this,
                                                                                   singleSent,
                                                                                   singleResponseReceived,
                                                                                   singleComplete);
        CompletionStage<WebClientServiceRequest> rcs = CompletableFuture.completedFuture(completedRequest);

        for (WebClientService service : services) {
            rcs = rcs.thenCompose(service::request)
                    .thenApply(servReq -> {
                        finalUri = recreateURI(servReq);
                        return servReq;
                    });
        }

        return Single.create(rcs.thenCompose(serviceRequest -> {
            //Relative URI is used in request if no proxy set
            URI requestURI = proxy == Proxy.noProxy() ? prepareRelativeURI() : finalUri;
            requestId = serviceRequest.requestId();
            HttpHeaders headers = toNettyHttpHeaders();
            DefaultHttpRequest request = new DefaultHttpRequest(toNettyHttpVersion(httpVersion),
                                                                toNettyMethod(method),
                                                                requestURI.toASCIIString(),
                                                                headers);
            boolean keepAlive = HttpUtil.isKeepAlive(request);

            requestConfiguration = RequestConfiguration.builder(finalUri)
                    .update(configuration)
                    .followRedirects(followRedirects)
                    .clientServiceRequest(serviceRequest)
                    .readerContext(readerContext)
                    .writerContext(writerContext)
                    .connectTimeout(connectTimeout)
                    .readTimeout(readTimeout)
                    .services(services)
                    .context(context)
                    .proxy(proxy)
                    .keepAlive(keepAlive)
                    .requestId(requestId)
                    .build();
            WebClientRequestImpl clientRequest = new WebClientRequestImpl(this);

            CompletableFuture<WebClientResponse> result = new CompletableFuture<>();

            EventLoopGroup group = eventGroup.get();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new NettyClientInitializer(requestConfiguration))
                    .option(ChannelOption.SO_KEEPALIVE, keepAlive)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis());

            ChannelFuture channelFuture = keepAlive
                    ? obtainChannelFuture(requestConfiguration, bootstrap)
                    : bootstrap.connect(finalUri.getHost(), finalUri.getPort());

            channelFuture.addListener((ChannelFutureListener) future -> {
                LOGGER.finest(() -> "(client reqID: " + requestId + ") "
                        + "Channel hashcode -> " + channelFuture.channel().hashCode());
                channelFuture.channel().attr(REQUEST).set(clientRequest);
                channelFuture.channel().attr(RESPONSE_RECEIVED).set(new AtomicBoolean(false));
                channelFuture.channel().attr(RECEIVED).set(responseReceived);
                channelFuture.channel().attr(COMPLETED).set(complete);
                channelFuture.channel().attr(RESULT).set(result);
                channelFuture.channel().attr(REQUEST_ID).set(requestId);
                Throwable cause = future.cause();
                if (null == cause) {
                    RequestContentSubscriber requestContentSubscriber = new RequestContentSubscriber(request,
                                                                                                     channelFuture.channel(),
                                                                                                     result,
                                                                                                     sent,
                                                                                                     allowChunkedEncoding);
                    requestEntity.subscribe(requestContentSubscriber);
                } else {
                    sent.completeExceptionally(cause);
                    responseReceived.completeExceptionally(cause);
                    complete.completeExceptionally(cause);
                    result.completeExceptionally(new WebClientException(finalUri.toString(), cause));
                }
            });
            return result;
        }));
    }

    private MessageBodyReadableContent getContentFromClientResponse(WebClientResponse response) {
        //If the response status is greater then 300, ask user to change requested entity to ClientResponse
        if (response.status().code() >= Http.Status.MOVED_PERMANENTLY_301.code()) {
            throw new WebClientException("Request failed with code " + response.status().code());
        }
        return response.content();
    }

    private URI recreateURI(WebClientServiceRequest request) {
        clearUri(request.schema(), request.host(), request.port());
        return prepareFinalURI();
    }

    private URI prepareFinalURI() {
        if (uri == null) {
            throw new WebClientException("There is no specified uri for the request.");
        } else if (uri.getHost() == null) {
            throw new WebClientException("Invalid uri " + uri + ". Uri.getHost() returned null.");
        }
        String scheme = Optional.ofNullable(uri.getScheme())
                .orElseThrow(() -> new WebClientException("Transport protocol has be to be specified in uri: "
                                                                  + uri.toString()));
        if (!DEFAULT_SUPPORTED_PROTOCOLS.containsKey(scheme)) {
            throw new WebClientException(scheme + " transport protocol is not supported!");
        }
        int port = uri.getPort() > -1 ? uri.getPort() : DEFAULT_SUPPORTED_PROTOCOLS.getOrDefault(scheme, -1);
        if (port == -1) {
            throw new WebClientException("Client could not get port for schema " + scheme + ". "
                                                 + "Please specify correct port to use.");
        }
        String path = resolvePath();
        this.path = ClientPath.create(null, path, new HashMap<>());
        //We need null values for query and fragment if we dont want to have trailing ?# chars
        String query = resolveQuery();
        String fragment = resolveFragment();
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(uri.getHost()).append(":").append(port);
        constructRelativeURI(sb, path, query, fragment);
        clearUri(scheme, uri.getHost(), port);
        return URI.create(sb.toString());
    }

    private void clearUri(String scheme, String host, int port) {
        try {
            this.uri = new URI(scheme,
                               null,
                               host,
                               port,
                               null,
                               null,
                               null);
        } catch (URISyntaxException e) {
            throw new WebClientException("Could not create URI!", e);
        }
    }

    private String resolveFragment() {
        if (fragment == null) {
            fragment(uri.getRawFragment());
        }
        if (skipUriEncoding || fragment == null) {
            return fragment;
        }
        return UriComponentEncoder.encode(fragment, UriComponentEncoder.Type.FRAGMENT);
    }

    private URI prepareRelativeURI() {
        String path = finalUri.getRawPath();
        String fragment = finalUri.getRawFragment();
        String query = finalUri.getRawQuery();
        StringBuilder sb = new StringBuilder();
        constructRelativeURI(sb, path, query, fragment);
        return URI.create(sb.toString());
    }

    private void constructRelativeURI(StringBuilder stringBuilder, String path, String query, String fragment) {
        if (path != null) {
            stringBuilder.append(path);
        }
        if (query != null) {
            stringBuilder.append('?').append(query);
        }
        if (fragment != null) {
            stringBuilder.append('#').append(fragment);
        }
    }

    private String resolveQuery() {
        String queries = queryFromParams();
        String uriQuery = uri.getRawQuery();
        if (queries.isEmpty()) {
            queries = uriQuery;
        } else if (uriQuery != null) {
            queries = uriQuery + "&" + queries;
        }
        if (uriQuery != null) {
            String[] uriQueries = uriQuery.split("&");
            Arrays.stream(uriQueries)
                    .map(s -> s.split("="))
                    .forEach(keyValue -> {
                        if (keyValue.length == 1) {
                            queryParam("", keyValue[0]);
                        } else {
                            queryParam(keyValue[0], keyValue[1]);
                        }
                    });
        }
        return queries;
    }

    private String resolvePath() {
        String uriPath = uri.getRawPath();
        String extendedPath = this.path.toRawString();
        String finalPath;
        if (uriPath.endsWith("/") && extendedPath.startsWith("/")) {
            finalPath = uriPath.substring(0, uriPath.length() - 1) + extendedPath;
        } else if (extendedPath.isEmpty()) {
            finalPath = uriPath;
        } else {
            finalPath = uriPath.endsWith("/") || extendedPath.startsWith("/")
                    ? uriPath + extendedPath
                    : uriPath + "/" + extendedPath;
        }
        if (skipUriEncoding) {
            return finalPath;
        }
        return UriComponentEncoder.encode(finalPath, UriComponentEncoder.Type.PATH);
    }

    private HttpMethod toNettyMethod(Http.RequestMethod method) {
        //This method creates also new netty HttpMethod.
        return HttpMethod.valueOf(method.name());
    }

    private HttpVersion toNettyHttpVersion(Http.Version version) {
        return HttpVersion.valueOf(version.value());
    }

    private HttpHeaders toNettyHttpHeaders() {
        HttpHeaders headers = new DefaultHttpHeaders(this.configuration.validateHeaders());
        try {
            Map<String, List<String>> cookieHeaders = this.configuration.cookieManager().get(finalUri, new HashMap<>());
            List<String> cookies = new ArrayList<>(cookieHeaders.get(Http.Header.COOKIE));
            cookies.addAll(this.headers.values(Http.Header.COOKIE));
            if (!cookies.isEmpty()) {
                headers.add(Http.Header.COOKIE, String.join("; ", cookies));
            }
        } catch (IOException e) {
            throw new WebClientException("An error occurred while setting cookies.", e);
        }
        this.headers.toMap().forEach(headers::add);
        addHeaderIfAbsent(headers, HttpHeaderNames.HOST, finalUri.getHost() + ":" + finalUri.getPort());
        addHeaderIfAbsent(headers, HttpHeaderNames.CONNECTION, keepAlive ? HttpHeaderValues.KEEP_ALIVE : HttpHeaderValues.CLOSE);
        addHeaderIfAbsent(headers, HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        addHeaderIfAbsent(headers, HttpHeaderNames.USER_AGENT, configuration.userAgent());
        return headers;
    }

    private void addHeaderIfAbsent(HttpHeaders headers, AsciiString header, Object headerValue) {
        if (!headers.contains(header)) {
            headers.set(header, headerValue);
        }
    }

    /**
     * {@link HttpRequest.Path} client implementation.
     * Temporal implementation until {@link HttpRequest.Path} has implementation in common.
     */
    private static class ClientPath implements HttpRequest.Path {

        private final String path;
        private final String rawPath;
        private final Map<String, String> params;
        private final ClientPath absolutePath;
        private List<String> segments;

        /**
         * Creates new instance.
         *
         * @param path         actual relative URI path.
         * @param rawPath      actual relative URI path without any decoding.
         * @param params       resolved path parameters.
         * @param absolutePath absolute path.
         */
        ClientPath(String path, String rawPath, Map<String, String> params,
                   ClientPath absolutePath) {

            this.path = path;
            this.rawPath = rawPath;
            this.params = params == null ? Collections.emptyMap() : params;
            this.absolutePath = absolutePath;
        }

        @Override
        public String param(String name) {
            return params.get(name);
        }

        @Override
        public List<String> segments() {
            List<String> result = segments;
            // No synchronisation needed, worth case is multiple splitting.
            if (result == null) {
                StringTokenizer stok = new StringTokenizer(path, "/");
                result = new ArrayList<>();
                while (stok.hasMoreTokens()) {
                    result.add(stok.nextToken());
                }
                this.segments = result;
            }
            return result;
        }

        @Override
        public String toString() {
            return path;
        }

        @Override
        public String toRawString() {
            return rawPath;
        }

        @Override
        public HttpRequest.Path absolute() {
            return absolutePath == null ? this : absolutePath;
        }

        static HttpRequest.Path create(ClientPath contextual, String path,
                                       Map<String, String> params) {

            return create(contextual, path, path, params);
        }

        static HttpRequest.Path create(ClientPath contextual, String path, String rawPath,
                                       Map<String, String> params) {

            if (contextual == null) {
                return new ClientPath(path, rawPath, params, null);
            } else {
                return contextual.createSubpath(path, rawPath, params);
            }
        }

        HttpRequest.Path createSubpath(String path, String rawPath,
                                       Map<String, String> params) {

            if (params == null) {
                params = Collections.emptyMap();
            }
            if (absolutePath == null) {
                HashMap<String, String> map =
                        new HashMap<>(this.params.size() + params.size());
                map.putAll(this.params);
                map.putAll(params);
                return new ClientPath(path, rawPath, params, new ClientPath(this.path, this.rawPath, map, null));
            } else {
                int size = this.params.size() + params.size()
                        + absolutePath.params.size();
                HashMap<String, String> map = new HashMap<>(size);
                map.putAll(absolutePath.params);
                map.putAll(this.params);
                map.putAll(params);
                return new ClientPath(path, rawPath, params, new ClientPath(absolutePath.path, absolutePath.rawPath, map,
                        /* absolute path */ null));
            }
        }
    }

    private static class ChannelRecord {

        private final ChannelFuture channelFuture;
        private final Channel channel;

        private ChannelRecord(ChannelFuture channelFuture) {
            this.channelFuture = channelFuture;
            this.channel = channelFuture.channel();
        }

        private ChannelRecord(Channel channel) {
            this.channelFuture = null;
            this.channel = channel;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ChannelRecord that = (ChannelRecord) o;
            //Intentional comparison without equals
            return channel == that.channel;
        }

        @Override
        public int hashCode() {
            return channel.hashCode();
        }
    }

    static class ConnectionIdent {

        private final URI base;
        private final Duration readTimeout;
        private final Proxy proxy;
        private final WebClientTls tls;

        private ConnectionIdent(RequestConfiguration requestConfiguration) {
            URI uri = requestConfiguration.requestURI();
            this.base = URI.create(uri.getScheme() + "://" + uri.getAuthority());
            this.readTimeout = requestConfiguration.readTimout();
            this.proxy = requestConfiguration.proxy().orElse(null);
            this.tls = requestConfiguration.tls();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConnectionIdent that = (ConnectionIdent) o;
            return Objects.equals(base, that.base)
                    && Objects.equals(readTimeout, that.readTimeout)
                    && Objects.equals(proxy, that.proxy)
                    && Objects.equals(tls, that.tls);
        }

        @Override
        public int hashCode() {
            return Objects.hash(base, readTimeout, proxy, tls);
        }

        @Override
        public String toString() {
            return "ConnectionIdent{"
                    + "base=" + base
                    + ", readTimeout=" + readTimeout
                    + ", proxy=" + proxy
                    + ", tls=" + tls
                    + '}';
        }
    }

}
