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

package io.helidon.http.media;

import java.io.InputStream;

import io.helidon.common.Api;
import io.helidon.common.GenericType;
import io.helidon.http.Headers;

/**
 * Entity-reading policy for nested entities, such as individual multipart parts.
 * A reader can retain this context when returning a lazy entity. Each conversion through the context
 * applies the original entity's materialization limit independently and preserves its limit-failure policy.
 * The limit counts decoded input bytes consumed by a materializing reader, rather than the resulting heap usage.
 * A server request limit failure is reported as {@link io.helidon.http.HttpException} with status 413;
 * a client response limit failure is reported as {@link java.lang.IllegalStateException}.
 * The limit-failure policy is inherited from the original entity, independently of the nested headers or read overload.
 * Streaming readers bypass that limit; other limits already applied to the supplied stream remain effective.
 * The selected reader retains responsibility for stream ownership as described by {@link EntityReader#streaming()}.
 */
@Api.Stable
public interface EntityReaderContext {
    /**
     * Read a nested entity using the selected reader's server-request read overload.
     * Each materializing conversion has its own byte counter. A limit failure remains observable even if the
     * selected reader wraps it or returns normally after catching it.
     *
     * @param reader selected media reader
     * @param type entity type
     * @param stream stream of the nested entity's content
     * @param headers nested entity headers
     * @param <T> entity type
     * @return converted entity
     */
    <T> T read(EntityReader<T> reader, GenericType<T> type, InputStream stream, Headers headers);

    /**
     * Read a nested entity using the selected reader's client-response read overload.
     * Each materializing conversion has its own byte counter. A limit failure remains observable even if the
     * selected reader wraps it or returns normally after catching it.
     *
     * @param reader selected media reader
     * @param type entity type
     * @param stream stream of the nested entity's content
     * @param requestHeaders request headers
     * @param responseHeaders nested response entity headers
     * @param <T> entity type
     * @return converted entity
     */
    <T> T read(EntityReader<T> reader,
               GenericType<T> type,
               InputStream stream,
               Headers requestHeaders,
               Headers responseHeaders);
}
