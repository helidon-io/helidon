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

package io.helidon.webclient.api.compatibility;

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ClientResponseProtocolIdCompatibilityTest {
    private static final ClientResponseHeaders EMPTY_HEADERS =
            ClientResponseHeaders.create(WritableHeaders.create());

    @Test
    void externalHttpClientResponseImplementationInheritsHttp1Default() {
        HttpClientResponse response = new ExternalHttpClientResponse();

        assertThat(response.protocolId(), is("http/1.1"));
    }

    @Test
    void externalTypedResponseImplementationInheritsHttp1Default() {
        ClientResponseTyped<String> response = new ExternalTypedResponse();

        assertThat(response.protocolId(), is("http/1.1"));
    }

    private static final class ExternalHttpClientResponse implements HttpClientResponse {
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
        public void close() {
        }
    }

    private static final class ExternalTypedResponse implements ClientResponseTyped<String> {
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
        public String entity() {
            return "entity";
        }

        @Override
        public void close() {
        }
    }
}
