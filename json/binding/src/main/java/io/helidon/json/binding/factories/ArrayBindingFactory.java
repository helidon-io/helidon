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

import java.lang.reflect.Array;
import java.lang.reflect.Type;
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
class ArrayBindingFactory implements JsonBindingFactory<Object[]> {

    @Override
    public JsonDeserializer<Object[]> createDeserializer(Class<? extends Object[]> type) {
        return new ArrayConverter(type);
    }

    @Override
    public JsonDeserializer<Object[]> createDeserializer(GenericType<? extends Object[]> type) {
        return new ArrayConverter(type.type());
    }

    @Override
    public JsonSerializer<Object[]> createSerializer(Class<? extends Object[]> type) {
        return new ArrayConverter(type);
    }

    @Override
    public JsonSerializer<Object[]> createSerializer(GenericType<? extends Object[]> type) {
        return new ArrayConverter(type.type());
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(Array.class);
    }

    private static class ArrayConverter implements JsonConverter<Object[]> {

        private final GenericType<Object[]> type;
        private final Class<?> componentType;
        private final Object[] emptyArray;
        private JsonDeserializer<Object> deserializer;
        private JsonSerializer<Object> serializer;

        private ArrayConverter(Type type) {
            this.type = GenericType.create(type);
            Class<?> classType = (Class<?>) type;
            this.componentType = classType.componentType();
            emptyArray = createArrayInstance(0);
        }

        @Override
        public void serialize(JsonGenerator generator, Object[] instance, boolean writeNulls) {
            if (instance == null) {
                generator.writeNull();
                return;
            }
            generator.writeArrayStart();
            for (Object value : instance) {
                if (value == null) {
                    serializer.serializeNull(generator);
                } else {
                    serializer.serialize(generator, value, writeNulls);
                }
            }
            generator.writeArrayEnd();
        }

        @Override
        public Object[] deserialize(JsonParser parser) {
            byte lastByte = parser.currentByte();
            if (lastByte != '[') {
                throw parser.createException("Expected '[' to start an array", lastByte);
            }
            Object[] array = createArrayInstance(5);
            lastByte = parser.nextToken();
            int index = 0;
            if (lastByte == ']') {
                return emptyArray;
            }
            array[index++] = Deserializers.deserialize(parser, deserializer);
            lastByte = parser.nextToken();
            while (lastByte == ',') {
                if (index == array.length) {
                    Object[] tmp = createArrayInstance(array.length * 2);
                    System.arraycopy(array, 0, tmp, 0, array.length);
                    array = tmp;
                }
                parser.nextToken();
                array[index++] = Deserializers.deserialize(parser, deserializer);
                lastByte = parser.nextToken();
            }
            if (lastByte != ']') {
                throw parser.createException("Expected ',' or ']'", lastByte);
            }
            if (index == array.length) {
                return array;
            }
            Object[] toReturn = createArrayInstance(index);
            System.arraycopy(array, 0, toReturn, 0, toReturn.length);
            return toReturn;
        }

        @Override
        public GenericType<Object[]> type() {
            return type;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void configure(JsonBindingConfigurator jsonBindingConfigurator) {
            deserializer = (JsonDeserializer<Object>) jsonBindingConfigurator.deserializer(componentType);
            serializer = (JsonSerializer<Object>) jsonBindingConfigurator.serializer(componentType);
        }

        private Object[] createArrayInstance(int size) {
            return (Object[]) Array.newInstance(componentType, size);
        }
    }
}
