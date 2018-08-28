/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * TODO javadoc.
 */
final class ReflectionUtil {
    private ReflectionUtil() {
    }

    /**
     * Check whether the {@code invoker} can access {@code target} in a static context.
     * This method can be used to validate access to static methods, static fields and constructors.
     *
     * @param invoker invoking class
     * @param target  target reflection object ({@link Constructor}, {@link java.lang.reflect.Field},
     *                {@link java.lang.reflect.Method})
     * @return true if the {@code target} can be accessed by the invoker (e.g. it is public; package private and in the same
     * package as invoker; protected and invoker inherits from the class; or private and invoker is the same class)
     */
    static boolean canAccess(Class<?> invoker, Member target) {
        return checkAccess(invoker, target, null);
    }

    static boolean canAccess(Class<?> invoker, Member target, Object anInstance) {
        return checkAccess(invoker, target, anInstance);
    }

    private static boolean checkAccess(Class<?> invoker, Member target, Object anInstance) {
        int modifiers = target.getModifiers();

        boolean isStatic = Modifier.isStatic(modifiers);
        boolean isPublic = Modifier.isPublic(modifiers);
        boolean isProtected = Modifier.isProtected(modifiers);
        boolean isPrivate = Modifier.isPrivate(modifiers);
        boolean isPackage = !(isPrivate || isProtected || isPublic);

        if (isStatic && (null != anInstance)) {
            return false;
        }

        if (target instanceof Constructor) {
            if (null != anInstance) {
                return false;
            }
        }

        Class<?> targetClass = target.getDeclaringClass();

        if (null != anInstance) {
            // the instance must be descendant of the declaring class of the target or implement the interface
            if (!targetClass.isAssignableFrom(anInstance.getClass())) {
                return false;
            }
        }

        // for non-static non-constructor targets, use the instance class to determine result
        if (!isStatic && (anInstance != null)) {
            targetClass = anInstance.getClass();
        }

        if (invoker == targetClass) {
            return true;
        }

        // check modifiers of target class
        if (!checkAccess(invoker, targetClass)) {
            return false;
        }

        // public - we are fine
        if (isPublic) {
            return true;
        }

        Class<?> invokerRoot = getRoot(invoker);
        Class<?> targetRoot = getRoot(targetClass);

        // protected
        if (isProtected) {
            return isProtected(invoker, targetClass);
        }

        // package private
        if (isPackage) {
            return Objects.equals(invokerRoot.getPackage(), targetRoot.getPackage());
        }

        if (isPrivate) {
            // private
            return Objects.equals(invokerRoot, targetRoot);
        }

        return false;
    }

    private static boolean checkAccess(Class<?> invoker, Class<?> targetClass) {
        int modifiers = targetClass.getModifiers();

        boolean isPublic = Modifier.isPublic(modifiers);
        boolean isProtected = Modifier.isProtected(modifiers);
        boolean isPrivate = Modifier.isPrivate(modifiers);
        boolean isPackage = !(isPrivate || isProtected || isPublic);

        if (isPublic) {
            return true;
        }

        if (isProtected) {
            return isProtected(invoker, targetClass);

        }

        // must be contained within the invoker or invoker's class
        Class<?> rootInvokerClass = getRoot(invoker);
        Class<?> rootTargetClass = getRoot(targetClass);

        if (!Objects.equals(rootInvokerClass.getPackage(), rootTargetClass.getPackage())) {
            // package and private classes must share package to be visible to each other
            return false;
        }

        if (isPackage) {
            return true; // already checked
        }

        if (isPrivate) {
            return rootInvokerClass == rootTargetClass;
        }

        // wrong accessors
        return false;
    }

    private static boolean isProtected(Class<?> invoker, Class<?> targetClass) {
        // target class must be inner class of invoker or its superclass hierarchy
        Class<?> current = invoker;
        Class<?> targetRoot = getRoot(targetClass);

        do {
            // must be this class or enclosed within this class
            if (current == targetRoot) {
                return true;
            }
            current = current.getEnclosingClass();
        } while (current != null);

        // now hierarchy
        current = invoker;
        do {
            if (current == targetRoot) {
                return true;
            }
            current = current.getSuperclass();
        } while (current != null);

        return false;
    }

    private static Class<?> getRoot(Class<?> invoker) {
        Class<?> result = invoker;
        while (result.getEnclosingClass() != null) {
            result = result.getEnclosingClass();
        }
        return result;
    }
}
