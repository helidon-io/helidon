/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiFunction;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isEquals;
import static net.bytebuddy.matcher.ElementMatchers.isHashCode;
import static net.bytebuddy.matcher.ElementMatchers.isToString;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Proxy helper.
 */
public class ProxyHelper {

    private ProxyHelper() {
        // cannot be instantiated
    }

    /**
     * Mirror an annotation.
     *
     * @param type       annotation type
     * @param annotation annotation
     * @param <T>        annotation type
     * @return mirror
     */
    public static <T extends Annotation> T mirror(Class<T> type, Annotation annotation) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Object o = Proxy.newProxyInstance(cl, new Class[] {type}, (proxy, method, args) -> {
            try {
                Method sourceMethod = annotation.getClass().getMethod(method.getName());
                return sourceMethod.invoke(annotation, args);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
        return type.cast(o);
    }

    /**
     * Create a delegated proxy instance.
     *
     * @param type     type
     * @param resolver function to resolve the delegate
     * @param <T>      type
     * @return proxy
     */
    public static <T> T proxyDelegate(Class<T> type, BiFunction<Class<T>, Method, T> resolver) {
        return proxyDelegate(type, List.of(), resolver);
    }

    /**
     * Create a delegated proxy instance.
     *
     * @param type     type
     * @param excludes method annotation types to skip interception
     * @param resolver function to resolve the delegate
     * @param <T>      type
     * @return proxy
     */
    public static <T> T proxyDelegate(Class<T> type,
                                      List<Class<? extends Annotation>> excludes,
                                      BiFunction<Class<T>, Method, T> resolver) {

        ElementMatcher.Junction<MethodDescription> matcher = not(isEquals())
                .and(not(isHashCode()))
                .and(not(isToString()));
        for (Class<? extends Annotation> exclude : excludes) {
            matcher = matcher.and(not(isAnnotatedWith(exclude)));
        }

        try (DynamicType.Unloaded<T> unloaded = new ByteBuddy()
                .subclass(type, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                .withHashCodeEquals()
                .method(matcher)
                .intercept(InvocationHandlerAdapter.of((proxy, method, args) -> {
                    T instance = resolver.apply(type, method);
                    if (instance != null) {
                        method.setAccessible(true);
                        return method.invoke(instance, args);
                    }
                    return null;
                }))
                .make()) {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            Class<? extends T> loaded = unloaded.load(type.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(lookup))
                    .getLoaded();
            // instantiate without running constructors
            return loaded.cast(unsafe().allocateInstance(loaded));
        } catch (InstantiationException
                 | IllegalAccessException
                 | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static sun.misc.Unsafe unsafe() throws IllegalAccessException, NoSuchFieldException {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
