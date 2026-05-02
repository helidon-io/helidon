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

package io.helidon.declarative.tests.http;

import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.RestClient;

@Service.Singleton
@SuppressWarnings("deprecation")
class Optional404ErrorHandler implements RestClient.ErrorHandler {
    private static final String HANDLED_PATH = "/greet/optional/not-found/handled";

    @Override
    public boolean handles(String requestUri,
                           ClientRequestHeaders requestHeaders,
                           Status status,
                           io.helidon.http.ClientResponseHeaders headers) {
        return status == Status.NOT_FOUND_404 && requestUri.endsWith(HANDLED_PATH);
    }

    @Override
    public Optional<? extends RuntimeException> handleError(String requestUri,
                                                            ClientRequestHeaders requestHeaders,
                                                            HttpClientResponse response) {
        return Optional.of(new IllegalStateException("optional 404 handled"));
    }

    @Override
    public Optional<? extends RuntimeException> handleError(String requestUri,
                                                            ClientRequestHeaders requestHeaders,
                                                            ClientResponseTyped<?> typedResponse,
                                                            GenericType<?> type) {
        return Optional.of(new IllegalStateException("optional 404 handled"));
    }
}
