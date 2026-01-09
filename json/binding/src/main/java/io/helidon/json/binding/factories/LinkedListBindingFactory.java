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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.binding.JsonSerializer;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class LinkedListBindingFactory extends ListBindingFactory {

    @Override
    public JsonDeserializer<List<?>> createDeserializer(Class<? extends List<?>> type) {
        return new LinkedListConverter(type);
    }

    @Override
    public JsonDeserializer<List<?>> createDeserializer(GenericType<? extends List<?>> type) {
        return new LinkedListConverter(type.type());
    }

    @Override
    public JsonSerializer<List<?>> createSerializer(Class<? extends List<?>> type) {
        return new LinkedListConverter(type);
    }

    @Override
    public JsonSerializer<List<?>> createSerializer(GenericType<? extends List<?>> type) {
        return new LinkedListConverter(type.type());
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(LinkedList.class);
    }

    private static final class LinkedListConverter extends ListConverter {

        LinkedListConverter(Type type) {
            super(type);
        }

        @Override
        List<Object> createInstance(int size) {
            return new LinkedList<>();
        }

    }

}
