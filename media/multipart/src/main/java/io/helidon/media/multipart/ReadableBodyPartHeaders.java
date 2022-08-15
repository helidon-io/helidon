/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.http.ContentDisposition;
import io.helidon.common.http.Headers;
import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;

/**
 * Readable body part headers.
 */
public final class ReadableBodyPartHeaders implements BodyPartHeaders {

    private final Object internalLock = new Object();
    private final Headers headers;
    private ContentDisposition contentDisposition;

    private ReadableBodyPartHeaders(Headers headers) {
        this.headers = headers;
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

    @Override
    public List<String> all(Http.HeaderName name, Supplier<List<String>> defaultSupplier) {
        return headers.all(name, defaultSupplier);
    }

    @Override
    public boolean contains(Http.HeaderName name) {
        return headers.contains(name);
    }

    @Override
    public boolean contains(Http.HeaderValue value) {
        return headers.contains(value);
    }

    @Override
    public Http.HeaderValue get(Http.HeaderName name) {
        return headers.get(name);
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        return headers.acceptedTypes();
    }

    @Override
    public Iterator<Http.HeaderValue> iterator() {
        return headers.iterator();
    }

    @Override
    public HttpMediaType partContentType() {
        return contentType()
                .orElseGet(this::defaultContentType);
    }

    @Override
    public ContentDisposition contentDisposition() {
        if (contentDisposition == null) {
            synchronized (internalLock) {
                contentDisposition = first(Http.Header.CONTENT_DISPOSITION)
                        .map(ContentDisposition::parse)
                        .orElse(ContentDisposition.empty());
            }
        }
        return contentDisposition;
    }

    /**
     * Builder class to create {@link ReadableBodyPartHeaders} instances.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, ReadableBodyPartHeaders> {

        /**
         * The headers map.
         */
        private final HeadersWritable<?> headers = HeadersWritable.create();

        /**
         * Force the use of {@link ReadableBodyPartHeaders#builder() }.
         */
        private Builder() {
        }

        @Override
        public ReadableBodyPartHeaders build() {
            return new ReadableBodyPartHeaders(headers);
        }

        /**
         * Add a new header.
         *
         * @param name  header name
         * @param value header value
         * @return this builder
         */
        Builder header(Http.HeaderName name, String value) {
            headers.add(name, value);
            return this;
        }
    }
}
