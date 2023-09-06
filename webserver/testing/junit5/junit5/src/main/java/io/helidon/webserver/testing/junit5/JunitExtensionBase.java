/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

abstract class JunitExtensionBase implements AfterAllCallback {
    private Class<?> testClass;

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        callAfterStop();
    }

    void testClass(Class<?> testClass) {
        this.testClass = testClass;
    }

    Class<?> testClass() {
        return testClass;
    }

    private void callAfterStop() {
        if (testClass == null) {
            return;
        }

        List<Method> toInvoke = new ArrayList<>();

        Method[] methods = testClass.getMethods();
        for (Method method : methods) {
            AfterStop annotation = method.getAnnotation(AfterStop.class);
            if (annotation != null) {
                if (method.getParameterCount() != 0) {
                    throw new IllegalStateException("Method " + method + " is annotated with @AfterStop, but it has parameters");
                }
                if (Modifier.isStatic(method.getModifiers())) {
                    method.setAccessible(true);
                    toInvoke.add(method);
                } else {
                    throw new IllegalStateException("Method " + method + " is annotated with @AfterStop, but it is not static");
                }
            }
        }

        for (Method method : toInvoke) {
            try {
                method.invoke(testClass);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to invoke method: " + method, e);
            }
        }
    }
}
