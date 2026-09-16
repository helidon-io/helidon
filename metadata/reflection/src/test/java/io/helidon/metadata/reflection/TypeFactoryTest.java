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

package io.helidon.metadata.reflection;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeFactoryTest {
    @Test
    void resolvesPrimitiveClasses() {
        for (Class<?> type : List.of(boolean.class, byte.class, char.class, short.class, int.class,
                                    long.class, float.class, double.class, void.class)) {
            assertThat(type.getName(), TypeFactory.toClass(TypeName.create(type)), sameInstance(type));
            assertThat(type.getName(), TypeFactory.toType(TypeName.create(type.getName())), sameInstance(type));
        }
    }

    @Test
    void resolvesArrayClasses() {
        for (Class<?> type : List.of(boolean[].class, byte[].class, char[].class, short[].class, int[].class,
                                    long[].class, float[].class, double[].class, String[].class, Object[].class,
                                    byte[][].class, String[][][].class, Map.Entry[][].class)) {
            assertThat(type.getTypeName(), TypeFactory.toClass(TypeName.create(type)), sameInstance(type));
            assertThat(type.getTypeName(), TypeFactory.toClass(TypeName.create(type.getCanonicalName())), sameInstance(type));
            assertThat(type.getTypeName(), TypeFactory.toType(TypeName.create(type)), sameInstance(type));
        }
    }

    @Test
    void resolvesBuilderArraysWithoutExplicitComponent() {
        for (Class<?> component : List.of(byte.class, String.class, Map.Entry.class)) {
            TypeName typeName = TypeName.builder(TypeName.create(component))
                    .array(true)
                    .build();

            assertThat(component.getTypeName(), TypeFactory.toClass(typeName), sameInstance(component.arrayType()));
        }
    }

    @Test
    void resolvesVarargsAsArrays() {
        assertThat(TypeFactory.toClass(TypeName.create("byte...")), sameInstance(byte[].class));
        assertThat(TypeFactory.toClass(TypeName.create("java.lang.String[]...")), sameInstance(String[][].class));
    }

    @Test
    void resolvesArrayTypeArguments() {
        for (Class<?> argument : List.of(byte[].class, char[].class, String[].class, byte[][].class, Map.Entry[][].class)) {
            TypeName typeName = TypeName.create("java.util.List<" + argument.getCanonicalName() + ">");
            ParameterizedType type = (ParameterizedType) TypeFactory.toType(typeName);

            assertThat(type.getRawType(), sameInstance(List.class));
            assertThat(argument.getTypeName(), type.getActualTypeArguments(), arrayContaining(argument));
        }
    }

    @Test
    void resolvesNestedArrayTypeArguments() {
        TypeName typeName = TypeName.create("java.util.List<java.util.List<byte[][]>>");
        ParameterizedType outer = (ParameterizedType) TypeFactory.toType(typeName);
        ParameterizedType inner = (ParameterizedType) outer.getActualTypeArguments()[0];

        assertThat(outer.getRawType(), sameInstance(List.class));
        assertThat(inner.getRawType(), sameInstance(List.class));
        assertThat(inner.getActualTypeArguments(), arrayContaining(byte[][].class));
    }

    @Test
    void resolvesReferenceAndNestedClasses() {
        assertThat(TypeFactory.toClass(TypeName.create(String.class)), sameInstance(String.class));
        assertThat(TypeFactory.toClass(TypeName.create("java.util.Map.Entry")), sameInstance(Map.Entry.class));
    }

    @Test
    void rejectsParameterizedArraysAsTypesButResolvesTheirErasedClasses() {
        TypeName typeName = TypeName.create("java.util.List<java.lang.String>[]");

        assertThat(TypeFactory.toClass(typeName), sameInstance(List[].class));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> TypeFactory.toType(typeName));
        assertThat(exception.getMessage(), containsString(typeName.resolvedName()));
    }

    @Test
    void rejectsParameterizedBuilderArrayComponents() {
        TypeName componentType = TypeName.create("java.util.List<java.lang.String>");
        for (Class<?> arrayClass : List.of(List[].class, List[][].class, List[][][].class)) {
            TypeName typeName = TypeName.builder(TypeName.create(List.class))
                    .array(true)
                    .componentType(componentType)
                    .build();

            assertThat(arrayClass.getTypeName(), TypeFactory.toClass(typeName), sameInstance(arrayClass));
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                             () -> TypeFactory.toType(typeName),
                                                             arrayClass.getTypeName());
            assertThat(exception.getMessage(), containsString(typeName.resolvedName()));
            componentType = typeName;
        }
    }
}
