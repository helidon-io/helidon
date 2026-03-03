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
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaSupportBase;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

/**
 * Helidon JSON media support.
 * <p>
 * This media support adds serialization and deserialization for {@link io.helidon.json.JsonValue}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JsonSupport extends MediaSupportBase<JsonSupportConfig> implements RuntimeType.Api<JsonSupportConfig> {

    static final String ID = "json";

    private final JsonValueReader reader;
    private final JsonValueWriter writer;

    private JsonSupport(JsonSupportConfig config) {
        super(config, ID);

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
    public JsonSupportConfig prototype() {
        return config();
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
        return JsonValue.class.isAssignableFrom(type.rawType());
    }

    @Override
    protected boolean canDeserialize(GenericType<?> type) {
        return canSerialize(type);
    }

    // force to correct generic
    private <T> EntityReader<T> reader() {
        return reader;
    }

    private <T> EntityWriter<T> writer() {
        return writer;
    }

}
