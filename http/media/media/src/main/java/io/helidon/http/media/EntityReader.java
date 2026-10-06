/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.http.media;

import java.io.InputStream;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.common.GenericType;
import io.helidon.http.Headers;

/**
 * Reader of entity into a specific type.
 *
 * @param <T> type of object to read
 */
@Api.Stable
public interface EntityReader<T> {
    /**
     * Whether this reader processes the entity incrementally without materializing it in memory.
     * This includes copying the supplied stream to a file or another sink during the read call,
     * as well as returning a lazy entity that consumes the stream after the read call returns.
     * Streaming readers are not subject to {@code max-buffered-entity-size}; independently
     * configured payload limits still apply. The default is {@code false}, including for custom readers.
     *
     * A reader that consumes the stream during the read call must close it before returning.
     * A reader that returns a lazy entity must transfer responsibility for closing the stream
     * to that entity and keep the stream usable until consumption is complete.
     *
     * @return {@code true} if the entity is processed without materializing it in memory
     */
    default boolean streaming() {
        return false;
    }

    /**
     * Read server request entity and close the stream when consumption is complete.
     * A streaming reader may transfer stream ownership to a lazy entity as described by {@link #streaming()}.
     *
     * @param type    type of entity
     * @param stream  stream to read from
     * @param headers request headers
     * @return correctly typed entity
     */
    T read(GenericType<T> type, InputStream stream, Headers headers);

    /**
     * Read client response entity and close the stream when consumption is complete.
     * A streaming reader may transfer stream ownership to a lazy entity as described by {@link #streaming()}.
     *
     * @param type            type of entity
     * @param stream          stream to read from
     * @param requestHeaders  request headers
     * @param responseHeaders response headers
     * @return correctly typed entity
     */
    T read(GenericType<T> type,
           InputStream stream,
           Headers requestHeaders,
           Headers responseHeaders);

    /**
     * Read a server request entity with its policy for nested entity conversions.
     * Readers that return nested entities can retain {@code context} and use it for each nested conversion.
     * The supplied stream already has the appropriate limits applied; the context is for nested streams.
     * The default implementation delegates to {@link #read(GenericType, InputStream, Headers)}.
     *
     * @param type entity type
     * @param stream stream to read
     * @param headers request headers
     * @param context reading policy inherited by nested entities
     * @return converted entity
     */
    default T read(GenericType<T> type, InputStream stream, Headers headers, EntityReaderContext context) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(stream);
        Objects.requireNonNull(headers);
        Objects.requireNonNull(context);
        return read(type, stream, headers);
    }

    /**
     * Read a client response entity with its policy for nested entity conversions.
     * Readers that return nested entities can retain {@code context} and use it for each nested conversion.
     * The supplied stream already has the appropriate limits applied; the context is for nested streams.
     * The default implementation delegates to {@link #read(GenericType, InputStream, Headers, Headers)}.
     *
     * @param type entity type
     * @param stream stream to read
     * @param requestHeaders request headers
     * @param responseHeaders response headers
     * @param context reading policy inherited by nested entities
     * @return converted entity
     */
    default T read(GenericType<T> type,
                   InputStream stream,
                   Headers requestHeaders,
                   Headers responseHeaders,
                   EntityReaderContext context) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(stream);
        Objects.requireNonNull(requestHeaders);
        Objects.requireNonNull(responseHeaders);
        Objects.requireNonNull(context);
        return read(type, stream, requestHeaders, responseHeaders);
    }
}
