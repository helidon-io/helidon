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

package io.helidon.http.media.multipart;

import java.util.Optional;

import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.media.ReadableEntity;

/**
 * A single part of a multipart message.
 * When read from an HTTP entity, each materializing conversion inherits that entity's
 * {@code max-buffered-entity-size} limit independently. The limit counts decoded input bytes
 * consumed by the conversion, not the combined size of all parts or the resulting object's heap usage.
 * Direct {@link #inputStream()} access and media readers that declare streaming behavior remain
 * unrestricted by this limit. A configured request {@code max-payload-size} still applies to the entire payload.
 */
public interface ReadablePart extends ReadableEntity {
    /**
     * Name of this multipart part.
     * Non-ASCII {@code Content-Disposition} parameters are decoded as UTF-8 when valid;
     * otherwise, their original single-byte representation is preserved.
     *
     * @return param name
     */
    String name();

    /**
     * File name of this part if defined in part {@code Content-Disposition}.
     * Raw non-ASCII parameters are decoded as UTF-8 when valid; otherwise, their
     * original single-byte representation is preserved.
     * <p>
     * The returned filename is percent-decoded and contains only the final path
     * segment. Invalid filenames, such as an empty segment, {@code .},
     * {@code ..}, or names containing control characters, are rejected.
     * </p>
     *
     * @return file name if defined
     * @throws IllegalArgumentException if the filename is malformed or unsafe
     */
    Optional<String> fileName();

    /**
     * Content type of this multipart part.
     *
     * @return HTTP content type
     */
    HttpMediaType contentType();

    /**
     * Headers of this part.
     *
     * @return headers
     */
    Headers partHeaders();
}
