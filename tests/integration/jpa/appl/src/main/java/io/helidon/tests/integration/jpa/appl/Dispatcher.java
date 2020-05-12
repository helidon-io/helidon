/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.appl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * Test name and class dispatcher.
 */
@ApplicationScoped
public class Dispatcher {

    /**
     * Test invocation handler.
     */
    private static final class Handle {

        private final Object instance;
        private final Method method;
        private final TestResult result;

        /**
         * Creates an instance of test invocation handler.
         *
         * @param instance test class instance
         * @param method method handler to invoke
         * @param result test execution result
         */
        private Handle(final Object instance, final Method method, final TestResult result) {
            this.instance = instance;
            this.method = method;
            this.result = result;
        }

        /**
         * Invoke test.
         *
         * @return test execution result
         */
        private TestResult invoke() {
            try {
                if (method.getAnnotation(MPTest.class) != null) {
                    try {
                        return (TestResult) method.invoke(instance, result);
                    } catch (InvocationTargetException ie) {
                        Throwable cause = ie.getCause();
                        return result.throwed(cause != null ? cause : ie);
                    }
                } else {
                    return result.fail("Method is missing MPTest annotation");
                }
            } catch (IllegalAccessException | IllegalArgumentException ex) {
                result.throwed(ex);
            }
            return result;
        }

        /**
         * Get result of test execution.
         *
         * @return result of test execution
         */
        private TestResult result() {
            return result;
        }

    }

    @Inject
    private InsertIT insertIt;

    @Inject
    private UpdateIT updateIt;

    @Inject
    private DeleteIT deleteIt;

    @Inject
    private QueryIT queryIt;

    private Handle getHandle(final String name) {
        final int nameLen = name.length();
        final int serpPos = name.indexOf('.');
        final TestResult result = new TestResult();
        result.name(name);
        if (serpPos < 0 || (serpPos + 1) >= nameLen) {
            result.fail("Invalid test identifier: " + name);
            return null;
        }
        final String className = name.substring(0, serpPos);
        final String methodName = name.substring(serpPos + 1, nameLen);
        if ("InsertIT".equals(className)) {
            try {
                return new Handle(
                        insertIt,
                        InsertIT.class.getDeclaredMethod(methodName, TestResult.class),
                        result);
            } catch (NoSuchMethodException ex) {
                result.throwed(ex);
            }
        } else if ("UpdateIT".equals(className)) {
            try {
                return new Handle(
                        updateIt,
                        UpdateIT.class.getDeclaredMethod(methodName, TestResult.class),
                        result);
            } catch (NoSuchMethodException ex) {
                result.throwed(ex);
            }
        } else if ("DeleteIT".equals(className)) {
            try {
                return new Handle(
                        deleteIt,
                        DeleteIT.class.getDeclaredMethod(methodName, TestResult.class),
                        result);
            } catch (NoSuchMethodException ex) {
                result.throwed(ex);
            }
        } else if ("QueryIT".equals(className)) {
            try {
                return new Handle(
                        queryIt,
                        QueryIT.class.getDeclaredMethod(methodName, TestResult.class),
                        result);
            } catch (NoSuchMethodException ex) {
                result.throwed(ex);
            }
        } else {
            result.fail("Unknown test class: " + className);
        }
        return null;
    }

    /**
     * Run test identified by it's name ({@code<class>.<method>}).
     *
     * @param name name of the test
     * @return test execution result
     */
    public TestResult runTest(final String name) {
        Handle handle = getHandle(name);
        if (handle == null) {
            return handle.result().fail("Missing method handle.");
        }
        return handle.invoke();
    }

}
