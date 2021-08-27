/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.arquillian;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.control.RequestContextController;

import io.helidon.microprofile.arquillian.HelidonContainerExtension.HelidonCDIInjectionEnricher;

import org.jboss.arquillian.container.test.spi.ContainerMethodExecutor;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.Description;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * Class HelidonMethodExecutor.
 */
public class HelidonMethodExecutor implements ContainerMethodExecutor {
    private static final Logger LOGGER = Logger.getLogger(ContainerMethodExecutor.class.getName());

    private HelidonCDIInjectionEnricher enricher = new HelidonCDIInjectionEnricher();

    /**
     * Invoke method after enrichment.
     *
     * - JUnit: Inexplicably, the {@code @Before} and {@code @After} methods are
     * not called when running this executor, so we call them manually.
     *
     * - TestNG: Methods decorated with {@code @BeforeMethod} and {@code AfterMethod}
     * are called too early, before enrichment takes places. Here we call them
     * again to make sure instances are initialized properly.
     *
     * @param testMethodExecutor Method executor.
     * @return Test result.
     */
    public TestResult invoke(TestMethodExecutor testMethodExecutor) {
        RequestContextController controller = enricher.getRequestContextController();
        try {
            controller.activate();
            Object instance = testMethodExecutor.getInstance();
            Method method = testMethodExecutor.getMethod();
            LOGGER.info("Invoking '" + method + "' on " + instance);
            enricher.enrich(instance);
            jUnitTestNameRule(testMethodExecutor);
            invokeBefore(instance, method);
            testMethodExecutor.invoke(enricher.resolve(method));
            invokeAfter(instance, method);
        } catch (Throwable t) {
            return TestResult.failed(t);
        } finally {
            controller.deactivate();
        }
        return TestResult.passed();
    }

    private void jUnitTestNameRule(TestMethodExecutor testMethodExecutor) {
        Object instance = testMethodExecutor.getInstance();
        AtomicReference<Class<?>> clazz = new AtomicReference<>(instance.getClass());

        Stream.generate(() -> clazz.getAndUpdate(old -> old == null ? null : old.getSuperclass()))
                .takeWhile(Objects::nonNull)
                .map(Class::getDeclaredFields)
                .flatMap(Stream::of)
                .filter(f -> f.getAnnotation(Rule.class) != null)
                .filter(f -> TestName.class.isAssignableFrom(f.getType()))
                .forEach(rethrow(f -> {
                    Object testName = f.get(instance);
                    if (testName != null) {
                        Field nameField = TestName.class.getDeclaredField("name");
                        nameField.setAccessible(true);
                        nameField.set(testName,
                                Description.createTestDescription(
                                        instance.getClass(),
                                        testMethodExecutor.getMethod().getName()).getMethodName());
                    }
                }));
    }

    /**
     * Invoke before methods.
     *
     * @param instance Test instance.
     */
    private static void invokeBefore(Object instance, Method testMethod) {
        invokeAnnotated(instance, testMethod, Before.class);          // Junit
        invokeAnnotated(instance, testMethod, BeforeMethod.class);    // TestNG
    }

    /**
     * Invoke after methods.
     *
     * @param instance Test instance.
     */
    private static void invokeAfter(Object instance, Method testMethod) {
        invokeAnnotated(instance, testMethod, After.class);           // JUnit
        invokeAnnotated(instance, testMethod, AfterMethod.class);     // TestNG
    }

    /**
     * Invoke an annotated method.
     *
     * @param object     Test instance.
     * @param annotClass Annotation to look for.
     */
    private static void invokeAnnotated(Object object, Method testMethod, Class<? extends Annotation> annotClass) {
        invokeAnnotated(object, testMethod, object.getClass(), annotClass, Collections.emptySet());
    }

    private static void invokeAnnotated(Object object, Method testMethod, Class<?> clazz,
                                        Class<? extends Annotation> annotClass, Set<Method> overridden) {
        // Collect list of candidates
        Set<Method> invocable = Stream.of(clazz.getDeclaredMethods())
                .filter(m -> m.getAnnotation(annotClass) != null)
                .collect(Collectors.toSet());

        // First call superclass methods
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            invokeAnnotated(object, testMethod, superClass, annotClass, invocable);
        }

        // Invoke all candidates skipping those that are overridden. Methods compared
        // by name only since they typically have no params.
        invocable.stream()
                .filter(m -> overridden.stream().map(Method::getName).noneMatch(s -> s.equals(m.getName())))
                .forEach(rethrow(m -> {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Method.class) {
                        // @BeforeMethod
                        // org.jboss.arquillian.testng.Arquillian.arquillianBeforeTest(java.lang.reflect.Method)
                        m.invoke(object, testMethod);
                    } else {
                        m.invoke(object);
                    }
                }));
    }

    static <T> Consumer<? super T> rethrow(ThrowingConsumer<T> consumer) {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };
    }

    @FunctionalInterface
    interface ThrowingConsumer<T> {
        void accept(T item) throws Throwable;
    }
}
