/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http.media.jackson;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.http.media.EntityWriter;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

class JacksonWriter<T> implements EntityWriter<T> {
    private final ObjectMapper objectMapper;

    JacksonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(GenericType<T> type,
                      T object,
                      OutputStream outputStream,
                      Headers requestHeaders,
                      WritableHeaders<?> responseHeaders) {

        responseHeaders.setIfAbsent(Http.Headers.CONTENT_TYPE_JSON);

        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON)) {
                Optional<String> charset = acceptedType.charset();
                if (charset.isPresent()) {
                    Charset characterSet = Charset.forName(charset.get());
                    write(type, object, new OutputStreamWriter(outputStream, characterSet));
                } else {
                    write(type, object, outputStream);
                }
                return;
            }
        }

        write(type, object, outputStream);
    }

    @Override
    public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
        headers.setIfAbsent(Http.Headers.CONTENT_TYPE_JSON);
        write(type, object, outputStream);
    }

    private void write(GenericType<T> type, T object, Writer out) {
        try {
            writer(type).writeValue(out, object);
            out.flush();
        } catch (IOException e) {
            throw new JacksonRuntimeException("Failed to serialize to JSON: " + type, e);
        }
    }

    private void write(GenericType<T> type, T object, OutputStream out) {
        try (out) {
            writer(type).writeValue(out, object);
        } catch (IOException e) {
            throw new JacksonRuntimeException("Failed to serialize to JSON: " + type, e);
        }
    }

    private ObjectWriter writer(GenericType<T> type) {
        Type t = type.type();
        if (t instanceof ParameterizedType) {
            JavaType javaType = objectMapper.getTypeFactory().constructType(t);
            return objectMapper.writerFor(javaType);
        } else {
            return objectMapper.writerFor(type.rawType());
        }
    }
}
