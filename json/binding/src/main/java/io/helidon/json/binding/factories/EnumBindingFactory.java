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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
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

        private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
        private static final int FNV_PRIME = 0x01000193;

        private final Type type;
        private final Map<Integer, Enum<?>> enumConstants;
        private final Map<String, Integer> names;

        private EnumConverter(Class<?> type) {
            this.type = type;
            if (!type.isEnum()) {
                throw new IllegalStateException("Type \"" + type + "\" is not an enum");
            }
            HashMap<Integer, Enum<?>> map = new HashMap<>();
            Map<String, Integer> names = new HashMap<>();
            Object[] constants = type.getEnumConstants();
            for (Object o : constants) {
                Enum<?> constant = (Enum<?>) o;
                String costName = constant.name();
                int nameHash = calculateNameHash(costName);
                names.put(costName, nameHash);
                map.put(nameHash, constant);
            }
            this.enumConstants = Collections.unmodifiableMap(map);
            this.names = Collections.unmodifiableMap(names);
        }

        @Override
        public Enum<?> deserialize(JsonParser parser) {
            int enumNameHash = parser.readStringAsHash();
            Enum<?> enumValue = enumConstants.get(enumNameHash);
            if (enumValue == null) {
                throw parser.createException("Invalid enum name hash \"" + enumNameHash + "\". Valid names and hashes are: "
                                                     + names);
            }
            return enumValue;
        }

        @Override
        public void serialize(JsonGenerator generator, Enum<?> instance, boolean writeNulls) {
            generator.write(instance.name());
        }

        @Override
        public GenericType<Enum<?>> type() {
            return GenericType.create(type);
        }

        private static int calculateNameHash(String name) {
            int fnvHash = FNV_OFFSET_BASIS;
            for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
                fnvHash ^= (b & 0xFF);
                fnvHash *= FNV_PRIME;
            }
            return fnvHash;
        }

    }
}
