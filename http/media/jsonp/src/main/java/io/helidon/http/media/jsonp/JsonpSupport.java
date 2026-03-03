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

package io.helidon.http.media.jsonp;

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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;

/**
 * Media support implementation for JSON Processing media support.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JsonpSupport extends MediaSupportBase<JsonpSupportConfig> implements RuntimeType.Api<JsonpSupportConfig> {
    /**
     * Json object generic type.
     */
    public static final GenericType<JsonObject> JSON_OBJECT_TYPE = GenericType.create(JsonObject.class);
    /**
     * Json array generic type.
     */
    public static final GenericType<JsonArray> JSON_ARRAY_TYPE = GenericType.create(JsonArray.class);

    static final String ID = "jsonp";

    private static final JsonpSupportConfig DEFAULT_CONFIG = JsonpSupportConfig.create();
    private static final System.Logger LOGGER = System.getLogger(JsonpSupport.class.getName());

    private final JsonpReader reader;
    private final JsonpWriter writer;

    private JsonpSupport(JsonpSupportConfig jsonpSupportConfig) {
        super(jsonpSupportConfig, ID);

        this.reader = new JsonpReader(jsonpSupportConfig);
        this.writer = new JsonpWriter(jsonpSupportConfig);
    }

    /**
     * Creates a new {@link JsonpSupport}.
     *
     * @return a new {@link JsonpSupport}
     */
    public static MediaSupport create() {
        return builder().build();
    }

    /**
     * Creates a new {@link JsonpSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link JsonpSupport}
     * @deprecated use {@link #create(io.helidon.config.Config)} instead
     */
    @Deprecated(forRemoval = true, since = "4.4.0")
    @SuppressWarnings("removal")
    public static MediaSupport create(io.helidon.common.config.Config config) {
        return create(config, ID);
    }

    /**
     * Creates a new {@link JsonpSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link JsonpSupport}
     */
    public static MediaSupport create(Config config) {
        return create(config, ID);
    }

    /**
     * Creates a new named {@link JsonpSupport}.
     *
     * @param config must not be {@code null}
     * @param name name of the support
     * @return a new {@link JsonpSupport}
     * @deprecated use {@link #create(io.helidon.config.Config, java.lang.String)} instead
     */
    @Deprecated(forRemoval = true, since = "4.4.0")
    @SuppressWarnings("removal")
    public static MediaSupport create(io.helidon.common.config.Config config, String name) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(name);
        return builder()
                .name(name)
                .build();
    }

    /**
     * Creates a new named {@link JsonpSupport}.
     *
     * @param config must not be {@code null}
     * @param name name of the support
     * @return a new {@link JsonpSupport}
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
     * Creates a new {@link JsonpSupport} based on the {@link JsonpSupportConfig}.
     *
     * @param jsonpSupportConfig must not be {@code null}
     * @return a new {@link JsonpSupport}
     */
    public static JsonpSupport create(JsonpSupportConfig jsonpSupportConfig) {
        Objects.requireNonNull(jsonpSupportConfig);
        return new JsonpSupport(jsonpSupportConfig);
    }

    /**
     * Creates a new customized {@link JsonpSupport}.
     *
     * @param consumer config builder consumer
     * @return a new {@link JsonpSupport}
     */
    public static JsonpSupport create(Consumer<JsonpSupportConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder instance
     */
    public static JsonpSupportConfig.Builder builder() {
        return JsonpSupportConfig.builder();
    }

    /**
     * Server response writer direct access.
     *
     * @param <T> type to write
     * @return a writer to write JSON-P objects
     */
    public static <T extends JsonStructure> EntityWriter<T> serverResponseWriter() {
        return new JsonpWriter<>(DEFAULT_CONFIG);
    }

    /**
     * Server request reader direct access.
     *
     * @param <T> type to read
     * @return a reader to read JSON-P objects
     */
    public static <T extends JsonStructure> EntityReader<T> serverRequestReader() {
        return new JsonpReader<>(DEFAULT_CONFIG);
    }

    @Override
    public String type() {
        return ID;
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (matchesServerRequest(type, requestHeaders)) {
            return new ReaderResponse<>(SupportLevel.SUPPORTED, this::reader);
        }
        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {

        if (matchesServerResponse(type, requestHeaders, responseHeaders)) {
            return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
        }

        if (LOGGER.isLoggable(System.Logger.Level.TRACE) && canSerialize(type)) {
            LOGGER.log(System.Logger.Level.TRACE, "Refusing writer for " + type.rawType().getName()
                    + ", request headers: " + requestHeaders + ", response headers: " + responseHeaders);
        }

        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {

        if (matchesClientResponse(type, responseHeaders)) {
            return new ReaderResponse<>(SupportLevel.SUPPORTED, this::reader);
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (matchesClientRequest(type, requestHeaders)) {
            return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
        }
        return WriterResponse.unsupported();
    }

    @Override
    protected boolean canSerialize(GenericType<?> type) {
        return JsonStructure.class.isAssignableFrom(type.rawType());
    }

    @Override
    protected boolean canDeserialize(GenericType<?> type) {
        return canSerialize(type);
    }

    @Override
    public JsonpSupportConfig prototype() {
        return config();
    }

    <T> EntityReader<T> reader() {
        return reader;
    }

    <T> EntityWriter<T> writer() {
        return writer;
    }
}
