/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.media.jackson;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.helidon.common.LazyValue;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * Support for Jackson integration.
 *
 * For usage examples navigate to the {@link MediaSupport}.
 */
public final class JacksonSupport implements MediaSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule());
    private static final LazyValue<JacksonSupport> DEFAULT = LazyValue.create(() -> new JacksonSupport(MAPPER));

    private final JacksonBodyReader reader;
    private final JacksonBodyWriter writer;
    private final JacksonBodyStreamWriter streamWriter;
    private final JacksonEsBodyStreamWriter esStreamWriter;

    private JacksonSupport(final ObjectMapper objectMapper) {
        this.reader = JacksonBodyReader.create(objectMapper);
        this.writer = JacksonBodyWriter.create(objectMapper);
        this.streamWriter = JacksonBodyStreamWriter.create(objectMapper);
        this.esStreamWriter = JacksonEsBodyStreamWriter.create(objectMapper);
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @return a new {@link JacksonSupport}
     */
    public static JacksonSupport create() {
        return DEFAULT.get();
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @param objectMapper must not be {@code null}
     * @return a new {@link JacksonSupport}
     */
    public static JacksonSupport create(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper);
        return new JacksonSupport(objectMapper);
    }

    /**
     * Return a default Jackson entity reader.
     *
     * @return default Jackson body writer instance
     */
    public static MessageBodyReader<Object> reader() {
        return DEFAULT.get().reader;
    }

    /**
     * Create a new Jackson entity reader based on {@link ObjectMapper} instance.
     *
     * @param objectMapper object mapper instance
     * @return new Jackson body reader instance
     */
    public static MessageBodyReader<Object> reader(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper);
        return JacksonBodyReader.create(objectMapper);
    }

    /**
     * Return a default Jackson entity writer.
     *
     * @return default Jackson body writer instance
     */
    public static MessageBodyWriter<Object> writer() {
        return DEFAULT.get().writer;
    }

    /**
     * Create a new Jackson entity writer based on {@link ObjectMapper} instance.
     *
     * @param objectMapper object mapper instance
     * @return new Jackson body writer instance
     */
    public static MessageBodyWriter<Object> writer(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper);
        return JacksonBodyWriter.create(objectMapper);
    }

    /**
     * Return a default Jackson entity stream writer.
     *
     * @return default Jackson body writer stream instance
     */
    public static MessageBodyStreamWriter<Object> streamWriter() {
        return DEFAULT.get().streamWriter;
    }

    /**
     * Create a new Jackson entity stream writer based on {@link ObjectMapper} instance.
     *
     * @param objectMapper object mapper instance
     * @return new Jackson body stream writer instance
     */
    public static MessageBodyStreamWriter<Object> streamWriter(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper);
        return JacksonEsBodyStreamWriter.create(objectMapper);
    }

    /**
     * Return a default Jackson entity event stream writer.
     * This writer is for {@code text/event-stream} content type.
     *
     * @return new Jackson body stream writer instance
     */
    public static MessageBodyStreamWriter<Object> eventStreamWriter() {
        return DEFAULT.get().esStreamWriter;
    }

    /**
     * Create a new Jackson entity stream writer based on {@link ObjectMapper} instance.
     * This writer is for {@code text/event-stream} content type.
     *
     * @param objectMapper object mapper instance
     * @return new Jackson body stream writer instance
     */
    public static MessageBodyStreamWriter<Object> eventStreamWriter(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper);
        return JacksonEsBodyStreamWriter.create(objectMapper);
    }

    /**
     * Return Jackson reader instance.
     *
     * @return Jackson reader instance
     */
    public MessageBodyReader<Object> readerInstance() {
        return reader;
    }

    /**
     * Return Jackson writer instance.
     *
     * @return Jackson writer instance
     */
    public MessageBodyWriter<Object> writerInstance() {
        return writer;
    }

    /**
     * Return Jackson stream writer instance.
     *
     * @return Jackson stream writer instance
     */
    public MessageBodyStreamWriter<Object> streamWriterInstance() {
        return streamWriter;
    }

    /**
     * Return Jackson stream writer instance for {@code text/event-stream} content type.
     *
     * @return Jackson event stream writer instance
     */
    public MessageBodyStreamWriter<Object> eventStreamWriterInstance() {
        return esStreamWriter;
    }

    @Override
    public Collection<MessageBodyReader<?>> readers() {
        return List.of(reader);
    }

    @Override
    public Collection<MessageBodyWriter<?>> writers() {
        return List.of(writer);
    }

    @Override
    public Collection<MessageBodyStreamWriter<?>> streamWriters() {
        return List.of(esStreamWriter, streamWriter);
    }
}
