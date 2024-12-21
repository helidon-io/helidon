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

package io.helidon.microprofile.testing.testng;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.microprofile.testing.HelidonTestInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.ClassInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.MethodInfo;

import org.testng.IClassListener;
import org.testng.IConfigurationListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestClass;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.xml.XmlTest;

/**
 * Base listener that implements before/after invoke methods.
 */
abstract class HelidonTestNgListenerBase implements IInvokedMethodListener,
                                                    IConfigurationListener,
                                                    IClassListener {

    private final Map<XmlTest, Map<ITestClass, ClassContext>> contexts = new ConcurrentHashMap<>();

    /**
     * Filter the given class.
     *
     * @param cls class
     * @return {@code true} if should be processed, {@code false} otherwise
     */
    abstract boolean filterClass(Class<?> cls);

    /**
     * Before invocation.
     *
     * @param methodInfo invoked method info
     * @param testInfo   test info
     * @param last       {@code true} if this method is the last one, {@code false} otherwise
     */
    abstract void onBeforeInvocation(MethodInfo methodInfo, HelidonTestInfo<?> testInfo, boolean last);

    /**
     * After invocation.
     *
     * @param methodInfo invoked method info
     * @param testInfo   test info
     * @param last       {@code true} if this method is the last one, {@code false} otherwise
     */
    abstract void onAfterInvocation(MethodInfo methodInfo, HelidonTestInfo<?> testInfo, boolean last);

    /**
     * Before class.
     *
     * @param classInfo class info
     */
    abstract void onBeforeClass(ClassInfo classInfo);

    /**
     * After class.
     *
     * @param classInfo class info
     */
    abstract void onAfterClass(ClassInfo classInfo);

    @Override
    public void onConfigurationFailure(ITestResult tr, ITestNGMethod tm) {
        if (filterClass(realClass(tr))) {
            ITestNGMethod im = tr.getMethod();
            classContext(im.getTestClass()).afterInvocation(im, tm);
        }
    }

    @Override
    public void onConfigurationSuccess(ITestResult tr, ITestNGMethod tm) {
        if (filterClass(realClass(tr))) {
            ITestNGMethod im = tr.getMethod();
            classContext(im.getTestClass()).afterInvocation(im, tm);
        }
    }

    @Override
    public void beforeConfiguration(ITestResult tr, ITestNGMethod tm) {
        if (filterClass(realClass(tr))) {
            ITestNGMethod im = tr.getMethod();
            classContext(im.getTestClass()).beforeInvocation(im, tm);
        }
    }

    @Override
    public void beforeInvocation(IInvokedMethod im, ITestResult tr) {
        if (filterClass(realClass(tr)) && im.isTestMethod()) {
            ITestNGMethod tm = im.getTestMethod();
            classContext(tm.getTestClass()).beforeInvocation(tm, tm);
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod im, ITestResult tr) {
        if (filterClass(realClass(tr)) && im.isTestMethod()) {
            ITestNGMethod tm = im.getTestMethod();
            classContext(tm.getTestClass()).afterInvocation(tm, tm);
        }
    }

    @Override
    public void onBeforeClass(ITestClass tc) {
        Class<?> cls = tc.getRealClass();
        if (filterClass(cls)) {
            onBeforeClass(classInfo(cls));
        }
    }

    @Override
    public void onAfterClass(ITestClass tc) {
        if (filterClass(tc.getRealClass())) {
            classContext(tc).afterClass();
        }
    }

    /**
     * Get a class info.
     *
     * @param cls class
     * @return ClassInfo
     */
    static ClassInfo classInfo(Class<?> cls) {
        return HelidonTestInfo.classInfo(cls, HelidonTestDescriptorImpl::new);
    }

    /**
     * Get a method info.
     *
     * @param classInfo class info
     * @param method    method
     * @return MethodInfo
     */
    static MethodInfo methodInfo(ClassInfo classInfo, Method method) {
        return HelidonTestInfo.methodInfo(method, classInfo, HelidonTestDescriptorImpl::new);
    }

    private ClassContext classContext(ITestClass tc) {
        return contexts.computeIfAbsent(tc.getXmlTest(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(tc, ClassContext::new);
    }

    private static Class<?> realClass(ITestResult tr) {
        return tr.getTestClass().getRealClass();
    }

    private static Method realMethod(ITestNGMethod tm) {
        return tm.getConstructorOrMethod().getMethod();
    }

    private final class ClassContext {
        private final ClassInfo classInfo;
        private final List<MethodInfo> testMethods;
        private final List<MethodInfo> eachTestMethods;
        private final List<MethodInfo> otherMethods;
        private final List<MethodInfo> scheduledMethods = new ArrayList<>();
        private final List<MethodInfo> invokedMethods = new ArrayList<>();
        private final Map<HelidonTestInfo<?>, List<MethodInfo>> graph = new HashMap<>();
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicBoolean afterClass = new AtomicBoolean();

        ClassContext(ITestClass tc) {
            classInfo = classInfo(tc.getRealClass());
            testMethods = methodInfos(classInfo, tc.getTestMethods());
            eachTestMethods = methodInfos(classInfo, tc.getBeforeTestMethods(), tc.getBeforeTestMethods());
            otherMethods = methodInfos(classInfo, tc.getBeforeClassMethods(), tc.getAfterClassMethods());
        }

        void beforeInvocation(ITestNGMethod invokedMethod, ITestNGMethod testMethod) {
            // TODO assert afterClass == false
            MethodInfo methodInfo = methodInfo(classInfo, realMethod(invokedMethod));
            HelidonTestInfo<?> testInfo = testMethod != null ? methodInfo(classInfo, realMethod(testMethod)) : classInfo;
            try {
                lock.lock();
                scheduledMethods.add(methodInfo);
                graph.compute(testInfo, (k, v) -> {
                    if (v == null) {
                        v = new ArrayList<>();
                    }
                    v.add(methodInfo);
                    return v;
                });
            } finally {
                lock.unlock();
            }
            onBeforeInvocation(methodInfo, testInfo, invokedMethod.isTest());
        }

        void afterInvocation(ITestNGMethod invokedMethod, ITestNGMethod testMethod) {
            // TODO assert afterClass == false
            MethodInfo methodInfo = methodInfo(classInfo, realMethod(invokedMethod));
            HelidonTestInfo<?> testInfo = testMethod != null ? methodInfo(classInfo, realMethod(testMethod)) : classInfo;
            List<MethodInfo> deps;
            try {
                lock.lock();
                invokedMethods.add(methodInfo);
                deps = graph.compute(testInfo, (k, v) -> {
                    if (v == null) {
                        v = new ArrayList<>();
                    }
                    v.remove(methodInfo);
                    return v;
                });
            } finally {
                lock.unlock();
            }
            onAfterInvocation(methodInfo, testInfo, deps.isEmpty());
            afterClass();
        }

        void afterClass() {
            try {
                lock.lock();
                if (scheduledMethods.size() == invokedMethods.size()) {
                    int total = testMethods.size() + otherMethods.size() + eachTestMethods.size();
                    if (invokedMethods.size() >= total && afterClass.compareAndSet(false, true)) {
                        onAfterClass(classInfo);
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public String toString() {
            return "ClassContext{"
                   + "class=" + classInfo.element().getName()
                   + ", testMethods: " + methodNames(testMethods)
                   + ", otherMethods: " + methodNames(otherMethods)
                   + ", eachTestMethods: " + methodNames(eachTestMethods)
                   + ", scheduledMethods: " + methodNames(scheduledMethods)
                   + ", invokedMethods: " + methodNames(invokedMethods)
                   + '}';
        }

        private static List<MethodInfo> methodInfos(ClassInfo classInfo, ITestNGMethod[]... methods) {
            return Stream.of(methods)
                    .flatMap(Stream::of)
                    .map(m -> methodInfo(classInfo, realMethod(m)))
                    .toList();
        }

        private static String methodNames(List<MethodInfo> methodInfos) {
            return methodInfos.stream()
                    .map(MethodInfo::element)
                    .map(Method::getName)
                    .collect(Collectors.joining(",", "[", "]"));
        }
    }
}
