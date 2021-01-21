/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.enterprise.inject.spi.DeploymentException;
import javax.inject.Provider;

/**
 * A three tier description of a field type (main type, first
 * generic type, second generic type).
 */
final class FieldTypes {
    // such as List
    // if this is not a generic type, all three fields will be the same value
    private TypedField field0;
    // such as Optional
    private TypedField field1;
    // such as String
    private TypedField field2;

    static FieldTypes create(Type type) {
        FieldTypes ft = new FieldTypes();

        // if the first type is a Provider or an Instance, we do not want it and start from its child
        TypedField firstType = getTypedField(type);
        if (Provider.class.isAssignableFrom(firstType.rawType)) {
            ft.field0 = getTypedField(firstType);
            firstType = ft.field0;
        } else {
            ft.field0 = firstType;
        }

        ft.field1 = getTypedField(ft.field0);

        // now suppliers, optionals may have two levels deep
        if (firstType.rawType == Optional.class || firstType.rawType == Supplier.class) {
            ft.field2 = getTypedField(ft.field1);
        } else {
            ft.field2 = ft.field1;
        }

        return ft;
    }

    private static TypedField getTypedField(Type type) {
        if (type instanceof Class) {
            return new TypedField((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;

            return new TypedField((Class<?>) paramType.getRawType(), paramType);
        }

        throw new UnsupportedOperationException("No idea how to handle " + type);
    }

    private static TypedField getTypedField(TypedField field) {
        if (field.isParameterized()) {
            ParameterizedType paramType = field.paramType;
            Type[] typeArgs = paramType.getActualTypeArguments();

            if (typeArgs.length == 1) {
                Type typeArg = typeArgs[0];
                return getTypedField(typeArg);
            }

            if ((typeArgs.length == 2) && (field.rawType == Map.class)) {
                if ((typeArgs[0] == typeArgs[1]) && (typeArgs[0] == String.class)) {
                    return new TypedField(String.class);
                }
            }

            throw new DeploymentException("Cannot create config property for " + field.rawType + ", params: " + Arrays
                    .toString(typeArgs));
        }

        return field;
    }

    TypedField field0() {
        return field0;
    }

    TypedField field1() {
        return field1;
    }

    TypedField field2() {
        return field2;
    }

    static final class TypedField {
        private final Class<?> rawType;
        private ParameterizedType paramType;

        private TypedField(Class<?> rawType) {
            this.rawType = rawType;
        }

        private TypedField(Class<?> rawType, ParameterizedType paramType) {
            this.rawType = rawType;
            this.paramType = paramType;
        }

        private boolean isParameterized() {
            return paramType != null;
        }

        Class<?> rawType() {
            return rawType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if ((o == null) || (getClass() != o.getClass())) {
                return false;
            }
            TypedField that = (TypedField) o;
            return Objects.equals(rawType, that.rawType)
                    && Objects.equals(paramType, that.paramType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rawType, paramType);
        }

        @Override
        public String toString() {
            return "TypedField{"
                    + "rawType=" + rawType
                    + ", paramType=" + paramType
                    + '}';
        }
    }
}
