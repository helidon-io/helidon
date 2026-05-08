/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.media.jackson;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import io.helidon.builder.api.Prototype;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.EnumFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

final class JacksonConfigSupport {
    private JacksonConfigSupport() {
    }

    static class Decorator implements Prototype.BuilderDecorator<JacksonSupportConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(JacksonSupportConfig.BuilderBase<?, ?> target) {
            if (target.objectMapper().isEmpty()) {
                if (target.properties().isEmpty()) {
                    target.objectMapper(createDefaultObjectMapper());
                } else {
                    JsonMapper.Builder builder = JsonMapper.builder()
                            .enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER)
                            .enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER);
                    configureJsonMapper(builder, target.properties());
                    ObjectMapper objectMapper = builder.build()
                            .registerModule(new ParameterNamesModule())
                            .registerModule(new Jdk8Module())
                            .registerModule(new JavaTimeModule());
                    target.objectMapper(objectMapper);
                }
            }
        }

        private void configureJsonMapper(JsonMapper.Builder jsonMapper, Map<String, Boolean> properties) {
            configure(StreamReadFeature.values(), properties, jsonMapper::configure);
            configure(StreamWriteFeature.values(), properties, jsonMapper::configure);
            configure(DeserializationFeature.values(), properties, jsonMapper::configure);
            configure(SerializationFeature.values(), properties, jsonMapper::configure);
            configure(JsonNodeFeature.values(), properties, jsonMapper::configure);
            configure(JsonParser.Feature.values(), properties, jsonMapper::configure);
            configure(MapperFeature.values(), properties, jsonMapper::configure);
            configure(JsonGenerator.Feature.values(), properties, jsonMapper::configure);
            configure(EnumFeature.values(), properties, jsonMapper::configure);
            configure(JsonNodeFeature.values(), properties, jsonMapper::configure);
        }

        private static <T extends Enum<?>> void configure(T[] values,
                                                          Map<String, Boolean> properties,
                                                          BiConsumer<T, Boolean> consumer) {
            Stream.of(values)
                    .forEach(enumValue -> Optional.ofNullable(properties.get(enumValue.name()))
                            .ifPresent(value -> consumer.accept(enumValue, value)));
        }


        private static ObjectMapper createDefaultObjectMapper() {
            return JsonMapper.builder()
                    .enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER)
                    .enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER)
                    .build()
                    .registerModule(new ParameterNamesModule())
                    .registerModule(new Jdk8Module())
                    .registerModule(new JavaTimeModule());
        }
    }
}
