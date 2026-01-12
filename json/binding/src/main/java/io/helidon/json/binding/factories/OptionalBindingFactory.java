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
import java.util.Optional;
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
class OptionalBindingFactory implements JsonBindingFactory<Optional<?>> {

    @Override
    public JsonDeserializer<Optional<?>> createDeserializer(Class<? extends Optional<?>> type) {
        return new OptionalConverter(type);
    }

    @Override
    public JsonDeserializer<Optional<?>> createDeserializer(GenericType<? extends Optional<?>> type) {
        return new OptionalConverter(type.type());
    }

    @Override
    public JsonSerializer<Optional<?>> createSerializer(Class<? extends Optional<?>> type) {
        return new OptionalConverter(type);
    }

    @Override
    public JsonSerializer<Optional<?>> createSerializer(GenericType<? extends Optional<?>> type) {
        return new OptionalConverter(type.type());
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(Optional.class);
    }

    private static final class OptionalConverter implements JsonConverter<Optional<?>> {

        private final GenericType<Optional<?>> type;
        private final Type componentType;
        private JsonDeserializer<Object> deserializer;
        private JsonSerializer<Object> serializer;

        OptionalConverter(Type type) {
            this.type = GenericType.create(type);
            if (type instanceof ParameterizedType parameterizedType) {
                componentType = parameterizedType.getActualTypeArguments()[0];
            } else {
                componentType = GenericType.OBJECT;
            }
        }

        @Override
        public void serialize(JsonGenerator generator, Optional<?> instance, boolean writeNulls) {
            if (instance.isEmpty()) {
                generator.writeNull();
                return;
            }
            serializer.serialize(generator, instance.get(), writeNulls);
        }

        @Override
        public Optional<?> deserialize(JsonParser parser) {
            return Optional.ofNullable(deserializer.deserialize(parser));
        }

        @Override
        public Optional<?> deserializeNull() {
            return Optional.empty();
        }

        @Override
        public GenericType<Optional<?>> type() {
            return type;
        }

        @Override
        public void configure(JsonBindingConfigurator jsonBindingConfigurator) {
            deserializer = jsonBindingConfigurator.deserializer(componentType);
            serializer = jsonBindingConfigurator.serializer(componentType);
        }
    }

}
