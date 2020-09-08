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

import io.helidon.common.http.HashParameters;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;

/**
 * Writeable body part headers.
 */
public final class WriteableBodyPartHeaders extends HashParameters implements BodyPartHeaders {

    private WriteableBodyPartHeaders(Map<String, List<String>> params) {
        super(params);
    }

    @Override
    public MediaType contentType() {
        return first(Http.Header.CONTENT_TYPE)
                .map(MediaType::parse)
                .orElseGet(this::defaultContentType);
    }

    /**
     * Sets the MIME type of the body part.
     *
     * @param contentType Media type of the content.
     */
    public void contentType(MediaType contentType) {
        if (contentType == null) {
            remove(Http.Header.CONTENT_TYPE);
        } else {
            put(Http.Header.CONTENT_TYPE, contentType.toString());
        }
    }

    @Override
    public ContentDisposition contentDisposition() {
        return first(Http.Header.CONTENT_DISPOSITION)
                .map(ContentDisposition::parse)
                .orElse(ContentDisposition.EMPTY);
    }

    /**
     * Sets the value of
     * {@value io.helidon.common.http.Http.Header#CONTENT_DISPOSITION} header.
     *
     * @param contentDisposition content disposition
     */
    public void contentDisposition(ContentDisposition contentDisposition) {
        if (contentDisposition != null) {
            put(Http.Header.CONTENT_DISPOSITION, contentDisposition.toString());
        }
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
     * @return WriteableBodyPartHeaders
     */
    public static WriteableBodyPartHeaders create() {
        return new WriteableBodyPartHeaders(null);
    }

    /**
     * Builder class to create {@link WriteableBodyPartHeaders} instances.
     */
    public static final class Builder implements io.helidon.common.Builder<WriteableBodyPartHeaders> {

        /**
         * The headers map.
         */
        private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
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
         * @param name header name
         * @param value header value
         * @return this builder
         */
        public Builder header(String name, String value) {
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            return this;
        }

        /**
         * Add a {@code Content-Type} header.
         * @param contentType value for the {@code Content-Type} header
         * @return this builder
         */
        public Builder contentType(MediaType contentType) {
           return header(Http.Header.CONTENT_TYPE, contentType.toString());
        }

        /**
         * Add a {@code Content-Disposition} header.
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
            if (!headers.containsKey(Http.Header.CONTENT_DISPOSITION) && name != null) {
                ContentDisposition.Builder builder = ContentDisposition.builder().name(this.name);
                if (fileName != null) {
                    builder.filename(fileName);
                    if (!headers.containsKey(Http.Header.CONTENT_TYPE)) {
                        contentType(MediaType.APPLICATION_OCTET_STREAM);
                    }
                }
                contentDisposition(builder.build());
            }

            return new WriteableBodyPartHeaders(headers);
        }

    }
}
