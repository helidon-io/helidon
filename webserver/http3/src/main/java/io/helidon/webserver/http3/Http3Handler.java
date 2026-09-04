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

package io.helidon.webserver.http3;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
/**
 * Internal entry point for HTTP/3 request handling.
 */
@FunctionalInterface
interface Http3Handler {
    /**
     * Handle an HTTP/3 request.
     *
     * @param stream HTTP/3 server stream owner
     * @return response to encode as a complete HTTP/3 message, or {@link Optional#empty()} if the handler already
     *         responded directly
     */
    Optional<BufferedResponse> handle(Http3ServerStream stream);

    /**
     * Buffered HTTP/3 response returned by internal raw handlers.
     *
     * @param status response status
     * @param headers response headers
     * @param body response body
     */
    record BufferedResponse(int status, Headers headers, byte[] body) {
        public BufferedResponse {
            headers = WritableHeaders.create(Objects.requireNonNull(headers, "headers"));
            body = body == null ? BufferData.EMPTY_BYTES : body.clone();
        }

        static BufferedResponse create(int status, Headers headers, byte[] body) {
            return new BufferedResponse(status, headers, body);
        }

        static BufferedResponse text(int status, String body) {
            Objects.requireNonNull(body, "body");
            return create(status,
                          WritableHeaders.create()
                                  .add(HeaderValues.create(HeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8")),
                          body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
