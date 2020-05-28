/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.Flow.Publisher;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbException;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyReaderContext;

/**
 * Message body writer supporting object binding with JSON-B.
 */
public class JsonbBodyReader implements MessageBodyReader<Object> {

    private final Jsonb jsonb;

    private JsonbBodyReader(Jsonb jsonb) {
        Objects.requireNonNull(jsonb);
        this.jsonb = jsonb;
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
        return !CharSequence.class.isAssignableFrom(type.rawType())
                ? PredicateResult.COMPATIBLE
                : PredicateResult.NOT_SUPPORTED;
    }

    @Override
    public <U extends Object> Single<U> read(Publisher<DataChunk> publisher,
            GenericType<U> type, MessageBodyReaderContext context) {

        return ContentReaders.readBytes(publisher).map(new BytesToObject<>(type, jsonb));
    }

    /**
     * Create a new {@link JsonbBodyReader} instance.
     * @param jsonb JSON-B instance
     * @return JsonbBodyReader
     */
    public static JsonbBodyReader create(Jsonb jsonb) {
        return new JsonbBodyReader(jsonb);
    }

    private static final class BytesToObject<T> implements Mapper<byte[], T> {

        private final GenericType<? super T> type;
        private final Jsonb jsonb;

        BytesToObject(GenericType<? super T> type, Jsonb jsonb) {
            this.type = type;
            this.jsonb = jsonb;
        }

        @Override
        public T map(byte[] bytes) {
            try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
                return jsonb.fromJson(inputStream, type.type());
            } catch (IOException ex) {
                throw new JsonbException(ex.getMessage(), ex);
            }
        }
    }
}
