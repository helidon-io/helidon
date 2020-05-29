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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.CharBuffer;
import io.helidon.media.common.ContentWriters;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.common.MessageBodyWriterContext;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Message body writer supporting object binding with Jackson.
 */
public final class JacksonBodyWriter implements MessageBodyWriter<Object> {

    private final ObjectMapper objectMapper;

    private JacksonBodyWriter(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper);
        this.objectMapper = objectMapper;
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        return !CharSequence.class.isAssignableFrom(type.rawType())
                && objectMapper.canSerialize(type.rawType())
                ? PredicateResult.COMPATIBLE
                : PredicateResult.NOT_SUPPORTED;
    }

    @Override
    public Publisher<DataChunk> write(Single<? extends Object> content, GenericType<? extends Object> type,
            MessageBodyWriterContext context) {

        MediaType contentType = context.findAccepted(MediaType.JSON_PREDICATE, MediaType.APPLICATION_JSON);
        context.contentType(contentType);
        return content.flatMap(new ObjectToChunks(objectMapper, context.charset()));
    }

    /**
     * Create a new {@link JacksonBodyWriter} instance.
     * @param objectMapper object mapper to use
     * @return JacksonBodyWriter
     */
    public static JacksonBodyWriter create(ObjectMapper objectMapper) {
        return new JacksonBodyWriter(objectMapper);
    }

    private static final class ObjectToChunks implements Mapper<Object, Publisher<DataChunk>> {

        private final ObjectMapper objectMapper;
        private final Charset charset;

        ObjectToChunks(ObjectMapper objectMapper, Charset charset) {
            this.objectMapper = objectMapper;
            this.charset = charset;
        }

        @Override
        public Publisher<DataChunk> map(Object content) {
            try {
                CharBuffer buffer = new CharBuffer();
                objectMapper.writeValue(buffer, content);
                return ContentWriters.writeCharBuffer(buffer, charset);
            } catch (IOException wrapMe) {
                throw new JacksonRuntimeException(wrapMe.getMessage(), wrapMe);
            }
        }
    }
}
