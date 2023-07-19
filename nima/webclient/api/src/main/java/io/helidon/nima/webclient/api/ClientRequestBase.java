package io.helidon.nima.webclient.api;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.helidon.common.Version;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.uri.UriEncoding;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.spi.WebClientService;

public abstract class ClientRequestBase<T extends ClientRequest<T>, R extends HttpClientResponse>
        implements FullClientRequest<T> {
    /**
     * Helidon user agent request header.
     */
    public static final Http.HeaderValue USER_AGENT_HEADER = Http.Header.create(Http.Header.USER_AGENT,
                                                                                 "Helidon " + Version.VERSION);
    private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();

    private final Map<String, String> pathParams = new HashMap<>();
    private final HttpClientConfig clientConfig;
    private final String protocolId;
    private final Http.Method method;
    private final ClientUri clientUri;
    private final UriQueryWriteable query;
    private final Map<String, String> properties;
    private final ClientRequestHeaders headers;
    private final String requestId;
    private final MediaContext mediaContext;

    private String uriTemplate;
    private boolean skipUriEncoding;
    private UriFragment fragment = UriFragment.empty();
    private boolean followRedirects;
    private int maxRedirects;
    private Duration readTimeout;
    private Tls tls;
    private Proxy proxy;
    private boolean keepAlive;
    private ClientConnection connection;

    protected ClientRequestBase(HttpClientConfig clientConfig,
                                String protocolId,
                                Http.Method method,
                                ClientUri clientUri,
                                UriQueryWriteable query,
                                Map<String, String> properties) {
        this.clientConfig = clientConfig;
        this.protocolId = protocolId;
        this.method = method;
        this.clientUri = clientUri;
        this.query = query;
        this.properties = new HashMap<>(properties);

        this.headers = clientConfig.defaultRequestHeaders();
        this.readTimeout = clientConfig.socketOptions().readTimeout();
        this.mediaContext = clientConfig.mediaContext();
        this.followRedirects = clientConfig.followRedirects();
        this.maxRedirects = clientConfig.maxRedirects();
        this.tls = clientConfig.tls();
        this.proxy = clientConfig.proxy();
        this.keepAlive = clientConfig.keepAlive();

        this.requestId = nextRequestId(protocolId);
    }

    @Override
    public T tls(Tls tls) {
        this.tls = tls;
        return identity();
    }

    @Override
    public T uri(URI uri) {
        this.uriTemplate = null;
        this.clientUri.resolve(uri, query);
        return identity();
    }

    @Override
    public T uri(String uri) {
        if (uri.indexOf('{') > -1) {
            this.uriTemplate = uri;
        } else {
            if (skipUriEncoding) {
                uri(URI.create(uri));
            } else {
                uri(URI.create(UriEncoding.encodeUri(uri)));
            }
        }

        return identity();
    }

    @Override
    public ClientUri resolvedUri() {
        // we do not want to update our own URI, as this method may be called multiple times
        return resolveUri(ClientUri.create(this.clientUri), UriQueryWriteable.create());
    }

    @Override
    public ClientRequestHeaders headers() {
        return headers;
    }

    @Override
    public T header(Http.HeaderValue header) {
        this.headers.set(header);
        return identity();
    }

    @Override
    public T headers(Headers headers) {
        for (Http.HeaderValue header : headers) {
            this.headers.add(header);
        }
        return identity();
    }

    @Override
    public T headers(Consumer<ClientRequestHeaders> headersConsumer) {
        headersConsumer.accept(headers);
        return identity();
    }

    @Override
    public T fragment(UriFragment fragment) {
        this.fragment = fragment;
        return identity();
    }

    @Override
    public T skipUriEncoding(boolean skip) {
        this.skipUriEncoding = skip;
        this.clientUri.skipUriEncoding(skip);
        return identity();
    }

    @Override
    public T queryParam(String name, String... values) {
        query.set(name, values);
        return identity();
    }

    @Override
    public T property(String propertyName, String propertyValue) {
        this.properties.put(propertyName, propertyValue);
        return identity();
    }

    @Override
    public T pathParam(String name, String value) {
        pathParams.put(name, value);
        return identity();
    }

    @Override
    public T followRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return identity();
    }

    @Override
    public T maxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return identity();
    }

    @Override
    public T connection(ClientConnection connection) {
        this.connection = connection;
        return identity();
    }

    @Override
    public T keepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return identity();
    }

    /**
     * Read timeout for this request.
     *
     * @param readTimeout response read timeout
     * @return updated client request
     */
    public T readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return identity();
    }

    @Override
    public T proxy(Proxy proxy) {
        this.proxy = Objects.requireNonNull(proxy);
        return identity();
    }

    @Override
    public R request() {
        headers.setIfAbsent(USER_AGENT_HEADER);
        return doSubmit(BufferData.EMPTY_BYTES);
    }

    @Override
    public final R submit(Object entity) {
        if (!(entity instanceof byte[] bytes && bytes.length == 0)) {
            rejectHeadWithEntity();
        }
        headers.setIfAbsent(USER_AGENT_HEADER);
        return doSubmit(entity);
    }

    @Override
    public final R outputStream(OutputStreamHandler outputStreamConsumer) {
        rejectHeadWithEntity();
        headers.setIfAbsent(USER_AGENT_HEADER);
        return doOutputStream(outputStreamConsumer);
    }

    /**
     * HTTP method to be invoked.
     *
     * @return HTTP method
     */
    @Override
    public Http.Method method() {
        return method;
    }

    /**
     * Properties configured by a user or by other components.
     *
     * @return properties
     */
    @Override
    public Map<String, String> properties() {
        return properties;
    }

    @Override
    public boolean followRedirects() {
        return followRedirects;
    }

    @Override
    public int maxRedirects() {
        return maxRedirects;
    }

    @Override
    public Tls tls() {
        return tls;
    }

    @Override
    public Proxy proxy() {
        return proxy;
    }

    @Override
    public Optional<ClientConnection> connection() {
        return Optional.ofNullable(connection);
    }

    @Override
    public Map<String, String> pathParams() {
        return pathParams;
    }

    @Override
    public UriQueryWriteable query() {
        return query;
    }

    @Override
    public UriFragment fragment() {
        return fragment;
    }

    @Override
    public String requestId() {
        return requestId;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public boolean keepAlive() {
        return keepAlive;
    }

    protected abstract R doSubmit(Object entity);

    protected abstract R doOutputStream(OutputStreamHandler outputStreamHandler);

    /**
     * Invoke configured client services.
     *
     * @param whenSent      completable future to be completed when the request is sent over the network
     * @param whenComplete  completable future to be completed when the request/response interaction finishes
     * @param httpCallChain invocation of the HTTP request (the actual network call)
     * @return web client service response
     */
    protected WebClientServiceResponse invokeServices(WebClientService.Chain httpCallChain,
                                                      CompletableFuture<WebClientServiceRequest> whenSent,
                                                      CompletableFuture<WebClientServiceResponse> whenComplete,
                                                      ClientUri usedUri) {

        WebClientServiceRequest serviceRequest = new ServiceRequestImpl(usedUri,
                                                                        method,
                                                                        protocolId,
                                                                        query,
                                                                        fragment,
                                                                        headers,
                                                                        Contexts.context().orElseGet(Context::create),
                                                                        requestId,
                                                                        whenComplete,
                                                                        whenSent,
                                                                        properties);

        WebClientService.Chain last = httpCallChain;

        List<WebClientService> services = clientConfig.services();
        ListIterator<WebClientService> serviceIterator = services.listIterator(services.size());
        while (serviceIterator.hasPrevious()) {
            last = new ServiceChainImpl(last, serviceIterator.previous());
        }

        return last.proceed(serviceRequest);
    }

    /**
     * Associated client configuration.
     *
     * @return client config
     */
    protected HttpClientConfig clientConfig() {
        return clientConfig;
    }

    /**
     * Media context configured for this request.
     *
     * @return media context
     */
    protected MediaContext mediaContext() {
        return mediaContext;
    }

    /**
     * Resolve possible templated URI definition against the provided {@link io.helidon.nima.webclient.api.ClientUri},
     * extracting possible query information into the provided writable query.
     *
     * @param toResolve         client uri to update from the template
     * @param uriQueryWriteable query to extract values into
     * @return updated client uri
     */
    protected ClientUri resolveUri(ClientUri toResolve, UriQueryWriteable uriQueryWriteable) {
        if (uriTemplate != null) {
            String resolved = resolvePathParams(uriTemplate);
            if (skipUriEncoding) {
                toResolve.resolve(URI.create(resolved), uriQueryWriteable);
            } else {
                toResolve.resolve(URI.create(UriEncoding.encodeUri(resolved)), uriQueryWriteable);
            }
        }
        return toResolve;
    }

    private static String nextRequestId(String protocolId) {
        AtomicLong counter = COUNTERS.computeIfAbsent(protocolId, it -> new AtomicLong());
        return "client-" + protocolId + "-" + Long.toHexString(counter.getAndIncrement());
    }

    private String resolvePathParams(String path) {
        String result = path;
        for (Map.Entry<String, String> entry : pathParams.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();

            result = result.replace("{" + name + "}", value);
        }

        if (result.contains("{")) {
            throw new IllegalArgumentException("Not all path parameters are defined. Template after resolving parameters: "
                                                       + result);
        }

        return result;
    }

    private void rejectHeadWithEntity() {
        if (this.method.equals(Http.Method.HEAD)) {
            throw new IllegalArgumentException("Payload in method '" + Http.Method.HEAD + "' has no defined semantics");
        }
    }

    @SuppressWarnings("unchecked")
    private T identity() {
        return (T) this;
    }
}
