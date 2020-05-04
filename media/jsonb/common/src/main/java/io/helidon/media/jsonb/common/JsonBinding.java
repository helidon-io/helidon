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
package io.helidon.media.jsonb.common;

import java.util.Objects;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.media.common.spi.MediaService;

/**
 * Support for JSON-B integration.
 *
 * @see Jsonb
 */
public final class JsonBinding implements MediaService {

    private static final Jsonb JSON_B = JsonbBuilder.create();
    private static final JsonBinding DEFAULT = new JsonBinding(JSON_B);

    private final Jsonb jsonb;

    private JsonBinding(final Jsonb jsonb) {
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
    public void register(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
        readerContext.registerReader(newReader());
        writerContext.registerWriter(newWriter());
    }

    /**
     * Creates a new {@link JsonBinding}.
     *
     * @param jsonb the JSON-B to use; must not be {@code null}
     *
     * @return a new {@link JsonBinding}
     *
     * @exception NullPointerException if {@code jsonb} is {@code
     * null}
     */
    public static JsonBinding create(final Jsonb jsonb) {
        Objects.requireNonNull(jsonb);
        return new JsonBinding(jsonb);
    }

    /**
     * Creates a new {@link JsonBinding}.
     *
     * @return a new {@link JsonBinding}
     */
    public static JsonBinding create() {
        return DEFAULT;
    }
}
