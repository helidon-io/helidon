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

import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.media.type.ParserMode;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.webclient.api.ClientUri;

class Http2ClientResponseImpl implements Http2ClientResponse {
    private final Http.Status responseStatus;
    private final ClientResponseHeaders responseHeaders;
    private final ClientUri lastEndpointUri;
    private Http2ClientStream stream;

    Http2ClientResponseImpl(Http2Headers headers,
                            Http2Headers http2Headers,
                            Http2ClientStream stream,
                            DataReader reader,
                            MediaContext mediaContext,
                            ParserMode parserMode,
                            ClientUri lastEndpointUri,
                            CompletableFuture<Void> complete) {
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
    public ClientResponseHeaders headers() {
        return responseHeaders;
    }

    @Override
    public ReadableEntity entity() {
        return stream.entity().copy(() -> this.stream = null);
    }

    @Override
    public ClientUri lastEndpointUri() {
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
