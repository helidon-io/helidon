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
import io.helidon.nima.webclient.spi.WebClientService;

class ClientRequestImpl extends ClientRequestBase<Http1ClientRequest, Http1ClientResponse> implements Http1ClientRequest {

    private final WebClient webClient;
    private final Http1ClientProtocolConfig protocolConfig;

    ClientRequestImpl(WebClient webClient,
                      HttpClientConfig clientConfig,
                      Http1ClientProtocolConfig protocolConfig,
                      Http.Method method,
                      ClientUri clientUri,
                      Map<String, String> properties) {
        super(clientConfig, Http1Client.PROTOCOL_ID, method, clientUri, properties);

        this.webClient = webClient;
        this.protocolConfig = protocolConfig;
    }

    //Copy constructor for redirection purposes
    private ClientRequestImpl(ClientRequestImpl request,
                              Http.Method method,
                              ClientUri clientUri,
                              Map<String, String> properties) {
        this(request.webClient, request.clientConfig(), request.protocolConfig, method, clientUri, properties);

        followRedirects(request.followRedirects());
        maxRedirects(request.maxRedirects());
        tls(request.tls());
        request.connection().ifPresent(this::connection);
    }

    @Override
    public Http1ClientResponse doSubmit(Object entity) {
        if (followRedirects()) {
            return invokeWithFollowRedirectsEntity(entity);
        }
        return invokeRequestWithEntity(entity);
    }

    @Override
    public Http1ClientResponse doOutputStream(OutputStreamHandler streamHandler) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        WebClientService.Chain callChain = new HttpCallOutputStreamChain(webClient,
                                                                         this,
                                                                         clientConfig(),
                                                                         protocolConfig,
                                                                         whenSent,
                                                                         whenComplete,
                                                                         streamHandler);

        return invokeWithServices(callChain, whenSent, whenComplete);
    }

    private ClientResponseImpl invokeWithFollowRedirectsEntity(Object entity) {
        //Request object which should be used for invoking the next request. This will change in case of any redirection.
        ClientRequestImpl clientRequest = this;
        //Entity to be sent with the request. Will be changed when redirect happens to prevent entity sending.
        Object entityToBeSent = entity;
        for (int i = 0; i < maxRedirects(); i++) {
            ClientResponseImpl clientResponse = clientRequest.invokeRequestWithEntity(entityToBeSent);
            int code = clientResponse.status().code();
            if (code < 300 || code >= 400) {
                return clientResponse;
            } else if (!clientResponse.headers().contains(Http.Header.LOCATION)) {
                throw new IllegalStateException("There is no " + Http.Header.LOCATION + " header present in the response! "
                                                        + "It is not clear where to redirect.");
            }
            String redirectedUri = clientResponse.headers().get(Http.Header.LOCATION).value();
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
                clientRequest = new ClientRequestImpl(clientRequest, clientRequest.method(), redirectUri, properties());
            } else {
                //It is possible to change to GET and send no entity with all other redirect codes
                entityToBeSent = BufferData.EMPTY_BYTES; //We do not want to send entity after this redirect
                clientRequest = new ClientRequestImpl(clientRequest, Http.Method.GET, redirectUri, properties());
            }
        }
        throw new IllegalStateException("Maximum number of request redirections ("
                                                + clientConfig().maxRedirects() + ") reached.");
    }

    private ClientResponseImpl invokeRequestWithEntity(Object entity) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        WebClientService.Chain callChain = new HttpCallEntityChain(webClient,
                                                                   this,
                                                                   clientConfig(),
                                                                   protocolConfig,
                                                                   whenSent,
                                                                   whenComplete,
                                                                   entity);

        return invokeWithServices(callChain, whenSent, whenComplete);
    }

    private ClientResponseImpl invokeWithServices(WebClientService.Chain callChain,
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

        return new ClientResponseImpl(serviceResponse.status(),
                                      serviceResponse.serviceRequest().headers(),
                                      serviceResponse.headers(),
                                      serviceResponse.connection(),
                                      serviceResponse.reader(),
                                      mediaContext(),
                                      clientConfig().mediaTypeParserMode(),
                                      resolvedUri,
                                      complete);
    }

}
