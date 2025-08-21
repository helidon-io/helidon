/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.http.media.jsonb;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

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

import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import static io.helidon.http.HeaderValues.CONTENT_TYPE_JSON;

/**
 * {@link java.util.ServiceLoader} provider implementation for JSON Binding media support.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@RuntimeType.PrototypedBy(JsonbSupportConfig.class)
public class JsonbSupport implements MediaSupport, RuntimeType.Api<JsonbSupportConfig> {
    private static final GenericType<JsonObject> JSON_OBJECT_TYPE = GenericType.create(JsonObject.class);
    private static final String DEFAULT_JSON_B_NAME = "jsonb";

    private static final Jsonb JSON_B = JsonbBuilder.create();

    private final JsonbReader reader;
    private final JsonbWriter writer;
    private final JsonbSupportConfig jsonbSupportConfig;
    private final String name;

    private JsonbSupport(JsonbSupportConfig jsonbSupportConfig) {
        this.jsonbSupportConfig = jsonbSupportConfig;
        this.reader = new JsonbReader(jsonbSupportConfig.jsonb());
        this.writer = new JsonbWriter(jsonbSupportConfig.jsonb());
        this.name = jsonbSupportConfig.name();
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @return a new {@link JsonbSupport}
     */
    public static MediaSupport create() {
        return builder().build();
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link JsonbSupport}
     */
    public static MediaSupport create(Config config) {
        return create(config, DEFAULT_JSON_B_NAME);
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @param config must not be {@code null}
     * @param name   name of this instance
     * @return a new {@link JsonbSupport}
     * @see #create(io.helidon.common.config.Config)
     */
    public static MediaSupport create(Config config, String name) {
        return builder()
                .name(name)
                .config(config)
                .build();
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @param jsonb jsonb instance
     * @return a new instance
     */
    public static MediaSupport create(Jsonb jsonb) {
        return builder()
                .jsonb(jsonb)
                .build();
    }

    /**
     * Creates a new {@link JsonbSupport} based on the {@link JsonbSupportConfig}.
     *
     * @param jsonbSupportConfig must not be {@code null}
     * @return a new {@link JsonbSupport}
     */
    public static JsonbSupport create(JsonbSupportConfig jsonbSupportConfig) {
        Objects.requireNonNull(jsonbSupportConfig);
        return new JsonbSupport(jsonbSupportConfig);
    }

    /**
     * Creates a new customized {@link JsonbSupport}.
     *
     * @param consumer config builder consumer
     * @return a new {@link JsonbSupport}
     */
    public static JsonbSupport create(Consumer<JsonbSupportConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder instance
     */
    public static JsonbSupportConfig.Builder builder() {
        return JsonbSupportConfig.builder();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "jsonb";
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (requestHeaders.contentType()
                .map(it -> it.test(MediaTypes.APPLICATION_JSON))
                .orElse(true)) {
            if (type.equals(JSON_OBJECT_TYPE)) {
                // leave this to JSON-P
                return ReaderResponse.unsupported();
            }
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {
        if (JSON_OBJECT_TYPE.equals(type)) {
            return WriterResponse.unsupported();
        }

        // check if accepted
        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON)) {
                return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
            }
        }

        if (requestHeaders.acceptedTypes().isEmpty()) {
            return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
        }

        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        if (JSON_OBJECT_TYPE.equals(type)) {
            return ReaderResponse.unsupported();
        }

        // check if accepted
        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON) || acceptedType.mediaType().isWildcardType()) {
                return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
            }
        }

        if (requestHeaders.acceptedTypes().isEmpty()) {
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (type.equals(JSON_OBJECT_TYPE)) {
            return WriterResponse.unsupported();
        }
        if (requestHeaders.contains(HeaderNames.CONTENT_TYPE)) {
            if (requestHeaders.contains(CONTENT_TYPE_JSON)) {
                return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
            }
        } else {
            return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
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
    public JsonbSupportConfig prototype() {
        return jsonbSupportConfig;
    }

    static class Decorator implements Prototype.BuilderDecorator<JsonbSupportConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(JsonbSupportConfig.BuilderBase<?, ?> target) {
            Map<String, Object> properties = target.properties();
            target.stringProperties().forEach(properties::putIfAbsent);
            target.booleanProperties().forEach(properties::putIfAbsent);
            target.classProperties().forEach(properties::putIfAbsent);

            if (target.jsonb().isEmpty()) {
                if (properties.isEmpty()) {
                    target.jsonb(JSON_B);
                } else {
                    JsonbConfig jsonbConfig = new JsonbConfig();
                    properties.forEach(jsonbConfig::setProperty);
                    target.jsonb(JsonbBuilder.create(jsonbConfig));
                }
            }
        }

    }
}
