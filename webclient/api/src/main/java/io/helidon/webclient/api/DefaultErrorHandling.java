/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HttpException;
import io.helidon.service.registry.Service;

@Service.Singleton
class DefaultErrorHandling implements RestClient.ErrorHandling {
    private final List<RestClient.ErrorHandler> errorHandlers;

    DefaultErrorHandling(List<RestClient.ErrorHandler> errorHandlers) {
        List<RestClient.ErrorHandler> handlers = new ArrayList<>(errorHandlers);
        handlers.add(HttpErrorHandler.INSTANCE);
        this.errorHandlers = List.copyOf(handlers);
    }

    @Override
    public void handle(String uri, ClientRequestHeaders requestHeaders, HttpClientResponse response) {
        for (RestClient.ErrorHandler errorHandler : errorHandlers) {
            if (errorHandler.handles(uri, requestHeaders, response.status(), response.headers())) {
                var maybeException = errorHandler.handleError(uri, requestHeaders, response);
                if (maybeException.isPresent()) {
                    throw maybeException.get();
                }
            }
        }
    }

    @Override
    public void handle(String uri, ClientRequestHeaders requestHeaders, ClientResponseTyped<?> response, Class<?> type) {
        for (RestClient.ErrorHandler errorHandler : errorHandlers) {
            if (errorHandler.handles(uri, requestHeaders, response.status(), response.headers())) {
                var maybeException = errorHandler.handleError(uri, requestHeaders, response, type);
                if (maybeException.isPresent()) {
                    throw maybeException.get();
                }
            }
        }
    }

    private static class HttpErrorHandler implements RestClient.ErrorHandler {
        private static final HttpErrorHandler INSTANCE = new HttpErrorHandler();

        private HttpErrorHandler() {
        }

        @Override
        public Optional<? extends RuntimeException> handleError(String requestUri,
                                                                ClientRequestHeaders requestHeaders,
                                                                HttpClientResponse response) {
            return Optional.of(new HttpException("Failed when invoking a client call to " + requestUri
                                                         + ", status: " + response.status(),
                                                 response.status()));
        }

        @Override
        public Optional<? extends RuntimeException> handleError(String requestUri,
                                                                ClientRequestHeaders requestHeaders,
                                                                ClientResponseTyped<?> response,
                                                                Class<?> type) {
            return Optional.of(new HttpException("Failed when invoking a client call to " + requestUri
                                                         + ", status: " + response.status(),
                                                 response.status()));
        }
    }
}
