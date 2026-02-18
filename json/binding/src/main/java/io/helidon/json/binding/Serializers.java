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

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import io.helidon.json.JsonGenerator;

/**
 * Utility class for serialization operations.
 */
public final class Serializers {
    /*
     * This type is used from generated code
     */

    private Serializers() {
    }

    /**
     * Serializes a value with a key using the provided serializer.
     *
     * @param generator the JSON generator
     * @param serializer the serializer to use
     * @param instance the instance to serialize
     * @param key the key to write
     * @param writeNulls whether to write null values
     * @param <T> the type of the instance
     */
    public static <T> void serialize(JsonGenerator generator,
                                     JsonSerializer<T> serializer,
                                     T instance,
                                     String key,
                                     boolean writeNulls) {
        if (instance == null) {
            if (writeNulls) {
                generator.writeKey(key);
                serializer.serializeNull(generator);
            }
        } else {
            generator.writeKey(key);
            serializer.serialize(generator, instance, writeNulls);
        }
    }

    /**
     * Serializes an {@link java.util.Optional} value with a key using the provided serializer.
     *
     * @param generator the JSON generator
     * @param serializer the serializer to use
     * @param instance the instance to serialize
     * @param key the key to write
     * @param writeNulls whether to write null values
     * @param <T> the type of the instance
     */
    public static <T extends Optional<?>> void serialize(JsonGenerator generator,
                                                         JsonSerializer<T> serializer,
                                                         T instance,
                                                         String key,
                                                         boolean writeNulls) {
        if (instance == null || instance.isEmpty()) {
            if (writeNulls) {
                generator.writeKey(key);
                serializer.serializeNull(generator);
            }
        } else {
            generator.writeKey(key);
            serializer.serialize(generator, instance, writeNulls);
        }
    }

    /**
     * Serializes an {@link java.util.OptionalInt} value with a key using the provided serializer.
     *
     * @param generator the JSON generator
     * @param serializer the serializer to use
     * @param instance the instance to serialize
     * @param key the key to write
     * @param writeNulls whether to write null values
     */
    @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
    public static void serialize(JsonGenerator generator,
                                 JsonSerializer<OptionalInt> serializer,
                                 OptionalInt instance,
                                 String key,
                                 boolean writeNulls) {
        if (instance == null || instance.isEmpty()) {
            if (writeNulls) {
                generator.writeKey(key);
                serializer.serializeNull(generator);
            }
        } else {
            generator.writeKey(key);
            serializer.serialize(generator, instance, writeNulls);
        }
    }

    /**
     * Serializes an {@link java.util.OptionalLong} value with a key using the provided serializer.
     *
     * @param generator the JSON generator
     * @param serializer the serializer to use
     * @param instance the instance to serialize
     * @param key the key to write
     * @param writeNulls whether to write null values
     */
    @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
    public static void serialize(JsonGenerator generator,
                                 JsonSerializer<OptionalLong> serializer,
                                 OptionalLong instance,
                                 String key,
                                 boolean writeNulls) {
        if (instance == null || instance.isEmpty()) {
            if (writeNulls) {
                generator.writeKey(key);
                serializer.serializeNull(generator);
            }
        } else {
            generator.writeKey(key);
            serializer.serialize(generator, instance, writeNulls);
        }
    }

    /**
     * Serializes an {@link java.util.OptionalDouble} value with a key using the provided serializer.
     *
     * @param generator the JSON generator
     * @param serializer the serializer to use
     * @param instance the instance to serialize
     * @param key the key to write
     * @param writeNulls whether to write null values
     */
    @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
    public static void serialize(JsonGenerator generator,
                                 JsonSerializer<OptionalDouble> serializer,
                                 OptionalDouble instance,
                                 String key,
                                 boolean writeNulls) {
        if (instance == null || instance.isEmpty()) {
            if (writeNulls) {
                generator.writeKey(key);
                serializer.serializeNull(generator);
            }
        } else {
            generator.writeKey(key);
            serializer.serialize(generator, instance, writeNulls);
        }
    }

}
