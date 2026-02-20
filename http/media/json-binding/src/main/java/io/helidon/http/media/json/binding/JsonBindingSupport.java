/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.http.media.json.binding;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaSupport;
import io.helidon.json.binding.JsonBinding;

import static io.helidon.http.HeaderValues.CONTENT_TYPE_JSON;

/**
 * Helidon JSON Binding media support implementation.
 * <p>
 * This class provides comprehensive JSON Binding media support for Helidon HTTP,
 * enabling automatic serialization and deserialization of Java objects to/from
 * JSON format in HTTP requests and responses. It supports content negotiation,
 * character encoding detection, and integrates with the Helidon media support
 * framework.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JsonBindingSupport implements MediaSupport, RuntimeType.Api<JsonBindingSupportConfig> {

    static final String HELIDON_JSON_BINDING_TYPE = "json-binding";

    private final String name;
    private final JsonBindingSupportConfig supportConfig;
    private final JsonBinding jsonBinding;

    private final JsonBindingReader reader;
    private final JsonBindingWriter writer;

    private JsonBindingSupport(JsonBindingSupportConfig supportConfig) {
        this.name = supportConfig.name();
        this.supportConfig = supportConfig;
        this.jsonBinding = supportConfig.jsonBinding();

        this.reader = new JsonBindingReader(jsonBinding);
        this.writer = new JsonBindingWriter(jsonBinding);
    }

    /**
     * Create a new Helidon JSON media support from configuration.
     *
     * @param config the configuration to use
     * @return a new {@link JsonBindingSupport} instance
     */
    public static MediaSupport create(Config config) {
        return create(config, HELIDON_JSON_BINDING_TYPE);
    }

    /**
     * Create a new Helidon JSON Binding media support with default configuration.
     * @return a new {@link JsonBindingSupport} instance
     */
    public static MediaSupport create() {
        return create(JsonBindingSupportConfig.create());
    }

    /**
     * Create a new Helidon JSON media support from configuration with a custom name.
     *
     * @param config the configuration to use
     * @param name the name for this media support instance
     * @return a new MediaSupport instance
     */
    public static MediaSupport create(Config config, String name) {
        return builder()
                .name(name)
                .config(config)
                .build();
    }

    /**
     * Create a new Helidon JSON Binding support from a configuration object.
     *
     * @param config the configuration object
     * @return a new {@link JsonBindingSupport} instance
     */
    public static JsonBindingSupport create(JsonBindingSupportConfig config) {
        return new JsonBindingSupport(config);
    }

    /**
     * Create a new Helidon JSON support using a configuration consumer.
     *
     * @param consumer the consumer to configure the builder
     * @return a new {@link JsonBindingSupport} instance
     */
    public static JsonBindingSupport create(Consumer<JsonBindingSupportConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Create a new builder for {@link JsonBindingSupport}.
     *
     * @return a new builder instance
     */
    public static JsonBindingSupportConfig.Builder builder() {
        return JsonBindingSupportConfig.builder();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return HELIDON_JSON_BINDING_TYPE;
    }

    @Override
    public JsonBindingSupportConfig prototype() {
        return supportConfig;
    }

    <T> EntityReader<T> reader() {
        return reader;
    }

    <T> EntityWriter<T> writer() {
        return writer;
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (requestHeaders.contentType()
                .map(it -> it.test(MediaTypes.APPLICATION_JSON))
                .orElse(true)) {
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
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
        if (requestHeaders.contains(HeaderNames.CONTENT_TYPE)) {
            if (requestHeaders.contains(CONTENT_TYPE_JSON)) {
                return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
            }
        } else {
            return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
        }
        return WriterResponse.unsupported();
    }
}
