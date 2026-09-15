/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.ClientRequestBase;
import io.helidon.webclient.api.ClientRequestOrigin;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.EntityWriterPreflight;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.ProxyRoute;
import io.helidon.webclient.api.RedirectSecurityState;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;

class Http2ClientRequestImpl extends ClientRequestBase<Http2ClientRequest, Http2ClientResponse>
        implements Http2ClientRequest, Http2StreamConfig, FullClientRequest<Http2ClientRequest> {
    private final Http2ClientImpl http2Client;
    private int priority = 16;
    private boolean priorKnowledge;
    private int requestPrefetch = 0;
    private Duration flowControlTimeout = Duration.ofMillis(100);
    private boolean outputStreamRedirect = false;
    private ClientUri finalRequestUri;
    private WebClientServiceRequest finalServiceRequest;
    private CompletableFuture<WebClientServiceRequest> redirectedWhenSent;
    private ClientRequestHeaders redirectHeadersAfterServices;
    private Consumer<WebClientProtocolResponse> handoffProtocolResponseConsumer;
    private boolean redirectRoutingRetained = true;
    private final FullClientRequest<?> delegate;
    private final List<String> tcpProtocolIds;

    Http2ClientRequestImpl(Http2ClientImpl http2Client,
                           FullClientRequest<?> delegate,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        this(http2Client, delegate, method, clientUri, properties, null, defaultTcpProtocolIds());
    }

    Http2ClientRequestImpl(Http2ClientImpl http2Client,
                           FullClientRequest<?> delegate,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties,
                           List<String> tcpProtocolIds) {
        this(http2Client, delegate, method, clientUri, properties, null, tcpProtocolIds);
    }

    private Http2ClientRequestImpl(Http2ClientImpl http2Client,
                                   FullClientRequest<?> delegate,
                                   Method method,
                                   ClientUri clientUri,
                                   Map<String, String> properties,
                                   ClientUri redirectSourceUri,
                                   List<String> tcpProtocolIds) {
        super(http2Client.clientConfig(),
                http2Client.webClient().cookieManager(),
                Http2Client.PROTOCOL_ID,
                method,
                clientUri,
                delegate == null ? null : delegate.sendExpectContinue().orElse(null),
                properties,
                redirectSourceUri);

        this.http2Client = http2Client;
        Http2ClientProtocolConfig protocolConfig = http2Client.protocolConfig();
        this.priorKnowledge = protocolConfig.priorKnowledge();
        this.delegate = delegate;
        this.tcpProtocolIds = List.copyOf(tcpProtocolIds);
        super.redirectSecurityState(delegate == null
                                            ? RedirectSecurityState.initial()
                                            : delegate.redirectSecurityState());
    }

    Http2ClientRequestImpl(Http2ClientRequestImpl request,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties,
                           ClientUri redirectSourceUri,
                           boolean replayingEntity) {
        this(request.http2Client,
             request.delegate,
             method,
             clientUri,
             properties,
             redirectSourceUri,
             request.tcpProtocolIds);

        redirectSecurityState(request.redirectSecurityState().forRedirect(replayingEntity));
        followRedirects(request.followRedirects());
        maxRedirects(request.maxRedirects());
        proxy(request.proxy());
        keepAlive(request.keepAlive());
        tls(request.tls());
        tlsGeneration(request.tlsGeneration());
        request.sni().ifPresent(this::sni);
        boolean retainRouting = canRetainRouting(request.resolvedUri(),
                                                 request.headers(),
                                                 redirectSourceUri,
                                                 clientUri,
                                                 request.redirectSecurityState());
        redirectRoutingRetained = retainRouting;
        headers().clear();
        headers(request.redirectSourceHeaders());
        sanitizeRedirectHeaders(retainRouting);
        if (retainRouting) {
            ClientRequestOrigin targetOrigin = ClientRequestOrigin.create(clientUri,
                                                                          normalizedRequestHeaders(headers()));
            request.address().ifPresent(value -> {
                var inheritedOrigin = request.inheritedAddressOrigin();
                if (inheritedOrigin.isEmpty()) {
                    address(value);
                } else if (inheritedOrigin.get().equals(targetOrigin)) {
                    inheritedAddress(value, inheritedOrigin.get());
                }
            });
            request.selectedProxyRoute().ifPresent(value -> {
                var inheritedOrigin = request.inheritedSelectedProxyRouteOrigin();
                if (inheritedOrigin.isEmpty()) {
                    selectedProxyRoute(value);
                } else if (inheritedOrigin.get().equals(targetOrigin)) {
                    inheritedSelectedProxyRoute(value, inheritedOrigin.get());
                }
            });
        }

        this.priority(request.priority);
        this.priorKnowledge(request.priorKnowledge);
        this.flowControlTimeout(request.flowControlTimeout);
        this.requestPrefetch(request.requestPrefetch);
        this.readTimeout(request.readTimeout());
        this.readContinueTimeout(request.readContinueTimeout());
        request.sendExpectContinue().ifPresent(this::sendExpectContinue);
        this.outputStreamRedirect(request.outputStreamRedirect);
        this.redirectedWhenSent = request.redirectedWhenSent;
        this.handoffProtocolResponseConsumer = request.handoffProtocolResponseConsumer;
    }

    @Override
    public boolean serviceRequestAfterServices(WebClientServiceRequest serviceRequest,
                                               Consumer<WebClientServiceResponse> responseConsumer,
                                               CompletableFuture<WebClientServiceRequest> whenSent,
                                               Consumer<String> protocolConsumer,
                                               boolean dispatchPrepared) {
        if (!super.serviceRequestAfterServices(serviceRequest,
                                               responseConsumer,
                                               whenSent,
                                               protocolConsumer,
                                               dispatchPrepared)) {
            return false;
        }
        handoffProtocolResponseConsumer = null;
        return true;
    }

    @Override
    protected ClientRequestHeaders normalizedRequestHeaders(ClientRequestHeaders headers) {
        return Http2RequestHeaders.normalizedRequestHeaders(headers);
    }

    @Override
    public boolean serviceRequestAfterServices(WebClientServiceRequest serviceRequest,
                                               Consumer<WebClientServiceResponse> responseConsumer,
                                               CompletableFuture<WebClientServiceRequest> whenSent,
                                               Consumer<String> protocolConsumer,
                                               boolean dispatchPrepared,
                                               Consumer<WebClientProtocolResponse> protocolResponseConsumer) {
        if (!super.serviceRequestAfterServices(serviceRequest,
                                               responseConsumer,
                                               whenSent,
                                               protocolConsumer,
                                               dispatchPrepared,
                                               protocolResponseConsumer)) {
            return false;
        }
        handoffProtocolResponseConsumer = protocolResponseConsumer;
        return true;
    }

    @Override
    public void selectedProxyRoute(ProxyRoute proxyRoute) {
        super.selectedProxyRoute(proxyRoute);
        if (delegate != null) {
            delegate.selectedProxyRoute(proxyRoute);
        }
    }

    @Override
    public void clearSelectedProxyRoute() {
        super.clearSelectedProxyRoute();
        if (delegate != null) {
            delegate.clearSelectedProxyRoute();
        }
    }

    @Override
    public Http2ClientRequest priority(int priority) {
        if (priority < 1 || priority > 256) {
            throw new IllegalArgumentException("Priority must be between 1 and 256 (inclusive), but is " + priority);
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
    public Http2ClientRequest flowControlTimeout(Duration timeout) {
        this.flowControlTimeout = timeout;
        return this;
    }

    @Override
    public Http2ClientResponse doSubmit(Object entity) {
        Http2CallEntityChain.RequestEntity requestEntity = Http2CallEntityChain.RequestEntity.create(entity);
        if (followRedirects()) {
            return RedirectionProcessor.invokeWithFollowRedirects(this, 0, requestEntity);
        }
        try {
            return invokeEntity(requestEntity);
        } finally {
            requestEntity.cancelIfUnattached();
        }
    }

    @Override
    public Http2ClientResponse doOutputStream(OutputStreamHandler streamHandler) {
        RedirectSecurityState invocationSecurityState = redirectSecurityState();
        AtomicBoolean handlerClaimed = new AtomicBoolean();
        try {
            return doOutputStream(streamHandler, handlerClaimed, 0);
        } finally {
            redirectSecurityState(invocationSecurityState);
        }
    }

    private Http2ClientResponseImpl doOutputStream(OutputStreamHandler streamHandler,
                                                   AtomicBoolean handlerClaimed,
                                                   int followedRedirects) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        OutputStreamHandler claimedHandler = outputStream -> {
            if (!handlerClaimed.compareAndSet(false, true)) {
                throw new IllegalStateException("HTTP/2 request entity is one-shot and has already been consumed");
            }
            streamHandler.handle(outputStream);
        };
        Http2CallOutputStreamChain callChain = new Http2CallOutputStreamChain(http2Client,
                                                                               this,
                                                                               whenSent,
                                                                               whenComplete,
                                                                               claimedHandler,
                                                                               followedRedirects);

        Http2ClientResponseImpl response = invokeWithServices(callChain, whenSent, whenComplete);
        if (!followRedirects() || !RedirectionProcessor.redirectionStatusCode(response.status())) {
            return response;
        }

        Status redirectStatus = response.status();
        ClientUri sourceUri = response.lastEndpointUri();
        String location;
        int totalFollowedRedirects = callChain.followedRedirects();
        try (response) {
            if (totalFollowedRedirects >= maxRedirects()) {
                throw new IllegalStateException("Maximum number of request redirections ("
                                                        + maxRedirects() + ") reached.");
            }
            RedirectionProcessor.checkRedirectHeaders(response.headers());
            location = response.headers().get(HeaderNames.LOCATION).get();
        }

        ClientUri redirectUri = resolveRedirectUri(sourceUri, location);
        boolean keepsEntity = RedirectionProcessor.keepsMethodAndEntity(response.serviceRequest().method(), redirectStatus);
        boolean requestEntitySent = callChain.requestEntitySent();
        if (keepsEntity && handlerClaimed.get() && requestEntitySent) {
            throw new IllegalStateException("HTTP/2 cannot replay a one-shot request entity after it was sent; "
                                                    + "redirect status was " + redirectStatus.code() + ".");
        }
        Http2ClientRequestImpl redirectRequest = new Http2ClientRequestImpl(this,
                                                                            keepsEntity ? method() : Method.GET,
                                                                            redirectUri,
                                                                            properties(),
                                                                            sourceUri,
                                                                            keepsEntity
                                                                                    && (!handlerClaimed.get()
                                                                                            || requestEntitySent));
        if (!keepsEntity) {
            redirectRequest.discardEntityHeaders();
        }
        if (callChain.rawServiceResponse() == null
                && canRetainRouting(resolvedUri(), headers(), sourceUri, redirectUri, redirectSecurityState())) {
            ClientRequestHeaders targetHeaders = normalizedRequestHeaders(redirectRequest.headers());
            ClientRequestOrigin targetOrigin = ClientRequestOrigin.create(redirectUri, targetHeaders);
            connection().ifPresent(value -> {
                var inheritedOrigin = inheritedConnectionOrigin();
                if (inheritedOrigin.isEmpty()) {
                    redirectRequest.connection(value);
                } else if (inheritedOrigin.get().equals(targetOrigin)) {
                    redirectRequest.inheritedConnection(value, inheritedOrigin.get());
                }
            });
        }
        int nextRedirect = totalFollowedRedirects + 1;
        Http2ClientResponseImpl redirectedResponse;
        if (keepsEntity) {
            if (handlerClaimed.get()) {
                redirectedResponse = RedirectionProcessor.invokeWithFollowRedirects(redirectRequest,
                                                                                     nextRedirect,
                                                                                     BufferData.EMPTY_BYTES);
            } else {
                redirectedResponse = redirectRequest.doOutputStream(streamHandler,
                                                                      handlerClaimed,
                                                                      nextRedirect);
            }
        } else {
            redirectedResponse = RedirectionProcessor.invokeWithFollowRedirects(redirectRequest,
                                                                                 nextRedirect,
                                                                                 BufferData.EMPTY_BYTES);
        }
        redirectSecurityState(redirectedResponse.redirectSecurityState());
        return redirectedResponse;
    }

    @Override
    public boolean priorKnowledge() {
        return priorKnowledge;
    }

    @Override
    public int priority() {
        return priority;
    }

    // this is currently not used - if it is to be used, it must be per stream configuration, not connection wide
    int requestPrefetch() {
        return requestPrefetch;
    }

    // this is currently not used - if it is to be used, it must be per stream configuration, not connection wide
    Duration flowControlTimeout() {
        return flowControlTimeout;
    }

    /**
     * Whether this request is part of output stream redirection
     * Default is {@code false}.
     *
     * @param outputStreamRedirect whether this request is part of output stream redirection
     * @return updated request
     */
    Http2ClientRequestImpl outputStreamRedirect(boolean outputStreamRedirect) {
        this.outputStreamRedirect = outputStreamRedirect;
        return this;
    }

    boolean outputStreamRedirect() {
        return outputStreamRedirect;
    }

    void redirectedWhenSent(CompletableFuture<WebClientServiceRequest> whenSent) {
        this.redirectedWhenSent = whenSent;
    }

    boolean ownsExplicitConnection() {
        return delegate != null && delegate.connection().isEmpty() && connection().isPresent();
    }

    List<String> tcpProtocolIds() {
        return tcpProtocolIds;
    }

    void finalRequestUri(ClientUri uri) {
        finalRequestUri = ClientUri.create(uri);
    }

    ClientUri finalRequestUri() {
        return finalRequestUri == null ? resolvedUri() : finalRequestUri;
    }

    void finalServiceRequest(WebClientServiceRequest serviceRequest) {
        this.finalServiceRequest = serviceRequest;
    }

    WebClientServiceRequest finalServiceRequest() {
        if (finalServiceRequest == null) {
            throw new IllegalStateException("Service request has not been finalized");
        }
        return finalServiceRequest;
    }

    void handoffProtocolResponse(WebClientProtocolResponse response) {
        Consumer<WebClientProtocolResponse> responseConsumer = handoffProtocolResponseConsumer;
        if (responseConsumer != null) {
            responseConsumer.accept(response);
        }
    }

    ClientRequestHeaders redirectSourceHeaders() {
        return redirectHeadersAfterServices == null
                ? EntityWriterPreflight.copyOf(normalizedRequestHeaders(headers()))
                : super.redirectSourceHeaders(redirectHeadersAfterServices);
    }

    void sanitizeRedirectHeaders() {
        sanitizeRedirectHeaders(redirectRoutingRetained);
    }

    void discardEntityHeaders() {
        headers().remove(HeaderNames.CONTENT_TYPE);
        headers().remove(HeaderNames.CONTENT_ENCODING);
        headers().remove(HeaderNames.CONTENT_LANGUAGE);
        headers().remove(HeaderNames.CONTENT_LOCATION);
    }

    private void sanitizeRedirectHeaders(boolean retainRouting) {
        headers().remove(HeaderNames.CONTENT_LENGTH);
        headers().remove(HeaderNames.TRANSFER_ENCODING);
        headers().remove(HeaderNames.EXPECT);
        if (!retainRouting) {
            headers().remove(HeaderNames.HOST);
            headers().remove(Http2Headers.AUTHORITY_NAME);
        }
    }

    private static List<String> defaultTcpProtocolIds() {
        return List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID);
    }

    private static boolean canRetainRouting(ClientUri configuredSourceUri,
                                            ClientRequestHeaders configuredHeaders,
                                            ClientUri sourceUri,
                                            ClientUri targetUri,
                                            RedirectSecurityState securityState) {
        ClientRequestOrigin sourceUriOrigin = ClientRequestOrigin.create(sourceUri);
        ClientRequestHeaders originHeaders = Http2RequestHeaders.normalizedRequestHeaders(configuredHeaders);
        ClientRequestOrigin configuredEffectiveOrigin = ClientRequestOrigin.create(configuredSourceUri,
                                                                                    originHeaders);
        return ClientRequestOrigin.create(configuredSourceUri).equals(sourceUriOrigin)
                && securityState.lastUriOrigin().orElse(sourceUriOrigin).equals(sourceUriOrigin)
                && securityState.lastEffectiveOrigin()
                        .orElse(configuredEffectiveOrigin)
                        .equals(configuredEffectiveOrigin)
                && ClientRequestOrigin.create(targetUri).equals(sourceUriOrigin);
    }

    Http2ClientResponseImpl invokeEntity(Object entity) {
        return invokeEntity(Http2CallEntityChain.RequestEntity.create(entity));
    }

    Http2ClientResponseImpl invokeEntity(Http2CallEntityChain.RequestEntity entity) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        Http2CallChainBase httpCall = new Http2CallEntityChain(http2Client,
                                                               this,
                                                               whenSent,
                                                               whenComplete,
                                                               entity);

        return invokeWithServices(httpCall, whenSent, whenComplete);
    }

    Http2ClientResponseImpl redirectProbe() {
        return (Http2ClientResponseImpl) requestWithoutRouteCleanup();
    }

    private Http2ClientResponseImpl invokeWithServices(Http2CallChainBase callChain,
                                                       CompletableFuture<WebClientServiceRequest> whenSent,
                                                       CompletableFuture<WebClientServiceResponse> whenComplete) {

        if (redirectedWhenSent != null) {
            whenSent.whenComplete((sentRequest, failure) -> {
                if (failure == null) {
                    redirectedWhenSent.complete(sentRequest);
                } else {
                    redirectedWhenSent.completeExceptionally(failure);
                }
            });
        }

        // will create a copy, so we could invoke this method multiple times
        ClientUri resolvedUri = resolvedUri();

        WebClientServiceResponse serviceResponse;
        try {
            serviceResponse = invokeServices(http2Client.webClient(),
                                             callChain,
                                             whenSent,
                                             whenComplete,
                                             resolvedUri,
                                             callChain::prepareRequest);
            redirectHeadersAfterServices = EntityWriterPreflight.copyOf(serviceResponse.serviceRequest().headers());
        } catch (RuntimeException | Error e) {
            if (callChain.rawServiceResponse() != null) {
                try {
                    callChain.closeResponse();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (e != cleanupFailure) {
                        e.addSuppressed(cleanupFailure);
                    }
                }
            }
            whenSent.completeExceptionally(e);
            whenComplete.completeExceptionally(e);
            throw e;
        }
        CompletableFuture<Void> complete = new CompletableFuture<>();
        complete.whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                serviceResponse.whenComplete().complete(serviceResponse);
                whenComplete.complete(serviceResponse);
            } else {
                serviceResponse.whenComplete().completeExceptionally(throwable);
                whenComplete.completeExceptionally(throwable);
            }
        });

        ClientRequestHeaders responseRequestHeaders = finalizedRequestHeaders();
        if (delegate != null) {
            ClientRequestHeaders delegateHeaders = delegate.headers();
            delegateHeaders.remove(HeaderNames.HOST);
            responseRequestHeaders.first(HeaderNames.HOST)
                    .ifPresent(value -> delegateHeaders.set(HeaderValues.create(HeaderNames.HOST, value)));
        }

        WebClientServiceResponse rawServiceResponse = callChain.rawServiceResponse();
        return new Http2ClientResponseImpl(clientConfig(),
                                           callChain.actualProtocolId(),
                                           serviceResponse.status(),
                                           serviceResponse.serviceRequest(),
                                           responseRequestHeaders,
                                           redirectSecurityState(),
                                           serviceResponse.headers(),
                                           serviceResponse.trailers(),
                                           serviceResponse.inputStream().orElse(null),
                                           mediaContext(),
                                           finalizedEndpointUri(),
                                           serviceResponse.connection(),
                                           rawServiceResponse == null
                                                   ? null
                                                   : rawServiceResponse.connection(),
                                           rawServiceResponse == null ? null : callChain.stream(),
                                           complete,
                                           callChain::closeResponse,
                                           http2Client.protocolConfig().maxBufferedEntitySize().toBytes());

    }

}
