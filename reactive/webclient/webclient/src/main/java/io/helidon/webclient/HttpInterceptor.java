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

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Intercepts and handles responses with specific codes.
 */
interface HttpInterceptor {

    /**
     * Process which should happen in case of interception.
     *
     * @param httpResponse   HTTP response
     * @param clientRequest  HTTP request
     * @param responseFuture completable future of the current response
     */
    void handleInterception(HttpResponse httpResponse,
                            WebClientRequestImpl clientRequest,
                            CompletableFuture<WebClientResponse> responseFuture);

    /**
     * If run of the response processing should continue after interception is handled by interceptor.
     *
     * @return if response should proceed
     */
    boolean continueAfterInterception();

    /**
     * Checks if response status is among intercepted statuses.
     *
     * @param responseStatus status of the response
     * @param configuration  configuration
     * @return if response should be intercepted
     */
    boolean shouldIntercept(HttpResponseStatus responseStatus, WebClientConfiguration configuration);
}
