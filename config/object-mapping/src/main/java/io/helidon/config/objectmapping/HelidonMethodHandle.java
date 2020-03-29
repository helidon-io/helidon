/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.objectmapping;

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * A replacement for {@link java.lang.invoke.MethodHandle} that we cannot use for the time being, due to
 * limitations of GraalVM native image.
 */
interface HelidonMethodHandle {
    static HelidonMethodHandle create(Class<?> type, Constructor<?> constructor) {
        return new ReflectionUtil.ConstructorMethodHandle(type, constructor);
    }

    static HelidonMethodHandle create(Class<?> type, Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            return new ReflectionUtil.StaticMethodHandle(method);
        } else {
            return new ReflectionUtil.InstanceMethodHandle(type, method);
        }
    }

    static HelidonMethodHandle create(Class<?> type, Field field) {
        return new ReflectionUtil.FieldMethodHandle(type, field);
    }

    /**
     * Invoke the method or constructor with params.
     * @param params parameters
     * @return response
     */
    Object invoke(List<Object> params);

    /**
     * Type of this handle, see {@link java.lang.invoke.MethodHandle#type()}.
     *
     * @return type of this handle
     */
    MethodType type();

    /**
     * Invoke with varargs, delegates to {@link #invoke(java.util.List)}.
     *
     * @param params parameters
     * @return result of the operation, or null (for setters)
     */
    default Object invoke(Object... params) {
        return invoke(Arrays.asList(params));
    }
}
