/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Commonly used type names.
 */
public final class TypeNames {
    /**
     * Type name for {@link java.lang.String}.
     */
    public static final TypeName STRING = TypeName.create(String.class);
    /**
     * Type name for {@link java.lang.Object}.
     */
    public static final TypeName OBJECT = TypeName.create(Object.class);
    /**
     * Type name for {@link java.util.List}.
     */
    public static final TypeName LIST = TypeName.create(List.class);
    /**
     * Type name for {@link java.util.Map}.
     */
    public static final TypeName MAP = TypeName.create(Map.class);
    /**
     * Type name for {@link java.util.Set}.
     */
    public static final TypeName SET = TypeName.create(Set.class);
    /**
     * Type name for {@link java.util.Optional}.
     */
    public static final TypeName OPTIONAL = TypeName.create(Optional.class);
    /**
     * Type name for {@link java.util.function.Supplier}.
     */
    public static final TypeName SUPPLIER = TypeName.create(Supplier.class);
    /**
     * Type name for {@link java.util.Collection}.
     */
    public static final TypeName COLLECTION = TypeName.create(Collection.class);
    /**
     * Type name for {@link java.time.Duration}.
     */
    public static final TypeName DURATION = TypeName.create(Duration.class);
    /*
    Primitive types and their boxed counterparts
     */
    /**
     * Primitive boolean type.
     */
    public static final TypeName PRIMITIVE_BOOLEAN = TypeName.create(boolean.class);
    /**
     * Primitive byte type.
     */
    public static final TypeName PRIMITIVE_BYTE = TypeName.create(byte.class);
    /**
     * Primitive short type.
     */
    public static final TypeName PRIMITIVE_SHORT = TypeName.create(short.class);
    /**
     * Primitive int type.
     */
    public static final TypeName PRIMITIVE_INT = TypeName.create(int.class);
    /**
     * Primitive long type.
     */
    public static final TypeName PRIMITIVE_LONG = TypeName.create(long.class);
    /**
     * Primitive char type.
     */
    public static final TypeName PRIMITIVE_CHAR = TypeName.create(char.class);
    /**
     * Primitive float type.
     */
    public static final TypeName PRIMITIVE_FLOAT = TypeName.create(float.class);
    /**
     * Primitive double type.
     */
    public static final TypeName PRIMITIVE_DOUBLE = TypeName.create(double.class);
    /**
     * Primitive void type.
     */
    public static final TypeName PRIMITIVE_VOID = TypeName.create(void.class);

    /**
     * Boxed boolean type.
     */
    public static final TypeName BOXED_BOOLEAN = TypeName.create(Boolean.class);
    /**
     * Boxed byte type.
     */
    public static final TypeName BOXED_BYTE = TypeName.create(Byte.class);
    /**
     * Boxed short type.
     */
    public static final TypeName BOXED_SHORT = TypeName.create(Short.class);
    /**
     * Boxed int type.
     */
    public static final TypeName BOXED_INT = TypeName.create(Integer.class);
    /**
     * Boxed long type.
     */
    public static final TypeName BOXED_LONG = TypeName.create(Long.class);
    /**
     * Boxed char type.
     */
    public static final TypeName BOXED_CHAR = TypeName.create(Character.class);
    /**
     * Boxed float type.
     */
    public static final TypeName BOXED_FLOAT = TypeName.create(Float.class);
    /**
     * Boxed double type.
     */
    public static final TypeName BOXED_DOUBLE = TypeName.create(Double.class);
    /**
     * Boxed void type.
     */
    public static final TypeName BOXED_VOID = TypeName.create(Void.class);
    /*
    Our own types
     */
    /**
     * Type name of the type name.
     */
    public static final TypeName TYPE_NAME = TypeName.create(TypeName.class);
    /**
     * Type name of typed element info.
     */
    public static final TypeName TYPED_ELEMENT_INFO = TypeName.create(TypedElementInfo.class);

    static final Map<String, TypeName> PRIMITIVES = Map.of(
            "boolean", PRIMITIVE_BOOLEAN,
            "byte", PRIMITIVE_BYTE,
            "short", PRIMITIVE_SHORT,
            "int", PRIMITIVE_INT,
            "long", PRIMITIVE_LONG,
            "char", PRIMITIVE_CHAR,
            "float", PRIMITIVE_FLOAT,
            "double", PRIMITIVE_DOUBLE,
            "void", PRIMITIVE_VOID
    );

    private static final Map<TypeName, TypeName> BOXED_TYPES = Map.of(
            PRIMITIVE_BOOLEAN, BOXED_BOOLEAN,
            PRIMITIVE_BYTE, BOXED_BYTE,
            PRIMITIVE_SHORT, BOXED_SHORT,
            PRIMITIVE_INT, BOXED_INT,
            PRIMITIVE_LONG, BOXED_LONG,
            PRIMITIVE_CHAR, BOXED_CHAR,
            PRIMITIVE_FLOAT, BOXED_FLOAT,
            PRIMITIVE_DOUBLE, BOXED_DOUBLE,
            PRIMITIVE_VOID, BOXED_VOID
    );

    private TypeNames() {
    }

    static TypeName boxed(TypeName original) {
        return Optional.ofNullable(BOXED_TYPES.get(original))
                .orElse(original);
    }
}
