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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

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
 * Base listener.
 * Implements the following features:
 * <ul>
 *     <li>Single instrumented test class running at a time</li>
 *     <li>{@link #onAfterClass(ClassInfo)} invoked last</li>
 * </ul>
 */
abstract class HelidonTestNgListenerBase implements IInvokedMethodListener,
                                                    IConfigurationListener,
                                                    IClassListener {

    private static final Map<XmlTest, Map<ITestClass, ClassContext>> CONTEXTS = new ConcurrentHashMap<>();
    private static final Semaphore SEMAPHORE = new Semaphore(1);

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
     * @param classContext class context
     * @param methodInfo   invoked method info
     * @param testInfo     test info
     */
    abstract void onBeforeInvocation(ClassContext classContext, MethodInfo methodInfo, HelidonTestInfo<?> testInfo);

    /**
     * After invocation.
     *
     * @param methodInfo invoked method info
     * @param testInfo   test info
     * @param last       {@code true} if this is the last invocation of {@code testInfo}, {@code false} otherwise
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
            classContext(tc).beforeClass();
        }
    }

    @Override
    public void onAfterClass(ITestClass tc) {
        if (filterClass(tc.getRealClass())) {
            classContext(tc).afterClass();
        }
    }

    private ClassContext classContext(ITestClass tc) {
        return CONTEXTS.computeIfAbsent(tc.getXmlTest(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(tc, k -> new ClassContext(k, SEMAPHORE, this));
    }

    private static Class<?> realClass(ITestResult tr) {
        return tr.getTestClass().getRealClass();
    }
}
