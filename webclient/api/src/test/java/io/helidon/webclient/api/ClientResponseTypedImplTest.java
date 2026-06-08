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

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.ReadableEntity;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class ClientResponseTypedImplTest {
    private static final GenericType<Optional<String>> OPTIONAL_STRING = new GenericType<Optional<String>>() { };

    @Test
    void optionalNotFoundUsesStatusCodeAndClosesWithoutReadingEntity() {
        TestHttpClientResponse response = new TestHttpClientResponse(Status.create(404, "Missing"));

        var typedResponse = new ClientResponseTypedImpl<>(response, OPTIONAL_STRING);

        assertThat(typedResponse.entity(), is(Optional.empty()));
        assertThat(response.closed(), is(true));
        assertThat(response.entityRequested(), is(false));
        assertThat(response.entityConsumed(), is(false));
    }

    @Test
    void defaultErrorHandlingUsesStatusCodeForOptionalNotFound() {
        DefaultErrorHandling handling = new DefaultErrorHandling(List.of());
        var response = new TestClientResponseTyped(Status.create(404, "Missing"));
        ClientRequestHeaders requestHeaders = ClientRequestHeaders.create(WritableHeaders.create());

        assertDoesNotThrow(() -> handling.handle("/missing", requestHeaders, response, OPTIONAL_STRING));
    }

    @Test
    void eagerDecodeFailureClosesResponseBeforeErrorHandlingThrows() {
        DefaultErrorHandling handling = new DefaultErrorHandling(List.of());
        var response = new TestHttpClientResponse(Status.INTERNAL_SERVER_ERROR_500, new FailingReadableEntity());
        ClientRequestHeaders requestHeaders = ClientRequestHeaders.create(WritableHeaders.create());

        var typedResponse = new ClientResponseTypedImpl<>(response, String.class);

        assertThrows(HttpException.class, () -> handling.handle("/failure", requestHeaders, typedResponse, String.class));
        assertThat(response.closed(), is(true));
    }

    private static class TestHttpClientResponse implements HttpClientResponse {
        private static final ClientResponseHeaders EMPTY_HEADERS =
                ClientResponseHeaders.create(WritableHeaders.create());

        private final Status status;
        private final ReadableEntity entity;
        private boolean entityRequested;
        private boolean closed;

        TestHttpClientResponse(Status status) {
            this(status, new TestReadableEntity());
        }

        TestHttpClientResponse(Status status, ReadableEntity entity) {
            this.status = status;
            this.entity = entity;
        }

        @Override
        public Status status() {
            return status;
        }

        @Override
        public ClientResponseHeaders headers() {
            return EMPTY_HEADERS;
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
            entityRequested = true;
            return entity;
        }

        @Override
        public void close() {
            closed = true;
        }

        boolean closed() {
            return closed;
        }

        boolean entityRequested() {
            return entityRequested;
        }

        boolean entityConsumed() {
            return entity.consumed();
        }
    }

    private static class TestClientResponseTyped implements ClientResponseTyped<Optional<String>> {
        private final Status status;

        TestClientResponseTyped(Status status) {
            this.status = status;
        }

        @Override
        public Status status() {
            return status;
        }

        @Override
        public ClientResponseHeaders headers() {
            return ClientResponseHeaders.create(WritableHeaders.create());
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
        public Optional<String> entity() {
            return Optional.empty();
        }

        @Override
        public void close() {
        }
    }

    private static class TestReadableEntity implements ReadableEntity {
        private boolean consumed;

        @Override
        public InputStream inputStream() {
            return fail("Entity should not be read");
        }

        @Override
        public <T> T as(GenericType<T> type) {
            return fail("Entity should not be read");
        }

        @Override
        public <T> Optional<T> asOptional(GenericType<T> type) {
            return fail("Entity should not be read");
        }

        @Override
        public boolean hasEntity() {
            return true;
        }

        @Override
        public boolean consumed() {
            return consumed;
        }

        @Override
        public ReadableEntity copy(Runnable entityProcessedRunnable) {
            return fail("Entity should not be copied");
        }

        @Override
        public void consume() {
            consumed = true;
        }
    }

    private static class FailingReadableEntity extends TestReadableEntity {
        @Override
        public <T> T as(GenericType<T> type) {
            throw new IllegalStateException("Cannot decode entity");
        }
    }
}
