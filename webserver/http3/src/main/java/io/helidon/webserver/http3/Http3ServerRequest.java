/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http3;

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.ServerRequestEntity;

final class Http3ServerRequest implements RoutingRequest {
    private static final RequestedUriDiscoveryContext DEFAULT_REQUESTED_URI_DISCOVERY_CONTEXT =
            RequestedUriDiscoveryContext.builder()
                    .build();
    private static final PathMatcher INITIAL_PATH_MATCHER = PathMatchers.any();

    private final ConnectionContext ctx;
    private final ServerRequestHeaders headers;
    private final HttpPrologue originalPrologue;
    private final int requestId;
    private final String authority;
    private final LazyValue<ReadableEntity> entity;
    private final HttpSecurity security;
    private final LazyValue<UriInfo> uriInfo = LazyValue.create(this::createUriInfo);
    private final LimitAlgorithm.Outcome limitOutcome;
    private final Runnable resetHandler;
    private final boolean expectContinue;
    private final ReentrantLock continueLock = new ReentrantLock();

    private HttpPrologue prologue;
    private RoutedPath path;
    private WritableHeaders<?> writable;
    private Context context;
    private boolean continueSent;
    private volatile Runnable continueHandler = () -> { };
    private UnaryOperator<InputStream> streamFilter = UnaryOperator.identity();
    private String matchingPattern;
    private Supplier<Optional<String>> matchingPatternSupplier;

    private Http3ServerRequest(ConnectionContext ctx,
                               HttpSecurity security,
                               HttpPrologue prologue,
                               ServerRequestHeaders headers,
                               String authority,
                               ContentDecoder decoder,
                               RequestMeta metadata) {
        this.ctx = ctx;
        this.security = security;
        this.originalPrologue = prologue;
        this.path = INITIAL_PATH_MATCHER.match(prologue.uriPath()).path();
        this.headers = headers;
        this.authority = authority;
        this.requestId = metadata.requestId();
        this.limitOutcome = metadata.limitOutcome();
        this.resetHandler = metadata.resetHandler();
        boolean hasEntity = metadata.hasEntity();
        this.expectContinue = hasEntity && headers.contains(HeaderValues.EXPECT_100);
        this.continueSent = !expectContinue;

        if (!hasEntity) {
            this.entity = LazyValue.create(ReadableEntityBase.empty());
        } else {
            this.entity = LazyValue.create(() -> ServerRequestEntity.create(this::entityRequested,
                                                                            streamFilter,
                                                                            decoder,
                                                                            metadata.entityReader(),
                                                                            metadata.entityProcessedRunnable(),
                                                                            this.headers,
                                                                            ctx.listenerContext().mediaContext(),
                                                                            metadata.entityLimits().maxPayloadSize(),
                                                                            metadata.entityLimits()
                                                                                    .maxBufferedEntitySize()));
        }
    }

    static Http3ServerRequest create(ConnectionContext ctx,
                                     HttpSecurity security,
                                     HttpPrologue prologue,
                                     ServerRequestHeaders requestHeaders,
                                     String authority,
                                     ContentDecoder decoder,
                                     RequestMeta metadata) {
        return new Http3ServerRequest(ctx,
                                      security,
                                      prologue,
                                      requestHeaders,
                                      authority,
                                      decoder,
                                      metadata);
    }

    @Override
    public void reset() {
        resetHandler.run();
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    public RoutedPath path() {
        return path;
    }

    @Override
    public ReadableEntity content() {
        return entity.get();
    }

    @Override
    public String socketId() {
        return ctx.childSocketId();
    }

    @Override
    public String serverSocketId() {
        return ctx.socketId();
    }

    @Override
    public Context context() {
        if (context == null) {
            context = Contexts.context().orElseGet(() -> Context.builder()
                    .parent(ctx.listenerContext().context())
                    .id("[" + serverSocketId() + " " + socketId() + "] http/3: " + requestId)
                    .build());
            if (limitOutcome != null) {
                context.register(limitOutcome);
            }
        }
        return context;
    }

    @Override
    public ListenerContext listenerContext() {
        return ctx.listenerContext();
    }

    @Override
    public HttpSecurity security() {
        return security;
    }

    @Override
    public HttpPrologue prologue() {
        return prologue == null ? originalPrologue : prologue;
    }

    @Override
    public ServerRequestHeaders headers() {
        return writable == null ? headers : ServerRequestHeaders.create(writable);
    }

    @Override
    public UriQuery query() {
        return prologue().query();
    }

    @Override
    public PeerInfo remotePeer() {
        return ctx.remotePeer();
    }

    @Override
    public PeerInfo localPeer() {
        return ctx.localPeer();
    }

    @Override
    public String authority() {
        return headers().find(HeaderNames.HOST)
                .map(Header::get)
                .orElse(authority);
    }

    @Override
    public void header(Header header) {
        if (writable == null) {
            writable = WritableHeaders.create(headers);
        }
        writable.set(header);
    }

    @Override
    public int id() {
        return requestId;
    }

    @Override
    public Http3ServerRequest path(RoutedPath routedPath) {
        this.path = Objects.requireNonNull(routedPath, "routedPath");
        return this;
    }

    @Override
    public Http3ServerRequest prologue(HttpPrologue newPrologue) {
        this.prologue = Objects.requireNonNull(newPrologue, "newPrologue");
        return this;
    }

    @Override
    public RoutingRequest matchingPattern(String matchingPattern) {
        this.matchingPattern = matchingPattern;
        this.matchingPatternSupplier = null;
        return this;
    }

    @Override
    public RoutingRequest matchingPattern(Supplier<Optional<String>> matchingPattern) {
        Objects.requireNonNull(matchingPattern, "Parameter 'matchingPattern' is null!");
        this.matchingPatternSupplier = LazyValue.create(() -> Objects.requireNonNull(matchingPattern.get(),
                                                                                     "Matching pattern supplier returned null"));
        this.matchingPattern = null;
        return this;
    }

    @Override
    public Optional<String> matchingPattern() {
        Supplier<Optional<String>> matchingPatternSupplier = this.matchingPatternSupplier;
        if (matchingPatternSupplier != null) {
            return matchingPatternSupplier.get();
        }
        return Optional.ofNullable(matchingPattern);
    }

    @Override
    public UriInfo requestedUri() {
        return uriInfo.get();
    }

    @Override
    public boolean continueSent() {
        return continueSent;
    }

    @Override
    public void streamFilter(UnaryOperator<InputStream> filterFunction) {
        Objects.requireNonNull(filterFunction);
        UnaryOperator<InputStream> current = this.streamFilter;
        this.streamFilter = it -> filterFunction.apply(current.apply(it));
    }

    @Override
    public Optional<ProxyProtocolData> proxyProtocolData() {
        return ctx.proxyProtocolData();
    }

    @Override
    public Optional<String> sniRequestedHost() {
        return ctx.sniContext().flatMap(SniContext::presentedHost);
    }

    @Override
    public Optional<String> sniMatchedHost() {
        return ctx.sniContext().flatMap(SniContext::matchedHost);
    }

    void continueHandler(Runnable continueHandler) {
        this.continueHandler = Objects.requireNonNull(continueHandler);
    }

    boolean expectsContinue() {
        return expectContinue;
    }

    private void entityRequested(boolean drain) {
        continueLock.lock();
        try {
            if (drain || !expectContinue || continueSent) {
                return;
            }
            continueHandler.run();
            continueSent = true;
        } finally {
            continueLock.unlock();
        }
    }

    private UriInfo createUriInfo() {
        return ctx.listenerContext().config().requestedUriDiscoveryContext()
                .orElse(DEFAULT_REQUESTED_URI_DISCOVERY_CONTEXT)
                .uriInfo(remotePeer().address(),
                         localPeer().address(),
                         path.absolute().path(),
                         headers(),
                         query(),
                         isSecure());
    }

    record RequestMeta(int requestId,
                       boolean hasEntity,
                       Function<Integer, BufferData> entityReader,
                       Runnable entityProcessedRunnable,
                       Runnable resetHandler,
                       LimitAlgorithm.Outcome limitOutcome,
                       EntityLimits entityLimits) {
    }

    record EntityLimits(long maxPayloadSize, long maxBufferedEntitySize) {
    }
}
