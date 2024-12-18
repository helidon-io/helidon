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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.microprofile.testing.HelidonTestInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.ClassInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.MethodInfo;

import org.testng.IConfigurationListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

/**
 * Base listener that implements before/after invoke methods.
 */
class HelidonTestNgListenerBase implements IInvokedMethodListener,
                                           IConfigurationListener {

    private final Map<HelidonTestInfo<?>, List<ITestNGMethod>> methods = new ConcurrentHashMap<>();

    /**
     * Before invocation.
     *
     * @param classInfo  test class info
     * @param methodInfo invoked method info
     * @param testInfo   test info
     */
    void onBeforeInvocation(ClassInfo classInfo, MethodInfo methodInfo, HelidonTestInfo<?> testInfo) {
        // no-op
    }

    /**
     * After invocation.
     *
     * @param methodInfo invoked method info
     * @param testInfo   test info
     * @param last       {@code true} if this method is the last one, {@code false} otherwise
     */
    void onAfterInvocation(MethodInfo methodInfo, HelidonTestInfo<?> testInfo, boolean last) {
        // no-op
    }

    @Override
    public void beforeConfiguration(ITestResult tr, ITestNGMethod tm) {
        ClassInfo classInfo = classInfo(tr);
        MethodInfo methodInfo = methodInfo(classInfo, tr.getMethod());
        HelidonTestInfo<?> testInfo = tm != null ? methodInfo(classInfo, tm) : classInfo;
        methods.compute(testInfo, (k, v) -> {
            if (v == null) {
                v = new ArrayList<>();
            }
            v.add(tm);
            return v;
        });
        onBeforeInvocation(classInfo, methodInfo, testInfo);
    }

    @Override
    public void onConfigurationFailure(ITestResult tr, ITestNGMethod tm) {
        afterConfiguration(tr, tm);
    }

    @Override
    public void onConfigurationSuccess(ITestResult tr, ITestNGMethod tm) {
        afterConfiguration(tr, tm);
    }

    /**
     * After configuration.
     *
     * @param tr test result
     * @param tm test method, may be {@code null}
     */
    void afterConfiguration(ITestResult tr, ITestNGMethod tm) {
        ClassInfo classInfo = classInfo(tr);
        MethodInfo methodInfo = methodInfo(classInfo, tr.getMethod());
        HelidonTestInfo<?> testInfo = tm != null ? methodInfo(classInfo, tm) : classInfo;
        List<ITestNGMethod> deps = methods.compute(testInfo, (k, v) -> {
            if (v == null) {
                return List.of();
            }
            v.remove(tm);
            return v;
        });
        onAfterInvocation(methodInfo, testInfo, deps.isEmpty());
    }

    @Override
    public void beforeInvocation(IInvokedMethod im, ITestResult tr) {
        if (im.isTestMethod()) {
            ClassInfo classInfo = classInfo(tr);
            MethodInfo methodInfo = methodInfo(classInfo, im.getTestMethod());
            onBeforeInvocation(classInfo, methodInfo, methodInfo);
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod im, ITestResult tr) {
        if (im.isTestMethod()) {
            ClassInfo classInfo = classInfo(tr);
            MethodInfo methodInfo = methodInfo(classInfo, im.getTestMethod());
            List<ITestNGMethod> deps = methods.getOrDefault(methodInfo, List.of());
            onAfterInvocation(methodInfo, methodInfo, deps.isEmpty());
        }
    }

    /**
     * Get a class info.
     *
     * @param clazz class
     * @return ClassInfo
     */
    static ClassInfo classInfo(Class<?> clazz) {
        return HelidonTestInfo.classInfo(clazz, HelidonTestDescriptorImpl::new);
    }

    /**
     * Get a class info.
     *
     * @param tr test result
     * @return ClassInfo
     */
    static ClassInfo classInfo(ITestResult tr) {
        return classInfo(tr.getTestClass().getRealClass());
    }

    /**
     * Get a method info.
     *
     * @param clazz  class
     * @param method method
     * @return MethodInfo
     */
    static MethodInfo methodInfo(Class<?> clazz, Method method) {
        return methodInfo(classInfo(clazz), method);
    }

    /**
     * Get a methodInfo info.
     *
     * @param classInfo class info
     * @param tm        method
     * @return MethodInfo
     */
    static MethodInfo methodInfo(ClassInfo classInfo, ITestNGMethod tm) {
        return methodInfo(classInfo, tm.getConstructorOrMethod().getMethod());
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
}
