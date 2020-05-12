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
package io.helidon.media.jackson.common;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyReaderContext;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.concurrent.Flow.Publisher;

/**
 * Message body reader supporting object binding with Jackson.
 */
public final class JacksonBodyReader implements MessageBodyReader<Object> {

    private final ObjectMapper objectMapper;

    private JacksonBodyReader(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean accept(GenericType<?> type, MessageBodyReaderContext context) {
        Class<?> clazz = type.rawType();
        return !CharSequence.class.isAssignableFrom(clazz)
                && objectMapper.canDeserialize(objectMapper.constructType(clazz));
    }

    @Override
    public <U extends Object> Single<U> read(Publisher<DataChunk> publisher,
            GenericType<U> type, MessageBodyReaderContext context) {

        return ContentReaders.readBytes(publisher).map(new BytesToObject<>(type, objectMapper));
    }

    /**
     * Create a new {@link JacksonBodyReader} instance.
     * @param objectMapper object mapper to use
     * @return JacksonBodyWriter
     */
    public static JacksonBodyReader create(ObjectMapper objectMapper) {
        return new JacksonBodyReader(objectMapper);
    }

    private static final class BytesToObject<T> implements Mapper<byte[], T> {

        private final GenericType<? super T> type;
        private final ObjectMapper objectMapper;

        BytesToObject(GenericType<T> type,
                ObjectMapper objectMapper) {

            this.type = type;
            this.objectMapper = objectMapper;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T map(byte[] bytes) {
            try {
                Type t = this.type.type();
                if (t instanceof ParameterizedType) {
                    TypeFactory typeFactory = objectMapper.getTypeFactory();
                    ParameterizedType pt = (ParameterizedType) t;
                    JavaType javaType = typeFactory.constructType(pt);
                    return objectMapper.readValue(bytes, javaType);
                } else {
                    return objectMapper.readValue(bytes, (Class<T>) this.type.rawType());
                }
            } catch (final IOException wrapMe) {
                throw new JacksonRuntimeException(wrapMe.getMessage(), wrapMe);
            }
        }
    }
}
