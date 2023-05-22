/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.media.multipart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.ReadOnlyHeaders;

/**
 * Readable body part headers.
 */
public final class ReadableBodyPartHeaders extends ReadOnlyHeaders implements BodyPartHeaders {

    private final Object internalLock = new Object();
    private ContentDisposition contentDisposition;

    private ReadableBodyPartHeaders(Map<String, List<String>> params) {
        super(params);
    }

    @Override
    public MediaType contentType() {
        return first(Http.Header.CONTENT_TYPE)
                .map(MediaType::parse)
                .orElseGet(this::defaultContentType);
    }

    @Override
    public ContentDisposition contentDisposition() {
        if (contentDisposition == null) {
            synchronized (internalLock) {
                contentDisposition = first(Http.Header.CONTENT_DISPOSITION)
                        .map(ContentDisposition::parse)
                        .orElse(ContentDisposition.EMPTY);
            }
        }
        return contentDisposition;
    }

    /**
     * Create a new builder instance.
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new instance of {@link ReadableBodyPartHeaders}.
     *
     * @return ReadableBodyPartHeaders
     */
    public static ReadableBodyPartHeaders create() {
        return new ReadableBodyPartHeaders(null);
    }

    /**
     * Builder class to create {@link ReadableBodyPartHeaders} instances.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, ReadableBodyPartHeaders> {

        /**
         * The headers map.
         */
        private final Map<String, List<String>> headers
                = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        /**
         * Force the use of {@link ReadableBodyPartHeaders#builder() }.
         */
        private Builder() {
        }

        /**
         * Add a new header.
         *
         * @param name  header name
         * @param value header value
         * @return this builder
         */
        public Builder header(String name, String value) {
            headers.computeIfAbsent(name, n -> new ArrayList<>())
                   .add(value);
            return this;
        }

        /**
         * Add a new header.
         *
         * @param name   header name
         * @param values header values
         * @return this builder
         */
        public Builder header(String name, List<String> values) {
            headers.computeIfAbsent(name, n -> new ArrayList<>())
                   .addAll(values);
            return this;
        }

        /**
         * Add new headers.
         *
         * @param headers headers map
         * @return this builder
         */
        public Builder headers(Map<String, List<String>> headers) {
            headers.forEach(this::header);
            return this;
        }

        @Override
        public ReadableBodyPartHeaders build() {
            return new ReadableBodyPartHeaders(headers);
        }
    }
}
