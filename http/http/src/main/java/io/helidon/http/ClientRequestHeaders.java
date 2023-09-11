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

package io.helidon.http;

import io.helidon.common.media.type.MediaType;

/**
 * Mutable headers of a client request.
 */
public interface ClientRequestHeaders extends ServerRequestHeaders,
                                              WritableHeaders<ClientRequestHeaders> {

    /**
     * Create client request headers from writable headers.
     *
     * @param delegate headers
     * @return client request headers
     */
    static ClientRequestHeaders create(WritableHeaders<?> delegate) {
        return new ClientRequestHeadersImpl(delegate);
    }

    /**
     * Create client request headers from headers.
     *
     * @param headers headers
     * @return client request headers
     */
    static ClientRequestHeaders create(Headers headers) {
        return new ClientRequestHeadersImpl(WritableHeaders.create(headers));
    }

    /**
     * Accepted media types. Supports quality factor and wildcards.
     *
     * @param accepted media types to accept
     * @return updated headers
     */
    default ClientRequestHeaders accept(MediaType... accepted) {
        String[] values = new String[accepted.length];
        for (int i = 0; i < accepted.length; i++) {
            MediaType mediaType = accepted[i];
            values[i] = mediaType.text();
        }
        set(HeaderValues.create(HeaderNames.ACCEPT, values));
        return this;
    }
}
