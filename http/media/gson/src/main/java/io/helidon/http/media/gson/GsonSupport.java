/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static io.helidon.http.HeaderValues.CONTENT_TYPE_JSON;

/**
 * {@link java.util.ServiceLoader} provider implementation for Gson media support.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class GsonSupport implements MediaSupport {
    private final Gson gson;
    private final GsonReader reader;
    private final GsonWriter writer;

    private final String name;

    private GsonSupport(Gson gson, GsonReader reader, GsonWriter writer, String name) {
        this.gson = gson;
        this.reader = reader;
        this.writer = writer;
        this.name = name;
    }

    /**
     * Creates a new {@link GsonSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link GsonSupport}
     */
    public static MediaSupport create(Config config) {
        return create(config, "gson");
    }

    /**
     * Creates a new {@link GsonSupport}.
     *
     * @param config must not be {@code null}
     * @param name of the Gson support
     * @return a new {@link GsonSupport}
     */
    public static MediaSupport create(Config config, String name) {
        Objects.requireNonNull(config, "Config must not be null");
        Objects.requireNonNull(name, "Name must not be null");

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        return create(gson, name);
    }

    /**
     * Creates a new {@link GsonSupport}.
     *
     * @param gson must not be {@code null}
     * @return a new {@link GsonSupport}
     */
    public static MediaSupport create(Gson gson) {
        return create(gson, "gson");
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

        GsonReader reader = new GsonReader(gson);
        GsonWriter writer = new GsonWriter(gson);
        return new GsonSupport(gson, reader, writer, name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "gson";
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (requestHeaders.contentType()
                .map(it -> it.test(MediaTypes.APPLICATION_JSON))
                .orElse(true)) {
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
        }

        return new ReaderResponse<>(SupportLevel.SUPPORTED, this::reader);
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders, Headers responseHeaders) {
        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON) || acceptedType.isWildcardType()) {
                return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
            }
        }

        if (requestHeaders.acceptedTypes().isEmpty()) {
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(
        GenericType<T> type,
        Headers requestHeaders,
        WritableHeaders<?> responseHeaders
    ){
        if (requestHeaders.contains(HeaderNames.CONTENT_TYPE)) {
            if (requestHeaders.contains(CONTENT_TYPE_JSON)) {
                return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
            }
        }

        return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (requestHeaders.contains(HeaderNames.CONTENT_TYPE)) {
            if (requestHeaders.contains(CONTENT_TYPE_JSON)) {
                return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
            }
        }

        return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
    }

    <T> EntityReader<T> reader() {
        return reader;
    }

    <T> EntityWriter<T> writer() {
        return writer;
    }
}
