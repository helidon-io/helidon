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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.testng.ITestClass;
import org.testng.ITestNGMethod;
import org.testng.internal.ITestClassConfigInfo;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlTest;

import static io.helidon.microprofile.testing.Instrumented.isInstrumented;

/**
 * A decorator for {@link ITestClass} that is used to customize the value of {@link ITestClass#getName()}.
 * <ul>
 *     <li>It is used to hide the instrumented name in the test results</li>
 *     <li>It is installed on every method using {@link ITestNGMethod#setTestClass(ITestClass)}</li>
 * </ul>
 *
 * @param delegate delegate
 */
record ClassDecorator(ITestClass delegate) implements ITestClass, ITestClassConfigInfo {

    private static final Map<ITestClass, ClassDecorator> CACHE = new ConcurrentHashMap<>();

    /**
     * Decorate the given test class.
     *
     * @param tc test class
     * @return decorated test class
     */
    static ITestClass decorate(ITestClass tc) {
        if (!(tc instanceof ClassDecorator)) {
            return CACHE.computeIfAbsent(tc, ClassDecorator::new);
        }
        return tc;
    }

    @Override
    public List<ITestNGMethod> getAllBeforeClassMethods() {
        if (delegate instanceof ITestClassConfigInfo info) {
            return info.getAllBeforeClassMethods().stream()
                    .peek(m -> m.setTestClass(decorate(m.getTestClass())))
                    .toList();
        }
        return List.of();
    }

    @Override
    public List<ITestNGMethod> getInstanceBeforeClassMethods(Object instance) {
        if (delegate instanceof ITestClassConfigInfo info) {
            return info.getInstanceBeforeClassMethods(instance).stream()
                    .peek(m -> m.setTestClass(decorate(m.getTestClass())))
                    .toList();
        }
        return List.of();
    }

    @Override
    public String getName() {
        Class<?> realClass = getRealClass();
        return isInstrumented(realClass) ? realClass.getSuperclass().getName() : realClass.getName();
    }

    @Override
    public Class<?> getRealClass() {
        return delegate.getRealClass();
    }

    private ITestNGMethod[] processMethods(ITestNGMethod[] methods) {
        for (ITestNGMethod method : methods) {
            method.setTestClass(decorate(method.getTestClass()));
        }
        return methods;
    }

    @Override
    public ITestNGMethod[] getAfterClassMethods() {
        return processMethods(delegate.getAfterClassMethods());
    }

    @Override
    public ITestNGMethod[] getAfterGroupsMethods() {
        return processMethods(delegate.getAfterGroupsMethods());
    }

    @Override
    public ITestNGMethod[] getAfterSuiteMethods() {
        return processMethods(delegate.getAfterSuiteMethods());
    }

    @Override
    public ITestNGMethod[] getAfterTestConfigurationMethods() {
        return processMethods(delegate.getAfterTestConfigurationMethods());
    }

    @Override
    public ITestNGMethod[] getAfterTestMethods() {
        return processMethods(delegate.getAfterTestMethods());
    }

    @Override
    public ITestNGMethod[] getBeforeClassMethods() {
        return processMethods(delegate.getBeforeClassMethods());
    }

    @Override
    public ITestNGMethod[] getBeforeGroupsMethods() {
        return processMethods(delegate.getBeforeGroupsMethods());
    }

    @Override
    public ITestNGMethod[] getBeforeSuiteMethods() {
        return processMethods(delegate.getBeforeSuiteMethods());
    }

    @Override
    public ITestNGMethod[] getBeforeTestConfigurationMethods() {
        return processMethods(delegate.getBeforeTestConfigurationMethods());
    }

    @Override
    public ITestNGMethod[] getBeforeTestMethods() {
        return processMethods(delegate.getBeforeTestMethods());
    }

    @Override
    public ITestNGMethod[] getTestMethods() {
        return processMethods(delegate.getTestMethods());
    }

    @Override
    public void addInstance(Object instance) {
        delegate.addInstance(instance);
    }

    @Override
    public long[] getInstanceHashCodes() {
        return delegate.getInstanceHashCodes();
    }

    @Override
    public Object[] getInstances(boolean create) {
        return delegate.getInstances(create);
    }

    @Override
    public Object[] getInstances(boolean create, String errorMsgPrefix) {
        return delegate.getInstances(create, errorMsgPrefix);
    }

    @Override
    public String getTestName() {
        return delegate.getTestName();
    }

    @Override
    public XmlClass getXmlClass() {
        return delegate.getXmlClass();
    }

    @Override
    public XmlTest getXmlTest() {
        return delegate.getXmlTest();
    }
}
