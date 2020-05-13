/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.multipart.common;

import io.helidon.common.http.Headers;
import io.helidon.common.http.MediaType;

/**
 * Body part headers.
 */
public interface BodyPartHeaders extends Headers {

    /**
     * Get the {@code Content-Type} header. If the {@code Content-Type} header
     * is not present, the default value is retrieved using
     * {@link #defaultContentType()}.
     *
     * @return MediaType, never {@code null}
     */
    MediaType contentType();

    /**
     * Get the {@code Content-Disposition} header.
     *
     * @return ContentDisposition, never {@code null}
     */
    ContentDisposition contentDisposition();

    /**
     * Returns the default {@code Content-Type} header value:
     * {@link MediaType#APPLICATION_OCTET_STREAM} if the
     * {@code Content-Disposition} header is present with a non empty value,
     * otherwise {@link MediaType#TEXT_PLAIN}.
     *
     * @see
     * <a href="https://tools.ietf.org/html/rfc7578#section-4.4">RFC-7578</a>
     * @return MediaType, never {@code null}
     */
    default MediaType defaultContentType() {
        return contentDisposition().filename()
                .map(fname -> MediaType.APPLICATION_OCTET_STREAM)
                .orElse(MediaType.TEXT_PLAIN);
    }
}
