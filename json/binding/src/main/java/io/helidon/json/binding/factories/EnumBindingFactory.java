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

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonStringMatcher;
import io.helidon.json.binding.JsonBindingFactory;
import io.helidon.json.binding.JsonConverter;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.binding.JsonSerializer;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class EnumBindingFactory implements JsonBindingFactory<Enum<?>> {

    @Override
    public JsonDeserializer<Enum<?>> createDeserializer(Class<? extends Enum<?>> type) {
        return new EnumConverter(type);
    }

    @Override
    public JsonDeserializer<Enum<?>> createDeserializer(GenericType<? extends Enum<?>> type) {
        return new EnumConverter(type.rawType());
    }

    @Override
    public JsonSerializer<Enum<?>> createSerializer(Class<? extends Enum<?>> type) {
        return new EnumConverter(type);
    }

    @Override
    public JsonSerializer<Enum<?>> createSerializer(GenericType<? extends Enum<?>> type) {
        return new EnumConverter(type.rawType());
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(Enum.class);
    }

    private static final class EnumConverter implements JsonConverter<Enum<?>> {

        private final Type type;
        private final Enum<?>[] enumConstants;
        private final String[] names;
        private final JsonStringMatcher matcher;

        private EnumConverter(Class<?> type) {
            this.type = type;
            if (!type.isEnum()) {
                throw new IllegalStateException("Type \"" + type + "\" is not an enum");
            }
            this.enumConstants = (Enum<?>[]) type.getEnumConstants();
            this.names = new String[enumConstants.length];
            for (int i = 0; i < enumConstants.length; i++) {
                names[i] = enumConstants[i].name();
            }
            this.matcher = JsonStringMatcher.create(names);
        }

        @Override
        public Enum<?> deserialize(JsonParser parser) {
            int index = parser.readString(matcher);
            if (index < 0) {
                throw parser.createException("Invalid enum name. Valid names are: " + Arrays.toString(names));
            }
            return enumConstants[index];
        }

        @Override
        public void serialize(JsonGenerator generator, Enum<?> instance, boolean writeNulls) {
            generator.write(instance.name());
        }

        @Override
        public GenericType<Enum<?>> type() {
            return GenericType.create(type);
        }

    }

}
