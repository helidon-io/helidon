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

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonStringLookup;
import io.helidon.json.binding.JsonBindingFactory;
import io.helidon.json.binding.JsonConverter;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.binding.JsonSerializer;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class EnumBindingFactory implements JsonBindingFactory<Enum<?>> {

    // Bootstrap-loaded holders avoid retaining our loader through a parent-loaded enum class.
    private static final ClassValue<AtomicReference<WeakReference<EnumInfo>>> ENUM_INFO = new ClassValue<>() {
        @Override
        protected AtomicReference<WeakReference<EnumInfo>> computeValue(Class<?> type) {
            return new AtomicReference<>(new WeakReference<>(new EnumInfo(type)));
        }
    };

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
        // Keep the shared metadata alive while this converter is in use.
        private final EnumInfo info;
        private final JsonStringLookup lookup;

        private EnumConverter(Class<?> type) {
            this.type = type;
            if (!type.isEnum()) {
                throw new IllegalStateException("Type \"" + type + "\" is not an enum");
            }
            AtomicReference<WeakReference<EnumInfo>> cached = ENUM_INFO.get(type);
            EnumInfo info = cached.get().get();
            if (info == null) {
                info = new EnumInfo(type);
                cached.set(new WeakReference<>(info));
            }
            this.info = info;
            this.enumConstants = info.enumConstants;
            this.lookup = info.lookup;
        }

        @Override
        public Enum<?> deserialize(JsonParser parser) {
            int index = parser.readString(lookup);
            if (index < 0) {
                throw parser.createException("Invalid enum name. Valid names are: " + Arrays.toString(info.names));
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

    private static final class EnumInfo {
        private final Enum<?>[] enumConstants;
        private final String[] names;
        private final JsonStringLookup lookup;

        private EnumInfo(Class<?> type) {
            this.enumConstants = (Enum<?>[]) type.getEnumConstants();
            this.names = new String[enumConstants.length];
            for (int i = 0; i < enumConstants.length; i++) {
                names[i] = enumConstants[i].name();
            }
            this.lookup = JsonStringLookup.create(names);
        }
    }
}
