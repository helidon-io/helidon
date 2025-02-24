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
package io.helidon.microprofile.tests.testing.testng;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.microprofile.testing.testng.HelidonTest;

import org.testng.IAlterSuiteListener;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ITestClass;
import org.testng.ITestContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlPackage;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

/**
 * An {@link IAlterSuiteListener} implementation that customizes the parallel mode.
 */
public class ParallelizerListenerImpl implements IAlterSuiteListener, IMethodInterceptor {

    @Override
    public void alter(List<XmlSuite> suites) {
        // set the parallel mode to methods
        // and set the thread count to the max method count
        for (XmlSuite suite : suites) {
            suite.setParallel(XmlSuite.ParallelMode.METHODS);
            for (XmlTest test : suite.getTests()) {
                int maxCount = suite.getThreadCount();
                for (XmlClass xmlClass : test.getXmlClasses()) {
                    int count = methodsCount(xmlClass);
                    if (count > maxCount) {
                        maxCount = count;
                    }
                }
                for (XmlPackage xmlPackage : test.getXmlPackages()) {
                    for (XmlClass xmlClass : xmlPackage.getXmlClasses()) {
                        int count = methodsCount(xmlClass);
                        if (count > maxCount) {
                            maxCount = count;
                        }
                    }
                }
                test.setThreadCount(maxCount);
            }
        }
    }

    @Override
    public List<IMethodInstance> intercept(List<IMethodInstance> methods, ITestContext context) {
        // sort methods by class to prevent deadlocks
        Map<ITestClass, List<IMethodInstance>> methodsByClass = new LinkedHashMap<>();
        for (IMethodInstance e : methods) {
            methodsByClass.computeIfAbsent(e.getMethod().getTestClass(), k -> new ArrayList<>())
                    .add(e);
        }
        return methodsByClass.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    private static int methodsCount(XmlClass xmlClass) {
        int count = 0;
        Class<?> testClass = xmlClass.getSupportClass();
        if (isHelidonTest(testClass)) {
            for (Method method : testClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Test.class)
                    || method.isAnnotationPresent(BeforeClass.class)
                    || method.isAnnotationPresent(AfterClass.class)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isHelidonTest(Class<?> testClass) {
        Deque<Class<?>> types = new ArrayDeque<>();
        types.push(testClass);
        while (!types.isEmpty()) {
            Class<?> type = types.pop();
            if (type.getPackage().getName().startsWith("java.")) {
                continue;
            }
            Deque<Class<? extends Annotation>> aTypes = new ArrayDeque<>();
            for (Annotation annotation : type.getAnnotations()) {
                aTypes.push(annotation.annotationType());
            }
            while (!aTypes.isEmpty()) {
                Class<? extends Annotation> aType = aTypes.pop();
                if (aType.equals(HelidonTest.class)) {
                    return true;
                }
                for (Annotation e : aType.getAnnotations()) {
                    Class<? extends Annotation> eType = e.annotationType();
                    if (!eType.getPackage().getName().startsWith("java.")
                        && !aTypes.contains(eType)) {
                        aTypes.push(eType);
                    }
                }
            }
            if (type.getSuperclass() != null) {
                types.push(type.getSuperclass());
            }
            for (Class<?> aClass : type.getInterfaces()) {
                if (!types.contains(aClass)) {
                    types.push(aClass);
                }
            }
        }
        return false;
    }
}
