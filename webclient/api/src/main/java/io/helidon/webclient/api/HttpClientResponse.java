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

package io.helidon.webclient.api;

import java.io.InputStream;

import io.helidon.common.GenericType;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.spi.Source;

/**
 * Http client response.
 */
public interface HttpClientResponse extends AutoCloseable, ClientResponseBase {
    /**
     * Response entity.
     *
     * @return entity
     */
    ReadableEntity entity();

    /**
     * Entity input stream.
     * This is a shortcut to {@link #entity()} and {@link io.helidon.http.media.ReadableEntity#inputStream()}.
     *
     * @return input stream
     */
    default InputStream inputStream() {
        return entity().inputStream();
    }

    /**
     * Read the entity as a specific type.
     * This is a shortcut to {@link #entity()} and {@link io.helidon.http.media.ReadableEntity#as(Class)}.
     *
     * @param type class of the entity
     * @param <T> type of the entity
     * @return typed entity
     */
    default <T> T as(Class<T> type) {
        return entity().as(type);
    }

    /**
     * Registers a source listener for this response.
     *
     * @param sourceType the generic source type
     * @param source the source
     * @param <T> the actual source type
     * @throws UnsupportedOperationException if operation not implemented on this response
     *                                       or not provider for this listener is found
     */
    default <T extends Source<?>> void source(GenericType<T> sourceType, T source) {
        throw new UnsupportedOperationException("No source available for " + sourceType);
    }

    /**
     * Closes the response.
     * This may have no impact on the underlying connection.
     * Response is implicitly closed if the entity is fully read (either through {@link #entity()} or
     * through {@link #inputStream()}.
     */
    @Override
    void close();
}
