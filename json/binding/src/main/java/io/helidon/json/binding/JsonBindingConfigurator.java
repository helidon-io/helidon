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

import java.lang.reflect.Type;

import io.helidon.common.GenericType;

/**
 * Interface for configuring JSON binding components.
 */
public interface JsonBindingConfigurator {
    /*
     * This type is used from generated code
     */

    /**
     * Return a deserializer for the specified type.
     *
     * @param type the type to get a deserializer for
     * @return the deserializer for the type
     * @param <T> the deserialized type
     */
    <T> JsonDeserializer<T> deserializer(Type type);

    /**
     * Return a deserializer for the specified class.
     *
     * @param type the class to get a deserializer for
     * @return the deserializer for the class
     * @param <T> the deserialized type
     */
    <T> JsonDeserializer<T> deserializer(Class<T> type);

    /**
     * Return a deserializer for the specified generic type.
     *
     * @param type the generic type to get a deserializer for
     * @return the deserializer for the generic type
     * @param <T> the deserialized type
     */
    <T> JsonDeserializer<T> deserializer(GenericType<T> type);

    /**
     * Return a serializer for the specified type.
     *
     * @param type the type to get a serializer for
     * @return the serializer for the type
     * @param <T> the serialized type
     */
    <T> JsonSerializer<T> serializer(Type type);

    /**
     * Return a serializer for the specified class.
     *
     * @param type the class to get a serializer for
     * @return the serializer for the class
     * @param <T> the serialized type
     */
    <T> JsonSerializer<T> serializer(Class<T> type);

    /**
     * Return a serializer for the specified generic type.
     *
     * @param type the generic type to get a serializer for
     * @return the serializer for the generic type
     * @param <T> the serialized type
     */
    <T> JsonSerializer<T> serializer(GenericType<T> type);

}
