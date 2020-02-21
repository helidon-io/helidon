/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

import io.helidon.common.http.Http;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Interceptor of redirection codes.
 */
class RedirectInterceptor implements HttpInterceptor {

    private static final Logger LOGGER = Logger.getLogger(RedirectInterceptor.class.getName());

    @Override
    public void handleInterception(HttpResponse httpResponse,
                                   ClientRequestBuilder.ClientRequest clientRequest,
                                   CompletableFuture<ClientResponse> responseFuture) {
        if (clientRequest.method() != Http.Method.GET) {
            throw new ClientException("Redirecting is currently supported only for GET method.");
        }
        if (httpResponse.headers().contains(Http.Header.LOCATION)) {
            String newUri = httpResponse.headers().get(Http.Header.LOCATION);
            LOGGER.fine(() -> "Redirecting to " + newUri);
            CompletionStage<ClientResponse> redirectResponse = ClientRequestBuilderImpl
                    .create(clientRequest)
                    .uri(newUri)
                    .request(ClientResponse.class);
            redirectResponse.whenComplete((clResponse, throwable) -> {
                if (throwable == null) {
                    responseFuture.complete(clResponse);
                } else {
                    responseFuture.completeExceptionally(throwable);
                }
            });
        } else {
            throw new ClientException("There is no " + Http.Header.LOCATION + " header present in response! "
                                              + "It is not clear where to redirect.");
        }
    }

    @Override
    public boolean continueAfterInterception() {
        return false;
    }

    @Override
    public boolean shouldIntercept(HttpResponseStatus responseStatus, ClientConfiguration configuration) {
        return configuration.followRedirects()
                && responseStatus == HttpResponseStatus.MOVED_PERMANENTLY
                || responseStatus == HttpResponseStatus.FOUND
                || responseStatus == HttpResponseStatus.SEE_OTHER
                || responseStatus == HttpResponseStatus.TEMPORARY_REDIRECT
                || responseStatus == HttpResponseStatus.PERMANENT_REDIRECT;
    }
}
