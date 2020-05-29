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
package io.helidon.media.jackson;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyWriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * Support for Jackson integration.
 *
 * For usage examples navigate to the {@link MediaSupport}
 */
public final class JacksonSupport implements MediaSupport {

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
    public Collection<MessageBodyReader<?>> readers() {
        return List.of(newReader());
    }

    @Override
    public Collection<MessageBodyWriter<?>> writers() {
        return List.of(newWriter());
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
