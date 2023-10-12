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

import java.util.Collection;
import java.util.function.Consumer;

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
    B setIfAbsent(Header header);

    /**
     * Add a header or add a header value if the header is already present.
     *
     * @param header header with value
     * @return this instance
     */
    B add(Header header);

    /**
     * Add a header or add a header value if the header is already present.
     *
     * @param header header name
     * @param value header value(s)
     * @return this instance
     */
    default B add(HeaderName header, String... value) {
        return add(HeaderValues.create(header, value));
    }

    /**
     * Add a header or add a header value if the header is already present.
     *
     * @param header header name
     * @param value header value
     * @return this instance
     */
    default B add(HeaderName header, int value) {
        return add(HeaderValues.create(header, value));
    }

    /**
     * Add a header or add a header value if the header is already present.
     *
     * @param header header name
     * @param value header value
     * @return this instance
     */
    default B add(HeaderName header, long value) {
        return add(HeaderValues.create(header, value));
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
    B remove(HeaderName name, Consumer<Header> removedConsumer);

    /**
     * Sets the MIME type of the response body.
     *
     * @param contentType Media type of the content, {@link io.helidon.http.HttpMediaType} may be used to add parameters
     * @return this instance
     */
    default B contentType(MediaType contentType) {
        return set(HeaderValues.create(HeaderNameEnum.CONTENT_TYPE, contentType.text()));
    }

    /**
     * Set a header and replace it if it already existed.
     *
     * @param header header to set
     * @return this instance
     */
    B set(Header header);

    /**
     * Set a header and replace it if it already existed.
     * Use {@link #set(Header)} for headers that are known in advance (use a constant),
     * or for headers obtained from Helidon server or client. This method is intended for headers that are unknown or change
     * value often.
     *
     * @param name header name to set
     * @param values value(s) of the header
     * @return this instance
     */
    default B set(HeaderName name, String... values) {
        return set(HeaderValues.create(name, true, false, values));
    }


    /**
     * Set a header and replace it if it already existed.
     * Use {@link #set(Header)} for headers that are known in advance (use a constant),
     * or for headers obtained from Helidon server or client. This method is intended for headers that are unknown or change
     * value often.
     *
     * @param name header name to set
     * @param value integer value of the header
     * @return this instance
     */
    default B set(HeaderName name, int value) {
        return set(HeaderValues.create(name, true, false, value));
    }

    /**
     * Set a header and replace it if it already existed.
     * Use {@link #set(Header)} for headers that are known in advance (use a constant),
     * or for headers obtained from Helidon server or client. This method is intended for headers that are unknown or change
     * value often.
     *
     * @param name header name to set
     * @param value long value of the header
     * @return this instance
     */
    default B set(HeaderName name, long value) {
        return set(HeaderValues.create(name, true, false, value));
    }

    /**
     * Set a header and replace it if it already existed.
     * Use {@link #set(Header)} for headers that are known in advance (use a constant),
     * or for headers obtained from Helidon server or client. This method is intended for headers that are unknown or change
     * value often.
     *
     * @param name header name to set
     * @param values value(s) of the header
     * @return this instance
     */
    default B set(HeaderName name, Collection<String> values) {
        return set(HeaderValues.create(name, values));
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
        return set(HeaderValues.create(HeaderNameEnum.CONTENT_LENGTH,
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

    /**
     * For each header from the provided headers, set its value on these headers.
     * If a header exists on these headers and on the provided headers, it would be replaced with the value(s) from
     * provided headers.
     *
     * @param headers to read headers from and set them on this instance
     * @return this instance
     */
    B from(Headers headers);
}
