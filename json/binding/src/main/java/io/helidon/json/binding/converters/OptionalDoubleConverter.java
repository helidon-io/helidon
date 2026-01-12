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

import java.util.OptionalDouble;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.JsonBindingConfigurator;
import io.helidon.json.binding.JsonConverter;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.binding.JsonSerializer;
import io.helidon.service.registry.Service;

@Service.PerLookup
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class OptionalDoubleConverter implements JsonConverter<OptionalDouble> {

    private static final GenericType<OptionalDouble> TYPE = GenericType.create(OptionalDouble.class);

    private JsonDeserializer<Double> deserializer;
    private JsonSerializer<Double> serializer;

    @Override
    public void serialize(JsonGenerator generator, OptionalDouble instance, boolean writeNulls) {
        if (instance.isEmpty()) {
            generator.writeNull();
            return;
        }
        serializer.serialize(generator, instance.getAsDouble(), writeNulls);
    }

    @Override
    public OptionalDouble deserialize(JsonParser parser) {
        return OptionalDouble.of(deserializer.deserialize(parser));
    }

    @Override
    public OptionalDouble deserializeNull() {
        return OptionalDouble.empty();
    }

    @Override
    public void configure(JsonBindingConfigurator jsonBindingConfigurator) {
        deserializer = jsonBindingConfigurator.deserializer(double.class);
        serializer = jsonBindingConfigurator.serializer(double.class);
    }

    @Override
    public GenericType<OptionalDouble> type() {
        return TYPE;
    }
}
