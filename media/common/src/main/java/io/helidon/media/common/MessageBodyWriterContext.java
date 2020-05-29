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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.http.ReadOnlyParameters;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

/**
 * Implementation of {@link MessageBodyWriters}.
 */
public final class MessageBodyWriterContext extends MessageBodyContext implements MessageBodyWriters, MessageBodyFilters {

    /**
     * {@link Mapper} used to map bytes chunks.
     */
    private static final BytesMapper BYTES_MAPPER = new BytesMapper();

    /**
     * Default (fallback) charset.
     */
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private final Parameters headers;
    private final List<MediaType> acceptedTypes;
    private final MessageBodyOperators<MessageBodyWriter<?>> writers;
    private final MessageBodyOperators<MessageBodyStreamWriter<?>> swriters;
    private boolean contentTypeCached;
    private Optional<MediaType> contentTypeCache;
    private boolean charsetCached;
    private Charset charsetCache;

    /**
     * Private to enforce the use of the static factory methods.
     */
    private MessageBodyWriterContext(MessageBodyWriterContext parent, EventListener eventListener, Parameters headers,
            List<MediaType> acceptedTypes) {

        super(parent, eventListener);
        Objects.requireNonNull(headers, "headers cannot be null!");
        this.headers = headers;
        if (acceptedTypes != null) {
            this.acceptedTypes = acceptedTypes;
        } else {
            this.acceptedTypes = List.of();
        }
        if (parent != null) {
            this.writers = new MessageBodyOperators<>(parent.writers);
            this.swriters = new MessageBodyOperators<>(parent.swriters);
        } else {
            this.writers = new MessageBodyOperators<>();
            this.swriters = new MessageBodyOperators<>();
        }
    }

    /**
     * Create a new standalone (non parented) context.
     * @param headers backing headers, may not be {@code null}
     */
    private MessageBodyWriterContext(Parameters headers) {
        super(null, null);
        Objects.requireNonNull(headers, "headers cannot be null!");
        this.headers = headers;
        this.writers = new MessageBodyOperators<>();
        this.swriters = new MessageBodyOperators<>();
        this.acceptedTypes = List.of();
    }

    /**
     * Create a new standalone (non parented) context.
     */
    private MessageBodyWriterContext() {
        super(null, null);
        this.headers = ReadOnlyParameters.empty();
        this.writers = new MessageBodyOperators<>();
        this.swriters = new MessageBodyOperators<>();
        this.acceptedTypes = List.of();
        this.contentTypeCache = Optional.empty();
        this.contentTypeCached = true;
        this.charsetCache = DEFAULT_CHARSET;
        this.charsetCached = true;
    }

    private MessageBodyWriterContext(MessageBodyWriterContext writerContext, Parameters headers) {
        super(writerContext);
        Objects.requireNonNull(headers, "headers cannot be null!");
        this.headers = headers;
        this.writers = new MessageBodyOperators<>(writerContext.writers);
        this.swriters = new MessageBodyOperators<>(writerContext.swriters);
        this.acceptedTypes = List.copyOf(writerContext.acceptedTypes);
        this.contentTypeCache = writerContext.contentTypeCache;
        this.contentTypeCached = writerContext.contentTypeCached;
        this.charsetCache = writerContext.charsetCache;
        this.charsetCached = writerContext.charsetCached;
    }

    /**
     * Create a new writer context.
     *
     * @param mediaContext media support used to derive the parent context, may
     * be {@code null}
     * @param eventListener message body subscription event listener, may be
     * {@code null}
     * @param headers backing headers, must not be {@code null}
     * @param acceptedTypes accepted types, may be {@code null}
     * @return MessageBodyWriterContext
     */
    public static MessageBodyWriterContext create(MediaContext mediaContext, EventListener eventListener, Parameters headers,
                                                  List<MediaType> acceptedTypes) {

        if (mediaContext == null) {
            return new MessageBodyWriterContext(null, eventListener, headers, acceptedTypes);
        }
        return new MessageBodyWriterContext(mediaContext.writerContext(), eventListener, headers, acceptedTypes);
    }

    /**
     * Create a new writer context.
     *
     * @param parent parent context, {@code may be null}
     * @param eventListener message body subscription event listener, may be
     * {@code null}
     * @param headers backing headers, must not be {@code null}
     * @param acceptedTypes accepted types, may be {@code null}
     * @return MessageBodyWriterContext
     */
    public static MessageBodyWriterContext create(MessageBodyWriterContext parent, EventListener eventListener,
            Parameters headers, List<MediaType> acceptedTypes) {

        return new MessageBodyWriterContext(parent, eventListener, headers, acceptedTypes);
    }

    /**
     * Create a new empty writer context backed by the specified headers.
     * @param headers headers
     * @return MessageBodyWriterContext
     */
    public static MessageBodyWriterContext create(Parameters headers) {
        return new MessageBodyWriterContext(headers);
    }

    /**
     * Create a new parented writer context.
     *
     * @param parent parent writer context
     * @return MessageBodyWriterContext
     */
    public static MessageBodyWriterContext create(MessageBodyWriterContext parent) {
        return new MessageBodyWriterContext(parent, parent.headers);
    }

    /**
     * Create a new parented writer context backed by the specified headers.
     *
     * @param parent parent writer context
     * @param headers headers
     * @return MessageBodyWriterContext
     */
    public static MessageBodyWriterContext create(MessageBodyWriterContext parent, Parameters headers) {
        return new MessageBodyWriterContext(parent, headers);
    }

    /**
     * Create a new empty writer context backed by empty read-only headers.
     * Such writer context is typically the parent context that is used to hold
     * application wide writers and outbound filters.
     * @return MessageBodyWriterContext
     */
    public static MessageBodyWriterContext create() {
        return new MessageBodyWriterContext(ReadOnlyParameters.empty());
    }

    @Override
    public MessageBodyWriterContext registerWriter(MessageBodyWriter<?> writer) {
        writers.registerFirst(writer);
        return this;
    }

    @Override
    public MessageBodyWriterContext registerWriter(MessageBodyStreamWriter<?> writer) {
        swriters.registerFirst(writer);
        return this;
    }
    /**
     * Registers a writer function with a given type.
     *
     * @param <T> entity type
     * @param type class representing the type supported by this writer
     * @param function writer function
     * @return this {@code MessageBodyWriteableContent} instance
     * @deprecated since 2.0.0, use {@link #registerWriter(MessageBodyWriter) } instead
     */
    @Deprecated
    public <T> MessageBodyWriterContext registerWriter(Class<T> type, Function<T, Publisher<DataChunk>> function) {
        writers.registerFirst(new WriterAdapter<>(function, type, null));
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
     * @deprecated since 2.0.0, use {@link #registerWriter(MessageBodyWriter) } instead
     */
    @Deprecated
    public <T> MessageBodyWriterContext registerWriter(Class<T> type, MediaType contentType,
            Function<? extends T, Publisher<DataChunk>> function) {

        writers.registerFirst(new WriterAdapter<>(function, type, contentType));
        return this;
    }

    /**
     * Registers a writer function with a given predicate.
     *
     * @param <T> entity type
     * @param accept the object predicate
     * @param function writer function
     * @return this {@code MessageBodyWriteableContent} instance
     * @deprecated since 2.0.0 use {@link #registerWriter(MessageBodyWriter) } instead
     */
    @Deprecated
    public <T> MessageBodyWriterContext registerWriter(Predicate<?> accept, Function<T, Publisher<DataChunk>> function) {
        writers.registerFirst(new WriterAdapter<>(function, accept, null));
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
     * @deprecated since 2.0.0, use {@link #registerWriter(MessageBodyWriter) } instead
     */
    @Deprecated
    public <T> MessageBodyWriterContext registerWriter(Predicate<?> accept, MediaType contentType,
            Function<T, Publisher<DataChunk>> function) {

        writers.registerFirst(new WriterAdapter<>(function, accept, contentType));
        return this;
    }

    /**
     * Convert a given input publisher into HTTP payload by selecting a
     * writer that accepts the specified type and current context.
     *
     * @param <T> entity type parameter
     * @param content input publisher
     * @param type actual representation of the entity type
     * @return publisher, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> Publisher<DataChunk> marshall(Single<T> content, GenericType<T> type) {
        try {
            if (content == null) {
                return applyFilters(Multi.<DataChunk>empty());
            }
            if (byte[].class.equals(type.rawType())) {
                return applyFilters(((Single<byte[]>) content).flatMap(BYTES_MAPPER));
            }

            // Flow.Publisher - can only be supported by streaming media
            if (Publisher.class.isAssignableFrom(type.rawType())) {
                throw new IllegalStateException("This method does not support marshalling of Flow.Publisher. Please use "
                                                        + "a method that accepts Flow.Publisher and type for stream marshalling"
                                                        + ".");
            }

            MessageBodyWriter<T> writer = (MessageBodyWriter<T>) writers.select(type, this);
            if (writer == null) {
                throw new IllegalStateException("No writer found for type: " + type);
            }
            return applyFilters(writer.write(content, type, this));
        } catch (Throwable ex) {
            throw new IllegalStateException("Transformation failed!", ex);
        }
    }

    /**
     * Convert a given input publisher into HTTP payload by selecting a
     * writer with the specified class.
     *
     * @param <T> entity type parameter
     * @param content input publisher
     * @param writerType the requested writer class
     * @param type actual representation of the entity type
     * @return publisher, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> Publisher<DataChunk> marshall(Single<T> content, Class<? extends MessageBodyWriter<T>> writerType,
            GenericType<T> type) {

        try {
            if (content == null) {
                return applyFilters(Multi.<DataChunk>empty());
            }
            MessageBodyWriter<T> writer = (MessageBodyWriter<T>) writers.get(writerType);
            if (writer == null) {
                throw new IllegalStateException("No writer found for type: " + type);
            }
            return applyFilters(writer.write(content, type, this));
        } catch (Throwable ex) {
            throw new IllegalStateException("Transformation failed!", ex);
        }
    }

    /**
     * Convert a given input publisher into HTTP payload by selecting a
     * stream writer that accepts the specified type and current context.
     *
     * @param <T> entity type parameter
     * @param content input publisher
     * @param type actual representation of the entity type
     * @return publisher, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> Publisher<DataChunk> marshallStream(Publisher<T> content, GenericType<T> type) {
        try {
            if (content == null) {
                return applyFilters(Multi.<DataChunk>empty());
            }
            MessageBodyStreamWriter<T> writer = (MessageBodyStreamWriter<T>) swriters.select(type, this);
            if (writer == null) {
                throw new IllegalStateException("No stream writer found for type: " + type);
            }
            return applyFilters(writer.write(content, type, this));
        } catch (Throwable ex) {
            throw new IllegalStateException("Transformation failed!", ex);
        }
    }

    /**
     * Convert a given input publisher into HTTP payload by selecting a
     * stream writer with the specified class.
     *
     * @param <T> entity type parameter
     * @param content input publisher
     * @param writerType the requested writer class
     * @param type actual representation of the entity type
     * @return publisher, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> Publisher<DataChunk> marshallStream(Publisher<T> content, Class<? extends MessageBodyWriter<T>> writerType,
            GenericType<T> type) {

        try {
            if (content == null) {
                return applyFilters(Multi.<DataChunk>empty());
            }
            MessageBodyStreamWriter<T> writer = (MessageBodyStreamWriter<T>) swriters.get(writerType);
            if (writer == null) {
                throw new IllegalStateException("No stream writer found for type: " + type);
            }
            return applyFilters(writer.write(content, type, this));
        } catch (Throwable ex) {
            throw new IllegalStateException("Transformation failed!", ex);
        }
    }

    /**
     * Get the underlying headers.
     *
     * @return Parameters, never {@code null}
     */
    public Parameters headers() {
        return headers;
    }

    /**
     * Get the {@code Content-Type} header.
     *
     * @return Optional, never {@code null}
     */
    public Optional<MediaType> contentType() {
        if (contentTypeCached) {
            return contentTypeCache;
        }
        contentTypeCache = Optional.ofNullable(headers
                .first(Http.Header.CONTENT_TYPE)
                .map(MediaType::parse)
                .orElse(null));
        contentTypeCached = true;
        return contentTypeCache;
    }

    /**
     * Get the inbound {@code Accept} header.
     *
     * @return List never {@code null}
     */
    public List<MediaType> acceptedTypes() {
        return acceptedTypes;
    }

    /**
     * Set the {@code Content-Type} header value in the underlying headers if
     * not present.
     *
     * @param contentType {@code Content-Type} value to set, must not be
     * {@code null}
     */
    public void contentType(MediaType contentType) {
        if (contentType != null) {
            headers.putIfAbsent(Http.Header.CONTENT_TYPE, contentType.toString());
        }
    }

    /**
     * Set the {@code Content-Length} header value in the underlying headers if
     * not present.
     *
     * @param contentLength {@code Content-Length} value to set, must be a
     * positive value
     */
    public void contentLength(long contentLength) {
        if (contentLength >= 0) {
            headers.putIfAbsent(Http.Header.CONTENT_LENGTH, String.valueOf(contentLength));
        }
    }

    /**
     * Find an media type in the inbound {@code Accept} header with the given
     * predicate and default value.
     * <ul>
     * <li>The default value is returned if the predicate matches a media type
     * with a wildcard subtype.<li>
     * <li>The default value if the current {@code Content-Type} header is not
     * set and the inbound {@code Accept} header is empty or missing.</li>
     * <li>When the {@code Content-Type} header is set, if the predicate matches
     * the {@code Content-Type} header value is returned.</li>
     * </ul>
     *
     * @param predicate a predicate to match against the inbound {@code Accept}
     * header
     * @param defaultType a default media type
     * @return MediaType, never {@code null}
     * @throws IllegalStateException if no media type can be returned
     */
    public MediaType findAccepted(Predicate<MediaType> predicate, MediaType defaultType) throws IllegalStateException {
        Objects.requireNonNull(predicate, "predicate cannot be null");
        Objects.requireNonNull(defaultType, "defaultType cannot be null");
        MediaType contentType = contentType().orElse(null);
        if (contentType == null) {
            if (acceptedTypes.isEmpty()) {
                return defaultType;
            } else {
                for (final MediaType acceptedType : acceptedTypes) {
                    if (predicate.test(acceptedType)) {
                        if (acceptedType.isWildcardType() || acceptedType.isWildcardSubtype()) {
                            return defaultType;
                        }
                        return MediaType.create(acceptedType.type(), acceptedType.subtype());
                    }
                }
            }
        } else {
            if (predicate.test(contentType)) {
                return contentType;
            }
        }
        throw new IllegalStateException("No accepted Content-Type");
    }

    /**
     * Find the given media type in the inbound {@code Accept} header.
     *
     * @param mediaType media type to search for
     * @return MediaType, never {@code null}
     * @throws IllegalStateException if the media type is not found
     */
    public MediaType findAccepted(MediaType mediaType) throws IllegalStateException {
        Objects.requireNonNull(mediaType, "mediaType cannot be null");
        for (MediaType acceptedType : acceptedTypes) {
            if (mediaType.equals(acceptedType)) {
                return acceptedType;
            }
        }
        throw new IllegalStateException("No accepted Content-Type");
    }

    @Override
    public Charset charset() throws IllegalStateException {
        if (charsetCached) {
            return charsetCache;
        }
        MediaType contentType = contentType().orElse(null);
        if (contentType != null) {
            try {
                charsetCache = contentType.charset().map(Charset::forName).orElse(DEFAULT_CHARSET);
                charsetCached = true;
                return charsetCache;
            } catch (IllegalCharsetNameException
                    | UnsupportedCharsetException ex) {
                throw new IllegalStateException(ex);
            }
        }
        charsetCache = DEFAULT_CHARSET;
        charsetCached = true;
        return charsetCache;
    }

    /**
     * Message body writer adapter for the old deprecated writer.
     * @param <T> writer type
     */
    private static final class WriterAdapter<T> implements MessageBodyWriter<T> {

        private final Function<T, Publisher<DataChunk>> function;
        private final Predicate predicate;
        private final Class<T> type;
        private final MediaType contentType;

        @SuppressWarnings("unchecked")
        WriterAdapter(Function<T, Publisher<DataChunk>> function, Predicate<?> predicate, MediaType contentType) {
            Objects.requireNonNull(function, "function cannot be null!");
            Objects.requireNonNull(predicate, "predicate cannot be null!");
            this.function = function;
            this.predicate = predicate;
            this.contentType = contentType;
            this.type = null;
        }

        @SuppressWarnings("unchecked")
        WriterAdapter(Function<? extends T, Publisher<DataChunk>> function, Class<T> type, MediaType contentType) {
            Objects.requireNonNull(function, "function cannot be null!");
            Objects.requireNonNull(type, "type cannot be null!");
            this.function = (Function<T, Publisher<DataChunk>>) function;
            this.type = type;
            this.contentType = contentType;
            this.predicate = null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
            if (this.type != null) {
                if (!this.type.isAssignableFrom(type.rawType())) {
                    return PredicateResult.NOT_SUPPORTED;
                }
            } else {
                if (!predicate.test((Object) type.rawType())) {
                    return PredicateResult.NOT_SUPPORTED;
                }
            }
            MediaType ct = context.contentType().orElse(null);
            if (!(contentType != null && ct != null && !ct.test(contentType))) {
                context.contentType(contentType);
                return PredicateResult.SUPPORTED;
            }
            return PredicateResult.NOT_SUPPORTED;
        }

        @Override
        public Publisher<DataChunk> write(Single<? extends T> single,
                                          GenericType<? extends T> type,
                                          MessageBodyWriterContext context) {
            return single.flatMap(function);
        }
    }

    /**
     * Implementation of {@link Mapper} to convert {@code byte[]} to
     * a publisher of {@link DataChunk}.
     */
    private static final class BytesMapper implements Mapper<byte[], Publisher<DataChunk>> {

        @Override
        public Publisher<DataChunk> map(byte[] item) {
            return ContentWriters.writeBytes(item, false);
        }
    }
}
