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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.JsonBindingConfigurator;
import io.helidon.json.binding.JsonBindingFactory;
import io.helidon.json.binding.JsonConverter;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.binding.JsonSerializer;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class ListBindingFactory implements JsonBindingFactory<List<?>> {

    @Override
    public JsonDeserializer<List<?>> createDeserializer(Class<? extends List<?>> type) {
        return new ListConverter(type);
    }

    @Override
    public JsonDeserializer<List<?>> createDeserializer(GenericType<? extends List<?>> type) {
        return new ListConverter(type.type());
    }

    @Override
    public JsonSerializer<List<?>> createSerializer(Class<? extends List<?>> type) {
        return new ListConverter(type);
    }

    @Override
    public JsonSerializer<List<?>> createSerializer(GenericType<? extends List<?>> type) {
        return new ListConverter(type.type());
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(List.class, ArrayList.class);
    }

    static class ListConverter implements JsonConverter<List<?>> {

        private final GenericType<List<?>> type;
        private final Type componentType;
        private volatile JsonDeserializer<Object> deserializer;
        private volatile JsonSerializer<Object> serializer;

        ListConverter(Type type) {
            this.type = GenericType.create(type);
            if (type instanceof ParameterizedType parameterizedType) {
                componentType = parameterizedType.getActualTypeArguments()[0];
            } else {
                componentType = GenericType.OBJECT;
            }
        }

        @Override
        public void serialize(JsonGenerator generator, List<?> instance, boolean writeNulls) {
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
        public List<?> deserialize(JsonParser parser) {
            byte lastByte = parser.currentByte();
            if (lastByte != '[') {
                throw parser.createException("Expected '[' to start an array", lastByte);
            }
            lastByte = parser.nextToken();
            if (lastByte == ']') {
                return createInstance(0);
            }
            Object v1 = parser.checkNull() ? deserializer.deserializeNull() : deserializer.deserialize(parser);
            lastByte = parser.nextToken();
            if (lastByte == ']') {
                List<Object> list = createInstance(1);
                list.add(v1);
                return list;
            } else if (lastByte != ',') {
                throw parser.createException("Expected ',' or ']'", lastByte);
            }
            parser.nextToken();
            Object v2 = parser.checkNull() ? deserializer.deserializeNull() : deserializer.deserialize(parser);
            lastByte = parser.nextToken();
            if (lastByte == ']') {
                List<Object> list = createInstance(2);
                list.add(v1);
                list.add(v2);
                return list;
            } else if (lastByte != ',') {
                throw parser.createException("Expected ',' or ']'", lastByte);
            }
            parser.nextToken();
            Object v3 = parser.checkNull() ? deserializer.deserializeNull() : deserializer.deserialize(parser);
            lastByte = parser.nextToken();
            if (lastByte == ']') {
                List<Object> list = createInstance(3);
                list.add(v1);
                list.add(v2);
                list.add(v3);
                return list;
            } else if (lastByte != ',') {
                throw parser.createException("Expected ',' or ']'", lastByte);
            }
            List<Object> list = createInstance(10);
            list.add(v1);
            list.add(v2);
            list.add(v3);
            while (lastByte == ',') {
                parser.nextToken();
                list.add(parser.checkNull() ? deserializer.deserializeNull() : deserializer.deserialize(parser));
                lastByte = parser.nextToken();
            }
            if (lastByte != ']') {
                throw parser.createException("Expected ']'", lastByte);
            }
            return list;
        }

        @Override
        public GenericType<List<?>> type() {
            return type;
        }

        @Override
        public void configure(JsonBindingConfigurator jsonBindingConfigurator) {
            deserializer = jsonBindingConfigurator.deserializer(componentType);
            serializer = jsonBindingConfigurator.serializer(componentType);
        }

        List<Object> createInstance(int capacity) {
            return new ArrayList<>(capacity);
        }
    }
}
