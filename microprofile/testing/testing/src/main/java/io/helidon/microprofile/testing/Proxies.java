/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;

import static io.helidon.microprofile.testing.ReflectionHelper.invoke;

/**
 * Proxy helper.
 */
public class Proxies {

    private Proxies() {
        // cannot be instantiated
    }

    /**
     * Mirror an annotation.
     *
     * @param type       target type
     * @param annotation source annotation
     * @param <T>        target type
     * @return mirror
     */
    public static <T extends Annotation> T mirror(Class<T> type, Annotation annotation) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Object o = Proxy.newProxyInstance(cl, new Class[] {type}, (proxy, method, args) -> {
            Method sourceMethod = annotation.getClass().getMethod(method.getName());
            return invoke(Object.class, sourceMethod, annotation);
        });
        return type.cast(o);
    }

    /**
     * Instantiate an annotation.
     *
     * @param type     annotation type
     * @param function attributes function
     * @param <T>      annotation type
     * @return annotation
     */
    public static <T extends Annotation> T annotation(Class<T> type, Function<String, Object> function) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Object o = Proxy.newProxyInstance(cl, List.of(type).toArray(Class[]::new),
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("annotationType".equals(methodName)) {
                        return type;
                    }
                    Object value = function.apply(methodName);
                    return value != null ? value : method.getDefaultValue();
                });
        return type.cast(o);
    }
}
