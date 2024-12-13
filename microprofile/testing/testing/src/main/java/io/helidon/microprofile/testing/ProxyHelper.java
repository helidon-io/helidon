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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

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
     * @param type       target type
     * @param annotation source annotation
     * @param <T>        target type
     * @return mirror
     */
    public static <T extends Annotation> T mirrorAnnotation(Class<T> type, Annotation annotation) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Object o = Proxy.newProxyInstance(cl, new Class[] {type}, (proxy, method, args) -> {
            Method sourceMethod = annotation.getClass().getMethod(method.getName());
            try {
                return sourceMethod.invoke(annotation);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
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
        Object o = proxy(List.of(type), (proxy, method, args) -> {
            String methodName = method.getName();
            if ("annotationType".equals(methodName)) {
                return type;
            }
            Object value = function.apply(methodName);
            return value != null ? value : method.getDefaultValue();
        });
        return type.cast(o);
    }

    /**
     * Create a proxy instance.
     *
     * @param interfaces interfaces
     * @param handler    invocation handler
     * @return proxy
     */
    public static Object proxy(List<Class<?>> interfaces, InvocationHandler handler) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return Proxy.newProxyInstance(cl, interfaces.toArray(Class[]::new), handler);
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
     * @param type           type
     * @param methodExcludes method annotation types to skip interception
     * @param resolver       function to resolve the delegate
     * @param <T>            type
     * @return proxy
     */
    public static <T> T proxyDelegate(Class<T> type,
                                      List<Class<? extends Annotation>> methodExcludes,
                                      BiFunction<Class<T>, Method, T> resolver) {

        Class<? extends T> loaded = proxyClass(type, List.of(), methodExcludes, resolver);
        return allocateInstance(loaded);
    }

    /**
     * Instantiate a class without running constructors.
     *
     * @param type type
     * @param <T>  type
     * @return instance
     */
    public static <T> T allocateInstance(Class<T> type) {
        try {
            // instantiate without running constructors
            return type.cast(unsafe().allocateInstance(type));
        } catch (InstantiationException
                 | IllegalAccessException
                 | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a delegated proxy class.
     *
     * @param type           type
     * @param annotations    annotations to add to the proxy class
     * @param methodExcludes method annotation types to skip interception
     * @param resolver       function to resolve the delegate
     * @param <T>            type
     * @return proxy class
     */
    public static <T> Class<? extends T> proxyClass(Class<T> type,
                                                    List<Annotation> annotations,
                                                    List<Class<? extends Annotation>> methodExcludes,
                                                    BiFunction<Class<T>, Method, T> resolver) {

        ElementMatcher.Junction<MethodDescription> matcher = not(isEquals()).and(not(isHashCode()));
        for (Class<? extends Annotation> exclude : methodExcludes) {
            matcher = matcher.and(not(isAnnotatedWith(exclude)));
        }

        try (DynamicType.Unloaded<T> unloaded = new ByteBuddy()
                .subclass(type, ConstructorStrategy.Default.IMITATE_SUPER_CLASS.withInheritedAnnotations())
                .annotateType(annotations.toArray(Annotation[]::new))
                .withHashCodeEquals()
                .method(matcher)
                .intercept(InvocationHandlerAdapter.of((proxy, method, args) -> {
                    if ("toString".equals(method.getName()) && method.getParameterCount() == 0) {
                        return type.getName();
                    }
                    T instance = resolver.apply(type, method);
                    if (instance != null) {
                        method.setAccessible(true);
                        return method.invoke(instance, args);
                    }
                    return null;
                }))
                .make()) {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            return unloaded.load(type.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(lookup))
                    .getLoaded();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static sun.misc.Unsafe unsafe() throws IllegalAccessException, NoSuchFieldException {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
