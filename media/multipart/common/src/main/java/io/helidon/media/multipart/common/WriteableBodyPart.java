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

import java.util.concurrent.Flow.Publisher;

import io.helidon.common.http.DataChunk;
import io.helidon.media.common.MessageBodyWriteableContent;

/**
 * Writeable body part entity.
 */
public final class WriteableBodyPart implements BodyPart {

    private final MessageBodyWriteableContent content;
    private final WriteableBodyPartHeaders headers;

    /**
     * Private to enforce the use of {@link #builder()} or
     * {@link #create(java.lang.Object)}.
     */
    private WriteableBodyPart(MessageBodyWriteableContent content, WriteableBodyPartHeaders headers) {

        this.content = content;
        this.headers = headers;
    }

    @Override
    public MessageBodyWriteableContent content() {
        return content;
    }

    @Override
    public WriteableBodyPartHeaders headers() {
        return headers;
    }

    /**
     * Create a new out-bound part backed by the specified entity.
     * @param entity entity for the created part content
     * @return BodyPart
     */
    public static WriteableBodyPart create(Object entity){
        return builder().entity(entity).build();
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
     * Builder class for creating {@link BodyPart} instances.
     */
    public static final class Builder implements io.helidon.common.Builder<WriteableBodyPart> {

        private WriteableBodyPartHeaders headers;
        private Object entity;
        private Publisher<DataChunk> publisher;

        /**
         * Private constructor to force the use of
         * {@link WriteableBodyPart#builder() }.
         */
        private Builder() {
        }

        /**
         * Create a new out-bound body part backed by the specified entity.
         * @param entity entity for the body part content
         * @return this builder instance
         */
        public Builder entity(Object entity) {
            this.entity = entity;
            return this;
        }

        /**
         * Create a new out-bound body part backed by the specified publisher.
         * @param publisher publisher for the part content
         * @return this builder instance
         */
        public Builder publisher(Publisher<DataChunk> publisher) {
            this.publisher = publisher;
            return this;
        }

        /**
         * Set the headers for this part.
         * @param headers headers
         * @return this builder instance
         */
        public Builder headers(WriteableBodyPartHeaders headers) {
            this.headers = headers;
            return this;
        }

        @Override
        public WriteableBodyPart build() {
            if (headers == null) {
                headers = WriteableBodyPartHeaders.create();
            }
            MessageBodyWriteableContent content;
            if (entity != null) {
                content = MessageBodyWriteableContent.create(entity, headers);
            } else if (publisher != null) {
                content = MessageBodyWriteableContent.create(publisher, headers);
            } else {
                throw new IllegalStateException("Cannot create writeable content");
            }
            return new WriteableBodyPart(content, headers);
        }
    }
}
