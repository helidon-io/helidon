/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.Objects;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.media.common.spi.MediaService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * Support for Jackson integration.
 */
public final class JacksonSupport implements MediaService {

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "Media", "Jackson");
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule());

    private static final JacksonSupport DEFAULT_JACKSON = new JacksonSupport(MAPPER);

    private final ObjectMapper objectMapper;

    private JacksonSupport(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Creates new Jackson reader instance.
     *
     * @return Jackson reader instance
     */
    public static JacksonBodyReader reader() {
        return create().newReader();
    }

    /**
     * Creates new Jackson writer instance.
     *
     * @return Jackson writer instance
     */
    public static JacksonBodyWriter writer() {
        return create().newWriter();
    }

    /**
     * Creates new Jackson reader instance.
     *
     * @return Jackson reader instance
     */
    public JacksonBodyReader newReader() {
        return JacksonBodyReader.create(objectMapper);
    }

    /**
     * Creates new Jackson writer instance.
     *
     * @return Jackson writer instance
     */
    public JacksonBodyWriter newWriter() {
        return JacksonBodyWriter.create(objectMapper);
    }

    @Override
    public void register(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
        readerContext.registerReader(newReader());
        writerContext.registerWriter(newWriter());
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @param objectMapper must not be {@code null}
     * @return a new {@link JacksonSupport}
     *
     * @exception NullPointerException if {@code objectMapper}
     * is {@code null}
     */
    public static JacksonSupport create(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper);
        return new JacksonSupport(objectMapper);
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @return a new {@link JacksonSupport}
     */
    public static JacksonSupport create() {
        return DEFAULT_JACKSON;
    }

}
