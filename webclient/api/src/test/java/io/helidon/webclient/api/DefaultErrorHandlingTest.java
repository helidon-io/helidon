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

import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultErrorHandlingTest {
    private static final String REQUEST_URI = "http://internal-backend.invalid:8181/v1/secret";
    private static final Status RESPONSE_STATUS = Status.create(503, "Custom error");
    private static final String EXPECTED_MESSAGE = "Failed when invoking a client call, status: " + RESPONSE_STATUS;
    private static final ClientRequestHeaders REQUEST_HEADERS = ClientRequestHeaders.create(WritableHeaders.create());
    private static final ClientResponseHeaders RESPONSE_HEADERS = ClientResponseHeaders.create(WritableHeaders.create());

    private final DefaultErrorHandling errorHandling = new DefaultErrorHandling(List.of());
    private final TestResponse response = new TestResponse();

    @Test
    void untypedResponseDoesNotExposeRequestUri() {
        HttpException exception = assertThrows(HttpException.class,
                                               () -> errorHandling.handle(REQUEST_URI,
                                                                          REQUEST_HEADERS,
                                                                          (HttpClientResponse) response));

        assertThat(exception.status(), is(RESPONSE_STATUS));
        assertThat(exception.getMessage(), is(EXPECTED_MESSAGE));
    }

    @Test
    void typedResponseDoesNotExposeRequestUri() {
        HttpException exception = assertThrows(HttpException.class,
                                               () -> errorHandling.handle(REQUEST_URI,
                                                                          REQUEST_HEADERS,
                                                                          (ClientResponseTyped<?>) response,
                                                                          GenericType.STRING));

        assertThat(exception.status(), is(RESPONSE_STATUS));
        assertThat(exception.getMessage(), is(EXPECTED_MESSAGE));
    }

    private static final class TestResponse implements HttpClientResponse, ClientResponseTyped<ReadableEntity> {
        @Override
        public Status status() {
            return RESPONSE_STATUS;
        }

        @Override
        public ClientResponseHeaders headers() {
            return RESPONSE_HEADERS;
        }

        @Override
        public ClientResponseTrailers trailers() {
            return ClientResponseTrailers.create();
        }

        @Override
        public ClientUri lastEndpointUri() {
            return ClientUri.create();
        }

        @Override
        public ReadableEntity entity() {
            return ReadableEntityBase.empty();
        }

        @Override
        public void close() {
        }
    }
}
