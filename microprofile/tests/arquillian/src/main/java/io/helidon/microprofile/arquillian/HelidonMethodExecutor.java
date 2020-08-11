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

package io.helidon.microprofile.arquillian;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.enterprise.context.control.RequestContextController;

import io.helidon.microprofile.arquillian.HelidonContainerExtension.HelidonCDIInjectionEnricher;

import org.jboss.arquillian.container.test.spi.ContainerMethodExecutor;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;
import org.junit.After;
import org.junit.Before;
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
            invokeBefore(instance);
            testMethodExecutor.invoke(enricher.resolve(method));
            invokeAfter(instance);
        } catch (Throwable t) {
            return TestResult.failed(t);
        } finally {
            controller.deactivate();
        }
        return TestResult.passed();
    }

    /**
     * Invoke before methods.
     *
     * @param instance Test instance.
     */
    private static void invokeBefore(Object instance) {
        invokeAnnotated(instance, Before.class);          // Junit
        invokeAnnotated(instance, BeforeMethod.class);    // TestNG
    }

    /**
     * Invoke after methods.
     *
     * @param instance Test instance.
     */
    private static void invokeAfter(Object instance) {
        invokeAnnotated(instance, After.class);           // JUnit
        invokeAnnotated(instance, AfterMethod.class);     // TestNG
    }

    /**
     * Invoke an annotated method.
     *
     * @param object Test instance.
     * @param annotClass Annotation to look for.
     */
    private static void invokeAnnotated(Object object, Class<? extends Annotation> annotClass) {
        Class<?> clazz = object.getClass();
        Stream.of(clazz.getDeclaredMethods())
                .filter(m -> m.getAnnotation(annotClass) != null)
                .forEach(m -> {
                    try {
                        m.invoke(object);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
