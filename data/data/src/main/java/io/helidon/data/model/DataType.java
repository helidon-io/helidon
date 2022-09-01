/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.model;

import io.helidon.core.utils.ReflectionUtils;
import io.helidon.core.utils.CollectionUtils;
import io.helidon.data.annotation.TypeDef;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * Enum of basic data types allowing compile time computation which can then subsequently be used at runtime for fast
 * switching.
 *
 * @author graemerocher
 * @since 1.0.0
 * @see PersistentProperty#getDataType()
 */
public enum DataType {
    /**
     * A big decimal such as {@link java.math.BigDecimal}.
     */
    BIGDECIMAL(BigDecimal.class, BigInteger.class),
    /**
     * A boolean value.
     */
    BOOLEAN(Boolean.class),
    /**
     * A byte.
     */
    BYTE(Byte.class),
    /**
     * A byte array. Often stored as binary.
     */
    BYTE_ARRAY(true, byte[].class),
    /**
     * A character.
     */
    CHARACTER(Character.class),
    /**
     * A date such as {@link java.util.Date} or {@link java.time.LocalDate}.
     */
    DATE(Date.class, java.sql.Date.class, LocalDate.class),
    /**
     * A timestamp such as {@link java.sql.Timestamp} or {@link java.time.Instant}.
     */
    TIMESTAMP(Timestamp.class, Instant.class, OffsetDateTime.class, ZonedDateTime.class),
    /**
     * A {@link Double} value.
     */
    DOUBLE(Double.class),
    /**
     * A {@link Float} value.
     */
    FLOAT(Float.class),
    /**
     * A {@link Integer} value.
     */
    INTEGER(Integer.class),
    /**
     * A {@link Long} value.
     */
    LONG(Long.class),
    /**
     * A {@link Short} value.
     */
    SHORT(Short.class),
    /**
     * A {@link String} value.
     */
    STRING(String.class, CharSequence.class, URL.class, URI.class, Locale.class, TimeZone.class, Charset.class),
    /**
     * An object of an indeterminate type.
     */
    OBJECT,
    /**
     * A class annotated with {@link io.helidon.data.annotation.MappedEntity}.
     */
    ENTITY,
    /**
     * A JSON type.
     */
    JSON,
    /**
     * The UUID type.
     */
    UUID(java.util.UUID.class),
    /**
     * A string array.
     */
    STRING_ARRAY(true, String[].class),
    /**
     * A short array.
     */
    SHORT_ARRAY(true, short[].class, Short[].class),
    /**
     * An integer array.
     */
    INTEGER_ARRAY(true, int[].class, Integer[].class),
    /**
     * A long array.
     */
    LONG_ARRAY(true, long[].class, Long[].class),
    /**
     * A long array.
     */
    FLOAT_ARRAY(true, float[].class, Float[].class),
    /**
     * A double array.
     */
    DOUBLE_ARRAY(true, double[].class, Double[].class),
    /**
     * A character array.
     */
    CHARACTER_ARRAY(true, char[].class, Character[].class),
    /**
     * A boolean array.
     */
    BOOLEAN_ARRAY(true, boolean[].class, Boolean[].class);

    /**
     * Empty array of data types.
     */
    public static final DataType[] EMPTY_DATA_TYPE_ARRAY = new DataType[0];
    private static final Map<Class<?>, DataType> CLASS_DATA_TYPE_MAP = new HashMap<>();

    static {
        DataType[] values = DataType.values();
        for (DataType dt : values) {
            for (Class<?> javaType : dt.javaTypes) {
                CLASS_DATA_TYPE_MAP.put(javaType, dt);
            }
        }
    }

    private final Set<Class<?>> javaTypes;
    private final boolean isArray;

    /**
     * Default constructor.
     * @param javaTypes Associated data types.
     */
    DataType(Class<?>...javaTypes) {
        this(false, javaTypes);
    }

    /**
     * Default constructor.
     * @param isArray Is an array type.
     * @param javaTypes Associated data types.
     */
    DataType(boolean isArray, Class<?>...javaTypes) {
        this.isArray = isArray;
        this.javaTypes = CollectionUtils.setOf(javaTypes);
    }

    /**
     * Is an array type.
     *
     * @return true if an array type
     */
    public boolean isArray() {
        return isArray;
    }

    /**
     * Obtains the data type for the given type.
     * @param type The type
     * @return The data type
     */
    public static DataType forType(Class<?> type) {
        Class<?> wrapper = ReflectionUtils.getWrapperType(Objects.requireNonNull(type, "The type cannot be null"));
        TypeDef td = wrapper.getAnnotation(TypeDef.class);
        if (td != null) {
            return td.type();
        } else {
            return CLASS_DATA_TYPE_MAP.getOrDefault(wrapper, DataType.OBJECT);
        }
    }
}
