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
import io.helidon.json.binding.JsonConverter;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class PrimitiveIntegerConverter implements JsonConverter<Integer> {

    private static final GenericType<Integer> TYPE = GenericType.create(int.class);

    @Override
    public GenericType<Integer> type() {
        return TYPE;
    }

    @Override
    public void serialize(JsonGenerator generator, Integer instance, boolean writeNulls) {
        generator.write(instance);
    }

    @Override
    public Integer deserialize(JsonParser parser) {
        byte lastByte = parser.currentByte();
        if (lastByte == '\"') {
            parser.nextToken();
            int value = parser.readInt();
            lastByte = parser.nextToken();
            if (lastByte != '\"') {
                throw parser.createException("Expected '\"' to end the integer value", lastByte);
            }
            return value;
        }
        return parser.readInt();
    }

    @Override
    public Integer deserializeNull() {
        return 0;
    }
}
