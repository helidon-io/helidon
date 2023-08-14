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

package io.helidon.nima.webclient.http1;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.Http;
import io.helidon.nima.webclient.api.ClientRequestBase;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;

class Http1ClientRequestImpl extends ClientRequestBase<Http1ClientRequest, Http1ClientResponse> implements Http1ClientRequest {
    private static final System.Logger LOGGER = System.getLogger(Http1ClientRequestImpl.class.getName());

    private final WebClient webClient;
    private final Http1ClientProtocolConfig protocolConfig;

    Http1ClientRequestImpl(WebClient webClient,
                           HttpClientConfig clientConfig,
                           Http1ClientProtocolConfig protocolConfig,
                           Http.Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        super(clientConfig, webClient.cookieManager(), Http1Client.PROTOCOL_ID, method, clientUri, properties);

        this.webClient = webClient;
        this.protocolConfig = protocolConfig;
    }

    //Copy constructor for redirection purposes
    Http1ClientRequestImpl(Http1ClientRequestImpl request,
                           Http.Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        this(request.webClient, request.clientConfig(), request.protocolConfig, method, clientUri, properties);

        followRedirects(request.followRedirects());
        maxRedirects(request.maxRedirects());
        tls(request.tls());
    }

    @Override
    public Http1ClientResponse doSubmit(Object entity) {
        if (followRedirects()) {
            return RedirectionProcessor.invokeWithFollowRedirects(this, entity);
        }
        return invokeRequestWithEntity(entity);
    }

    @Override
    public Http1ClientResponse doOutputStream(OutputStreamHandler streamHandler) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        Http1CallChainBase callChain = new Http1CallOutputStreamChain(webClient,
                                                                      this,
                                                                      clientConfig(),
                                                                      protocolConfig,
                                                                      whenSent,
                                                                      whenComplete,
                                                                      streamHandler);

        return invokeWithServices(callChain, whenSent, whenComplete);
    }

    @Override
    public UpgradeResponse upgrade(String protocol) {
        if (!headers().contains(Http.HeaderNames.UPGRADE)) {
            headers().set(Http.HeaderNames.UPGRADE, protocol);
        }
        Http.Header requestedUpgrade = headers().get(Http.HeaderNames.UPGRADE);
        Http1ClientResponseImpl response;

        if (followRedirects()) {
            response = RedirectionProcessor.invokeWithFollowRedirects(this, BufferData.EMPTY_BYTES);
        } else {
            response = invokeRequestWithEntity(BufferData.EMPTY_BYTES);
        }

        if (response.status() == Http.Status.SWITCHING_PROTOCOLS_101) {
            // yep, this is the response we want
            if (response.headers().contains(requestedUpgrade)) {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    response.connection()
                            .helidonSocket().log(LOGGER,
                                                 System.Logger.Level.TRACE,
                                                 "Upgrading to %s",
                                                 requestedUpgrade);
                }
                // upgrade was a success
                return UpgradeResponse.success(response, response.connection());
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    response.connection().helidonSocket().log(LOGGER,
                                                              System.Logger.Level.TRACE,
                                                              "Upgrade failed. Expected upgrade: {0}, got headers: {1}",
                                                              requestedUpgrade,
                                                              response.headers());
                }
            }
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                response.connection().helidonSocket().log(LOGGER,
                                                          System.Logger.Level.TRACE,
                                                          "Upgrade failed. Tried upgrading to %s, got status: %s",
                                                          requestedUpgrade,
                                                          response.status());
            }
        }

        return UpgradeResponse.failure(response);
    }

    Http1ClientResponseImpl invokeRequestWithEntity(Object entity) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        Http1CallChainBase callChain = new Http1CallEntityChain(webClient,
                                                                this,
                                                                clientConfig(),
                                                                protocolConfig,
                                                                whenSent,
                                                                whenComplete,
                                                                entity);

        return invokeWithServices(callChain, whenSent, whenComplete);
    }

    private Http1ClientResponseImpl invokeWithServices(Http1CallChainBase callChain,
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

        return new Http1ClientResponseImpl(clientConfig(),
                                           serviceResponse.status(),
                                           serviceResponse.serviceRequest().headers(),
                                           serviceResponse.headers(),
                                           callChain.connection(),
                                           serviceResponse.inputStream().orElse(null),
                                           mediaContext(),
                                           clientConfig().mediaTypeParserMode(),
                                           resolvedUri,
                                           complete);
    }

}
