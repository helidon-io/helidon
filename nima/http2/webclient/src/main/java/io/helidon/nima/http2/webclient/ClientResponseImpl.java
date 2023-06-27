/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http2.webclient;

import java.net.URI;

import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.http2.Http2Headers;

class ClientResponseImpl implements Http2ClientResponse {
    private final Http.Status responseStatus;
    private final ClientResponseHeaders responseHeaders;
    private final URI lastEndpointUri;
    private Http2ClientStream stream;

    ClientResponseImpl(Http2Headers headers, Http2ClientStream stream, URI lastEndpointUri) {
        this.responseStatus = headers.status();
        this.responseHeaders = ClientResponseHeaders.create(headers.httpHeaders());
        this.stream = stream;
        this.lastEndpointUri = lastEndpointUri;
    }

    @Override
    public Http.Status status() {
        return responseStatus;
    }

    @Override
    public Headers headers() {
        return responseHeaders;
    }

    @Override
    public ReadableEntity entity() {
        return stream.entity().copy(() -> this.stream = null);
    }

    @Override
    public URI lastEndpointUri() {
        return lastEndpointUri;
    }

    @Override
    public void close() {
        if (stream != null) {
            stream.cancel();
            stream = null;
        }
    }
}
