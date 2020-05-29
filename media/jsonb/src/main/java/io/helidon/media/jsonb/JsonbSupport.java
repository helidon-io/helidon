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
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyWriter;

/**
 * Support for JSON-B integration.
 *
 * For usage examples navigate to the {@link MediaSupport}
 *
 * @see Jsonb
 */
public final class JsonbSupport implements MediaSupport {

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "Media", "JSON-B");
    }

    private static final Jsonb JSON_B = JsonbBuilder.create();
    private static final JsonbSupport DEFAULT = new JsonbSupport(JSON_B);

    private final Jsonb jsonb;

    private JsonbSupport(final Jsonb jsonb) {
        this.jsonb = jsonb;
    }

    /**
     * Creates new JSON-B reader instance.
     *
     * @return JSON-B reader instance
     */
    public static JsonbBodyReader reader() {
        return create().newReader();
    }

    /**
     * Creates new JSON-B writer instance.
     *
     * @return JSON-B writer instance
     */
    public static JsonbBodyWriter writer() {
        return create().newWriter();
    }

    /**
     * Creates new JSON-B reader instance.
     *
     * @return JSON-B reader instance
     */
    public JsonbBodyReader newReader() {
        return JsonbBodyReader.create(jsonb);
    }

    /**
     * Creates new JSON-B writer instance.
     *
     * @return JSON-B writer instance
     */
    public JsonbBodyWriter newWriter() {
        return JsonbBodyWriter.create(jsonb);
    }

    @Override
    public Collection<MessageBodyReader<?>> readers() {
        return List.of(newReader());
    }

    @Override
    public Collection<MessageBodyWriter<?>> writers() {
        return List.of(newWriter());
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
    public static JsonbSupport create(final Jsonb jsonb) {
        Objects.requireNonNull(jsonb);
        return new JsonbSupport(jsonb);
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @return a new {@link JsonbSupport}
     */
    public static JsonbSupport create() {
        return DEFAULT;
    }
}
