/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.http.media.jackson;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.helidon.builder.api.Prototype;
import io.helidon.builder.api.RuntimeType;
import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaSupport;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.EnumFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import static io.helidon.http.HeaderValues.CONTENT_TYPE_JSON;

/**
 * {@link java.util.ServiceLoader} provider implementation for Jackson media support.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@RuntimeType.PrototypedBy(JacksonSupportConfig.class)
public class JacksonSupport implements MediaSupport, RuntimeType.Api<JacksonSupportConfig> {
    private final ObjectMapper objectMapper;
    private final JacksonReader reader;
    private final JacksonWriter writer;

    private final String name;
    private final JacksonSupportConfig jacksonSupportConfig;

    private JacksonSupport(JacksonSupportConfig jacksonSupportConfig) {
        this.jacksonSupportConfig = jacksonSupportConfig;
        this.objectMapper = jacksonSupportConfig.objectMapper();
        this.reader = new JacksonReader(objectMapper);
        this.writer = new JacksonWriter(objectMapper);
        this.name = jacksonSupportConfig.name();
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @return a new {@link JacksonSupport}
     */
    public static MediaSupport create() {
        return builder().build();
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link JacksonSupport}
     */
    public static MediaSupport create(Config config) {
        return create(config, "jackson");
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @param config must not be {@code null}
     * @param name of the Jackson support
     * @return a new {@link JacksonSupport}
     */
    public static MediaSupport create(Config config, String name) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(name);

        return builder()
                .name(name)
                .config(config)
                .build();
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @param objectMapper must not be {@code null}
     * @return a new {@link JacksonSupport}
     */
    public static MediaSupport create(ObjectMapper objectMapper) {
        return create(objectMapper, "jackson");
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @param objectMapper must not be {@code null}
     * @param name name of the jackson support to create
     *
     * @return a new {@link JacksonSupport}
     */
    public static MediaSupport create(ObjectMapper objectMapper, String name) {
        Objects.requireNonNull(objectMapper);
        Objects.requireNonNull(name);
        return builder()
                .name(name)
                .objectMapper(objectMapper)
                .build();
    }

    /**
     * Creates a new {@link JacksonSupport} based on the {@link JacksonSupportConfig}.
     *
     * @param jsonbSupportConfig must not be {@code null}
     * @return a new {@link JacksonSupport}
     */
    public static JacksonSupport create(JacksonSupportConfig jsonbSupportConfig) {
        Objects.requireNonNull(jsonbSupportConfig);
        return new JacksonSupport(jsonbSupportConfig);
    }

    /**
     * Creates a new customized {@link JacksonSupport}.
     *
     * @param consumer config builder consumer
     * @return a new {@link JacksonSupport}
     */
    public static JacksonSupport create(Consumer<JacksonSupportConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder instance
     */
    public static JacksonSupportConfig.Builder builder() {
        return JacksonSupportConfig.builder();
    }

    private static ObjectMapper createDefaultObjectMapper() {
        return JsonMapper.builder()
                .enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER)
                .enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER)
                .build()
                .registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "jackson";
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (requestHeaders.contentType()
                .map(it -> it.test(MediaTypes.APPLICATION_JSON))
                .orElse(true)) {
            if (objectMapper.canDeserialize(objectMapper.constructType(type.type()))) {
                return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
            }
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {
        // check if accepted
        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON)) {
                if (objectMapper.canSerialize(type.rawType())) {
                    return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
                }
                return WriterResponse.unsupported();
            }
        }

        if (requestHeaders.acceptedTypes().isEmpty()) {
            if (objectMapper.canSerialize(type.rawType())) {
                return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
            }
        }

        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        // check if accepted
        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON) || acceptedType.mediaType().isWildcardType()) {
                if (objectMapper.canDeserialize(objectMapper.constructType(type.type()))) {
                    return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
                }
            }
        }

        if (requestHeaders.acceptedTypes().isEmpty()) {
            if (objectMapper.canDeserialize(objectMapper.constructType(type.type()))) {
                return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
            }
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (requestHeaders.contains(HeaderNames.CONTENT_TYPE)) {
            if (requestHeaders.contains(CONTENT_TYPE_JSON)) {
                if (objectMapper.canSerialize(type.rawType())) {
                    return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
                }
                return WriterResponse.unsupported();
            }
        } else {
            if (objectMapper.canSerialize(type.rawType())) {
                return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
            }
            return WriterResponse.unsupported();
        }
        return WriterResponse.unsupported();
    }

    <T> EntityReader<T> reader() {
        return reader;
    }

    <T> EntityWriter<T> writer() {
        return writer;
    }

    @Override
    public JacksonSupportConfig prototype() {
        return jacksonSupportConfig;
    }

    static class Decorator implements Prototype.BuilderDecorator<JacksonSupportConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(JacksonSupportConfig.BuilderBase<?, ?> target) {
            if (target.objectMapper().isEmpty()) {
                if (target.properties().isEmpty()) {
                    target.objectMapper(createDefaultObjectMapper());
                } else {
                    JsonMapper.Builder builder = JsonMapper.builder()
                            .enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER)
                            .enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER);
                    configureJsonMapper(builder, target.properties());
                    ObjectMapper objectMapper = builder.build()
                            .registerModule(new ParameterNamesModule())
                            .registerModule(new Jdk8Module())
                            .registerModule(new JavaTimeModule());
                    target.objectMapper(objectMapper);
                }
            }
        }

        private void configureJsonMapper(JsonMapper.Builder jsonMapper, Map<String, Boolean> properties) {
            configure(StreamReadFeature.values(), properties, jsonMapper::configure);
            configure(StreamWriteFeature.values(), properties, jsonMapper::configure);
            configure(DeserializationFeature.values(), properties, jsonMapper::configure);
            configure(SerializationFeature.values(), properties, jsonMapper::configure);
            configure(JsonNodeFeature.values(), properties, jsonMapper::configure);
            configure(JsonParser.Feature.values(), properties, jsonMapper::configure);
            configure(MapperFeature.values(), properties, jsonMapper::configure);
            configure(JsonGenerator.Feature.values(), properties, jsonMapper::configure);
            configure(EnumFeature.values(), properties, jsonMapper::configure);
            configure(JsonNodeFeature.values(), properties, jsonMapper::configure);
        }

        private static <T extends Enum<?>> void configure(T[] values,
                                                          Map<String, Boolean> properties,
                                                          BiConsumer<T, Boolean> consumer) {
            Stream.of(values)
                    .forEach(enumValue -> Optional.ofNullable(properties.get(enumValue.name()))
                            .ifPresent(value -> consumer.accept(enumValue, value)));
        }
    }
}
