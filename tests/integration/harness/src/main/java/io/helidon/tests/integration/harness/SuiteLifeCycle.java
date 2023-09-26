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
package io.helidon.tests.integration.harness;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

import static java.util.Comparator.comparingInt;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

/**
 * An {@link BeforeAllCallback} extension that supports {@link BeforeSuite} and {@link AfterSuite}.
 */
public class SuiteLifeCycle implements BeforeAllCallback {

    private static final String STORE_KEY = SuiteLifeCycle.class.getName();
    private static final Comparator<Handler<BeforeSuite>> BS_COMPARATOR =
            comparator(BeforeSuite.class, value -> value.annotation().priority());
    private static final Comparator<Handler<AfterSuite>> AS_COMPARATOR =
            comparator(AfterSuite.class, value -> value.annotation().priority());
    private static final Comparator<Handler<SetUp>> SETUP_COMPARATOR =
            comparator(SetUp.class, value -> value.annotation().priority());

    @Override
    public void beforeAll(ExtensionContext context) {
        SuiteFinder.findSuite(context).ifPresent(suiteContext -> {
            Store store = context.getRoot().getStore(GLOBAL);
            Object flag = store.get(STORE_KEY);
            if (flag == null) {
                store.put(STORE_KEY, this);
                invokeBeforeSuite(suiteContext);
                suiteContext.future().thenRun(() -> invokeAfterSuite(suiteContext));
            }
            context.getTestClass().ifPresent(
                    clazz -> Arrays.stream(clazz.getMethods())
                            .filter(method -> method.isAnnotationPresent(SetUp.class))
                            .forEach(method -> handleSetUp(suiteContext, method))
            );
        });
    }

    private void handleBeforeSuite(SuiteContext context, Method method) {
        Object value = invoke(BeforeSuite.class, method);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> params = context.parameters();
            map.forEach((k, v) -> params.put(k.toString(), v));
        }
    }

    private void handleAfterSuite(SuiteContext context, Method method) {
        invoke(AfterSuite.class, method, resolveParameters(context, method));
    }

    private void handleSetUp(SuiteContext context, Method method) {
        invoke(SetUp.class, method, resolveParameters(context, method));
    }

    private void invokeBeforeSuite(SuiteContext suiteContext) {
        findHandlers(suiteContext.suiteClass(), BeforeSuite.class)
                .stream()
                .sorted(BS_COMPARATOR)
                .map(Handler::method)
                .forEach(method -> handleBeforeSuite(suiteContext, method));
    }

    private void invokeAfterSuite(SuiteContext suiteContext) {
        findHandlers(suiteContext.suiteClass(), AfterSuite.class)
                .stream()
                .sorted(AS_COMPARATOR)
                .map(Handler::method)
                .forEach(method -> handleAfterSuite(suiteContext, method));
    }

    private void invokeSetUp(SuiteContext suiteContext) {
        findHandlers(suiteContext.suiteClass(), SetUp.class)
                .stream()
                .sorted(SETUP_COMPARATOR)
                .map(Handler::method)
                .forEach(method -> handleSetUp(suiteContext, method));
    }

    private static Object[] resolveParameters(SuiteContext context, Method method) {
        List<Object> paramValues = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            Class<?> paramType = parameter.getType();
            Object paramValue = context.parameter(paramType)
                    .orElseThrow(() -> new IllegalArgumentException(String.format(
                            "Method %s has a parameter %s that is not supported",
                            method, paramType)));
            paramValues.add(paramValue);
        }
        return paramValues.toArray(new Object[0]);
    }

    private static Object invoke(Class<? extends Annotation> annotationClass, Method method, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException(String.format(
                    "Cannot invoke %s method",
                    annotationClass.getSimpleName()),
                    ex);
        }
    }

    private static <T extends Annotation> List<Handler<T>> findHandlers(Class<?> clazz, Class<T> annotationClass) {
        LinkedList<Handler<T>> handlers = new LinkedList<>();
        LinkedList<Class<?>> hierarchy = new LinkedList<>();
        Class<?> analyzedClass = clazz;
        while (analyzedClass != null && !analyzedClass.equals(Object.class)) {
            hierarchy.addFirst(analyzedClass);
            analyzedClass = analyzedClass.getSuperclass();
        }
        for (Class<?> aClass : hierarchy) {
            for (Method method : aClass.getDeclaredMethods()) {
                T annotation = method.getDeclaredAnnotation(annotationClass);
                if (annotation != null) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        handlers.add(new Handler<>(annotation, method));
                    } else {
                        throw new IllegalStateException("Method " + method + " is annotated with "
                                + annotationClass.getSimpleName()
                                + " yet it is not static");
                    }
                }
            }
        }
        return handlers;
    }

    private static <U extends Annotation> Comparator<Handler<U>> comparator(Class<U> ignored,
                                                                            ToIntFunction<Handler<U>> function) {
        return comparingInt(function);
    }

    private record Handler<T extends Annotation>(T annotation, Method method) {
    }
}
