/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.common.types;

import java.util.Objects;

/**
 * When creating an {@link io.helidon.common.types.Annotation}, we may need to create an enum value
 * without access to the enumeration.
 * <p>
 * In such a case, you can use this type when calling {@link io.helidon.common.types.Annotation.Builder#putValue(String, Object)}
 */
public interface EnumValue {
    /**
     * Create a new enum value, when the enum is not available on classpath.
     *
     * @param enumType type of the enumeration
     * @param enumName value of the enumeration
     * @return enum value
     */
    static EnumValue create(TypeName enumType, String enumName) {
        Objects.requireNonNull(enumType);
        Objects.requireNonNull(enumName);
        return new EnumValueImpl(enumType, enumName);
    }

    /**
     * Create a new enum value.
     *
     * @param type enum type
     * @param value enum value constant
     * @return new enum value
     * @param <T> type of the enum
     */
    static <T extends Enum<T>> EnumValue create(Class<T> type, T value) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(value);

        return new EnumValueImpl(TypeName.create(type), value.name());
    }

    /**
     * Type of the enumeration.
     *
     * @return type name of the enumeration
     */
    TypeName type();

    /**
     * The enum value.
     *
     * @return enum value
     */
    String name();
}
