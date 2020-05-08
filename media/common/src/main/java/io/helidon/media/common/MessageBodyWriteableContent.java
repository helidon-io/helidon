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
package io.helidon.media.common;

import java.util.Objects;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.reactive.Single;

/**
 * Implementation of {@code WriteableContent}.
 */
public final class MessageBodyWriteableContent implements MessageBodyContent, MessageBodyWriters, MessageBodyFilters {

    private final Object entity;
    private final Publisher<Object> stream;
    private final GenericType<Object> type;
    private final Publisher<DataChunk> publisher;
    private final MessageBodyWriterContext context;

    /**
     * Create a new writeable content backed by an entity. The created content
     * creates its own standalone (non parented) writer context with the
     * specified headers.
     *
     * @param entity entity object
     * @param headers writer context backing headers
     */
    MessageBodyWriteableContent(Object entity, Parameters headers) {
        Objects.requireNonNull(entity, "entity cannot be null!");
        this.type = GenericType.<Object>create(entity.getClass());
        this.entity = entity;
        this.context = MessageBodyWriterContext.create(headers);
        this.publisher = null;
        this.stream = null;
    }

    /**
     * Create a new writeable content backed by an entity stream.
     * The created content
     * creates its own standalone (non parented) writer context with the
     * specified headers.
     *
     * @param stream entity stream
     * @param type actual type representation
     * @param headers writer context backing headers
     */
    @SuppressWarnings("unchecked")
    MessageBodyWriteableContent(Publisher<Object> stream, GenericType<? extends Object> type, Parameters headers) {
        Objects.requireNonNull(stream, "stream cannot be null!");
        Objects.requireNonNull(type, "type cannot be null!");
        this.stream = stream;
        this.type = (GenericType<Object>) type;
        this.context = MessageBodyWriterContext.create(headers);
        this.entity = null;
        this.publisher = null;
    }

    /**
     * Create a new writeable content backed by a raw publisher.
     * The created content uses an standalone (non parented) writer context with
     * read-only headers.
     * @param publisher raw publisher
     * @param headers writer context backing headers
     */
    MessageBodyWriteableContent(Publisher<DataChunk> publisher, Parameters headers) {
        Objects.requireNonNull(publisher, "publisher cannot be null!");
        this.publisher = publisher;
        this.context = MessageBodyWriterContext.create(headers);
        this.entity = null;
        this.stream = null;
        this.type = null;
    }

    /**
     * Get the writer context used to marshall data.
     *
     * @return MessageBodyWriterContext
     */
    public MessageBodyWriterContext writerContext() {
        return context;
    }

    @Override
    public void subscribe(Subscriber<? super DataChunk> subscriber) {
        toPublisher(null).subscribe(subscriber);
    }

    /**
     * Convert this writeable content to a raw publisher.
     * @param fallback fallback context to use, may be {@code null}
     * @return publisher, never {@code null}
     */
    public Publisher<DataChunk> toPublisher(MessageBodyWriterContext fallback) {
        if (publisher != null) {
            Publisher<DataChunk> pub = context.applyFilters(publisher);
            if (fallback != null) {
                pub = fallback.applyFilters(pub);
            }
            return pub;
        }
        if (entity != null) {
            return context.marshall(Single.just(entity), type, fallback);
        }
        return context.marshallStream(stream, type, fallback);
    }

    @Override
    public MessageBodyWriteableContent registerFilter(MessageBodyFilter filter) {
        context.registerFilter(filter);
        return this;
    }

    @Override
    public MessageBodyWriteableContent registerWriter(MessageBodyWriter<?> writer) {
        context.registerWriter(writer);
        return this;
    }

    @Override
    public MessageBodyWriteableContent registerWriter(MessageBodyStreamWriter<?> writer) {
        context.registerWriter(writer);
        return this;
    }

    /**
     * Registers a writer function with a given type.
     *
     * @param <T> entity type
     * @param type class representing the type supported by this writer
     * @param function writer function
     * @return this {@code MessageBodyWriteableContent} instance
     * @deprecated use {@link #registerWriter(MessageBodyWriter) } instead
     */
    @Deprecated
    public <T> MessageBodyWriteableContent registerWriter(Class<T> type, Function<T, Publisher<DataChunk>> function) {
        context.registerWriter(type, function);
        return this;
    }

    /**
     * Registers a writer function with a given type and media type.
     *
     * @param <T> entity type
     * @param type class representing the type supported by this writer
     * @param contentType the media type
     * @param function writer function
     * @return this {@code MessageBodyWriteableContent} instance
     * @deprecated use {@link #registerWriter(MessageBodyWriter) } instead
     */
    @Deprecated
    public <T> MessageBodyWriteableContent registerWriter(Class<T> type, MediaType contentType,
            Function<? extends T, Publisher<DataChunk>> function) {

        context.registerWriter(type, contentType, function);
        return this;
    }

    /**
     * Registers a writer function with a given predicate.
     *
     * @param <T> entity type
     * @param accept the object predicate
     * @param function writer function
     * @return this {@code MessageBodyWriteableContent} instance
     * @deprecated use {@link #registerWriter(MessageBodyWriter) } instead
     */
    @Deprecated
    public <T> MessageBodyWriteableContent registerWriter(Predicate<?> accept, Function<T, Publisher<DataChunk>> function) {
        context.registerWriter(accept, function);
        return this;
    }

    /**
     * Registers a writer function with a given predicate and media type.
     *
     * @param <T> entity type
     * @param accept the object predicate
     * @param contentType the media type
     * @param function writer function
     * @return this {@code MessageBodyWriteableContent} instance
     * @deprecated use {@link #registerWriter(MessageBodyWriter) } instead
     */
    @Deprecated
    public <T> MessageBodyWriteableContent registerWriter(Predicate<?> accept, MediaType contentType,
            Function<T, Publisher<DataChunk>> function) {

        context.registerWriter(accept, contentType, function);
        return this;
    }

    /**
     * Register a filter function.
     * @param function filter function
     * @deprecated use {@link #registerFilter(MessageBodyFilter)} instead
     */
    @Deprecated
    public void registerFilter(Function<Publisher<DataChunk>, Publisher<DataChunk>> function) {
        context.registerFilter(function);
    }

    /**
     * Create a new writeable content backed by an entity object.
     *
     * @param entity object, must not be {@code null}
     * @param headers writer context backing headers, must not be {@code null}
     * @return MessageBodyWriteableContent
     */
    public static MessageBodyWriteableContent create(Object entity, Parameters headers) {
        return new MessageBodyWriteableContent(entity, headers);
    }

    /**
     * Create a new writeable content backed by an entity stream.
     *
     * @param stream entity stream, must not be {@code null}
     * @param type actual type representation, must not be {@code null}
     * @param headers writer context backing headers, must not be {@code null}
     * @return MessageBodyWriteableContent
     */
    public static MessageBodyWriteableContent create(Publisher<Object> stream, GenericType<? extends Object> type,
            Parameters headers) {

        return new MessageBodyWriteableContent(stream, type, headers);
    }

    /**
     * Create a new writeable content backed by a raw publisher.
     *
     * @param publisher raw publisher
     * @param headers writer context backing headers, must not be {@code null}
     * @return MessageBodyWriteableContent
     */
    public static MessageBodyWriteableContent create(Publisher<DataChunk> publisher, Parameters headers) {
        return new MessageBodyWriteableContent(publisher, headers);
    }
}
