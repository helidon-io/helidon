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
package io.helidon.microprofile.testing.testng;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.microprofile.testing.HelidonTestInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.ClassInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.MethodInfo;

import org.testng.IClass;
import org.testng.ITestClass;
import org.testng.ITestNGMethod;

/**
 * Class context.
 * Supports synchronization and ordering of {@link HelidonTestNgListenerBase}.
 */
class ClassContext {

    private final Semaphore semaphore;
    private final HelidonTestNgListenerBase listener;
    private final ClassInfo classInfo;
    private final List<MethodInfo> methods;
    private final AtomicInteger invocationCount = new AtomicInteger();
    private final AtomicBoolean afterClass = new AtomicBoolean();
    private final Map<HelidonTestInfo<?>, List<MethodInfo>> graph = new ConcurrentHashMap<>();
    private final Map<Object, CompletableFuture<Void>> methodsFutures = new ConcurrentHashMap<>();

    /**
     * Create a new instance.
     *
     * @param tc        test class
     * @param semaphore class semaphore
     * @param listener  listener
     */
    ClassContext(ITestClass tc, Semaphore semaphore, HelidonTestNgListenerBase listener) {
        this.listener = listener;
        this.semaphore = semaphore;
        this.classInfo = classInfo(realClass(tc));
        this.methods = methodInfos(classInfo,
                tc.getTestMethods(),
                tc.getBeforeTestMethods(),
                tc.getBeforeTestMethods(),
                tc.getBeforeClassMethods(),
                tc.getAfterClassMethods());
    }

    /**
     * Wait for the running methods.
     */
    void awaitMethods() {
        try {
            for (CompletableFuture<Void> future : Set.copyOf(methodsFutures.values())) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Process a before-invocation event.
     *
     * @param invokedMethod invoked method
     * @param testMethod    corresponding test method, may be {@code null}
     * @see org.testng.IInvokedMethodListener#beforeInvocation(org.testng.IInvokedMethod, org.testng.ITestResult)
     * @see org.testng.IConfigurationListener#beforeConfiguration(org.testng.ITestResult, org.testng.ITestNGMethod)
     */
    void beforeInvocation(ITestNGMethod invokedMethod, ITestNGMethod testMethod) {
        MethodInfo methodInfo = methodInfo(classInfo, realMethod(invokedMethod));
        HelidonTestInfo<?> testInfo = testMethod != null ? methodInfo(classInfo, realMethod(testMethod)) : classInfo;
        graph.compute(testInfo, (k, v) -> {
            if (v == null) {
                v = new ArrayList<>();
            }
            v.add(methodInfo);
            return v;
        });
        listener.onBeforeInvocation(this, methodInfo, testInfo);
        if (invokedMethod.isTest()) {
            methodsFutures.put(methodInfo, new CompletableFuture<>());
        }
    }

    /**
     * Process an after-invocation event.
     *
     * @param invokedMethod invoked method
     * @param testMethod    corresponding test method, may be {@code null}
     * @see org.testng.IInvokedMethodListener#afterInvocation(org.testng.IInvokedMethod, org.testng.ITestResult)
     * @see org.testng.IConfigurationListener#onConfigurationSuccess(org.testng.ITestResult, org.testng.ITestNGMethod)
     * @see org.testng.IConfigurationListener#onConfigurationFailure(org.testng.ITestResult, org.testng.ITestNGMethod)
     */
    void afterInvocation(ITestNGMethod invokedMethod, ITestNGMethod testMethod) {
        try {
            MethodInfo methodInfo = methodInfo(classInfo, realMethod(invokedMethod));
            HelidonTestInfo<?> testInfo = testMethod != null ? methodInfo(classInfo, realMethod(testMethod)) : classInfo;
            List<MethodInfo> deps = graph.compute(testInfo, (k, v) -> {
                if (v == null) {
                    v = new ArrayList<>();
                }
                v.remove(methodInfo);
                return v;
            });
            boolean last = deps.isEmpty();
            listener.onAfterInvocation(methodInfo, testInfo, last);
            CompletableFuture<Void> future = methodsFutures.get(methodInfo);
            if (future != null) {
                future.complete(null);
            }
        } finally {
            afterClass(AtomicInteger::incrementAndGet);
        }
    }

    /**
     * Process a before-class event.
     *
     * @see org.testng.IClassListener#onBeforeClass(org.testng.ITestClass)
     */
    void beforeClass() {
        try {
            semaphore.acquire();
            listener.onBeforeClass(classInfo);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Process an after-class event.
     *
     * @see org.testng.IClassListener#onAfterClass(org.testng.ITestClass)
     */
    void afterClass() {
        afterClass(AtomicInteger::get);
    }

    @Override
    public String toString() {
        return "ClassContext{"
               + "class=" + classInfo.element().getName()
               + ", methods: " + methodNames(methods)
               + '}';
    }

    private void afterClass(Function<AtomicInteger, Integer> op) {
        if (op.apply(invocationCount) == methods.size()
            && afterClass.compareAndSet(false, true)) {
            try {
                listener.onAfterClass(classInfo);
                methodsFutures.clear();
            } finally {
                semaphore.release();
            }
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

    private static Method realMethod(ITestNGMethod tm) {
        return tm.getConstructorOrMethod().getMethod();
    }

    private static Class<?> realClass(IClass tc) {
        if (tc instanceof ClassDecorator(ITestClass delegate)) {
            return delegate.getRealClass();
        }
        return tc.getRealClass();
    }
}
