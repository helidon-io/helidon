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

import io.helidon.json.JsonException;
import io.helidon.json.JsonGenerator;

/**
 * Interface for serializing Java objects to JSON.
 * <p>
 * Implementations of this interface handle the conversion of Java objects
 * of type T into their JSON representation using a {@link io.helidon.json.JsonGenerator}.
 * Custom serializers can be registered to provide specialized serialization
 * logic for specific types.
 * </p>
 *
 * @param <T> the type this serializer handles
 */
public interface JsonSerializer<T> extends JsonComponent<T> {

    /**
     * Serializes the given instance to JSON using the provided generator.
     *
     * @param generator   the JSON generator to write to
     * @param instance    the object instance to serialize
     * @param writeNulls  whether to write null values or skip them
     */
    void serialize(JsonGenerator generator, T instance, boolean writeNulls);

    /**
     * Serializes a null value.
     * <p>
     * This method is called when a null value needs to be serialized.
     * The default implementation writes a JSON null value.
     * </p>
     *
     * @param generator the JSON generator to write to
     */
    default void serializeNull(JsonGenerator generator) {
        generator.writeNull();
    }

    /**
     * Checks if this serializer can be used for serializing map keys.
     * <p>
     * Some types may be suitable for use as map keys in JSON objects.
     * The default implementation returns false.
     * </p>
     * Method {@link #serializeAsMapKey(Object)} should be implemented if this method should ever
     * return true.
     *
     * @return true if this serializer can handle map key serialization, false otherwise
     */
    default boolean isMapKeySerializer() {
        return false;
    }

    /**
     * Serializes the given instance as a map key string.
     * <p>
     * This method should only be called if {@link #isMapKeySerializer()} returns true.
     * The default implementation throws an exception indicating the type is not supported.
     * </p>
     *
     * @param instance the object instance to serialize as a map key
     * @return the string representation suitable for use as a JSON object key
     * @throws JsonException if the type is not supported for map key serialization
     */
    default String serializeAsMapKey(T instance) {
        throw new JsonException(instance.getClass().getName() + " is not supported as a map key serializer");
    }

}
