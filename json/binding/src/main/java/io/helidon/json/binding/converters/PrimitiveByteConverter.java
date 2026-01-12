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
class PrimitiveByteConverter implements JsonConverter<Byte> {

    private static final GenericType<Byte> TYPE = GenericType.create(byte.class);

    @Override
    public GenericType<Byte> type() {
        return TYPE;
    }

    @Override
    public void serialize(JsonGenerator generator, Byte instance, boolean writeNulls) {
        generator.write(instance);
    }

    @Override
    public Byte deserialize(JsonParser parser) {
        byte lastByte = parser.currentByte();
        if (lastByte == '\"') {
            parser.nextToken();
            byte value = parser.readByte();
            lastByte = parser.nextToken();
            if (lastByte != '\"') {
                throw parser.createException("Expected '\"' to end the byte value", lastByte);
            }
            return value;
        }
        return parser.readByte();
    }

    @Override
    public Byte deserializeNull() {
        return 0;
    }
}
