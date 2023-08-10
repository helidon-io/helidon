/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.http.encoding;

import java.util.Set;

import io.helidon.common.config.NamedService;

/**
 * Content encoding service created from {@link io.helidon.http.encoding.spi.ContentEncodingProvider},
 * or directly implemented.
 * Content encoding/decoding modifies bytes on the wire, for example to compress it.
 */
public interface ContentEncoding extends NamedService {
    /**
     * Identification(s) of this scheme as used in {@code Accept-Encoding} and {@code Content-Encoding} headers.
     *
     * @return identifications, such as {@code deflate}, {@code gzip}
     */
    Set<String> ids();

    /**
     * Does this provider support encoding.
     *
     * @return encoding supported
     */
    boolean supportsEncoding();

    /**
     * Does this provider support decoding.
     *
     * @return decoding supported
     */
    boolean supportsDecoding();

    /**
     * To decode bytes.
     *
     * @return decoder
     */
    ContentDecoder decoder();

    /**
     * To encode bytes.
     *
     * @return encoder
     */
    ContentEncoder encoder();
}
