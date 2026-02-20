/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.media.json;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaSupport;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

/**
 * Helidon JSON media support.
 * <p>
 * This media support adds serialization and deserialization for {@link io.helidon.json.JsonValue}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JsonSupport implements MediaSupport, RuntimeType.Api<JsonSupportConfig> {

    static final String HELIDON_JSON_TYPE = "json";

    private final String name;
    private final JsonSupportConfig config;
    private final JsonValueReader reader;
    private final JsonValueWriter writer;

    private JsonSupport(JsonSupportConfig config) {
        this.name = config.name();
        this.config = config;

        this.reader = new JsonValueReader();
        this.writer = new JsonValueWriter(config);
    }

    /**
     * Create a new instance of JSON Support with default configuration.
     *
     * @return json media support for {@link io.helidon.json.JsonValue}
     */
    public static JsonSupport create() {
        return create(JsonSupportConfig.create());
    }

    /**
     * Create a new builder for {@link io.helidon.http.media.json.JsonSupport}.
     *
     * @return a new builder instance
     */
    public static JsonSupportConfig.Builder builder() {
        return JsonSupportConfig.builder();
    }

    /**
     * Create a new Helidon JSON support from a configuration object.
     *
     * @param config the configuration object
     * @return a new {@link io.helidon.http.media.json.JsonSupport} instance
     */
    public static JsonSupport create(JsonSupportConfig config) {
        return new JsonSupport(config);
    }

    /**
     * Create a JSON writer with default settings.
     *
     * @return a writer for {@link io.helidon.json.JsonObject}
     */
    public static EntityWriter<JsonObject> serverResponseWriter() {
        return new JsonValueWriter<>(JsonSupportConfig.create());
    }

    /**
     * Create a new Helidon JSON support using a configuration consumer.
     *
     * @param consumer the consumer to configure the builder
     * @return a new {@link io.helidon.http.media.json.JsonSupport} instance
     */
    static JsonSupport create(Consumer<JsonSupportConfig.Builder> consumer) {
        return JsonSupportConfig.builder()
                .update(consumer)
                .build();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return HELIDON_JSON_TYPE;
    }

    @Override
    public JsonSupportConfig prototype() {
        return config;
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (isSupportedType(type)) {
            if (requestHeaders.contentType()
                    .map(this::isMediaTypeSupported)
                    .orElse(true)) {
                return new ReaderResponse<>(SupportLevel.SUPPORTED, this::reader);
            }
        }
        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {

        // server response writer
        // we can write if JSON is accepted and it is our type
        if (isSupportedType(type) && requestHeaders.isAccepted(config.contentType())) {
            return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
        }

        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        // client response reader
        if (isSupportedType(type)) {
            // if the content type of the response is one we understand, we support it (regardless of accept header)
            if (responseHeaders.contentType().map(this::isMediaTypeSupported).orElse(true)) {
                return new ReaderResponse<>(SupportLevel.SUPPORTED, this::reader);
            }
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (!isSupportedType(type)) {
            return WriterResponse.unsupported();
        }

        // client request writer
        var configuredContentType = requestHeaders.contentType();

        if (configuredContentType.isPresent()) {
            if (isMediaTypeSupported(configuredContentType.get())) {
                return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
            }
            return WriterResponse.unsupported();
        }

        return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
    }

    boolean isSupportedType(GenericType<?> type) {
        return JsonValue.class.isAssignableFrom(type.rawType());
    }

    boolean isMediaTypeSupported(HttpMediaType mediaType) {
        for (MediaType acceptedMediaType : config.acceptedMediaTypes()) {
            if (mediaType.test(acceptedMediaType)) {
                return true;
            }
        }
        return false;
    }

    // force to correct generic
    private <T> EntityReader<T> reader() {
        return reader;
    }

    private <T> EntityWriter<T> writer() {
        return writer;
    }

}
