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
 * Interface for deserializing JSON to Java objects.
 *
 * @param <T> the type this deserializer produces
 */
public interface JsonDeserializer<T> extends JsonComponent<T> {

    /**
     * Deserializes JSON data from the parser into an object of type T.
     * This method should never be called if the value in the parser is null.
     * If the value is null, use {@link #deserializeNull()} instead.
     *
     * @param parser the JSON parser to read from
     * @return the deserialized object
     */
    T deserialize(JsonParser parser);

    /**
     * Return the default value when deserializing a null JSON value.
     * <p>
     * This method is called when a JSON null value is encountered.
     * The default implementation returns null.
     * </p>
     *
     * @return the deserialized null value, typically null
     */
    default T deserializeNull() {
        return null;
    }

}
