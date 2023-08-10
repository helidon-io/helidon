/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.media.type.MediaType;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.HttpMediaTypes;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;

/**
 * A single part of a {@link WriteableMultiPart} message.
 */
public interface WriteablePart {
    /**
     * Builder to create a new part.
     *
     * @param partName name of this part (will be used in content disposition if used)
     * @return a new builder
     */
    static Builder builder(String partName) {
        return new Builder(partName);
    }

    /**
     * Name of this part.
     *
     * @return part name
     */
    String name();

    /**
     * File name if defined in content disposition.
     *
     * @return file name
     */
    Optional<String> fileName();

    /**
     * Content type of this part.
     *
     * @return content type
     */
    HttpMediaType contentType();

    /**
     * Full set of configured headers of this part.
     *
     * @return headers
     */
    Headers headers();

    /**
     * Write this part as part of server response.
     *
     * @param context        media context
     * @param outputStream   output stream to write to
     * @param requestHeaders request headers (server request headers)
     */
    void writeServerResponse(MediaContext context, OutputStream outputStream, Headers requestHeaders);

    /**
     * Write this part as part of client request.
     *
     * @param context      media context
     * @param outputStream output stream to write to
     */
    void writeClientRequest(MediaContext context, OutputStream outputStream);

    /**
     * Fluent API builder for {@link WriteablePart}.
     */
    class Builder implements io.helidon.common.Builder<Builder, WriteablePart> {
        private final WritableHeaders<?> headers = WritableHeaders.create();

        private final String partName;
        private String fileName;
        private HttpMediaType mediaType = HttpMediaTypes.PLAINTEXT_UTF_8;
        private Supplier<Object> objectContent;
        private Supplier<InputStream> inputStreamSupplier;
        private byte[] byteContent;

        private Builder(String partName) {
            this.partName = partName;
        }

        @Override
        public WriteablePart build() {
            if (byteContent != null) {
                return new WriteablePartBytes(this, byteContent);
            }

            if (objectContent != null) {
                return new WriteablePartObject(this, objectContent);
            }

            if (inputStreamSupplier != null) {
                return new WriteablePartStream(this, inputStreamSupplier);
            }

            throw new IllegalStateException("No content provided for multipart part named " + partName);
        }

        /**
         * Configure file name to be added to content disposition (for {@code multipart/form-data}).
         *
         * @param fileName file name
         * @return updated builder
         */
        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Content type of this part, with possible parameters.
         *
         * @param mediaType media type to use
         * @return updated builder
         */
        public Builder contentType(HttpMediaType mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        /**
         * Content type of this part.
         *
         * @param mediaType media type to use
         * @return updated builder
         */
        public Builder contentType(MediaType mediaType) {
            this.mediaType = HttpMediaType.create(mediaType);
            return this;
        }

        /**
         * Configure content to be serialized using {@link io.helidon.http.media.MediaContext}.
         *
         * @param content content to use
         * @return updated builder
         */
        public Builder content(Object content) {
            if (content instanceof byte[] bytes) {
                this.byteContent = bytes;
            } else if (content instanceof InputStream is) {
                this.inputStreamSupplier = () -> is;
            } else {
                this.objectContent = () -> content;
            }
            return this;
        }

        /**
         * Configure content to be serialized using {@link io.helidon.http.media.MediaContext}.
         *
         * @param contentSupplier content supplier to use, will be called when serializing the message
         * @return updated builder
         */
        public Builder content(Supplier<Object> contentSupplier) {
            this.objectContent = contentSupplier;
            return this;
        }

        /**
         * Configure content from an input stream. The input stream will be obtained when serializing this part.
         *
         * @param streamSupplier supplier of an input stream
         * @return updated builder
         */
        public Builder inputStream(Supplier<InputStream> streamSupplier) {
            this.inputStreamSupplier = streamSupplier;
            return this;
        }

        Headers headers() {
            return headers;
        }

        String partName() {
            return partName;
        }

        Optional<String> fileName() {
            return Optional.ofNullable(fileName);
        }

        HttpMediaType contentType() {
            return mediaType;
        }
    }
}
