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

package io.helidon.webclient.http2;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Http;
import io.helidon.webclient.api.ClientRequestBase;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http2ClientRequestImpl extends ClientRequestBase<Http2ClientRequest, Http2ClientResponse>
        implements Http2ClientRequest, Http2StreamConfig, FullClientRequest<Http2ClientRequest> {

    private final Http2ClientImpl http2Client;
    private int priority;
    private boolean priorKnowledge;
    private int requestPrefetch = 0;
    private Duration flowControlTimeout = Duration.ofMillis(100);
    private Duration timeout = Duration.ofSeconds(10);

    Http2ClientRequestImpl(Http2ClientImpl http2Client,
                           Http.Method method,
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
    }

    Http2ClientRequestImpl(Http2ClientRequestImpl request,
                           Http.Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        this(request.http2Client, method, clientUri, properties);

        followRedirects(request.followRedirects());
        maxRedirects(request.maxRedirects());
        tls(request.tls());

        this.priority(request.priority);
        this.priorKnowledge(request.priorKnowledge);
        this.flowControlTimeout(request.flowControlTimeout);
        this.requestPrefetch(request.requestPrefetch);
        this.timeout(request.timeout);
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
        if (followRedirects()) {
            return invokeEntityFollowRedirects(entity);
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

    @Override
    public Duration timeout() {
        return timeout;
    }

    // this is currently not used - if it is to be used, it must be per stream configuration, not connection wide
    int requestPrefetch() {
        return requestPrefetch;
    }

    // this is currently not used - if it is to be used, it must be per stream configuration, not connection wide
    Duration flowControlTimeout() {
        return flowControlTimeout;
    }

    private Http2ClientResponse invokeEntityFollowRedirects(Object entity) {
        //Request object which should be used for invoking the next request. This will change in case of any redirection.
        Http2ClientRequestImpl clientRequest = this;
        //Entity to be sent with the request. Will be changed when redirect happens to prevent entity sending.
        Object entityToBeSent = entity;
        for (int i = 0; i < maxRedirects(); i++) {
            Http2ClientResponseImpl clientResponse = clientRequest.invokeEntity(entityToBeSent);
            int code = clientResponse.status().code();
            if (code < 300 || code >= 400) {
                return clientResponse;
            } else if (!clientResponse.headers().contains(Http.HeaderNames.LOCATION)) {
                throw new IllegalStateException("There is no " + Http.HeaderNames.LOCATION + " header present in the response! "
                                                        + "It is not clear where to redirect.");
            }
            String redirectedUri = clientResponse.headers().get(Http.HeaderNames.LOCATION).value();
            URI newUri = URI.create(redirectedUri);
            ClientUri redirectUri = ClientUri.create(newUri);

            if (newUri.getHost() == null) {
                //To keep the information about the latest host, we need to use uri from the last performed request
                //Example:
                //request -> my-test.com -> response redirect -> my-example.com
                //new request -> my-example.com -> response redirect -> /login
                //with using the last request uri host etc, we prevent my-test.com/login from happening
                ClientUri resolvedUri = clientRequest.resolvedUri();
                redirectUri.scheme(resolvedUri.scheme());
                redirectUri.host(resolvedUri.host());
                redirectUri.port(resolvedUri.port());
            }
            //Method and entity is required to be the same as with original request with 307 and 308 requests
            if (clientResponse.status() == Http.Status.TEMPORARY_REDIRECT_307
                    || clientResponse.status() == Http.Status.PERMANENT_REDIRECT_308) {
                clientRequest = new Http2ClientRequestImpl(clientRequest, clientRequest.method(), redirectUri, properties());
            } else {
                //It is possible to change to GET and send no entity with all other redirect codes
                entityToBeSent = BufferData.EMPTY_BYTES; //We do not want to send entity after this redirect
                clientRequest = new Http2ClientRequestImpl(clientRequest, Http.Method.GET, redirectUri, properties());
            }
        }
        throw new IllegalStateException("Maximum number of request redirections ("
                                                + clientConfig().maxRedirects() + ") reached.");
    }

    private Http2ClientResponseImpl invokeEntity(Object entity) {
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

        // if this was an HTTP/1.1 response, do something different (just re-use response)
        return new Http2ClientResponseImpl(callChain.responseStatus(),
                                           callChain.requestHeaders(),
                                           serviceResponse.headers(),
                                           serviceResponse.inputStream().orElse(null),
                                           mediaContext(),
                                           resolvedUri,
                                           complete,
                                           callChain::closeResponse);

    }
}
