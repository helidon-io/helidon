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

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Predicate;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.ReadOnlyParameters;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

/**
 * Reader message body context.
 * @see MessageBodyReaders
 * @see MessageBodyFilters
 */
public final class MessageBodyReaderContext extends MessageBodyContext implements MessageBodyReaders, MessageBodyFilters {

    /**
     * The default (fallback) charset.
     */
    static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private final ReadOnlyParameters headers;
    private final Optional<MediaType> contentType;
    private final MessageBodyOperators<MessageBodyReader<?>> readers;
    private final MessageBodyOperators<MessageBodyStreamReader<?>> sreaders;

    /**
     * Private to enforce the use of the static factory methods.
     */
    private MessageBodyReaderContext(MessageBodyReaderContext parent, EventListener eventListener, ReadOnlyParameters headers,
            Optional<MediaType> contentType) {

        super(parent, eventListener);
        Objects.requireNonNull(headers, "headers cannot be null!");
        Objects.requireNonNull(contentType, "contentType cannot be null!");
        this.headers = headers;
        this.contentType = contentType;
        if (parent != null) {
            this.readers = new MessageBodyOperators<>(parent.readers);
            this.sreaders = new MessageBodyOperators<>(parent.sreaders);
        } else {
            this.readers = new MessageBodyOperators<>();
            this.sreaders = new MessageBodyOperators<>();
        }
    }

    /**
     * Create a new standalone (non parented) context backed by empty read-only
     * headers.
     */
    private MessageBodyReaderContext() {
        super(null, null);
        this.headers = ReadOnlyParameters.empty();
        this.contentType = Optional.empty();
        this.readers = new MessageBodyOperators<>();
        this.sreaders = new MessageBodyOperators<>();
    }

    private MessageBodyReaderContext(MessageBodyReaderContext parent) {
        super(parent);
        this.headers = parent.headers;
        this.contentType = parent.contentType;
        this.readers = new MessageBodyOperators<>(parent.readers);
        this.sreaders = new MessageBodyOperators<>(parent.sreaders);
    }

    /**
     * Create a new empty reader context backed by empty read-only headers. Such
     * reader context is typically the parent context that is used to hold
     * application wide readers and inbound filters.
     *
     * @return MessageBodyWriterContext
     */
    public static MessageBodyReaderContext create() {
        return new MessageBodyReaderContext();
    }

    @Override
    public MessageBodyReaderContext registerReader(MessageBodyReader<?> reader) {
        readers.registerFirst(reader);
        return this;
    }

    @Override
    public MessageBodyReaderContext registerReader(MessageBodyStreamReader<?> reader) {
        sreaders.registerFirst(reader);
        return this;
    }

    /**
     * Register a reader function with the given type.
     * @param <T> supported type
     * @param type class representing the supported type
     * @param reader reader function
     * @deprecated since 2.0.0 use {@link #registerReader(MessageBodyReader) } instead
     */
    @Deprecated
    public <T> void registerReader(Class<T> type, io.helidon.common.http.Reader<T> reader) {
        readers.registerFirst(new ReaderAdapter<>(type, reader));
    }

    /**
     * Register a reader function with the type predicate.
     * @param <T> supported type
     * @param predicate class predicate
     * @param reader reader function
     * @deprecated since 2.0.0 use {@link #registerReader(MessageBodyReader) } instead
     */
    @Deprecated
    public <T> void registerReader(Predicate<Class<?>> predicate, io.helidon.common.http.Reader<T> reader) {
        readers.registerFirst(new ReaderAdapter<>(predicate, reader));
    }

    /**
     * Convert a given HTTP payload into a publisher by selecting a reader that
     * accepts the specified type and current context.
     *
     * @param <T> entity type
     * @param payload inbound payload
     * @param type actual representation of the entity type
     * @return publisher, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> Single<T> unmarshall(Publisher<DataChunk> payload, GenericType<T> type) {
        if (payload == null) {
            return Single.empty();
        }

        // Flow.Publisher - can only be supported by streaming media
        if (Flow.Publisher.class.isAssignableFrom(type.rawType())) {
            throw new IllegalStateException("This method does not support unmarshalling of Flow.Publisher. Please use "
                                                    + "a stream unmarshalling method.");
        }

        try {
            Publisher<DataChunk> filteredPayload = applyFilters(payload, type);
            if (byte[].class.equals(type.rawType())) {
                return (Single<T>) ContentReaders.readBytes(filteredPayload);
            }
            MessageBodyReader<T> reader = (MessageBodyReader<T>) readers.select(type, this);
            if (reader == null) {
                return readerNotFound(type.getTypeName());
            }
            return reader.read(filteredPayload, type, this);
        } catch (Throwable ex) {
            return transformationFailed(ex);
        }
    }

    /**
     * Convert a given HTTP payload into a publisher by selecting a reader with
     * the specified class.
     *
     * @param <T> entity type
     * @param payload inbound payload
     * @param reader specific reader
     * @param type actual representation of the entity type
     * @return publisher, never {@code null}
     */
    public <T> Single<T> unmarshall(Publisher<DataChunk> payload, MessageBodyReader<T> reader, GenericType<T> type) {
        Objects.requireNonNull(reader);
        if (payload == null) {
            return Single.empty();
        }

        // Flow.Publisher - can only be supported by streaming media
        if (Flow.Publisher.class.isAssignableFrom(type.rawType())) {
            throw new IllegalStateException("This method does not support unmarshalling of Flow.Publisher. Please use "
                                                    + "a stream unmarshalling method.");
        }

        try {
            Publisher<DataChunk> filteredPayload = applyFilters(payload, type);
            return reader.read(filteredPayload, type, this);
        } catch (Throwable ex) {
            return transformationFailed(ex);
        }
    }

    /**
     * Convert a given HTTP payload into a publisher by selecting a
     * stream reader that accepts the specified type and current context.
     *
     * @param <T> entity type
     * @param payload inbound payload
     * @param type actual representation of the entity type
     * @return publisher, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> Publisher<T> unmarshallStream(Publisher<DataChunk> payload, GenericType<T> type) {
        if (payload == null) {
            return Multi.empty();
        }
        try {
            Publisher<DataChunk> filteredPayload = applyFilters(payload, type);
            MessageBodyStreamReader<T> reader = (MessageBodyStreamReader<T>) sreaders.select(type, this);
            if (reader == null) {
                return readerNotFound(type.getTypeName());
            }
            return reader.read(filteredPayload, type, this);
        } catch (Throwable ex) {
            return transformationFailed(ex);
        }
    }

    /**
     * Convert a given HTTP payload into a publisher by selecting a stream
     * reader with the payload class.
     *
     * @param <T> entity type
     * @param payload inbound payload
     * @param reader specific reader
     * @param type actual representation of the entity type
     * @return publisher, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> Publisher<T> unmarshallStream(Publisher<DataChunk> payload, MessageBodyStreamReader<T> reader,
            GenericType<T> type) {
        Objects.requireNonNull(reader);
        if (payload == null) {
            return Multi.empty();
        }
        try {
            Publisher<DataChunk> filteredPayload = applyFilters(payload, type);
            return reader.read(filteredPayload, type, this);
        } catch (Throwable ex) {
            return transformationFailed(ex);
        }
    }

    /**
     * Get the underlying headers.
     *
     * @return Parameters, never {@code null}
     */
    public ReadOnlyParameters headers() {
        return headers;
    }

    /**
     * Get the {@code Content-Type} header.
     *
     * @return Optional, never {@code null}
     */
    public Optional<MediaType> contentType() {
        return contentType;
    }

    @Override
    public Charset charset() throws IllegalStateException {
        if (contentType.isPresent()) {
            try {
                return contentType.get().charset().map(Charset::forName).orElse(DEFAULT_CHARSET);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return DEFAULT_CHARSET;
    }

    /**
     * Creates a new parented reader context.
     *
     * @param parent parent reader context
     * @return new instance of MessageBodyReaderContext
     */
    public static MessageBodyReaderContext create(MessageBodyReaderContext parent) {
        return new MessageBodyReaderContext(parent);
    }

    /**
     * Create a new empty reader context backed by the specified headers.
     *
     * @param mediaContext mediaSupport instance used to derived the parent
     * context, may be {@code null}
     * @param eventListener subscription event listener, may be {@code null}
     * @param headers backing headers, must not be {@code null}
     * @param contentType content type, must not be {@code null}
     * @return MessageBodyReaderContext
     */
    public static MessageBodyReaderContext create(MediaContext mediaContext, EventListener eventListener,
                                                  ReadOnlyParameters headers, Optional<MediaType> contentType) {

        if (mediaContext == null) {
            return new MessageBodyReaderContext(null, eventListener, headers, contentType);
        }
        return new MessageBodyReaderContext(mediaContext.readerContext(), eventListener, headers, contentType);
    }

    /**
     * Create a new empty reader context backed by the specified headers.
     *
     * @param parent parent context, must not be {@code null}
     * @param eventListener subscription event listener, may be {@code null}
     * @param headers backing headers, must not be {@code null}
     * @param contentType content type, must not be {@code null}
     * @return MessageBodyReaderContext
     */
    public static MessageBodyReaderContext create(MessageBodyReaderContext parent, EventListener eventListener,
            ReadOnlyParameters headers, Optional<MediaType> contentType) {

        return new MessageBodyReaderContext(parent, eventListener, headers, contentType);
    }

    /**
     * Create a single that will emit a reader not found error to its subscriber.
     *
     * @param <T> publisher item type
     * @param type reader type that is not found
     * @return Single
     */
    private static <T> Single<T> readerNotFound(String type) {
        return Single.<T>error(new IllegalStateException("No reader found for type: " + type));
    }

    /**
     * Create a single that will emit a transformation failed error to its
     * subscriber.
     *
     * @param <T> publisher item type
     * @param ex exception cause
     * @return Single
     */
    private static <T> Single<T> transformationFailed(Throwable ex) {
        return Single.<T>error(new IllegalStateException("Transformation failed!", ex));
    }

    /**
     * Message body reader adapter for the old deprecated reader.
     * @param <T> reader type
     */
    @SuppressWarnings("deprecation")
    private static final class ReaderAdapter<T> implements MessageBodyReader<T> {

        private final io.helidon.common.http.Reader<T> reader;
        private final Predicate<Class<?>> predicate;
        private final Class<T> clazz;

        ReaderAdapter(Predicate<Class<?>> predicate, io.helidon.common.http.Reader<T> reader) {
            Objects.requireNonNull(predicate, "predicate cannot be null!");
            Objects.requireNonNull(reader, "reader cannot be null!");
            this.reader = reader;
            this.predicate = predicate;
            this.clazz = null;
        }

        ReaderAdapter(Class<T> clazz, io.helidon.common.http.Reader<T> reader) {
            Objects.requireNonNull(clazz, "clazz cannot be null!");
            Objects.requireNonNull(reader, "reader cannot be null!");
            this.reader = reader;
            this.clazz = clazz;
            this.predicate = null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <U extends T> Single<U> read(Publisher<DataChunk> publisher, GenericType<U> type,
                MessageBodyReaderContext context) {

            return Single.create(reader.applyAndCast(publisher, (Class<U>) type.rawType()));
        }

        @Override
        public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
            if (predicate != null) {
                return predicate.test(type.rawType())
                        ? PredicateResult.SUPPORTED
                        : PredicateResult.NOT_SUPPORTED;
            }
            return PredicateResult.supports(clazz, type);
        }
    }
}
