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

package io.helidon.webclient.api;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.Api;
import io.helidon.http.ClientResponseTrailers;

/**
 * Support for adapting a fallback client response to the client-service response contract.
 */
@Api.Internal
public final class WebClientServiceResponseSupport {
    private WebClientServiceResponseSupport() {
    }

    /**
     * Adapt a fallback transport response without losing its trailer lifecycle.
     *
     * @param serviceRequest service-finalized request
     * @param whenComplete service response completion
     * @param response fallback transport response
     * @return service response
     */
    public static WebClientServiceResponse create(WebClientServiceRequest serviceRequest,
                                                  CompletableFuture<WebClientServiceResponse> whenComplete,
                                                  HttpClientResponse response) {
        Objects.requireNonNull(serviceRequest, "serviceRequest");
        Objects.requireNonNull(whenComplete, "whenComplete");
        Objects.requireNonNull(response, "response");

        CompletableFuture<ClientResponseTrailers> trailers = new CompletableFuture<>();
        WebClientServiceResponse.Builder builder = WebClientServiceResponse.builder()
                .serviceRequest(serviceRequest)
                .whenComplete(whenComplete)
                .status(response.status())
                .headers(response.headers())
                .trailers(trailers)
                .connection(() -> {
                    trailers.completeExceptionally(
                            new IllegalStateException("Fallback response closed before trailers were read."));
                    response.close();
                });

        if (response.entity().hasEntity()) {
            builder.inputStream(new TrailerInputStream(response.inputStream(), response, trailers));
        } else {
            try {
                response.serviceEntityConsumed();
                trailers.complete(response.trailers());
            } catch (RuntimeException | Error e) {
                trailers.completeExceptionally(e);
            }
        }
        return builder.build();
    }

    private static final class TrailerInputStream extends FilterInputStream {
        private final HttpClientResponse response;
        private final CompletableFuture<ClientResponseTrailers> trailers;
        private final AtomicBoolean finished = new AtomicBoolean();

        private TrailerInputStream(InputStream inputStream,
                                   HttpClientResponse response,
                                   CompletableFuture<ClientResponseTrailers> trailers) {
            super(inputStream);
            this.response = response;
            this.trailers = trailers;
        }

        @Override
        public int read() throws IOException {
            try {
                int result = super.read();
                if (result == -1) {
                    completeTrailers();
                }
                return result;
            } catch (IOException | RuntimeException | Error e) {
                trailers.completeExceptionally(e);
                throw e;
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            try {
                int result = super.read(bytes, offset, length);
                if (result == -1) {
                    completeTrailers();
                }
                return result;
            } catch (IOException | RuntimeException | Error e) {
                trailers.completeExceptionally(e);
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (finished.compareAndSet(false, true)) {
                    trailers.completeExceptionally(
                            new IllegalStateException("Fallback response entity closed before trailers were read."));
                }
            }
        }

        private void completeTrailers() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            try {
                response.serviceEntityConsumed();
                trailers.complete(response.trailers());
            } catch (RuntimeException | Error e) {
                trailers.completeExceptionally(e);
                throw e;
            }
        }
    }
}
