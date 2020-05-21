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

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Parameters;
import io.helidon.common.reactive.Single;
import io.helidon.common.reactive.SubscriptionHelper;
import io.helidon.media.common.MessageBodyWriterContext;

/**
 * Writeable body part entity.
 */
public final class WriteableBodyPart implements BodyPart {

    private final WriteableBodyPartContent content;
    private final WriteableBodyPartHeaders headers;

    private WriteableBodyPart(WriteableBodyPartContent content, WriteableBodyPartHeaders headers) {
        this.content = content;
        this.headers = headers;
    }

    @Override
    public WriteableBodyPartContent content() {
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

        private static final WriteableBodyPartContent EMPTY_BODY_CONTENT = new RawBodyPartContent(Single.empty());

        private WriteableBodyPartHeaders headers;
        private WriteableBodyPartContent content;

        private Builder() {
            headers = WriteableBodyPartHeaders.create();
            content = EMPTY_BODY_CONTENT;
        }

        /**
         * Create a new body part backed by the specified entity.
         * @param entity entity for the body part content
         * @return this builder instance
         */
        public Builder entity(Object entity) {
            content = new EntityBodyPartContent(entity, headers);
            return this;
        }

        /**
         * Create a new body part backed by the specified entity stream.
         * @param <T> stream item type
         * @param stream stream of entities for the body part content
         * @param type actual representation of the entity type
         * @return this builder instance
         */
        public <T> Builder entityStream(Publisher<T> stream, Class<T> type) {
            Objects.requireNonNull(type, "type cannot be null!");
            content = new EntityStreamBodyPartContent<>(stream, GenericType.create(type), headers);
            return this;
        }

        /**
         * Create a new body part backed by the specified publisher.
         * @param publisher publisher for the part content
         * @return this builder instance
         */
        public Builder publisher(Publisher<DataChunk> publisher) {
            content = new RawBodyPartContent(publisher);
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
            return new WriteableBodyPart(content, headers);
        }
    }

    private static final class RawBodyPartContent implements WriteableBodyPartContent {

        private final Publisher<DataChunk> publisher;

        RawBodyPartContent(Publisher<DataChunk> publisher) {
            this.publisher = Objects.requireNonNull(publisher, "entity cannot be null!");
        }

        @Override
        public WriteableBodyPartContent init(MessageBodyWriterContext context) {
            return this;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            publisher.subscribe(subscriber);
        }
    }

    private static final class EntityBodyPartContent implements WriteableBodyPartContent {

        private final Object entity;
        private final GenericType<Object> type;
        private final Parameters headers;
        private Publisher<DataChunk> publisher;

        EntityBodyPartContent(Object entity, Parameters headers) {
            this.entity = Objects.requireNonNull(entity, "entity cannot be null!");
            this.headers = Objects.requireNonNull(headers, "headers cannot be null");
            type = GenericType.<Object>create(entity.getClass());
        }

        @Override
        public WriteableBodyPartContent init(MessageBodyWriterContext context) {
            publisher = MessageBodyWriterContext.create(context, headers).marshall(Single.just(entity), type);
            return this;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            if (publisher == null) {
                subscriber.onSubscribe(SubscriptionHelper.CANCELED);
                subscriber.onError(new IllegalStateException("Not ready yet"));
                return;
            }
            publisher.subscribe(subscriber);
        }
    }

    private static final class EntityStreamBodyPartContent<T> implements WriteableBodyPartContent {

        private final Publisher<T> stream;
        private final GenericType<T> type;
        private final Parameters headers;
        private Publisher<DataChunk> publisher;

        EntityStreamBodyPartContent(Publisher<T> stream, GenericType<T> type, Parameters headers) {
            this.stream = Objects.requireNonNull(stream, "entity cannot be null!");
            this.type = Objects.requireNonNull(type, "type cannot be null!");
            this.headers = Objects.requireNonNull(headers, "headers cannot be null");
        }

        @Override
        public WriteableBodyPartContent init(MessageBodyWriterContext context) {
            publisher = MessageBodyWriterContext.create(headers).marshallStream(stream, type);
            return this;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            if (publisher == null) {
                subscriber.onSubscribe(SubscriptionHelper.CANCELED);
                subscriber.onError(new IllegalStateException("Not ready yet"));
                return;
            }
            publisher.subscribe(subscriber);
        }
    }
}
