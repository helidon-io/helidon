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

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Predicate;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.media.type.MediaType;
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

    private final WritableHeaders<?> headers;
    private final List<HttpMediaType> acceptedTypes;
    private final MessageBodyOperators<MessageBodyWriter<?>> writers;
    private final MessageBodyOperators<MessageBodyStreamWriter<?>> swriters;
    private boolean contentTypeCached;
    private Optional<HttpMediaType> contentTypeCache;
    private boolean charsetCached;
    private Charset charsetCache;

    /**
     * Private to enforce the use of the static factory methods.
     */
    private MessageBodyWriterContext(MessageBodyWriterContext parent,
                                     EventListener eventListener,
                                     WritableHeaders<?> headers,
                                     List<HttpMediaType> acceptedTypes) {

        super(parent, eventListener);
        Objects.requireNonNull(headers, "headers cannot be null!");
        this.headers = headers;
        this.acceptedTypes = Objects.requireNonNullElseGet(acceptedTypes, List::of);
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
    private MessageBodyWriterContext(WritableHeaders<?> headers) {
        super(null, null);
        Objects.requireNonNull(headers, "headers cannot be null!");
        this.headers = headers;
        this.writers = new MessageBodyOperators<>();
        this.swriters = new MessageBodyOperators<>();
        this.acceptedTypes = List.of();
    }

    private MessageBodyWriterContext(MessageBodyWriterContext writerContext, WritableHeaders<?> headers) {
        super(writerContext);
        Objects.requireNonNull(headers, "headers cannot be null!");
        this.headers = headers;
        this.writers = new MessageBodyOperators<>(writerContext.writers);
        this.swriters = new MessageBodyOperators<>(writerContext.swriters);
        this.acceptedTypes = writerContext.acceptedTypes;
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
    public static MessageBodyWriterContext create(MediaContext mediaContext,
                                                  EventListener eventListener,
                                                  WritableHeaders<?> headers,
                                                  List<HttpMediaType> acceptedTypes) {

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
                                                  WritableHeaders<?> headers, List<HttpMediaType> acceptedTypes) {

        return new MessageBodyWriterContext(parent, eventListener, headers, acceptedTypes);
    }

    /**
     * Create a new empty writer context backed by the specified headers.
     * @param headers headers
     * @return MessageBodyWriterContext
     */
    public static MessageBodyWriterContext create(WritableHeaders<?> headers) {
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
    public static MessageBodyWriterContext create(MessageBodyWriterContext parent, WritableHeaders<?> headers) {
        return new MessageBodyWriterContext(parent, headers);
    }

    /**
     * Create a new empty writer context backed by empty read-only headers.
     * Such writer context is typically the parent context that is used to hold
     * application wide writers and outbound filters.
     * @return MessageBodyWriterContext
     */
    public static MessageBodyWriterContext create() {
        return new MessageBodyWriterContext(WritableHeaders.create());
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
                return applyFilters(Multi.empty());
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
                throw new IllegalStateException("No writer found for type: " + type
                        + ". This usually occurs when the appropriate MediaSupport has not been added.");
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
     * @param writer specific writer
     * @param type actual representation of the entity type
     * @return publisher, never {@code null}
     */
    public <T> Publisher<DataChunk> marshall(Single<T> content, MessageBodyWriter<T> writer, GenericType<T> type) {
        Objects.requireNonNull(writer);
        try {
            if (content == null) {
                return applyFilters(Multi.empty());
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
                return applyFilters(Multi.empty());
            }
            MessageBodyStreamWriter<T> writer = (MessageBodyStreamWriter<T>) swriters.select(type, this);
            if (writer == null) {
                throw new IllegalStateException("No stream writer found for type: " + type
                        + ". This usually occurs when the appropriate MediaSupport has not been added.");
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
     * @param writer specific writer
     * @param type actual representation of the entity type
     * @return publisher, never {@code null}
     */
    public <T> Publisher<DataChunk> marshallStream(Publisher<T> content, MessageBodyStreamWriter<T> writer,
            GenericType<T> type) {
        Objects.requireNonNull(writer);
        try {
            if (content == null) {
                return applyFilters(Multi.empty());
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
    public WritableHeaders<?> headers() {
        return headers;
    }

    /**
     * Get the {@code Content-Type} header.
     *
     * @return Optional, never {@code null}
     */
    public Optional<HttpMediaType> contentType() {
        if (contentTypeCached) {
            return contentTypeCache;
        }
        contentTypeCache = headers.contentType();

        contentTypeCached = true;
        return contentTypeCache;
    }

    /**
     * Get the inbound {@code Accept} header.
     *
     * @return List never {@code null}
     */
    public List<HttpMediaType> acceptedTypes() {
        return headers.acceptedTypes();
    }

    /**
     * Set the {@code Content-Type} header value in the underlying headers if
     * not present.
     *
     * @param contentType {@code Content-Type} value to set, must not be
     * {@code null}
     */
    public void contentType(HttpMediaType contentType) {
        Objects.requireNonNull(contentType);
        headers.setIfAbsent(Http.Header.create(Http.Header.CONTENT_TYPE, false, false, contentType.text()));
    }

    /**
     * Set the {@code Content-Type} header value in the underlying headers if
     * not present.
     *
     * @param mediaType {@code Content-Type} value to set, must not be
     * {@code null}
     */
    public void contentType(MediaType mediaType) {
        Objects.requireNonNull(mediaType);
        headers.setIfAbsent(Http.Header.create(Http.Header.CONTENT_TYPE, false, false, mediaType.text()));
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
            headers.setIfAbsent(Http.Header.create(Http.Header.CONTENT_LENGTH, true, false,
                                                   String.valueOf(contentLength)));
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
    public HttpMediaType findAccepted(Predicate<HttpMediaType> predicate, HttpMediaType defaultType)
            throws IllegalStateException {
        Objects.requireNonNull(predicate, "predicate cannot be null");
        Objects.requireNonNull(defaultType, "defaultType cannot be null");

        HttpMediaType contentType = contentType().orElse(null);
        if (contentType == null) {
            if (acceptedTypes.isEmpty()) {
                return defaultType;
            } else {
                for (HttpMediaType acceptedType : acceptedTypes) {
                    if (predicate.test(acceptedType)) {
                        MediaType mt = acceptedType.mediaType();
                        if (mt.isWildcardType() || mt.isWildcardSubtype()) {
                            return defaultType;
                        }
                        if (acceptedType.test(defaultType)) {
                            // if application/json; q=0.4 and default is application/json; charset="UTF-8" I want to use default
                            return defaultType;
                        }
                        // return a type without quality factor and other parameters
                        return HttpMediaType.create(acceptedType.mediaType());
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
    public HttpMediaType findAccepted(HttpMediaType mediaType) throws IllegalStateException {
        Objects.requireNonNull(mediaType, "mediaType cannot be null");
        for (HttpMediaType acceptedType : acceptedTypes) {
            if (mediaType.test(acceptedType)) {
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
        HttpMediaType contentType = contentType().orElse(null);
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
