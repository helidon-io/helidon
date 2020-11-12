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
 * Message body stream writer supporting object binding with JSON-B.
 * This writer is for {@link MediaType#TEXT_EVENT_STREAM} with no element-type parameter or element-type="application/json".
 */
class JsonbEsBodyStreamWriter implements MessageBodyStreamWriter<Object> {

    private static final MediaType TEXT_EVENT_STREAM_JSON = MediaType
            .parse("text/event-stream;element-type=\"application/json\"");
    private static final byte[] DATA = "data: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NL = "\n\n".getBytes(StandardCharsets.UTF_8);

    private final Jsonb jsonb;

    private JsonbEsBodyStreamWriter(Jsonb jsonb) {
        this.jsonb = Objects.requireNonNull(jsonb);
    }

    static JsonbEsBodyStreamWriter create(Jsonb jsonb) {
        return new JsonbEsBodyStreamWriter(jsonb);
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        if (CharSequence.class.isAssignableFrom(type.rawType())) {
            return PredicateResult.NOT_SUPPORTED;
        }
        return context.contentType()
                .or(() -> findMediaType(context))
                .filter(mediaType -> mediaType.equals(TEXT_EVENT_STREAM_JSON) || mediaType.equals(MediaType.TEXT_EVENT_STREAM))
                .map(it -> PredicateResult.COMPATIBLE)
                .orElse(PredicateResult.NOT_SUPPORTED);
    }

    @Override
    public Multi<DataChunk> write(Flow.Publisher<?> publisher, GenericType<?> type, MessageBodyWriterContext context) {
        MediaType contentType = context.contentType()
                .or(() -> findMediaType(context))
                .orElse(TEXT_EVENT_STREAM_JSON);
        context.contentType(contentType);
        return Multi.create(publisher)
                .flatMap(m -> Multi.just(
                        DataChunk.create(DATA),
                        DataChunk.create(jsonb.toJson(m).getBytes(StandardCharsets.UTF_8)),
                        DataChunk.create(NL))
                );
    }

    private Optional<MediaType> findMediaType(MessageBodyWriterContext context) {
        try {
            return Optional.of(context.findAccepted(MediaType.JSON_EVENT_STREAM_PREDICATE, TEXT_EVENT_STREAM_JSON));
        } catch (IllegalStateException ignore) {
            //Not supported. Ignore exception.
            return Optional.empty();
        }
    }
}
