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

package io.helidon.webserver.testing.junit5;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.LinkedList;
import java.util.function.BiConsumer;

import io.helidon.webserver.WebServer;

/**
 * Utility methods for JUnit5 extensions.
 */
public final class Junit5Util {
    private Junit5Util() {
    }

    /**
     * Discover socket name using {@link Socket} annotation.
     * If none found, {@link WebServer#DEFAULT_SOCKET_NAME} is returned.
     *
     * @param parameter parameter to check
     * @return name of the socket the parameter belongs to
     */
    public static String socketName(Parameter parameter) {
        Socket socketAnnot = parameter.getAnnotation(Socket.class);

        if (socketAnnot == null) {
            return WebServer.DEFAULT_SOCKET_NAME;
        }

        return socketAnnot.value();
    }

    /**
     * Finds all static methods annotated with the defined annotation on the test class (and its hierarchy).
     *
     * @param testClass      current test class
     * @param annotationType class of the annotation
     * @param handler        handler of discovered methods
     * @param <T>            type of the annotation
     */
    static <T extends Annotation> void withStaticMethods(Class<?> testClass,
                                                         Class<T> annotationType,
                                                         BiConsumer<T, Method> handler) {
        LinkedList<Class<?>> hierarchy = new LinkedList<>();
        Class<?> analyzedClass = testClass;
        while (analyzedClass != null && !analyzedClass.equals(Object.class)) {
            hierarchy.addFirst(analyzedClass);
            analyzedClass = analyzedClass.getSuperclass();
        }
        for (Class<?> aClass : hierarchy) {
            for (Method method : aClass.getDeclaredMethods()) {
                T annotation = method.getDeclaredAnnotation(annotationType);
                if (annotation != null) {
                    // maybe our method
                    if (Modifier.isStatic(method.getModifiers())) {
                        handler.accept(annotation, method);
                    } else {
                        throw new IllegalStateException("Method " + method + " is annotated with "
                                                                + annotationType.getSimpleName()
                                                                + " yet it is not static");
                    }
                }
            }
        }
    }
}
