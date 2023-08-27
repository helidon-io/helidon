/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;

import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.common.configurable.AllowList;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Forwarded;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.http.UriComponent;
import io.helidon.common.http.UriInfo;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyContext;
import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;

/**
 * The basic abstract implementation of {@link ServerRequest}.
 */
abstract class Request implements ServerRequest {

    private static final String TRACING_CONTENT_READ_NAME = "content-read";

    /**
     * The default charset to use in case that no charset or no mime-type is
     * defined in the content type header.
     */
    static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private final BareRequest bareRequest;
    private final WebServer webServer;
    private final Context context;
    private final Parameters queryParams;
    private final HashRequestHeaders headers;
    private final MessageBodyReadableContent content;
    private final MessageBodyEventListener eventListener;
    private final LazyValue<UriInfo> requestedUri;

    /**
     * Creates new instance.
     *
     * @param req bare request from HTTP SPI implementation.
     * @param webServer relevant server.
     */
    Request(BareRequest req, WebServer webServer, HashRequestHeaders headers) {
        this.bareRequest = req;
        this.webServer = webServer;
        this.headers = headers;
        this.context = Contexts.context().orElseGet(() -> Context.create(webServer.context()));
        this.queryParams = UriComponent.decodeQuery(req.uri().getRawQuery(), true);
        this.eventListener = new MessageBodyEventListener();
        MessageBodyReaderContext readerContext = MessageBodyReaderContext
                .create(webServer.readerContext(), eventListener, headers, headers.contentType());
        this.content = MessageBodyReadableContent.create(req.bodyPublisher(), readerContext);
        this.requestedUri = LazyValue.create(this::createRequestedUri);
    }

    /**
     * Creates clone of existing instance.
     *
     * @param request a request to clone.
     */
    Request(Request request) {
        this.bareRequest = request.bareRequest;
        this.webServer = request.webServer;
        this.context = request.context;
        this.queryParams = request.queryParams;
        this.headers = request.headers;
        this.content = request.content;
        this.eventListener = request.eventListener;
        this.requestedUri = LazyValue.create(this::createRequestedUri);
    }

    /**
     * Obtain the charset from the request.
     *
     * @param request the request to extract the charset from
     * @return the charset or {@link #DEFAULT_CHARSET} if none found
     */
    static Charset contentCharset(ServerRequest request) {
        return request.headers()
                .contentType()
                .flatMap(MediaType::charset)
                .map(Charset::forName)
                .orElse(DEFAULT_CHARSET);
    }

    @Override
    public WebServer webServer() {
        return webServer;
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public Http.RequestMethod method() {
        return bareRequest.method();
    }

    @Override
    public Http.Version version() {
        return bareRequest.version();
    }

    @Override
    public URI uri() {
        return bareRequest.uri();
    }

    @Override
    public String query() {
        return bareRequest.uri().getRawQuery();
    }

    @Override
    public Parameters queryParams() {
        return queryParams;
    }

    @Override
    public String fragment() {
        return bareRequest.uri().getFragment();
    }

    @Override
    public String localAddress() {
        return bareRequest.localAddress();
    }

    @Override
    public int localPort() {
        return bareRequest.localPort();
    }

    @Override
    public String remoteAddress() {
        return bareRequest.remoteAddress();
    }

    @Override
    public int remotePort() {
        return bareRequest.remotePort();
    }

    @Override
    public boolean isSecure() {
        return bareRequest.isSecure();
    }

    @Override
    public RequestHeaders headers() {
        return headers;
    }

    @Override
    public MessageBodyReadableContent content() {
        return this.content;
    }

    @Override
    public long requestId() {
        return bareRequest.requestId();
    }

    @Override
    public Single<Void> closeConnection() {
        return this.bareRequest.closeConnection();
    }

    @Override
    public UriInfo requestedUri() {
        return requestedUri.get();
    }

    // this method is called max once per request
    private UriInfo createRequestedUri() {
        RequestHeaders headers = headers();
        String scheme = null;
        String authority = null;
        String host = null;
        int port = -1;
        String path = null;
        String query = query();

        // Note: requestedUriDiscoveryEnabled() returns true if discovery is explicitly enabled or if either
        // requestedUriDiscoveryTypes or trustedProxies is set.
        if (bareRequest.socketConfiguration().requestedUriDiscoveryEnabled()) {
            AllowList trustedProxies = bareRequest.socketConfiguration().trustedProxies();
            if (trustedProxies.test(hostPart(remoteAddress()))) {
                // Once we discover trusted information using one of the discovery types, we do not mix in
                // information from other types.

                nextDiscoveryType:
                for (var type : bareRequest.socketConfiguration().requestedUriDiscoveryTypes()) {
                    switch (type) {
                    case FORWARDED -> {
                        ForwardedDiscovery discovery = discoverUsingForwarded(headers(), trustedProxies);
                        if (discovery != null) {
                            authority = discovery.authority();
                            scheme = discovery.scheme();

                            break nextDiscoveryType;
                        }
                    }
                    case X_FORWARDED -> {
                        XForwardedDiscovery discovery = discoverUsingXForwarded(headers, trustedProxies);
                        if (discovery != null) {
                            scheme = discovery.scheme();
                            host = discovery.host();
                            port = discovery.port();
                            path = discovery.path();

                            break nextDiscoveryType;
                        }
                    }
                    default -> {
                        authority = headers.first(Http.Header.HOST).orElse(null);

                        break nextDiscoveryType;
                    }
                    }
                }
            }
        }

        // now we must fill values that were not discovered (to have a valid URI information)
        if (host == null && authority == null) {
            authority = headers.first(Http.Header.HOST).orElse(null);
        }

        if (path == null) {
            path = path().absolute().toString();
        }

        if (host == null && authority != null) {
            Authority a;
            if (scheme == null) {
                a = Authority.create(authority);
            } else {
                a = Authority.create(scheme, authority);
            }
            if (a.host() != null) {
                host = a.host();
            }
            if (port == -1) {
                port = a.port();
            }
        }

        /*
        Discover final values to be used
         */

        if (scheme == null) {
            if (port == 80) {
                scheme = "http";
            } else if (port == 443) {
                scheme = "https";
            } else {
                scheme = isSecure() ? "https" : "http";
            }
        }

        if (host == null) {
            host = localAddress();
        }

        // we may still have -1, if port was not explicitly defined by a header - use default port of protocol
        if (port == -1) {
            if ("https".equals(scheme)) {
                port = 443;
            } else {
                port = 80;
            }
        }
        if (query == null || query.isEmpty()) {
            query = null;
        }
        return new UriInfo(scheme, host, port, path, Optional.ofNullable(query));
    }

    private ForwardedDiscovery discoverUsingForwarded(RequestHeaders headers, AllowList trustedProxies) {
        String scheme = null;
        String authority = null;
        List<Forwarded> forwardedList = Forwarded.create(headers);
        if (!forwardedList.isEmpty()) {
            for (int i = forwardedList.size() - 1; i >= 0; i--) {
                Forwarded f = forwardedList.get(i);

                // Because we remained in the loop, the Forwarded entry we are looking at is trustworthy.
                if (scheme == null && f.proto().isPresent()) {
                    scheme = f.proto().get();
                }
                if (authority == null && f.host().isPresent()) {
                    authority = f.host().get();
                }
                if (f.forClient().isPresent() && !trustedProxies.test(f.forClient().get())
                        || scheme != null && authority != null) {
                    // This is the first Forwarded entry we've found for which the "for" value is untrusted (and
                    // therefore the proxy which created this Forwarded entry is the most remote trusted one)
                    //   OR
                    // we have already harvested the values we need from trusted proxies.
                    // Either way, we do not need to look at further Forwarded entries.
                    break;
                }
            }
        }
        return authority != null ? new ForwardedDiscovery(authority, scheme) : null;
    }

    private XForwardedDiscovery discoverUsingXForwarded(RequestHeaders headers, AllowList trustedProxies) {
        // With X-Forwarded-* headers, the X-Forwarded-Host and X-Forwarded-Proto headers appear only once, indicating
        // the host and protocol supposedly requested by the original client as seen by the proxy which received the
        // original request. To trust those single values, we need to trust all the X-Forwarded-For instances except
        // the very first one (the original client itself).
        boolean discovered = false;
        String scheme = null;
        String host = null;
        int port = -1;
        String path = null;

        List<String> xForwardedFors = headers.all(Http.Header.X_FORWARDED_FOR);
        boolean areProxiesTrusted = true;
        if (xForwardedFors.size() > 0) {
            // Intentionally skip the first X-Forwarded-For value. That is the originating client, and as such it
            // is not a proxy and we do not need to check its trustworthiness.
            for (int i = 1; i < xForwardedFors.size(); i++) {
                areProxiesTrusted &= trustedProxies.test(xForwardedFors.get(i));
            }
        }
        if (areProxiesTrusted) {
            scheme = headers.first(Http.Header.X_FORWARDED_PROTO).orElse(null);
            host = headers.first(Http.Header.X_FORWARDED_HOST).orElse(null);
            port = headers.first(Http.Header.X_FORWARDED_PORT).map(Integer::parseInt).orElse(-1);
            path = headers.first(Http.Header.X_FORWARDED_PREFIX)
                    .map(prefix -> {
                        String absolute = path().absolute().toString();
                        return prefix + (absolute.startsWith("/") ? "" : "/") + absolute;
                    })
                    .orElse(null);
            // at least one header was present
            discovered = scheme != null || host != null || port != -1 || path != null;
        }
        return discovered ? new XForwardedDiscovery(scheme, host, port, path) : null;
    }

    private record Authority(String host, int port) {
        static Authority create(String hostHeader) {
            // this may be an IPv6 address, such as [::1]:port, or [::1]
            int colon = hostHeader.lastIndexOf(':');
            int closingBrackets = hostHeader.lastIndexOf(']');
            if (colon > closingBrackets) {
                // there is a port
                String hostString = hostHeader.substring(0, colon);
                String portString = hostHeader.substring(colon + 1);
                return new Authority(hostString, Integer.parseInt(portString));
            }
            // there is no port
            return new Authority(hostHeader, -1);
        }
        static Authority create(String scheme, String hostHeader) {
            int colon = hostHeader.indexOf(':');
            if (colon == -1) {
                // define port by protocol
                return new Authority(hostHeader, "https".equals(scheme) ? 443 : 80);
            }
            String hostString = hostHeader.substring(0, colon);
            String portString = hostHeader.substring(colon + 1);
            return new Authority(hostString, Integer.parseInt(portString));
        }
    }

    private record ForwardedDiscovery(String authority, String scheme) {}
    private record XForwardedDiscovery(String scheme, String host, int port, String path) {}

    private static String hostPart(String address) {
        int colon = address.indexOf(':');
        return colon == -1 ? address : address.substring(0, colon);
    }

    private final class MessageBodyEventListener implements MessageBodyContext.EventListener {

        private Span readSpan;

        private Span createReadSpan(GenericType<?> type) {
            // only create this span if we have a parent span
            Optional<SpanContext> parentSpan = spanContext();
            if (parentSpan.isEmpty()) {
                return null;
            }

            SpanTracingConfig spanConfig = TracingConfigUtil
                    .spanConfig(NettyWebServer.TRACING_COMPONENT,
                                TRACING_CONTENT_READ_NAME,
                                context());

            String spanName = spanConfig.newName().orElse(TRACING_CONTENT_READ_NAME);

            if (spanConfig.enabled()) {
                // only create a real span if enabled
                Span.Builder spanBuilder = tracer().spanBuilder(spanName);
                spanBuilder.parent(parentSpan.get());

                if (type != null) {
                    spanBuilder.tag("requested.type", type.getTypeName());
                }
                return spanBuilder.start();
            } else {
                return null;
            }
        }

        @Override
        public void onEvent(MessageBodyContext.Event event) {
            switch (event.eventType()) {
            case BEFORE_ONSUBSCRIBE:
                GenericType<?> type = event.entityType().orElse(null);
                readSpan = createReadSpan(type);
                break;

            case AFTER_ONERROR:
                if (readSpan != null) {
                    readSpan.status(Span.Status.ERROR);
                    Throwable ex = event.asErrorEvent().error();
                    readSpan.end(ex);
                }
                break;
            case AFTER_ONCOMPLETE:
                if (readSpan != null) {
                    readSpan.end();
                }
                break;
            default:
                // do nothing
            }
        }
    }

    /**
     * {@link ServerRequest.Path} implementation.
     */
    static class Path implements ServerRequest.Path {

        private final String path;
        private final String rawPath;
        private final Map<String, String> params;
        private final Path absolutePath;
        private List<String> segments;

        /**
         * Creates new instance.
         *
         * @param path actual relative URI path.
         * @param rawPath actual relative URI path without any decoding.
         * @param params resolved path parameters.
         * @param absolutePath absolute path.
         */
        Path(String path, String rawPath, Map<String, String> params, Path absolutePath) {
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
            if (result == null) { // No synchronisation needed, worth case is multiple splitting.
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
        public Path absolute() {
            return absolutePath == null ? this : absolutePath;
        }

        static Path create(Path contextual, String path, Map<String, String> params) {
            return create(contextual, path, path, params);
        }

        static Path create(Path contextual, String path, String rawPath, Map<String, String> params) {
            if (contextual == null) {
                return new Path(path, rawPath, params, null);
            } else {
                return contextual.createSubpath(path, rawPath, params);
            }
        }

        Path createSubpath(String path, String rawPath, Map<String, String> params) {
            if (params == null) {
                params = Collections.emptyMap();
            }
            if (absolutePath == null) {
                HashMap<String, String> map = new HashMap<>(this.params.size() + params.size());
                map.putAll(this.params);
                map.putAll(params);
                return new Path(path, rawPath, params, new Path(this.path, this.rawPath, map, null));
            } else {
                int size = this.params.size() + params.size()
                        + absolutePath.params.size();
                HashMap<String, String> map = new HashMap<>(size);
                map.putAll(absolutePath.params);
                map.putAll(this.params);
                map.putAll(params);
                return new Path(path, rawPath, params, new Path(absolutePath.path, absolutePath.rawPath, map, null));
            }
        }
    }
}
