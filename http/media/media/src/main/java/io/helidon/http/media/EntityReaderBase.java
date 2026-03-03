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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;

/**
 * Base for readers that care about charset.
 *
 * @param <T> type of the supported objects
 */
public abstract class EntityReaderBase<T> implements EntityReader<T> {
    /**
     * Constructor with no side effects.
     */
    protected EntityReaderBase() {
    }

    /**
     * Get Content-Type from the provided headers (if available), then get its charset (if available), and return it,
     * otherwise return {@code UTF-8}.
     *
     * @param headers headers that are expected to contain {@code Content-Type} header
     * @return charset either from the header, or the default value provided
     */
    protected static Charset contentTypeCharset(Headers headers) {
        return headers.contentType()
                .flatMap(HttpMediaType::charset)
                .map(EntityIoBase::charset)
                .orElse(StandardCharsets.UTF_8);
    }

    /**
     * Get Content-Type from the provided headers (if available), then get its charset (if available), and return it,
     * otherwise return empty optional.
     *
     * @param headers headers that are expected to contain {@code Content-Type} header
     * @return charset from the header, or an empty optional
     */
    protected static Optional<Charset> findContentTypeCharset(Headers headers) {
        return headers.contentType()
                .flatMap(HttpMediaType::charset)
                .map(EntityIoBase::charset);
    }
}
