/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

/**
 * Readable {@link MessageBodyContent}.
 */
@SuppressWarnings("deprecation")
public final class MessageBodyReadableContent
        implements MessageBodyReaders, MessageBodyFilters, MessageBodyContent, io.helidon.common.http.Content {

    private final Publisher<DataChunk> publisher;
    private final MessageBodyReaderContext context;

    /**
     * Create a new readable content backed by the specified publisher.
     * @param publisher content publisher
     * @param context reader context
     */
    MessageBodyReadableContent(Publisher<DataChunk> publisher, MessageBodyReaderContext context) {
        Objects.requireNonNull(publisher, "publisher is null!");
        Objects.requireNonNull(context, "context is null!");
        this.publisher = publisher;
        this.context = context;
    }

    /**
     * Copy constructor.
     * @param orig original context to be copied
     */
    private MessageBodyReadableContent(MessageBodyReadableContent orig) {
        Objects.requireNonNull(orig, "orig is null!");
        this.publisher = orig.publisher;
        this.context = orig.context;
    }

    /**
     * Get the reader context used to unmarshall data.
     *
     * @return MessageBodyReaderContext
     */
    public MessageBodyReaderContext readerContext() {
        return context;
    }

    @Override
    public MessageBodyReadableContent registerFilter(MessageBodyFilter filter) {
        context.registerFilter(filter);
        return this;
    }

    @Override
    public MessageBodyReadableContent registerReader(MessageBodyReader<?> reader) {
        context.registerReader(reader);
        return this;
    }

    @Override
    public MessageBodyReadableContent registerReader(MessageBodyStreamReader<?> reader) {
        context.registerReader(reader);
        return this;
    }

    @Deprecated
    @Override
    public void registerFilter(Function<Publisher<DataChunk>, Publisher<DataChunk>> function) {
        context.registerFilter(p -> function.apply(p));
    }

    @Deprecated
    @Override
    public <T> void registerReader(Class<T> type, io.helidon.common.http.Reader<T> reader) {
        context.registerReader(type, reader);
    }

    @Deprecated
    @Override
    public <T> void registerReader(Predicate<Class<?>> predicate, io.helidon.common.http.Reader<T> reader) {
        context.registerReader(predicate, reader);
    }

    @Override
    public void subscribe(Subscriber<? super DataChunk> subscriber) {
        try {
            context.applyFilters(publisher).subscribe(subscriber);
        } catch (Exception e) {
            subscriber.onError(new IllegalArgumentException("Unexpected exception occurred during publishers chaining", e));
        }
    }

    @Override
    public <T> Single<T> as(final Class<T> type) {
        return context.unmarshall(publisher, GenericType.create(type));
    }

    /**
     * Consumes and converts the content payload into a completion stage of the
     * requested type.
     *
     * @param type the requested type class
     * @param <T> the requested type
     * @return a completion stage of the requested type
     */
    public <T> Single<T> as(final GenericType<T> type) {
        return context.unmarshall(publisher, type);
    }

    /**
     * Consumes and converts the content payload into a stream of entities of
     * the requested type.
     *
     * @param type the requested type class
     * @param <T> the requested type
     * @return a stream of entities
     */
    public <T> Multi<T> asStream(Class<T> type) {
        return asStream(GenericType.create(type));
    }

    /**
     * Consumes and converts the content payload into a stream of entities of
     * the requested type.
     *
     * @param type the requested type class
     * @param <T> the requested type
     * @return a stream of entities
     */
    public <T> Multi<T> asStream(GenericType<T> type) {
        return Multi.create(context.unmarshallStream(publisher, type));
    }

    /**
     * Create a new readable content backed by the given publisher and context.
     * @param publisher content publisher
     * @param context reader context
     * @return MessageBodyReadableContent
     */
    public static MessageBodyReadableContent create(Publisher<DataChunk> publisher, MessageBodyReaderContext context) {
        return new MessageBodyReadableContent(publisher, context);
    }
}
