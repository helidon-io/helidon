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

package io.helidon.json.binding.converters;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.JsonBindingConfigurator;
import io.helidon.json.binding.JsonConverter;
import io.helidon.json.binding.JsonSerializer;
import io.helidon.service.registry.Service;

@Service.PerLookup
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class ObjectConverter implements JsonConverter<Object> {

    private JsonBindingConfigurator jsonBindingConfigurator;

    @Override
    public void configure(JsonBindingConfigurator jsonBindingConfigurator) {
        this.jsonBindingConfigurator = jsonBindingConfigurator;
    }

    @Override
    public GenericType<Object> type() {
        return GenericType.OBJECT;
    }

    @Override
    public Object deserialize(JsonParser parser) {
        throw parser.createException("Deserialization into Object is not supported");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(JsonGenerator generator, Object instance, boolean writeNulls) {
        JsonSerializer<Object> serializer = (JsonSerializer<Object>) jsonBindingConfigurator.serializer(instance.getClass());
        serializer.serialize(generator, instance, writeNulls);
    }

    @Override
    public boolean isMapKeySerializer() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String serializeAsMapKey(Object instance) {
        JsonSerializer<Object> serializer = (JsonSerializer<Object>) jsonBindingConfigurator.serializer(instance.getClass());
        return serializer.serializeAsMapKey(instance);
    }
}
