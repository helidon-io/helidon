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
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import io.helidon.common.UncheckedException;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.implementation.attribute.MethodAttributeAppender;
import net.bytebuddy.matcher.ElementMatcher;

import static io.helidon.microprofile.testing.ReflectionHelper.invoke;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isEquals;
import static net.bytebuddy.matcher.ElementMatchers.isHashCode;
import static net.bytebuddy.matcher.ElementMatchers.isToString;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Marker interface for instrumented type.
 */
public interface Instrumented {

    /**
     * Instantiate a class without running constructors.
     *
     * @param type type
     * @param <T>  type
     * @return instance
     */
    static <T> T allocateInstance(Class<T> type) {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            // allocateInstance is OK to use (not planned for removal by JEP471)
            return type.cast(unsafe.allocateInstance(type));
        } catch (InstantiationException
                 | IllegalAccessException
                 | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test if the given type is instrumented.
     *
     * @param type type
     * @return {@code true} if instrumented, {@code false} otherwise
     */
    static boolean isInstrumented(Class<?> type) {
        for (Class<?> iface : type.getInterfaces()) {
            if (iface.equals(Instrumented.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create an instrumented class.
     *
     * @param type           type
     * @param annotations    annotations to add to the proxy class
     * @param methodExcludes method annotations that skip interception
     * @param resolver       function to resolve the delegate
     * @param <T>            type
     * @return instrumented class
     */
    static <T> Class<? extends T> instrument(Class<T> type,
                                             List<Annotation> annotations,
                                             List<Class<? extends Annotation>> methodExcludes,
                                             BiFunction<Class<T>, Method, T> resolver) {

        return instrument(type, annotations, methodExcludes, (proxy, method, args) -> {
            T instance = resolver.apply(type, method);
            if (instance != null) {
                try {
                    return invoke(Object.class, method, instance, args);
                } catch (UncheckedException e) {
                    if (e.getCause() instanceof InvocationTargetException te) {
                        throw te;
                    }
                    throw e;
                }
            }
            return null;
        });
    }

    /**
     * Create an instrumented class.
     *
     * @param type           type
     * @param annotations    annotations to add to the proxy class
     * @param methodExcludes method annotations that skip interception
     * @param handler        invocation handler
     * @param <T>            type
     * @return instrumented class
     */
    static <T> Class<? extends T> instrument(Class<T> type,
                                             List<Annotation> annotations,
                                             List<Class<? extends Annotation>> methodExcludes,
                                             InvocationHandler handler) {

        // always skip delegation for equals, hashCode, toString
        // the goal is to instantiate the delegate for "concrete" methods
        ElementMatcher.Junction<MethodDescription> matcher = not(isEquals())
                .and(not(isHashCode()))
                .and(not(isToString()));

        // also skip delegation for methods annotated with the given annotations
        for (Class<? extends Annotation> exclude : methodExcludes) {
            matcher = matcher.and(not(isAnnotatedWith(exclude)));
        }

        try (DynamicType.Unloaded<T> unloaded = new ByteBuddy()
                // preserve constructors annotations
                // must-have for constructor injection
                .subclass(type, ConstructorStrategy.Default.IMITATE_SUPER_CLASS.withInheritedAnnotations())
                .implement(Instrumented.class)
                // repeat type annotations and add the given annotations
                .annotateType(Stream.concat(
                                Arrays.stream(type.getDeclaredAnnotations()),
                                annotations.stream())
                        .toArray(Annotation[]::new))
                .withHashCodeEquals()
                .method(matcher)
                .intercept(InvocationHandlerAdapter.of(handler))
                // repeat all method annotations on the subclass
                .attribute(MethodAttributeAppender.ForInstrumentedMethod.INCLUDING_RECEIVER)
                .make()) {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            return unloaded.load(type.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(lookup))
                    .getLoaded();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
