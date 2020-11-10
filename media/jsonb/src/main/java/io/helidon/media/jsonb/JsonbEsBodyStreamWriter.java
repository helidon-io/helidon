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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;

import javax.json.bind.Jsonb;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriterContext;

/**
 * TODO javadoc
 */
class JsonbEsBodyStreamWriter implements MessageBodyStreamWriter<Object> {

    private static final MediaType TEXT_EVENT_STREAM_JSON = MediaType
            .parse("text/event-stream;element-type=\"application/json\"");
    private static final byte[] DATA = "data: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NL = "\n\n".getBytes(StandardCharsets.UTF_8);

    private final Jsonb jsonb;

    public JsonbEsBodyStreamWriter(Jsonb jsonb) {
        this.jsonb = Objects.requireNonNull(jsonb);
    }

    static JsonbEsBodyStreamWriter create(Jsonb jsonb) {
        return new JsonbEsBodyStreamWriter(jsonb);
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        return context.contentType()
                .or(() -> Optional.of(TEXT_EVENT_STREAM_JSON))
                .filter(mediaType -> mediaType == TEXT_EVENT_STREAM_JSON)
//                .filter(it -> !CharSequence.class.isAssignableFrom(type.rawType()))
                .map(it -> PredicateResult.COMPATIBLE)
                .orElse(PredicateResult.NOT_SUPPORTED);
    }

    @Override
    public Multi<DataChunk> write(Flow.Publisher<?> publisher, GenericType<?> type, MessageBodyWriterContext context) {
        context.contentType(TEXT_EVENT_STREAM_JSON);
        return Multi.defer(() -> publisher)
                .flatMap(m -> Multi.just(
                        DataChunk.create(DATA),
                        DataChunk.create(jsonb.toJson(m).getBytes(StandardCharsets.UTF_8)),
                        DataChunk.create(NL))
                );
    }
}
