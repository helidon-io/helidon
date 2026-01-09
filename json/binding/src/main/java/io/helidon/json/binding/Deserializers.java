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

package io.helidon.json.binding;

import io.helidon.json.JsonParser;

/**
 * Utility class for deserialization operations.
 */
public final class Deserializers {
    /*
     * This type is used from generated code
     */

    private Deserializers() {
    }

    /**
     * Deserializes a value using the provided deserializer, handling null values.
     *
     * @param parser the JSON parser
     * @param deserializer the deserializer to use
     * @param <T> the type of the deserialized value
     * @return the deserialized value
     */
    public static <T> T deserialize(JsonParser parser, JsonDeserializer<T> deserializer) {
        if (parser.checkNull()) {
            return deserializer.deserializeNull();
        }
        return deserializer.deserialize(parser);
    }

}
