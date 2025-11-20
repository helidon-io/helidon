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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static io.helidon.http.HeaderValues.CONTENT_TYPE_JSON;

/**
 * {@link java.util.ServiceLoader} provider implementation for Gson media support.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class GsonSupport implements MediaSupport, RuntimeType.Api<GsonSupportConfig> {

    private static final Gson GSON = new GsonBuilder().create();

    private final Gson gson;
    private final GsonReader reader;
    private final GsonWriter writer;

    private final String name;
    private final GsonSupportConfig config;

    private GsonSupport(GsonSupportConfig config) {
        this.config = config;
        this.name = config.name();
        this.gson = config.gson();
        this.reader = new GsonReader(gson);
        this.writer = new GsonWriter(gson);
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
    ) {
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

    @Override
    public GsonSupportConfig prototype() {
        return config;
    }

    static class Decorator implements Prototype.BuilderDecorator<GsonSupportConfig.BuilderBase<?, ?>> {

        private static final Map<String, Consumer<GsonBuilder>> BOOLEAN_PROPERTIES;

        static {
            BOOLEAN_PROPERTIES = Map.of("pretty-printing",
                                        GsonBuilder::setPrettyPrinting,
                                        "disable-html-escaping",
                                        GsonBuilder::disableHtmlEscaping,
                                        "disable-inner-class-serialization",
                                        GsonBuilder::disableInnerClassSerialization,
                                        "disable-jdk-unsafe",
                                        GsonBuilder::disableJdkUnsafe,
                                        "enable-complex-map-key-serialization",
                                        GsonBuilder::enableComplexMapKeySerialization,
                                        "exclude-fields-without-expose-annotation",
                                        GsonBuilder::excludeFieldsWithoutExposeAnnotation,
                                        "generate-non-executable-json",
                                        GsonBuilder::generateNonExecutableJson,
                                        "serialize-special-floating-point-values",
                                        GsonBuilder::serializeSpecialFloatingPointValues,
                                        "lenient",
                                        GsonBuilder::setLenient,
                                        "serialize-nulls",
                                        GsonBuilder::serializeNulls);
        }

        @Override
        public void decorate(GsonSupportConfig.BuilderBase<?, ?> target) {
            if (target.gson().isEmpty()) {
                if (target.properties().isEmpty()) {
                    target.gson(GSON);
                } else {
                    GsonBuilder builder = new GsonBuilder();
                    target.properties()
                            .entrySet()
                            .stream()
                            .filter(Map.Entry::getValue)
                            .filter(entry -> BOOLEAN_PROPERTIES.containsKey(entry.getKey()))
                            .forEach(entry -> BOOLEAN_PROPERTIES.get(entry.getKey()).accept(builder));
                    target.gson(builder.create());
                }
            }
        }
    }
}
