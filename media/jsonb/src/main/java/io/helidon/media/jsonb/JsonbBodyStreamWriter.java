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
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.json.bind.Jsonb;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriterContext;

import static io.helidon.media.jsonb.JsonbBodyWriter.ObjectToChunks;

/**
 * Message body stream writer supporting object binding with JSON-B.
 */
class JsonbBodyStreamWriter implements MessageBodyStreamWriter<Object> {

    private static final byte[] ARRAY_JSON_END_BYTES = "]".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ARRAY_JSON_BEGIN_BYTES = "[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMA_BYTES = ",".getBytes(StandardCharsets.UTF_8);

    private final Jsonb jsonb;

    private JsonbBodyStreamWriter(Jsonb jsonb) {
        this.jsonb = Objects.requireNonNull(jsonb);
    }

    static JsonbBodyStreamWriter create(Jsonb jsonb) {
        return new JsonbBodyStreamWriter(jsonb);
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        return !CharSequence.class.isAssignableFrom(type.rawType())
                ? PredicateResult.COMPATIBLE
                : PredicateResult.NOT_SUPPORTED;
    }

    @Override
    public Multi<DataChunk> write(Flow.Publisher<?> publisher, GenericType<?> type, MessageBodyWriterContext context) {

        MediaType contentType = context.findAccepted(MediaType.JSON_PREDICATE, MediaType.APPLICATION_JSON);
        context.contentType(contentType);

        AtomicBoolean first = new AtomicBoolean(true);

        ObjectToChunks jsonToChunks = new ObjectToChunks(jsonb, context.charset());

        return Multi.create(publisher)
                .flatMap(jsonToChunks)
                .flatMap(it -> {
                    if (first.getAndSet(false)) {
                        // first record, do not prepend a comma
                        return Multi.just(DataChunk.create(ARRAY_JSON_BEGIN_BYTES), it);
                    } else {
                        // any subsequent record starts with a comma
                        return Multi.just(DataChunk.create(COMMA_BYTES), it);
                    }
                })
                .onCompleteResume(DataChunk.create(ARRAY_JSON_END_BYTES));
    }
}
