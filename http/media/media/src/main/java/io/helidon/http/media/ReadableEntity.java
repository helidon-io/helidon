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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

import io.helidon.common.GenericType;

/**
 * Readable HTTP entity.
 * This may be server request entity, or client response entity.
 * Materializing conversions are limited by the HTTP protocol's
 * {@code max-buffered-entity-size} (decoded input bytes, not heap usage).
 * {@link #inputStream()} and media readers that declare streaming behavior are
 * exempt. Server-side {@code max-payload-size} applies independently to both
 * streamed and materialized decoded request bodies when configured.
 */
public interface ReadableEntity {
    /**
     * Input stream to read bytes of the entity.
     * Cannot be combined with methods {@link #as(Class)} or {@link #as(io.helidon.common.GenericType)}
     * If there is no entity, returns input stream on empty byte array.
     * This stream is not limited by {@code max-buffered-entity-size}.
     *
     * @return input stream to entity bytes
     */
    InputStream inputStream();

    /**
     * If called before {@link #inputStream()}, it will buffer the entity and support
     * multiple calls to {@link #inputStream()} after that. Normally, {@link #inputStream()}
     * can only be called at most once.
     * On a server request, this buffers decoded bytes and applies both the
     * protocol's {@code max-buffered-entity-size} and the configured
     * {@code max-payload-size} before allowing the entity to be read again.
     *
     * @throws java.lang.IllegalStateException if called after {@link #inputStream()} or
     *          if the buffer size limit was exceeded outside a server request
     * @throws io.helidon.http.HttpException if a server request exceeds either size limit
     * @throws java.lang.UnsupportedOperationException if not supported
     */
    default void buffer() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Get the entity as a specific class.
     * The entity will use {@link MediaContext} to find correct media mapper.
     *
     * @param type class of the entity
     * @param <T>  type of the entity
     * @return entity correctly typed
     * @throws IllegalArgumentException        in case the entity type is not supported
     * @throws java.lang.IllegalStateException if there is no entity or the materialized entity size limit
     *                                        is exceeded outside a server request
     * @throws io.helidon.http.HttpException   if a server request exceeds its materialized entity size limit
     * @throws java.io.UncheckedIOException    in case I/O fails
     */
    default <T> T as(Class<T> type) {
        return as(GenericType.create(type));
    }

    /**
     * Get the entity as a specific class (optional).
     * The entity will use {@link MediaContext} to find correct media mapper.
     *
     * @param type class of the entity
     * @param <T>  type of the entity
     * @return entity correctly typed, or an empty optional if entity is not present
     * @throws IllegalArgumentException        in case the entity type is not supported
     * @throws java.lang.IllegalStateException if the materialized entity size limit is exceeded outside a server request
     * @throws io.helidon.http.HttpException   if a server request exceeds its materialized entity size limit
     * @throws java.io.UncheckedIOException    in case I/O fails
     */
    default <T> Optional<T> asOptional(Class<T> type) {
        return asOptional(GenericType.create(type));
    }

    /**
     * Get the entity as a specific type.
     * The entity will use {@link MediaContext} to find correct media mapper.
     *
     * @param type generic type of the entity
     * @param <T>  type of the entity
     * @return entity correctly typed
     * @throws IllegalArgumentException        in case the entity type is not supported
     * @throws java.lang.IllegalStateException if there is no entity or the materialized entity size limit
     *                                        is exceeded outside a server request
     * @throws io.helidon.http.HttpException   if a server request exceeds its materialized entity size limit
     * @throws java.io.UncheckedIOException    in case I/O fails
     */
    <T> T as(GenericType<T> type);

    /**
     * Get the entity as a specific type (optional).
     * The entity will use {@link MediaContext} to find correct media mapper.
     *
     * @param type generic type of the entity
     * @param <T>  type of the entity
     * @return entity correctly typed, or an empty optional if entity is not present
     * @throws IllegalArgumentException        in case the entity type is not supported
     * @throws java.lang.IllegalStateException if the materialized entity size limit is exceeded outside a server request
     * @throws io.helidon.http.HttpException   if a server request exceeds its materialized entity size limit
     * @throws java.io.UncheckedIOException    in case I/O fails
     */
    <T> Optional<T> asOptional(GenericType<T> type);

    /**
     * Whether an entity actually exists.
     *
     * @return {@code true} if an entity exists and can be read
     */
    boolean hasEntity();

    /**
     * Whether this entity has been consumed already.
     *
     * @return {@code true} if the entity is already consumed; a consumed entity cannot be consumed again
     */
    boolean consumed();

    /**
     * Copy this entity and add a new runnable to be executed after this entity is consumed.
     *
     * @param entityProcessedRunnable runnable to execute on consumed entity
     * @return a new entity delegating to this entity
     */
    ReadableEntity copy(Runnable entityProcessedRunnable);

    /**
     * Consume the entity if not yet consumed.
     * Reads all bytes from the entity and discards them. If entity is larger than allowed, appropriate HTTP exception is thrown.
     * If entity is already consumed, this is a no-op.
     */
    default void consume() {
        if (consumed()) {
            return;
        }
        try (InputStream inputStream = inputStream()) {
            byte[] buffer = new byte[2048];
            while (inputStream.read(buffer) > 0) {
                // ignore
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
