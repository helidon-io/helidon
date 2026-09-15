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

import java.util.Objects;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.EntityWriterPreflight;

final class Http2RequestHeaders {
    private Http2RequestHeaders() {
    }

    static ClientRequestHeaders normalizedRequestHeaders(ClientRequestHeaders headers) {
        Objects.requireNonNull(headers, "headers");
        if (!headers.contains(Http2Headers.AUTHORITY_NAME)) {
            return headers;
        }
        ClientRequestHeaders normalized = EntityWriterPreflight.copyOf(headers);
        var authority = normalized.get(Http2Headers.AUTHORITY_NAME);
        if (authority.valueCount() != 1) {
            throw new IllegalArgumentException("Request :authority must contain exactly one value");
        }
        normalized.remove(Http2Headers.AUTHORITY_NAME);
        normalized.set(HeaderValues.create(HeaderNames.HOST,
                                           authority.changing(),
                                           authority.sensitive(),
                                           authority.get()));
        return normalized;
    }
}
