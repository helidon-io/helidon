/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Class JavaMethodFinder.
 */
class JavaMethodFinder {

    private JavaMethodFinder() {
    }

    /**
     * Finds a method in a class or any of its supertypes. Supertype methods are
     * ignored if they are private or package-private and in a different package.
     *
     * @param clazz Class to start search at.
     * @param methodName Method name.
     * @param paramTypes Types of params.
     * @return The method found.
     * @throws NoSuchMethodException If not found.
     */
    static Method findMethod(Class<?> clazz, String methodName, Type... paramTypes)
            throws NoSuchMethodException {
        // Initialize queue with first class
        Queue<Class<?>> queue = new LinkedBlockingQueue<>();
        queue.add(clazz);

        // Init variables to start search
        Method method = null;
        Class<?> current = null;

        // Continue searching until found or queue exhausted
        while (!queue.isEmpty() && method == null) {
            current = queue.remove();

            // Search for compatible method in the current class
            Method[] methods = current.getDeclaredMethods();
            for (Method m : methods) {
                Type[] leftParamTypes = m.getGenericParameterTypes();
                if (m.getName().equals(methodName) && isCompatible(leftParamTypes, paramTypes)) {
                    method = m;
                    break;
                }
            }

            // If not found, collect supertypes and continue
            if (method == null && current.getSuperclass() != null) {
                queue.add(current.getSuperclass());
                Arrays.stream(current.getInterfaces()).forEach(queue::add);     // default methods
            }
        }

        // If not found, throw an exception
        if (method == null) {
            throw new NoSuchMethodException();
        }

        // If found on a different class, check access
        if (current != clazz) {
            int mod = method.getModifiers();
            if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)
                    && (Modifier.isPrivate(mod) || !current.getPackage().equals(clazz.getPackage()))) {
                throw new NoSuchMethodException();
            }
        }

        // Otherwise, set accessible and return
        method.setAccessible(true);
        return method;
    }

    /**
     * Determines compatibility between two arrays of types.
     *
     * @param leftArray Left array.
     * @param rightArray Right array.
     * @return Outcome of test.
     */
    private static boolean isCompatible(final Type[] leftArray, final Type[] rightArray) {
        if (leftArray.length == rightArray.length) {
            int i;
            for (i = 0; i < leftArray.length; i++) {
                if (!isCompatible(leftArray[i], rightArray[i])) {
                    break;
                }
            }
            if (i == leftArray.length) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines compatibility between two types.
     *
     * @param left Left type.
     * @param right Right type.
     * @return Outcome of test.
     */
    private static boolean isCompatible(final Type left, final Type right) {
        // Simple case, handles same type vars too
        if (left.equals(right)) {
            return true;
        }

        // Type variable instantiation such as 'T' and 'String'
        if (left instanceof TypeVariable<?> && !(right instanceof TypeVariable<?>)) {
            return true;
        }

        // Wildcards such as '? extends String' and '? extends T'
        if (left instanceof WildcardType && right instanceof WildcardType) {
            WildcardType wleft = (WildcardType) left;
            WildcardType wright = (WildcardType) right;
            if (isCompatible(wleft.getUpperBounds(), wright.getUpperBounds())
                && isCompatible(wleft.getLowerBounds(), wright.getLowerBounds())) {
                return true;
            }
        }

        // Generic array such as 'T[][]' and class 'String[][]'
        if (left instanceof GenericArrayType && right instanceof Class<?>) {
            Type tleft = left;
            Type tright = right;
            try {
                do {
                    tleft = ((GenericArrayType) tleft).getGenericComponentType();
                    tright = ((Class<?>) tright).getComponentType();
                } while (tleft instanceof GenericArrayType);

                if (tright != null && isCompatible(tleft, tright)) {
                    return true;
                }
            } catch (ClassCastException e) {
                // falls through
            }
        }

        // Handle parameterized types such as 'List<? extends T>' and 'List<? extends String>'
        if (left instanceof ParameterizedType && right instanceof ParameterizedType) {
            ParameterizedType pleft = (ParameterizedType) left;
            ParameterizedType pright = (ParameterizedType) right;
            if (pleft.getRawType().equals(pright.getRawType())
                    && isCompatible(pleft.getActualTypeArguments(), pright.getActualTypeArguments())) {
                return true;
            }
            return false;
        }

        // Class compatibility
        try {
            return ((Class<?>) left).isAssignableFrom((Class<?>) right);
        } catch (ClassCastException e) {
            return false;
        }
    }
}
