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
package io.helidon.media.jsonp;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.json.JsonStructure;
import javax.json.JsonWriterFactory;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.media.jsonp.JsonpBodyWriter.JsonStructureToChunks;

/**
 * Message body writer for {@link JsonStructure} sub-classes (JSON-P).
 * This writer is for {@link MediaType#APPLICATION_X_NDJSON} media type.
 */
class JsonpNdBodyStreamWriter implements MessageBodyStreamWriter<JsonStructure> {

    private static final byte[] NL = "\n".getBytes(StandardCharsets.UTF_8);

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
                .filter(mediaType -> mediaType.equals(MediaType.APPLICATION_X_NDJSON))
                .map(it -> PredicateResult.COMPATIBLE)
                .orElse(PredicateResult.NOT_SUPPORTED);
    }

    @Override
    public Multi<DataChunk> write(Flow.Publisher<? extends JsonStructure> publisher,
                                  GenericType<? extends JsonStructure> type,
                                  MessageBodyWriterContext context) {

        MediaType contentType = context.contentType()
                .or(() -> findMediaType(context))
                .orElse(MediaType.APPLICATION_X_NDJSON);

        context.contentType(contentType);

        JsonStructureToChunks jsonToChunks = new JsonStructureToChunks(true,
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

    private Optional<MediaType> findMediaType(MessageBodyWriterContext context) {
        try {
            return Optional.of(context.findAccepted(MediaType.APPLICATION_X_NDJSON));
        } catch (IllegalStateException ignore) {
            //Not supported. Ignore exception.
            return Optional.empty();
        }
    }

}
