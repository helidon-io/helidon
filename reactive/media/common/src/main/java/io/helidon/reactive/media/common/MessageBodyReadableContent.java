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
package io.helidon.reactive.media.common;

import java.util.Objects;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

/**
 * Readable {@link MessageBodyContent}.
 */
public final class MessageBodyReadableContent
        implements MessageBodyReaders, MessageBodyFilters, MessageBodyContent, Multi<DataChunk> {

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

    @Override
    public void subscribe(Subscriber<? super DataChunk> subscriber) {
        try {
            context.applyFilters(publisher).subscribe(subscriber);
        } catch (Exception e) {
            subscriber.onError(new IllegalArgumentException("Unexpected exception occurred during publishers chaining", e));
        }
    }

    /**
     * Consumes and converts the request content into a completion stage of the requested type.
     * <p>
     * The conversion requires an appropriate reader to be already registered
     * (see {@link #registerReader(MessageBodyReader)}). If no such reader is found, the
     * resulting completion stage ends exceptionally.
     * <p>
     * Any callback related to the returned value, should not be blocking. Blocking operation could cause deadlock.
     * If you need to use blocking API such as {@link java.io.InputStream} it is highly recommended to do so out of
     * the scope of reactive chain, or to use methods like
     * {@link java.util.concurrent.CompletionStage#thenAcceptAsync(java.util.function.Consumer, java.util.concurrent.Executor)}.
     *
     * @param <T>  the requested type
     * @param type the requested type class
     * @return a completion stage of the requested type
     */
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
