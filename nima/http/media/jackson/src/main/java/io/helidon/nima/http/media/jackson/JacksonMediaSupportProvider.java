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

import io.helidon.common.GenericType;
import io.helidon.common.Weighted;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.http.media.EntityReader;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.spi.MediaSupportProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import static io.helidon.common.http.Http.HeaderValues.CONTENT_TYPE_JSON;

/**
 * {@link java.util.ServiceLoader} provider implementation for Jackson media support.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JacksonMediaSupportProvider implements MediaSupportProvider, Weighted {
    private JacksonReader reader;
    private JacksonWriter writer;
    private ObjectMapper objectMapper;

    @Override
    public void init(MediaContext context) {
        objectMapper = new ObjectMapper()
                .registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());

        reader = new JacksonReader(objectMapper);
        writer = new JacksonWriter(objectMapper);
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (requestHeaders.contentType()
                .map(it -> it.test(MediaTypes.APPLICATION_JSON))
                .orElse(true)) {
            if (objectMapper.canDeserialize(objectMapper.constructType(type.type()))) {
                return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
            }
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {
        // check if accepted
        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON)) {
                if (objectMapper.canSerialize(type.rawType())) {
                    return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
                }
                return WriterResponse.unsupported();
            }
        }

        if (requestHeaders.acceptedTypes().isEmpty()) {
            if (objectMapper.canSerialize(type.rawType())) {
                return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
            }
        }

        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        // check if accepted
        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON) || acceptedType.mediaType().isWildcardType()) {
                if (objectMapper.canDeserialize(objectMapper.constructType(type.type()))) {
                    return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
                }
            }
        }

        if (requestHeaders.acceptedTypes().isEmpty()) {
            if (objectMapper.canDeserialize(objectMapper.constructType(type.type()))) {
                return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
            }
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (requestHeaders.contains(Http.Header.CONTENT_TYPE)) {
            if (requestHeaders.contains(CONTENT_TYPE_JSON)) {
                if (objectMapper.canSerialize(type.rawType())) {
                    return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
                }
                return WriterResponse.unsupported();
            }
        } else {
            if (objectMapper.canSerialize(type.rawType())) {
                return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
            }
            return WriterResponse.unsupported();
        }
        return WriterResponse.unsupported();
    }

    @Override
    public double weight() {
        // very low weight, as this covers all, but higher than JSON-B
        return 11;
    }

    <T> EntityReader<T> reader() {
        return reader;
    }

    <T> EntityWriter<T> writer() {
        return writer;
    }
}
