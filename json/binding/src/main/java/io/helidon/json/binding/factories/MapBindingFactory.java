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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.JsonException;
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
class MapBindingFactory implements JsonBindingFactory<Map<?, ?>> {

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(Map.class, HashMap.class);
    }

    @Override
    public JsonDeserializer<Map<?, ?>> createDeserializer(Class<? extends Map<?, ?>> type) {
        return new MapConverter(type);
    }

    @Override
    public JsonDeserializer<Map<?, ?>> createDeserializer(GenericType<? extends Map<?, ?>> type) {
        return new MapConverter(type.type());
    }

    @Override
    public JsonSerializer<Map<?, ?>> createSerializer(GenericType<? extends Map<?, ?>> type) {
        return new MapConverter(type.type());
    }

    @Override
    public JsonSerializer<Map<?, ?>> createSerializer(Class<? extends Map<?, ?>> type) {
        return new MapConverter(type);
    }

    private static final class MapConverter implements JsonConverter<Map<?, ?>> {

        private final GenericType<Map<?, ?>> type;
        private final Type keyType;
        private final Type valueType;
        private JsonDeserializer<Object> keyDeserializer;
        private JsonDeserializer<Object> valueDeserializer;
        private JsonSerializer<Object> keySerializer;
        private JsonSerializer<Object> valueSerializer;

        MapConverter(Type type) {
            this.type = GenericType.create(type);
            if (type instanceof ParameterizedType parameterizedType) {
                keyType = parameterizedType.getActualTypeArguments()[0];
                valueType = parameterizedType.getActualTypeArguments()[1];
            } else {
                keyType = GenericType.OBJECT;
                valueType = GenericType.OBJECT;
            }
        }

        @Override
        public void serialize(JsonGenerator generator, Map<?, ?> instance, boolean writeNulls) {
            if (instance == null) {
                generator.writeNull();
                return;
            }
            generator.writeObjectStart();
            for (var entry : instance.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                generator.writeKey(keySerializer.serializeAsMapKey(key));
                if (value == null) {
                    valueSerializer.serializeNull(generator);
                } else {
                    valueSerializer.serialize(generator, value, writeNulls);
                }
            }
            generator.writeObjectEnd();
        }

        @Override
        public Map<?, ?> deserialize(JsonParser parser) {
            Map<Object, Object> map = new HashMap<>();
            byte lastByte = parser.currentByte();
            if (lastByte != '{') {
                throw parser.createException("Expected '{' to start a map", lastByte);
            }
            lastByte = parser.nextToken();
            if (lastByte != '}') {
                if (lastByte != '"') {
                    throw parser.createException("Expected a map key", lastByte);
                }
                Object key = Deserializers.deserialize(parser, keyDeserializer);
                lastByte = parser.nextToken();
                if (lastByte != ':') {
                    throw parser.createException("Expected ':' to separate key and value", lastByte);
                }
                parser.nextToken();
                Object value = Deserializers.deserialize(parser, valueDeserializer);
                map.put(key, value);
                lastByte = parser.nextToken();
                while (lastByte == ',') {
                    parser.nextToken();
                    key = Deserializers.deserialize(parser, keyDeserializer);
                    lastByte = parser.nextToken();
                    if (lastByte != ':') {
                        throw parser.createException("Expected ':' to separate key and value", lastByte);
                    }
                    parser.nextToken();
                    value = Deserializers.deserialize(parser, valueDeserializer);
                    map.put(key, value);
                    lastByte = parser.nextToken();
                }
                if (lastByte != '}') {
                    throw parser.createException("Expected a map key", lastByte);
                }
            }
            return map;
        }

        @Override
        public GenericType<Map<?, ?>> type() {
            return type;
        }

        @Override
        public void configure(JsonBindingConfigurator jsonBindingConfigurator) {
            keyDeserializer = jsonBindingConfigurator.deserializer(keyType);
            valueDeserializer = jsonBindingConfigurator.deserializer(valueType);
            keySerializer = jsonBindingConfigurator.serializer(keyType);
            if (!keySerializer.isMapKeySerializer()) {
                throw new JsonException("Unsupported key serializer: "
                                                + keySerializer.type());
            }
            valueSerializer = jsonBindingConfigurator.serializer(valueType);
        }
    }

}
