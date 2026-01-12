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
import io.helidon.json.binding.Deserializers;
import io.helidon.json.binding.JsonBindingConfigurator;
import io.helidon.json.binding.JsonConverter;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.binding.JsonSerializer;
import io.helidon.service.registry.Service;

@Service.PerLookup
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class LongArrayConverter implements JsonConverter<long[]> {

    private static final GenericType<long[]> TYPE = GenericType.create(long[].class);

    private final long[] emptyArray = new long[0];
    private JsonDeserializer<Long> deserializer;
    private JsonSerializer<Long> serializer;

    @Override
    public void serialize(JsonGenerator generator, long[] instance, boolean writeNulls) {
        generator.writeArrayStart();
        for (long value : instance) {
            serializer.serialize(generator, value, writeNulls);
        }
        generator.writeArrayEnd();
    }

    @Override
    public long[] deserialize(JsonParser parser) {
        byte lastByte = parser.currentByte();
        if (lastByte != '[') {
            throw parser.createException("Expected '[' to start an array", lastByte);
        }
        long[] array = new long[5];
        lastByte = parser.nextToken();
        int index = 0;
        if (lastByte == ']') {
            return emptyArray;
        }
        array[index++] = Deserializers.deserialize(parser, deserializer);
        lastByte = parser.nextToken();
        while (lastByte == ',') {
            if (index == array.length) {
                long[] tmp = new long[array.length * 2];
                System.arraycopy(array, 0, tmp, 0, array.length);
                array = tmp;
            }
            parser.nextToken();
            array[index++] = Deserializers.deserialize(parser, deserializer);
            lastByte = parser.nextToken();
        }
        if (lastByte != ']') {
            throw parser.createException("Expected ']'", lastByte);
        }
        if (index == array.length) {
            return array;
        }
        long[] toReturn = new long[index];
        System.arraycopy(array, 0, toReturn, 0, toReturn.length);
        return toReturn;
    }

    @Override
    public void configure(JsonBindingConfigurator jsonBindingConfigurator) {
        deserializer = jsonBindingConfigurator.deserializer(long.class);
        serializer = jsonBindingConfigurator.serializer(long.class);
    }

    @Override
    public GenericType<long[]> type() {
        return TYPE;
    }
}
