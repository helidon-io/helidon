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
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;

final class Http1FallbackHandler {
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final Function<Http1ClientRequest, Http1ClientResponse> responseFunction;
    private final boolean upgradeFailureResponseAllowed;

    Http1FallbackHandler(CompletableFuture<WebClientServiceRequest> whenSent,
                         Function<Http1ClientRequest, Http1ClientResponse> responseFunction,
                         boolean upgradeFailureResponseAllowed) {
        this.whenSent = whenSent;
        this.responseFunction = responseFunction;
        this.upgradeFailureResponseAllowed = upgradeFailureResponseAllowed;
    }

    Http1ClientResponse apply(Http1ClientRequest request, WebClientServiceRequest serviceRequest) {
        return invoke(request, serviceRequest, () -> responseFunction.apply(request));
    }

    <T> T invoke(Http1ClientRequest request,
                 WebClientServiceRequest serviceRequest,
                 Supplier<T> responseSupplier) {
        Context context = Http1FallbackService.context(serviceRequest, whenSent);
        copyFinalHeaders(request, serviceRequest);
        try {
            return Contexts.runInContext(context, responseSupplier::get);
        } catch (RuntimeException | Error e) {
            whenSent.completeExceptionally(e);
            throw e;
        }
    }

    void completeSent(WebClientServiceRequest serviceRequest) {
        whenSent.complete(serviceRequest);
    }

    void completeSentExceptionally(Throwable throwable) {
        whenSent.completeExceptionally(throwable);
    }

    boolean upgradeFailureResponseAllowed() {
        return upgradeFailureResponseAllowed;
    }

    static void copyFinalHeaders(Http1ClientRequest request, WebClientServiceRequest serviceRequest) {
        request.headers().clear();
        request.headers(serviceRequest.headers());
    }
}
