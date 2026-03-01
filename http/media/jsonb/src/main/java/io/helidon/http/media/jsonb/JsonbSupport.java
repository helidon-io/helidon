/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.GenericType;
import io.helidon.config.Config;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaSupport;
import io.helidon.http.media.MediaSupportBase;

import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import jakarta.json.bind.Jsonb;

/**
 * {@link java.util.ServiceLoader} provider implementation for JSON Binding media support.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JsonbSupport extends MediaSupportBase<JsonbSupportConfig> implements RuntimeType.Api<JsonbSupportConfig> {
    static final String ID = "jsonb";

    private static final GenericType<JsonObject> JSON_OBJECT_TYPE = GenericType.create(JsonObject.class);

    private final JsonbReader reader;
    private final JsonbWriter writer;

    private JsonbSupport(JsonbSupportConfig jsonbSupportConfig) {
        super(jsonbSupportConfig, ID);

        this.reader = new JsonbReader(jsonbSupportConfig.jsonb());
        this.writer = new JsonbWriter(jsonbSupportConfig, jsonbSupportConfig.jsonb());
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
     * @deprecated use {@link #create(io.helidon.config.Config)} instead
     */
    @Deprecated(forRemoval = true, since = "4.4.0")
    @SuppressWarnings("removal")
    public static MediaSupport create(io.helidon.common.config.Config config) {
        return create(config, ID);
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link JsonbSupport}
     */
    public static MediaSupport create(Config config) {
        return create(config, ID);
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @param config must not be {@code null}
     * @param name   name of this instance
     * @return a new {@link JsonbSupport}
     * @see #create(io.helidon.common.config.Config)
     * @deprecated use {@link #create(io.helidon.config.Config, java.lang.String)} instead
     */
    @Deprecated(forRemoval = true, since = "4.4.0")
    @SuppressWarnings("removal")
    public static MediaSupport create(io.helidon.common.config.Config config, String name) {
        return create(Config.config(config), name);
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @param config must not be {@code null}
     * @param name   name of this instance
     * @return a new {@link JsonbSupport}
     * @see #create(io.helidon.config.Config)
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
    public String type() {
        return ID;
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (matchesServerRequest(type, requestHeaders)) {
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {

        if (matchesServerResponse(type, requestHeaders, responseHeaders)) {
            return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
        }

        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        if (matchesClientResponse(type, responseHeaders)) {
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (matchesClientRequest(type, requestHeaders)) {
            return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
        }
        return WriterResponse.unsupported();
    }

    @Override
    protected boolean canSerialize(GenericType<?> type) {
        if (type.isClass()) {
            return !JsonStructure.class.isAssignableFrom(type.rawType());
        }
        return true;
    }

    @Override
    protected boolean canDeserialize(GenericType<?> type) {
        return canSerialize(type);
    }

    @Override
    public JsonbSupportConfig prototype() {
        return config();
    }

    <T> EntityReader<T> reader() {
        return reader;
    }

    <T> EntityWriter<T> writer() {
        return writer;
    }
}
