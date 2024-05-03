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
package io.helidon.microprofile.testing.junit5;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BiFunction;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;

import static net.bytebuddy.matcher.ElementMatchers.isEquals;
import static net.bytebuddy.matcher.ElementMatchers.isHashCode;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Proxy helper.
 */
class ProxyHelper {

    private ProxyHelper() {
        // cannot be instantiated
    }

    /**
     * Create a proxy instance.
     *
     * @param type     type
     * @param resolver function to resolve the delegate
     * @param <T>      type
     * @return proxy
     */
    static <T> T proxyDelegate(Class<T> type, BiFunction<Class<T>, Method, T> resolver) {
        try (DynamicType.Unloaded<T> unloaded = new ByteBuddy()
                .subclass(type, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                .withHashCodeEquals()
                .method(not(isEquals()).and(not(isHashCode())))
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
