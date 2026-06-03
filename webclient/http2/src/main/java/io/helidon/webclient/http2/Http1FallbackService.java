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

package io.helidon.webclient.http2;

import java.util.concurrent.CompletableFuture;

import io.helidon.common.context.Context;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

final class Http1FallbackService implements WebClientService {
    private static final Object CONTEXT_KEY = new Object();

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest clientRequest) {
        clientRequest.context()
                .get(CONTEXT_KEY, FallbackContext.class)
                .ifPresent(fallback -> clientRequest.whenSent()
                        .whenComplete((ignored, throwable) -> {
                            if (throwable == null) {
                                fallback.whenSent().complete(fallback.serviceRequest());
                            } else {
                                fallback.whenSent().completeExceptionally(throwable);
                            }
                        }));

        return chain.proceed(clientRequest);
    }

    static Context context(WebClientServiceRequest serviceRequest,
                           CompletableFuture<WebClientServiceRequest> whenSent) {
        Context context = Context.create(serviceRequest.context());
        context.register(CONTEXT_KEY, new FallbackContext(serviceRequest, whenSent));
        return context;
    }

    private record FallbackContext(WebClientServiceRequest serviceRequest,
                                   CompletableFuture<WebClientServiceRequest> whenSent) {
    }
}
