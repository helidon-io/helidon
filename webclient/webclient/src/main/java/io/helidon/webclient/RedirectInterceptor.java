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
                                   WebClientRequestImpl clientRequest,
                                   CompletableFuture<WebClientResponse> responseFuture) {
        if (clientRequest.method() != Http.Method.GET) {
            throw new WebClientException("Redirecting is currently supported only for GET method.");
        }
        if (httpResponse.headers().contains(Http.Header.LOCATION)) {
            String newUri = httpResponse.headers().get(Http.Header.LOCATION);
            LOGGER.fine(() -> "Redirecting to " + newUri);
            CompletionStage<WebClientResponse> redirectResponse = WebClientRequestBuilderImpl
                    .create(clientRequest)
                    .uri(newUri)
                    .request();
            redirectResponse.whenComplete((clResponse, throwable) -> {
                if (throwable == null) {
                    responseFuture.complete(clResponse);
                } else {
                    responseFuture.completeExceptionally(throwable);
                }
            });
        } else {
            throw new WebClientException("There is no " + Http.Header.LOCATION + " header present in response! "
                                                 + "It is not clear where to redirect.");
        }
    }

    @Override
    public boolean continueAfterInterception() {
        return false;
    }

    @Override
    public boolean shouldIntercept(HttpResponseStatus responseStatus, WebClientConfiguration configuration) {
        if (!configuration.followRedirects()) {
            return false;
        }
        return responseStatus.equals(HttpResponseStatus.MOVED_PERMANENTLY)
                || responseStatus.equals(HttpResponseStatus.FOUND)
                || responseStatus.equals(HttpResponseStatus.SEE_OTHER)
                || responseStatus.equals(HttpResponseStatus.TEMPORARY_REDIRECT)
                || responseStatus.equals(HttpResponseStatus.PERMANENT_REDIRECT);
    }
}
