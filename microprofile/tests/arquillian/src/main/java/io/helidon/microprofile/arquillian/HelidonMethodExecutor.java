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
import java.util.stream.Stream;

import io.helidon.microprofile.arquillian.HelidonContainerExtension.HelidonCDIInjectionEnricher;

import org.jboss.arquillian.container.test.spi.ContainerMethodExecutor;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;
import org.junit.After;
import org.junit.Before;

/**
 * Class HelidonMethodExecutor.
 */
public class HelidonMethodExecutor implements ContainerMethodExecutor {

    private HelidonCDIInjectionEnricher enricher = new HelidonCDIInjectionEnricher();

    /**
     * Invoke method after enrichment. Inexplicably, the {@code @Before}
     * and {@code @After} methods are not called when running this
     * executor. Calling them manually for now.
     *
     * @param testMethodExecutor Method executor.
     * @return Test result.
     */
    public TestResult invoke(TestMethodExecutor testMethodExecutor) {
        try {
            Object object = testMethodExecutor.getInstance();
            Method method = testMethodExecutor.getMethod();
            enricher.enrich(object);
            invokeAnnotated(object, Before.class);
            testMethodExecutor.invoke(enricher.resolve(method));
            invokeAnnotated(object, After.class);
        } catch (Throwable t) {
            return TestResult.failed(t);
        }
        return TestResult.passed();
    }

    /**
     * Invoke an annotated method.
     *
     * @param object Test instance.
     * @param annotClass Annotation to look for.
     */
    private void invokeAnnotated(Object object, Class<? extends Annotation> annotClass) {
        Class<?> clazz = object.getClass();
        Stream.of(clazz.getMethods())
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
