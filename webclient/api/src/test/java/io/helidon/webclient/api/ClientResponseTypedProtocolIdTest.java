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

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.ReadableEntity;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ClientResponseTypedProtocolIdTest {
    @Test
    void typedResponseDelegatesProtocolId() {
        var response = new ClientResponseTypedImpl<>(new Http2Response(), String.class);

        assertThat(response.protocolId(), is("h2"));
    }

    private static final class Http2Response implements HttpClientResponse {
        private static final ClientResponseHeaders EMPTY_HEADERS =
                ClientResponseHeaders.create(WritableHeaders.create());

        @Override
        public String protocolId() {
            return "h2";
        }

        @Override
        public Status status() {
            return Status.OK_200;
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
            throw new AssertionError("Entity should not be requested");
        }

        @Override
        public <T> T as(Class<T> type) {
            return type.cast("entity");
        }

        @Override
        public void close() {
        }
    }
}
