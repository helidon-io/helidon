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
package io.helidon.http.media.gson;

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

import com.google.gson.Gson;

/**
 * {@link java.util.ServiceLoader} provider implementation for Gson media support.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class GsonSupport extends MediaSupportBase<GsonSupportConfig> implements RuntimeType.Api<GsonSupportConfig> {
    static final String ID = "gson";

    private final Gson gson;
    private final GsonReader reader;
    private final GsonWriter writer;

    private GsonSupport(GsonSupportConfig config) {
        super(config, ID);

        this.gson = config.gson();
        this.reader = new GsonReader(gson);
        this.writer = new GsonWriter(config, gson);
    }

    /**
     * Creates a new {@link GsonSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link GsonSupport}
     * @deprecated use {@link #create(io.helidon.config.Config)} instead
     */
    @Deprecated(forRemoval = true, since = "4.4.0")
    @SuppressWarnings("removal")
    public static MediaSupport create(io.helidon.common.config.Config config) {
        return create(io.helidon.config.Config.config(config));
    }

    /**
     * Creates a new {@link GsonSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link GsonSupport}
     */
    public static MediaSupport create(Config config) {
        return create(config, ID);
    }

    /**
     * Creates a new {@link GsonSupport}.
     *
     * @param config must not be {@code null}
     * @param name   of the Gson support
     * @return a new {@link GsonSupport}
     * @deprecated use {@link #create(io.helidon.config.Config, java.lang.String)} instead
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "4.4.0")
    public static MediaSupport create(io.helidon.common.config.Config config, String name) {
        return create(Config.config(config), name);
    }

    /**
     * Creates a new {@link GsonSupport}.
     *
     * @param config must not be {@code null}
     * @param name   of the Gson support
     * @return a new {@link GsonSupport}
     */
    public static MediaSupport create(Config config, String name) {
        Objects.requireNonNull(config, "Config must not be null");
        Objects.requireNonNull(name, "Name must not be null");

        return builder()
                .name(name)
                .config(config)
                .build();
    }

    /**
     * Creates a new {@link GsonSupport}.
     *
     * @param gson must not be {@code null}
     * @return a new {@link GsonSupport}
     */
    public static MediaSupport create(Gson gson) {
        return create(gson, ID);
    }

    /**
     * Creates a new {@link GsonSupport}.
     *
     * @param gson must not be {@code null}
     * @param name of the Gson support
     * @return a new {@link GsonSupport}
     */
    public static MediaSupport create(Gson gson, String name) {
        Objects.requireNonNull(gson, "Gson must not be null");
        Objects.requireNonNull(name, "Name must not be null");

        return builder()
                .name(name)
                .gson(gson)
                .build();
    }

    /**
     * Creates a new {@link GsonSupport} based on the {@link GsonSupportConfig}.
     *
     * @param config must not be {@code null}
     * @return a new {@link GsonSupport}
     */
    public static GsonSupport create(GsonSupportConfig config) {
        return new GsonSupport(config);
    }

    /**
     * Creates a new customized {@link GsonSupport}.
     *
     * @param consumer config builder consumer
     * @return a new {@link GsonSupport}
     */
    public static GsonSupport create(Consumer<GsonSupportConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder instance
     */
    public static GsonSupportConfig.Builder builder() {
        return GsonSupportConfig.builder();
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
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders, Headers responseHeaders) {
        if (matchesClientResponse(type, responseHeaders)) {
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
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (matchesClientRequest(type, requestHeaders)) {
            return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
        }

        return WriterResponse.unsupported();
    }

    @Override
    public GsonSupportConfig prototype() {
        return config();
    }

    @Override
    protected boolean canSerialize(GenericType<?> type) {
        return true;
    }

    @Override
    protected boolean canDeserialize(GenericType<?> type) {
        return true;
    }

    <T> EntityReader<T> reader() {
        return reader;
    }

    <T> EntityWriter<T> writer() {
        return writer;
    }
}
