/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.common;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Stack;

/**
 * Utilities for {@link GenericType}.
 */
final class GenericTypeUtil {
    private GenericTypeUtil() {
    }

    /**
     * Return the value of the type parameter of {@code GenericType<T>}.
     *
     * @param clazz     subClass of {@code baseClass} to analyze.
     * @param baseClass base class having the type parameter the value of which we need to retrieve
     * @return the parameterized type of {@code GenericType<T>} (aka T)
     */
    static Type typeArgument(Class<?> clazz, Class<?> baseClass) {
        // collect superclasses
        Stack<Type> superclasses = new Stack<Type>();
        Type currentType;
        Class<?> currentClass = clazz;
        do {
            currentType = currentClass.getGenericSuperclass();
            superclasses.push(currentType);
            if (currentType instanceof Class) {
                currentClass = (Class<?>) currentType;
            } else if (currentType instanceof ParameterizedType) {
                currentClass = (Class<?>) ((ParameterizedType) currentType).getRawType();
            }

            if (currentClass.equals(Object.class)) {
                break;
            }

            if (currentClass.equals(baseClass)) {
                break;
            }
        } while (true);

        // find which one supplies type argument and return it
        TypeVariable<?> tv = baseClass.getTypeParameters()[0];
        while (!superclasses.isEmpty()) {
            currentType = superclasses.pop();

            if (currentType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) currentType;
                Class<?> rawType = (Class<?>) pt.getRawType();
                int argIndex = Arrays.asList(rawType.getTypeParameters()).indexOf(tv);
                if (argIndex > -1) {
                    Type typeArg = pt.getActualTypeArguments()[argIndex];
                    if (typeArg instanceof TypeVariable) {
                        // type argument is another type variable - look for the value of that
                        // variable in subclasses
                        tv = (TypeVariable<?>) typeArg;
                        continue;
                    } else {
                        // found the value - return it
                        return typeArg;
                    }
                }
            }

            // needed type argument not supplied - break and throw exception
            break;
        }
        throw new IllegalArgumentException(currentType + " does not specify the type parameter T of GenericType<T>");
    }

    /**
     * Returns the object representing the class or interface that declared
     * the supplied {@code type}.
     *
     * @param type {@code Type} to inspect.
     * @return the class or interface that declared the supplied {@code type}.
     */
    static Class<?> rawClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (parameterizedType.getRawType() instanceof Class) {
                return (Class<?>) parameterizedType.getRawType();
            }
        } else if (type instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType) type;
            final Class<?> componentRawType = rawClass(array.getGenericComponentType());
            return getArrayClass(componentRawType);
        }
        throw new IllegalArgumentException("Type parameter "
                                                   + type
                                                   + " is not a class or parameterized type whose raw type is a class");
    }

    /**
     * Get Array class of component class.
     *
     * @param c the component class of the array
     * @return the array class.
     */
    private static Class<?> getArrayClass(Class<?> c) {
        try {
            Object o = Array.newInstance(c, 0);
            return o.getClass();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
