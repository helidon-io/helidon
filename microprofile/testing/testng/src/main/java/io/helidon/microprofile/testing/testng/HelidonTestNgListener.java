/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.testing.testng;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.Semaphore;

import io.helidon.microprofile.testing.HelidonTestContainer;
import io.helidon.microprofile.testing.HelidonTestInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.ClassInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.MethodInfo;
import io.helidon.microprofile.testing.HelidonTestScope;
import io.helidon.microprofile.testing.Proxies;

import org.testng.IAlterSuiteListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Guice;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import static io.helidon.microprofile.testing.Instrumented.instrument;
import static io.helidon.microprofile.testing.Instrumented.isInstrumented;
import static io.helidon.microprofile.testing.testng.ClassContext.classInfo;

/**
 * A TestNG listener that integrates CDI with TestNG to support Helidon MP.
 * <p>
 * This extension starts a CDI container and adds the test class as a bean with support for injection.
 * <p>
 * The container is started lazily during test execution to ensure that it is started after all other extensions.
 * <p>
 * The container can be customized with the following annotations:
 * <ul>
 *     <li>{@link HelidonTest#resetPerTest()} force a new CDI container per test</li>
 *     <li>{@link io.helidon.microprofile.testing.DisableDiscovery} disables CDI discovery</li>
 *     <li>{@link io.helidon.microprofile.testing.AddBean} add CDI beans</li>
 *     <li>{@link io.helidon.microprofile.testing.AddExtension} add CDI extension</li>
 *     <li>{@link io.helidon.microprofile.testing.AddJaxRs} add JAX-RS (Jersey)</li>
 * </ul>
 * <p>
 * The configuration can be customized with the following annotations:
 * <ul>
 *     <li>{@link io.helidon.microprofile.testing.Configuration} global setting for MicroProfile configuration</li>
 *     <li>{@link io.helidon.microprofile.testing.AddConfig} declarative key/value pair configuration</li>
 *     <li>{@link io.helidon.microprofile.testing.AddConfigBlock} declarative fragment configuration</li>
 *     <li>{@link io.helidon.microprofile.testing.AddConfigSource} programmatic configuration</li>
 * </ul>
 * <p>
 * See also {@link io.helidon.microprofile.testing.Socket}, a CDI qualifier to inject JAX-RS client or URI.
 * <p>
 * The container is created per test class by default, unless
 * {@link HelidonTest#resetPerTest()} is {@code true}, in
 * which case the container is created per test method.
 * <p>
 * The container and the configuration can be customized per method regardless of the value of
 * {@link HelidonTest#resetPerTest()}. The container will be reset accordingly.
 * <p>
 * It is not recommended to provide a {@code beans.xml} along the test classes, as it would combine beans from all tests.
 * Instead, you should use {@link io.helidon.microprofile.testing.AddBean} to specify the beans per test or method.
 *
 * @see HelidonTest
 */
public class HelidonTestNgListener extends HelidonTestNgListenerBase implements ITestListener,
                                                                                ISuiteListener,
                                                                                IAlterSuiteListener {

    private static final Logger LOGGER = System.getLogger(HelidonTestNgListener.class.getName());

    private static final List<Annotation> TYPE_ANNOTATIONS = List.of(
            Proxies.annotation(Guice.class, attr -> {
                if (attr.equals("moduleFactory")) {
                    return HelidonTestNgModuleFactory.class;
                }
                return null;
            }));

    private static final List<Class<? extends Annotation>> METHOD_EXCLUDES = List.of(
            BeforeTest.class,
            BeforeSuite.class);

    private final Semaphore semaphore = new Semaphore(1);
    private volatile HelidonTestContainer container;

    @Override
    public void alter(List<XmlSuite> suites) {
        for (XmlSuite suite : suites) {
            for (XmlTest test : suite.getTests()) {
                for (XmlClass xmlClass : test.getXmlClasses()) {
                    ClassInfo classInfo = classInfo(xmlClass.getSupportClass());
                    if (classInfo.containsAnnotation(HelidonTest.class)) {
                        Class<?> testClass = classInfo.element();
                        if (Modifier.isAbstract(testClass.getModifiers())) {
                            continue;
                        }
                        if (Modifier.isFinal(testClass.getModifiers())) {
                            LOGGER.log(Level.WARNING, "Cannot instrument final class: {0}", testClass.getName());
                            continue;
                        }
                        // Instrument the test class
                        // Add a @Guice annotation to install HelidonTestNgModuleFactory
                        // Use a proxy to start the container lazily
                        xmlClass.setClass(instrument(testClass, TYPE_ANNOTATIONS, METHOD_EXCLUDES, this::resolve));
                    }
                }
            }
        }
    }

    @Override
    public void onStart(ISuite suite) {
        for (ITestNGMethod tm : suite.getAllMethods()) {
            if (isInstrumented(tm.getTestClass().getRealClass())) {
                // replace the built-in ITestClass with a decorator
                // to hide the instrumented class name in the test results
                tm.setTestClass(ClassDecorator.decorate(tm.getTestClass()));
            }
        }
    }

    @Override
    boolean filterClass(Class<?> cls) {
        return isInstrumented(cls);
    }

    @Override
    void onBeforeInvocation(ClassContext classContext, MethodInfo methodInfo, HelidonTestInfo<?> testInfo) {
        LOGGER.log(Level.DEBUG, "onBeforeInvocation: {0}", testInfo);
        try {
            if (testInfo.requiresReset()) {
                semaphore.acquire();
                classContext.awaitMethods();
                closeContainer(testInfo);
                initContainer(testInfo);
            } else {
                semaphore.acquire();
                initContainer(testInfo.classInfo());
                semaphore.release();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    void onAfterInvocation(MethodInfo methodInfo, HelidonTestInfo<?> testInfo, boolean last) {
        LOGGER.log(Level.DEBUG, "onAfterInvocation: {0}", methodInfo);
        if (last) {
            if (testInfo.requiresReset()) {
                closeContainer(testInfo);
                semaphore.release();
            }
        }
    }

    @Override
    void onBeforeClass(ClassInfo classInfo) {
        LOGGER.log(Level.DEBUG, "onBeforeClass: {0}", classInfo);
    }

    @Override
    void onAfterClass(ClassInfo classInfo) {
        LOGGER.log(Level.DEBUG, "onAfterClass: {0}", classInfo);
        closeContainer(classInfo);
        semaphore.drainPermits();
        semaphore.release();
    }

    private void initContainer(HelidonTestInfo<?> testInfo) {
        if (container == null) {
            LOGGER.log(Level.DEBUG, "initContainer: {0}", testInfo);
            HelidonTestScope testScope = HelidonTestScope.ofContainer();
            container = new HelidonTestContainer(testInfo, testScope, HelidonTestExtensionImpl::new);
        }
    }

    private void closeContainer(HelidonTestInfo<?> testInfo) {
        if (container != null) {
            LOGGER.log(Level.DEBUG, "closeContainer: {0}", testInfo);
            container.close();
            container = null;
        }
    }

    private <T> T resolve(Class<T> type, Method method) {
        if (container == null) {
            throw new IllegalStateException("Container not set");
        }
        return container.resolveInstance(type);
    }
}
