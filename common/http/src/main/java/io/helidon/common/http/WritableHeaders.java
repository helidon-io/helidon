/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.http;

import java.util.List;
import java.util.function.Consumer;

import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.media.type.MediaType;

/**
 * HTTP Headers that are mutable.
 *
 * @param <B> type of the headers (for inheritance)
 */
public interface WritableHeaders<B extends WritableHeaders<B>> extends Headers {
    /**
     * Create a new instance of writable headers.
     *
     * @return mutable HTTP headers
     */
    static WritableHeaders<?> create() {
        return new HeadersImpl<>();
    }

    /**
     * Create a new instance of writable headers from existing headers.
     *
     * @param headers headers to add to the new mutable instance
     * @return mutable HTTP headers
     */
    static WritableHeaders<?> create(Headers headers) {
        return new HeadersImpl<>(headers);
    }

    /**
     * Set a value of a header unless it is already present.
     *
     * @param header header with value to set
     * @return this instance
     */
    B setIfAbsent(HeaderValue header);

    /**
     * Add a header or add a header value if the header is already present.
     *
     * @param header header with value
     * @return this instance
     */
    B add(HeaderValue header);

    /**
     * Add a header or add a header value if the header is already present.
     *
     * @param header header name
     * @param value header value(s)
     * @return this instance
     */
    default B add(HeaderName header, String... value) {
        return add(Http.HeaderNames.create(header, value));
    }

    /**
     * Remove a header.
     *
     * @param name name of the header to remove
     * @return this instance
     */
    B remove(HeaderName name);

    /**
     * Remove a header.
     *
     * @param name name of the header to remove
     * @param removedConsumer consumer to be called with existing header; if the header did not exist, consumer will not be
     *                        called
     * @return this instance
     */
    B remove(HeaderName name, Consumer<HeaderValue> removedConsumer);

    /**
     * Sets the MIME type of the response body.
     *
     * @param contentType Media type of the content.
     * @return this instance
     */
    default B contentType(MediaType contentType) {
        return set(Http.HeaderNames.create(HeaderNameEnum.CONTENT_TYPE, contentType.text()));
    }

    /**
     * Sets the MIME type of the response body.
     *
     * @param contentType Media type of the content.
     * @return this instance
     */
    default B contentType(HttpMediaType contentType) {
        return set(Http.HeaderNames.create(HeaderNameEnum.CONTENT_TYPE, contentType.text()));
    }

    /**
     * Set a header and replace it if it already existed.
     *
     * @param header header to set
     * @return this instance
     */
    B set(HeaderValue header);

    /**
     * Set a header and replace it if it already existed.
     * Use {@link #set(io.helidon.common.http.Http.HeaderValue)} for headers that are known in advance (use a constant),
     * or for headers obtained from Helidon server or client. This method is intended for headers that are unknown or change
     * value often.
     *
     * @param name header name to set
     * @param values value(s) of the header
     * @return this instance
     */
    default B set(HeaderName name, String... values) {
        return set(Http.HeaderNames.create(name, true, false, values));
    }

    /**
     * Set a header and replace it if it already existed.
     * Use {@link #set(io.helidon.common.http.Http.HeaderValue)} for headers that are known in advance (use a constant),
     * or for headers obtained from Helidon server or client. This method is intended for headers that are unknown or change
     * value often.
     *
     * @param name header name to set
     * @param values value(s) of the header
     * @return this instance
     */
    default B set(HeaderName name, List<String> values) {
        return set(Http.HeaderNames.create(name, values));
    }

    /**
     * Content length of the entity in bytes.
     * If not configured and a non-streaming entity is used, it will be configured based on
     * the entity length. For streaming entities without content length we switch to chunked
     * encoding (HTTP/1).
     *
     * @param length length of the entity
     * @return this instance
     */
    default B contentLength(long length) {
        return set(Http.HeaderNames.create(HeaderNameEnum.CONTENT_LENGTH,
                                           true,
                                           false,
                                           String.valueOf(length)));
    }

    /**
     * Clear all current headers.
     *
     * @return this instance
     */
    B clear();
}
