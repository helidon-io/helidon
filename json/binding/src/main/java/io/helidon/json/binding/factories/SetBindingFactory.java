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

package io.helidon.json.binding.factories;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.Deserializers;
import io.helidon.json.binding.JsonBindingConfigurator;
import io.helidon.json.binding.JsonBindingFactory;
import io.helidon.json.binding.JsonConverter;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.binding.JsonSerializer;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class SetBindingFactory implements JsonBindingFactory<Set<?>> {

    @Override
    public JsonDeserializer<Set<?>> createDeserializer(Class<? extends Set<?>> type) {
        return new SetConverter(type);
    }

    @Override
    public JsonDeserializer<Set<?>> createDeserializer(GenericType<? extends Set<?>> type) {
        return new SetConverter(type.type());
    }

    @Override
    public JsonSerializer<Set<?>> createSerializer(Class<? extends Set<?>> type) {
        return new SetConverter(type);
    }

    @Override
    public JsonSerializer<Set<?>> createSerializer(GenericType<? extends Set<?>> type) {
        return new SetConverter(type.type());
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(Set.class, HashSet.class);
    }

    private static final class SetConverter implements JsonConverter<Set<?>> {

        private final GenericType<Set<?>> type;
        private final Type componentType;
        private JsonDeserializer<Object> deserializer;
        private JsonSerializer<Object> serializer;

        SetConverter(Type type) {
            this.type = GenericType.create(type);
            if (type instanceof ParameterizedType parameterizedType) {
                componentType = parameterizedType.getActualTypeArguments()[0];
            } else {
                componentType = GenericType.OBJECT;
            }
        }

        @Override
        public void serialize(JsonGenerator generator, Set<?> instance, boolean writeNulls) {
            if (instance == null) {
                generator.writeNull();
                return;
            }
            generator.writeArrayStart();
            for (Object value : instance) {
                if (value == null && !writeNulls) {
                    continue;
                }
                serializer.serialize(generator, value, writeNulls);
            }
            generator.writeArrayEnd();
        }

        @Override
        public Set<?> deserialize(JsonParser parser) {
            Set<Object> set = new HashSet<>();
            byte lastByte = parser.currentByte();
            if (lastByte != '[') {
                throw parser.createException("Expected '[' to start an array", lastByte);
            }
            lastByte = parser.nextToken();
            if (lastByte != ']') {
                set.add(Deserializers.deserialize(parser, deserializer));
                lastByte = parser.nextToken();
                while (lastByte == ',') {
                    parser.nextToken();
                    set.add(Deserializers.deserialize(parser, deserializer));
                    lastByte = parser.nextToken();
                }
                if (lastByte != ']') {
                    throw parser.createException("Expected ']'", lastByte);
                }
            }
            return set;
        }

        @Override
        public GenericType<Set<?>> type() {
            return type;
        }

        @Override
        public void configure(JsonBindingConfigurator jsonBindingConfigurator) {
            deserializer = jsonBindingConfigurator.deserializer(componentType);
            serializer = jsonBindingConfigurator.serializer(componentType);
        }
    }

}
