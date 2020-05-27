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
package io.helidon.media.multipart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.ReadOnlyParameters;

/**
 * Readable body part headers.
 */
public final class ReadableBodyPartHeaders extends ReadOnlyParameters implements BodyPartHeaders {

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
     * @return ReadableBodyPartHeaders
     */
    public static ReadableBodyPartHeaders create() {
        return new ReadableBodyPartHeaders(null);
    }

    /**
     * Builder class to create {@link ReadableBodyPartHeaders} instances.
     */
    public static final class Builder implements io.helidon.common.Builder<ReadableBodyPartHeaders> {

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
         * @param name header name
         * @param value header value
         * @return this builder
         */
        Builder header(String name, String value) {
            List<String> values = headers.get(name);
            if (values == null) {
                values = new ArrayList<>();
                headers.put(name, values);
            }
            values.add(value);
            return this;
        }

        @Override
        public ReadableBodyPartHeaders build() {
            return new ReadableBodyPartHeaders(headers);
        }
    }
}
