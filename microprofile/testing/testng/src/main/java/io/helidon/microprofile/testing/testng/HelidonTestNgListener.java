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
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.microprofile.testing.HelidonTestContainer;
import io.helidon.microprofile.testing.HelidonTestInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.ClassInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.MethodInfo;
import io.helidon.microprofile.testing.HelidonTestScope;
import io.helidon.microprofile.testing.Proxies;

import org.testng.IAlterSuiteListener;
import org.testng.IClass;
import org.testng.IClassListener;
import org.testng.IConfigurationListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
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
import static io.helidon.microprofile.testing.Instrumented.instrument;

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
                                              ISuiteListener,
                                              IAlterSuiteListener {

    private static final List<Annotation> TYPE_ANNOTATIONS = List.of(
            Proxies.annotation(Guice.class, attr -> {
                if (attr.equals("moduleFactory")) {
                    return HelidonTestNgModuleFactory.class;
                }
                return null;
            }));

    private static final List<Class<? extends Annotation>> METHOD_EXCLUDES = List.of(
            BeforeTest.class);

    // TODO remove thread local
    private static final ThreadLocal<HelidonTestContainer> CONTAINER = new ThreadLocal<>();

    private final Map<HelidonTestInfo<?>, HelidonTestContainer> containers = new ConcurrentHashMap<>();

    @Override
    public void alter(List<XmlSuite> suites) {
        for (XmlSuite suite : suites) {
            for (XmlTest test : suite.getTests()) {
                for (XmlClass xmlClass : test.getXmlClasses()) {
                    ClassInfo classInfo = classInfo(xmlClass.getSupportClass(), HelidonTestDescriptorImpl::new);
                    if (Modifier.isAbstract(classInfo.element().getModifiers())
                        || Modifier.isFinal(classInfo.element().getModifiers())) {
                        // TODO check if contains @Test
                        // TODO warning if final ?
                        continue;
                    }
                    // Use a proxy to start the container after the test instance creation
                    // The container is started lazily when invoking a method
                    // We also add @Guice(moduleFactory = HelidonTestNgModuleFactory.class)
                    xmlClass.setClass(instrument(classInfo.element(),
                            TYPE_ANNOTATIONS, METHOD_EXCLUDES, this::testInstance));
                }
            }
        }
    }

    @Override
    public void onStart(ISuite suite) {
        for (ITestNGMethod tm : suite.getAllMethods()) {
            // replace the test class with a decorator to customize the name
            // to hide the instrumented class name in the test results
            tm.setTestClass(TestClassDecorator.decorate(tm.getTestClass()));
        }
    }

    @Override
    public void beforeInvocation(IInvokedMethod im, ITestResult tr) {
        initContainer(tr, im.getTestMethod());
    }

    @Override
    public void afterInvocation(IInvokedMethod im, ITestResult tr) {
        HelidonTestInfo<?> testInfo = testInfo(tr, im.getTestMethod());
        if (requiresReset(testInfo)) {
            closeContainer(testInfo);
        }
        CONTAINER.remove();
    }

    @Override
    public void onAfterClass(ITestClass tc) {
        closeContainer(testInfo(tc));
    }

    private <T> T testInstance(Class<T> type, Method method) {
        HelidonTestContainer container = CONTAINER.get();
        if (container == null) {
            throw new IllegalStateException("Container not set");
        }
        return container.resolveInstance(type);
    }

    private void initContainer(ITestResult tr, ITestNGMethod tm) {
        HelidonTestInfo<?> testInfo = testInfo(tr, tm);
        HelidonTestContainer container = containers.compute(testInfo.classInfo(),
                (i, c) -> {
                    boolean requireReset = requiresReset(testInfo);
                    if (requireReset && c != null) {
                        c.close();
                    }
                    if (c == null || c.closed()) {
                        HelidonTestScope scope = HelidonTestScope.ofContainer();
                        if (requireReset) {
                            c = new HelidonTestContainer(testInfo, scope, HelidonTestExtensionImpl::new);
                        } else {
                            c = new HelidonTestContainer(testInfo.classInfo(), scope, HelidonTestExtensionImpl::new);
                        }
                    }
                    return c;
                });
        containers.putIfAbsent(testInfo, container);
        CONTAINER.set(container);
    }

    private void closeContainer(HelidonTestInfo<?> testInfo) {
        HelidonTestContainer container = containers.remove(testInfo);
        if (container != null) {
            container.close();
        }
    }

    private HelidonTestInfo<?> testInfo(ITestResult tr, ITestNGMethod tm) {
        ClassInfo classInfo = testInfo(tr.getTestClass());
        return tm != null ? testInfo(tm.getConstructorOrMethod().getMethod(), classInfo) : classInfo;
    }

    private ClassInfo testInfo(IClass ic) {
        return classInfo(ic.getRealClass(), HelidonTestDescriptorImpl::new);
    }

    private MethodInfo testInfo(Method method, ClassInfo classInfo) {
        return methodInfo(method, classInfo, HelidonTestDescriptorImpl::new);
    }

    private static boolean requiresReset(HelidonTestInfo<?> testInfo) {
        return testInfo instanceof MethodInfo methodInfo && methodInfo.requiresReset();
    }
}
