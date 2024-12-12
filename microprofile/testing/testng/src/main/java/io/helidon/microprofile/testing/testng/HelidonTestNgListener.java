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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.microprofile.testing.HelidonTestContainer;
import io.helidon.microprofile.testing.HelidonTestInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.ClassInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.MethodInfo;
import io.helidon.microprofile.testing.HelidonTestScope;
import io.helidon.microprofile.testing.ProxyHelper;

import org.testng.IAlterSuiteListener;
import org.testng.IClassListener;
import org.testng.IConfigurationListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestClass;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Guice;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import static io.helidon.microprofile.testing.HelidonTestInfo.classInfo;
import static io.helidon.microprofile.testing.HelidonTestInfo.methodInfo;

/**
 * A TestNG listener that integrates CDI with TestNG to support Helidon MP.
 * <p>
 * This extension starts a CDI container and adds the test class as a bean with support for injection. The test class uses
 * a CDI scope that follows the test lifecycle as defined by {@code TODO}.
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
public class HelidonTestNgListener implements ITestListener,
                                              IClassListener,
                                              IInvokedMethodListener,
                                              IConfigurationListener,
                                              IAlterSuiteListener {

    /**
     * Current container.
     */
    static final ThreadLocal<HelidonTestContainer> CONTAINER = new ThreadLocal<>();

    private static final List<Annotation> PROXY_ANNOTATIONS = List.of(
            ProxyHelper.newAnnotation(Guice.class, attr -> {
                if (attr.equals("moduleFactory")) {
                    return HelidonTestNgModuleFactory.class;
                }
                return null;
            }));

    private static final List<Class<? extends Annotation>> PROXY_METHOD_EXCLUDES = List.of(
            BeforeTest.class);

    private final Map<Class<?>, Class<?>> instrumentedClasses = new HashMap<>();
    private final Map<HelidonTestInfo<?>, HelidonTestContainer> containers = new ConcurrentHashMap<>();

    // TODO early hook to self-disable if no helidon used

    @Override
    public void alter(List<XmlSuite> suites) {
        for (XmlSuite suite : suites) {
            for (XmlTest test : suite.getTests()) {
                // TODO support ParallelMode with annotation
                test.setParallel(XmlSuite.ParallelMode.METHODS);

                // The test class is instrumented as a proxy to support lazy container initialization
                // and also to configure HelidonTestNgModuleFactory
                // I.e. we add @Guice(moduleFactory = HelidonTestNgModuleFactory.class)
                for (XmlClass xmlClass : test.getClasses()) {
                    Class<?> testClass = xmlClass.getSupportClass();
                    Class<?> instumentedClass = ProxyHelper.proxyClass(testClass,
                            PROXY_ANNOTATIONS, PROXY_METHOD_EXCLUDES, this::resolveInstance);
                    instrumentedClasses.put(instumentedClass, testClass);
                    xmlClass.setClass(instumentedClass);
                }
            }
        }
    }

    @Override
    public void onTestFailure(ITestResult tr) {
        onAfterTest(tr);
    }

    @Override
    public void onTestSuccess(ITestResult tr) {
        onAfterTest(tr);
    }

    @Override
    public void onTestStart(ITestResult tr) {
        initContainer(tr, tr.getMethod());
    }

    @Override
    public void beforeConfiguration(ITestResult tr, ITestNGMethod tm) {
        initContainer(tr, tm);
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        CONTAINER.remove();
    }

    @Override
    public void onAfterClass(ITestClass tc) {
        HelidonTestContainer container = containers.remove(classInfo(tc.getRealClass(), HelidonTestDescriptorImpl::new));
        if (container != null) {
            container.close();
        }
    }

    private <T> T resolveInstance(Class<T> type, Method method) {
        HelidonTestContainer container = CONTAINER.get();
        if (container == null) {
            throw new IllegalStateException("Container not set");
        }
        return container.resolveInstance(type);
    }

    private void initContainer(ITestResult tr, ITestNGMethod tm) {
        HelidonTestInfo<?> testInfo = testInfo(tr, tm);
        HelidonTestContainer container = containers.compute(testInfo.classInfo(), (i, c) -> {
            // TODO annotation for test method lifecycle

            if (testInfo instanceof MethodInfo methodInfo && methodInfo.requiresReset()) {
                // close the "class container" only for sequential executions
                // parallel & requireReset use multiple containers
                if (c != null && isSequential(tr)) {
                    c.close();
                }
            }

            if (c == null || c.closed()) {
                HelidonTestScope scope = HelidonTestScope.ofContainer();
                if (testInfo instanceof MethodInfo methodInfo && methodInfo.requiresReset()) {
                    c = new HelidonTestContainer(methodInfo, scope, HelidonTestExtensionImpl::new);
                } else {
                    if (!containers.containsKey(testInfo.classInfo())) {
                        c = new HelidonTestContainer(testInfo.classInfo(), scope, HelidonTestExtensionImpl::new);
                    }
                }
            }
            return c;
        });
        if (testInfo instanceof MethodInfo methodInfo) {
            containers.put(methodInfo, container);
        }
        CONTAINER.set(container);
    }

    private void onAfterTest(ITestResult tr) {
        HelidonTestInfo<?> testInfo = testInfo(tr, tr.getMethod());
        if (testInfo instanceof MethodInfo methodInfo) {
            HelidonTestContainer container = containers.remove(methodInfo);
            if (container != null && methodInfo.requiresReset()) {
                container.close();
            }
        }
    }

    private HelidonTestInfo<?> testInfo(ITestResult tr, ITestNGMethod tm) {
        Class<?> instrumentedClass = tr.getTestClass().getRealClass();
        Class<?> testClass = instrumentedClasses.getOrDefault(instrumentedClass, instrumentedClass);
        ClassInfo classInfo = classInfo(testClass, HelidonTestDescriptorImpl::new);
        if (tm != null) {
            return methodInfo(tm.getConstructorOrMethod().getMethod(), classInfo, HelidonTestDescriptorImpl::new);
        }
        return classInfo;
    }

    private static boolean isSequential(ITestResult tr) {
        return tr.getTestContext().getCurrentXmlTest().getParallel() != XmlSuite.ParallelMode.METHODS;
    }
}
