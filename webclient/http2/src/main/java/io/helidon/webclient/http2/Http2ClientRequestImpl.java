/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Method;
import io.helidon.webclient.api.ClientRequestBase;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http2ClientRequestImpl extends ClientRequestBase<Http2ClientRequest, Http2ClientResponse>
        implements Http2ClientRequest, Http2StreamConfig, FullClientRequest<Http2ClientRequest> {

    private final Http2ClientImpl http2Client;
    private int priority = 16;
    private boolean priorKnowledge;
    private int requestPrefetch = 0;
    private Duration flowControlTimeout = Duration.ofMillis(100);
    private boolean outputStreamRedirect = false;
    private final FullClientRequest<?> delegate;

    Http2ClientRequestImpl(Http2ClientImpl http2Client,
                           FullClientRequest<?> delegate,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        super(http2Client.clientConfig(),
                http2Client.webClient().cookieManager(),
                Http2Client.PROTOCOL_ID,
                method,
                clientUri,
                properties);

        this.http2Client = http2Client;
        Http2ClientProtocolConfig protocolConfig = http2Client.protocolConfig();
        this.priorKnowledge = protocolConfig.priorKnowledge();
        this.delegate = delegate;
    }

    Http2ClientRequestImpl(Http2ClientRequestImpl request,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        this(request.http2Client, request.delegate, method, clientUri, properties);

        followRedirects(request.followRedirects());
        maxRedirects(request.maxRedirects());
        tls(request.tls());

        this.priority(request.priority);
        this.priorKnowledge(request.priorKnowledge);
        this.flowControlTimeout(request.flowControlTimeout);
        this.requestPrefetch(request.requestPrefetch);
        this.readTimeout(request.readTimeout());
        this.outputStreamRedirect(request.outputStreamRedirect);
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
        if (followRedirects()) {
            return RedirectionProcessor.invokeWithFollowRedirects(this, 0, entity);
        }
        return invokeEntity(entity);
    }

    @Override
    public Http2ClientResponse doOutputStream(OutputStreamHandler streamHandler) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        Http2CallChainBase callChain = new Http2CallOutputStreamChain(http2Client,
                                                                      this,
                                                                      whenSent,
                                                                      whenComplete,
                                                                      streamHandler);

        return invokeWithServices(callChain, whenSent, whenComplete);
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

    Http2ClientResponseImpl invokeEntity(Object entity) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        Http2CallChainBase httpCall = new Http2CallEntityChain(http2Client,
                                                               this,
                                                               whenSent,
                                                               whenComplete,
                                                               entity);

        return invokeWithServices(httpCall, whenSent, whenComplete);
    }

    private Http2ClientResponseImpl invokeWithServices(Http2CallChainBase callChain,
                                                       CompletableFuture<WebClientServiceRequest> whenSent,
                                                       CompletableFuture<WebClientServiceResponse> whenComplete) {

        // will create a copy, so we could invoke this method multiple times
        ClientUri resolvedUri = resolvedUri();

        WebClientServiceResponse serviceResponse = invokeServices(callChain, whenSent, whenComplete, resolvedUri);

        CompletableFuture<Void> complete = new CompletableFuture<>();
        complete.thenAccept(ignored -> serviceResponse.whenComplete().complete(serviceResponse))
                .exceptionally(throwable -> {
                    serviceResponse.whenComplete().completeExceptionally(throwable);
                    return null;
                });

        if (delegate != null) {
            ClientRequestHeaders delegateHeaders = delegate.headers();
            this.headers().forEach(delegateHeaders::set);
        }

        // if this was an HTTP/1.1 response, do something different (just re-use response)
        return new Http2ClientResponseImpl(clientConfig(),
                                           serviceResponse.status(),
                                           callChain.requestHeaders(),
                                           serviceResponse.headers(),
                                           serviceResponse.trailers(),
                                           serviceResponse.inputStream().orElse(null),
                                           mediaContext(),
                                           resolvedUri,
                                           serviceResponse.connection(),
                                           complete,
                                           callChain::closeResponse);

    }
}
