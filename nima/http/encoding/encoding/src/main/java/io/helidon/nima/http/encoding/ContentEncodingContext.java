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

package io.helidon.nima.http.encoding;

import java.util.NoSuchElementException;

import io.helidon.common.http.Headers;

/**
 * Content encoding support to obtain encoders and decoders.
 */
public interface ContentEncodingContext {
    /**
     * Create a new encoding support.
     *
     * @return content encoding support
     */
    static ContentEncodingContext create() {
        return new ContentEncodingSupportImpl();
    }

    /**
     * There is at least one content encoder.
     *
     * @return whether there is at least one content encoder
     */
    boolean contentEncodingEnabled();

    /**
     * There is at least one content decoder.
     *
     * @return whether there is at least one content decoder
     */
    boolean contentDecodingEnabled();

    /**
     * Whether there is a content encoder for the provided id.
     *
     * @param encodingId encoding id
     * @return whether a provider exists for this id
     */
    boolean contentEncodingSupported(String encodingId);

    /**
     * Whether there is a content decoder for the provided id.
     *
     * @param encodingId encoding id
     * @return whether a provider exists for this id
     */
    boolean contentDecodingSupported(String encodingId);

    /**
     * Obtain a content encoder for the id.
     *
     * @param encodingId encoding id
     * @return content encoder to use
     * @throws NoSuchElementException in case an encoding provider does not exist
     */
    ContentEncoder encoder(String encodingId) throws NoSuchElementException;

    /**
     * Obtain a content decoder for the id.
     *
     * @param encodingId encoding id
     * @return content decoder to use
     * @throws NoSuchElementException in case a decoding provider does not exist
     */
    ContentDecoder decoder(String encodingId) throws NoSuchElementException;

    /**
     * Discover content encoder based on the HTTP headers.
     *
     * @param headers headers to analyze
     * @return content encoder to use
     */
    ContentEncoder encoder(Headers headers);
}
