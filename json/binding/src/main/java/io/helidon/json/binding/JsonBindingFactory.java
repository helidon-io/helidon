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

import java.util.Set;

import io.helidon.common.GenericType;

/**
 * Factory interface for creating more complex and universal JSON serializers and deserializers.
 *
 * @param <T> the base type this factory handles
 */
public interface JsonBindingFactory<T> {
    /*
     * This type is used from generated code
     */

    /**
     * Create a deserializer for the specified class type.
     *
     * @param type the class type to create a deserializer for
     * @return a deserializer for the type
     */
    JsonDeserializer<T> createDeserializer(Class<? extends T> type);

    /**
     * Create a deserializer for the specified generic type.
     *
     * @param type the generic type to create a deserializer for
     * @return a deserializer for the type
     */
    JsonDeserializer<T> createDeserializer(GenericType<? extends T> type);

    /**
     * Create a serializer for the specified class type.
     *
     * @param type the class type to create a serializer for
     * @return a serializer for the type
     */
    JsonSerializer<T> createSerializer(Class<? extends T> type);

    /**
     * Create a serializer for the specified generic type.
     *
     * @param type the generic type to create a serializer for
     * @return a serializer for the type
     */
    JsonSerializer<T> createSerializer(GenericType<? extends T> type);

    /**
     * Return the set of supported types by this factory.
     *
     * @return a set of supported class types
     */
    Set<Class<?>> supportedTypes();

}
