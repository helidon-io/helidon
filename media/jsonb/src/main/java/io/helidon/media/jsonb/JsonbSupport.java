/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.jsonb;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.LazyValue;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyWriter;

/**
 * Support for JSON-B integration.
 *
 * For usage examples navigate to the {@link MediaSupport}.
 *
 * @see Jsonb
 */
public final class JsonbSupport implements MediaSupport {

    private static final Jsonb JSON_B = JsonbBuilder.create();
    private static final LazyValue<JsonbSupport> DEFAULT = LazyValue.create(() -> new JsonbSupport(JSON_B));

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "Media", "JSON-B");
    }

    private final JsonbBodyReader reader;
    private final JsonbBodyWriter writer;

    private JsonbSupport(Jsonb jsonb) {
        this.reader = JsonbBodyReader.create(jsonb);
        this.writer = JsonbBodyWriter.create(jsonb);
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @return a new {@link JsonbSupport}
     */
    public static JsonbSupport create() {
        return DEFAULT.get();
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @param jsonb the JSON-B to use; must not be {@code null}
     *
     * @return a new {@link JsonbSupport}
     *
     * @exception NullPointerException if {@code jsonb} is {@code
     * null}
     */
    public static JsonbSupport create(Jsonb jsonb) {
        Objects.requireNonNull(jsonb);
        return new JsonbSupport(jsonb);
    }

    /**
     * Return a default JSON-B entity reader.
     *
     * @return default JSON-B body writer instance
     */
    public static MessageBodyReader<Object> reader() {
        return DEFAULT.get().reader;
    }

    /**
     * Create a new JSON-B entity reader based on {@link Jsonb} instance.
     *
     * @param jsonb jsonb instance
     * @return new JSON-B body reader instance
     */
    public static MessageBodyReader<Object> reader(Jsonb jsonb) {
        Objects.requireNonNull(jsonb);
        return JsonbBodyReader.create(jsonb);
    }

    /**
     * Return a default JSON-B entity writer.
     *
     * @return default JSON-B body writer instance
     */
    public static MessageBodyWriter<Object> writer() {
        return DEFAULT.get().writer;
    }

    /**
     * Create a new JSON-B entity writer based on {@link Jsonb} instance.
     *
     * @param jsonb jsonb instance
     * @return new JSON-B body writer instance
     */
    public static MessageBodyWriter<Object> writer(Jsonb jsonb) {
        Objects.requireNonNull(jsonb);
        return JsonbBodyWriter.create(jsonb);
    }

    /**
     * Return JSON-B reader instance.
     *
     * @return JSON-B reader instance
     */
    public MessageBodyReader<Object> newReader() {
        return reader;
    }

    /**
     * Return JSON-B writer instance.
     *
     * @return JSON-B writer instance
     */
    public MessageBodyWriter<Object> newWriter() {
        return writer;
    }

    @Override
    public Collection<MessageBodyReader<?>> readers() {
        return List.of(newReader());
    }

    @Override
    public Collection<MessageBodyWriter<?>> writers() {
        return List.of(newWriter());
    }

}
