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
package io.helidon.media.jackson;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Flow;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriterContext;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * TODO javadoc
 */
class JacksonEsBodyStreamWriter implements MessageBodyStreamWriter<Object> {

    private static final MediaType TEXT_EVENT_STREAM_JSON = MediaType
            .parse("text/event-stream;element-type=\"application/json\"");
    private static final byte[] DATA = "data: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NL = "\n\n".getBytes(StandardCharsets.UTF_8);

    private final ObjectMapper objectMapper;

    private JacksonEsBodyStreamWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    static JacksonEsBodyStreamWriter create(ObjectMapper objectMapper) {
        return new JacksonEsBodyStreamWriter(objectMapper);
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        return context.contentType()
//                .or(() -> Optional.ofNullable(context.findAccepted(TEXT_EVENT_STREAM_JSON)))
                .filter(mediaType -> mediaType == TEXT_EVENT_STREAM_JSON)
                .map(it -> PredicateResult.SUPPORTED)
                .orElse(PredicateResult.NOT_SUPPORTED);
    }

    @Override
    public Multi<DataChunk> write(Flow.Publisher<?> publisher, GenericType<?> type, MessageBodyWriterContext context) {
        context.contentType(TEXT_EVENT_STREAM_JSON);
        JacksonBodyWriter.ObjectToChunks objectToChunks = new JacksonBodyWriter.ObjectToChunks(objectMapper, context.charset());
        return Multi.defer(() -> publisher)
                .flatMap(objectToChunks)
                .flatMap(chunk -> Multi.just(
                        DataChunk.create(DATA),
                        chunk,
                        DataChunk.create(NL))
                );
    }
}
