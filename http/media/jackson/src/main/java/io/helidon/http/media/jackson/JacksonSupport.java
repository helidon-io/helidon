/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link java.util.ServiceLoader} provider implementation for Jackson media support.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JacksonSupport extends MediaSupportBase<JacksonSupportConfig> implements RuntimeType.Api<JacksonSupportConfig> {
    static final String ID = "jackson";

    private final ObjectMapper objectMapper;
    private final JacksonReader reader;
    private final JacksonWriter writer;

    private JacksonSupport(JacksonSupportConfig jacksonSupportConfig) {
        super(jacksonSupportConfig, ID);

        this.objectMapper = jacksonSupportConfig.objectMapper();
        this.reader = new JacksonReader(objectMapper);
        this.writer = new JacksonWriter(jacksonSupportConfig, objectMapper);
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
     * @deprecated use {@link #create(io.helidon.config.Config)} instead
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "4.4.0")
    public static MediaSupport create(io.helidon.common.config.Config config) {
        return create(config, ID);
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link JacksonSupport}
     */
    public static MediaSupport create(Config config) {
        return create(config, ID);
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @param config must not be {@code null}
     * @param name of the Jackson support
     * @return a new {@link JacksonSupport}
     * @deprecated use {@link #create(io.helidon.config.Config, java.lang.String)} instead
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "4.4.0")
    public static MediaSupport create(io.helidon.common.config.Config config, String name) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(name);

        return create(io.helidon.config.Config.config(config), name);
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
        return create(objectMapper, ID);
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
    public JacksonSupportConfig prototype() {
        return super.config();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected boolean canSerialize(GenericType<?> type) {
        // once the method is removed, just return `true` here
        return objectMapper.canSerialize(type.rawType());
    }

    @SuppressWarnings("deprecation")
    @Override
    protected boolean canDeserialize(GenericType<?> type) {
        // once the method is removed, just return `true` here
        return objectMapper.canDeserialize(objectMapper.constructType(type.type()));
    }

    private <T> EntityReader<T> reader() {
        return reader;
    }

    private <T> EntityWriter<T> writer() {
        return writer;
    }

}
