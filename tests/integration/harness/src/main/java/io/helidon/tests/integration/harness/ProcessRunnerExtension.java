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
package io.helidon.tests.integration.harness;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

/**
 * A JUnit extension that manages the lifecycle of {@link ProcessRunner} instances annotated with {@link TestProcess}.
 */
public class ProcessRunnerExtension implements BeforeAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ProcessRunnerExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        List<ProcessRunner> runners = findProcessRunners(testClass);
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        String key = testClass.getName();
        TestProcesses testProcesses = findTestProcesses(testClass);
        List<ProcessMonitor> monitors = new ArrayList<>();
        if (testProcesses != null && testProcesses.parallel()) {
            runners.forEach(p -> monitors.add(p.start()));
            monitors.forEach(r -> store.put(key, (CloseableResource) r::close));
            monitors.forEach(ProcessMonitor::await);
        } else {
            //noinspection resource
            runners.forEach(r -> store.put(key, (CloseableResource) r.start().await()::close));
        }
    }

    private static TestProcesses findTestProcesses(Class<?> testClass) {
        for (Class<?> clazz : allTypes(testClass)) {
            if (clazz.isAnnotationPresent(TestProcesses.class)) {
                return clazz.getDeclaredAnnotation(TestProcesses.class);
            }
        }
        return null;
    }

    private static List<ProcessRunner> findProcessRunners(Class<?> testClass) throws IllegalAccessException {
        List<ProcessRunner> runners = new ArrayList<>();
        for (Class<?> clazz : allTypes(testClass)) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) && method.isAnnotationPresent(TestProcess.class)) {
                    try {
                        method.setAccessible(true);
                        Object value = method.invoke(null);
                        if (value instanceof ProcessRunner pr) {
                            runners.add(pr);
                        }
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.isAnnotationPresent(TestProcess.class)) {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof ProcessRunner pr) {
                        runners.add(pr);
                    }
                }
            }
        }
        return runners;
    }

    private static List<Class<?>> allTypes(Class<?> clazz) {
        List<Class<?>> types = new ArrayList<>();
        Deque<Class<?>> stack = new ArrayDeque<>();
        stack.push(clazz);
        while (!stack.isEmpty()) {
            Class<?> aClass = stack.pop();
            types.add(aClass);
            Class<?> superclass = aClass.getSuperclass();
            if (superclass != null) {
                stack.push(superclass);
            }
            for (Class<?> interfaceClass : aClass.getInterfaces()) {
                stack.push(interfaceClass);
            }
        }
        return types;
    }
}
