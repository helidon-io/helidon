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
package io.helidon.reactive.media.multipart;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.http.ContentDisposition;
import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;

/**
 * Writeable body part headers.
 */
public final class WriteableBodyPartHeaders implements BodyPartHeaders, HeadersWritable<WriteableBodyPartHeaders> {

    private final HeadersWritable<?> delegate;

    private WriteableBodyPartHeaders(HeadersWritable<?> delegate) {
        this.delegate = delegate;
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
     * Create a new instance of {@link WriteableBodyPartHeaders} with empty
     * headers.
     *
     * @return WriteableBodyPartHeaders
     */
    public static WriteableBodyPartHeaders create() {
        return new WriteableBodyPartHeaders(HeadersWritable.create());
    }

    @Override
    public List<String> all(Http.HeaderName name, Supplier<List<String>> defaultSupplier) {
        return delegate.all(name, defaultSupplier);
    }

    @Override
    public boolean contains(Http.HeaderName name) {
        return delegate.contains(name);
    }

    @Override
    public boolean contains(Http.HeaderValue value) {
        return delegate.contains(value);
    }

    @Override
    public Http.HeaderValue get(Http.HeaderName name) {
        return delegate.get(name);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        return delegate.acceptedTypes();
    }

    @Override
    public WriteableBodyPartHeaders setIfAbsent(Http.HeaderValue header) {
        delegate.setIfAbsent(header);
        return this;
    }

    @Override
    public WriteableBodyPartHeaders add(Http.HeaderValue header) {
        delegate.add(header);
        return this;
    }

    @Override
    public WriteableBodyPartHeaders remove(Http.HeaderName name) {
        delegate.remove(name);
        return this;
    }

    @Override
    public WriteableBodyPartHeaders remove(Http.HeaderName name, Consumer<Http.HeaderValue> removedConsumer) {
        delegate.remove(name, removedConsumer);
        return this;
    }

    @Override
    public WriteableBodyPartHeaders set(Http.HeaderValue header) {
        delegate.set(header);
        return this;
    }

    @Override
    public WriteableBodyPartHeaders clear() {
        delegate.clear();
        return this;
    }

    @Override
    public Iterator<Http.HeaderValue> iterator() {
        return delegate.iterator();
    }

    @Override
    public HttpMediaType partContentType() {
        return contentType()
                .orElseGet(this::defaultContentType);
    }

    @Override
    public ContentDisposition contentDisposition() {
        return first(Http.Header.CONTENT_DISPOSITION)
                .map(ContentDisposition::parse)
                .orElse(ContentDisposition.empty());
    }

    /**
     * Sets the value of
     * {@link io.helidon.common.http.Http.Header#CONTENT_DISPOSITION} header.
     *
     * @param contentDisposition content disposition
     */
    public void contentDisposition(ContentDisposition contentDisposition) {
        if (contentDisposition != null) {
            set(Http.Header.CONTENT_DISPOSITION, contentDisposition.toString());
        }
    }

    /**
     * Builder class to create {@link WriteableBodyPartHeaders} instances.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, WriteableBodyPartHeaders> {

        /**
         * The headers map.
         */
        private final HeadersWritable<?> headers = HeadersWritable.create();
        private String name;
        private String fileName;

        /**
         * Force the use of {@link WriteableBodyPartHeaders#builder() }.
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
        public Builder header(Http.HeaderName name, String value) {
            headers.add(name, value);
            return this;
        }

        /**
         * Add a {@code Content-Type} header.
         *
         * @param contentType value for the {@code Content-Type} header
         * @return this builder
         */
        public Builder contentType(HttpMediaType contentType) {
            return header(Http.Header.CONTENT_TYPE, contentType.toString());
        }

        /**
         * Add a {@code Content-Disposition} header.
         *
         * @param contentDisp content disposition
         * @return this builder
         */
        public Builder contentDisposition(ContentDisposition contentDisp) {
            return header(Http.Header.CONTENT_DISPOSITION, contentDisp.toString());
        }

        /**
         * Name which will be used in {@link ContentDisposition}.
         *
         * This value will be ignored if an actual instance of {@link ContentDisposition} is set.
         *
         * @param name content disposition name parameter
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Filename which will be used in {@link ContentDisposition}.
         *
         * This value will be ignored if an actual instance of {@link ContentDisposition} is set.
         *
         * @param fileName content disposition filename parameter
         * @return this builder
         */
        public Builder filename(String fileName) {
            this.fileName = fileName;
            return this;
        }

        @Override
        public WriteableBodyPartHeaders build() {
            if (!headers.contains(Http.Header.CONTENT_DISPOSITION) && name != null) {
                ContentDisposition.Builder builder = ContentDisposition.builder().name(this.name);
                if (fileName != null) {
                    builder.filename(fileName);
                    if (!headers.contains(Http.Header.CONTENT_TYPE)) {
                        contentType(HttpMediaType.create(MediaTypes.APPLICATION_OCTET_STREAM));
                    }
                }
                contentDisposition(builder.build());
            }

            return new WriteableBodyPartHeaders(headers);
        }

    }
}
