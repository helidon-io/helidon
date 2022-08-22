/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.reactive.media.jsonp;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.reactive.media.common.MessageBodyStreamWriter;
import io.helidon.reactive.media.common.MessageBodyWriterContext;

import jakarta.json.JsonStructure;
import jakarta.json.JsonWriterFactory;

/**
 * Message body writer for {@link JsonStructure} sub-classes (JSON-P).
 * This writer is for {@link io.helidon.common.media.type.MediaTypes#APPLICATION_X_NDJSON} media type.
 */
class JsonpNdBodyStreamWriter implements MessageBodyStreamWriter<JsonStructure> {

    private static final byte[] NL = "\n".getBytes(StandardCharsets.UTF_8);
    public static final HttpMediaType MEDIA_TYPE = HttpMediaType.create(MediaTypes.APPLICATION_X_NDJSON);

    private final JsonWriterFactory jsonWriterFactory;

    JsonpNdBodyStreamWriter(JsonWriterFactory jsonWriterFactory) {
        this.jsonWriterFactory = Objects.requireNonNull(jsonWriterFactory);
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        if (!JsonStructure.class.isAssignableFrom(type.rawType())) {
            return PredicateResult.NOT_SUPPORTED;
        }
        return context.contentType()
                .or(() -> findMediaType(context))
                .filter(mediaType -> mediaType.equals(MEDIA_TYPE))
                .map(it -> PredicateResult.COMPATIBLE)
                .orElse(PredicateResult.NOT_SUPPORTED);
    }

    @Override
    public Multi<DataChunk> write(Flow.Publisher<? extends JsonStructure> publisher,
                                  GenericType<? extends JsonStructure> type,
                                  MessageBodyWriterContext context) {

        HttpMediaType contentType = context.contentType()
                .or(() -> findMediaType(context))
                .orElse(MEDIA_TYPE);

        context.contentType(contentType);

        JsonpBodyWriter.JsonStructureToChunks jsonToChunks = new JsonpBodyWriter.JsonStructureToChunks(true,
                                                                                                       jsonWriterFactory,
                                                                                                       context.charset());

        AtomicBoolean first = new AtomicBoolean(true);

        return Multi.create(publisher)
                .map(jsonToChunks)
                .flatMap(dataChunk -> {
                    if (first.getAndSet(false)) {
                        return Single.just(dataChunk);
                    } else {
                        return Multi.just(DataChunk.create(NL),
                                          dataChunk);
                    }
                });
    }

    private Optional<HttpMediaType> findMediaType(MessageBodyWriterContext context) {
        try {
            return Optional.of(context.findAccepted(MEDIA_TYPE));
        } catch (IllegalStateException ignore) {
            //Not supported. Ignore exception.
            return Optional.empty();
        }
    }

}
