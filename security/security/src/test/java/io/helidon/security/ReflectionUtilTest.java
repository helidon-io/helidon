/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import io.helidon.security.reflection.Tester;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link ReflectionUtil}.
 */
class ReflectionUtilTest {
    public static String publicFieldStatic;
    protected static String protectedFieldStatic;
    static String packageFieldStatic;
    private static String privateFieldStatic;
    private final Target localTester = new Target(ReflectionUtilTester.class, new ReflectionUtilTester());
    private final Target tester = new Target(Tester.class, new Tester());
    private final Target packageTester = new Target(Tester.packageClass(), Tester.packageInstance());
    private final Target privateTester = new Target(Tester.privateClass(), Tester.privateInstance());
    private final Target protectedTester = new Target(Tester.protectedClass(), Tester.protectedInstance());
    private final Target me = new Target(ReflectionUtilTest.class, this);
    public String publicField;
    protected String protectedField;
    String packageField;
    private String privateField;

    public static void testPublicStatic() {
    }

    protected static void testProtectedStatic() {
    }

    private static void testPrivateStatic() {
    }

    static void testPackageStatic() {
    }

    @Test
    void testPublicConstructorAccess() {
        Class<?>[] paramType = new Class[0];

        testConstructor(getClass(), me, paramType, true);
        testConstructor(getClass(), localTester, paramType, true);
        testConstructor(getClass(), tester, paramType, true);
        testConstructor(getClass(), packageTester, paramType, false);
        testConstructor(getClass(), privateTester, paramType, false);
        testConstructor(getClass(), protectedTester, paramType, false);
        testConstructor(tester.clazz, packageTester, paramType, true);
        testConstructor(tester.clazz, protectedTester, paramType, true);
        testConstructor(tester.clazz, privateTester, paramType, true);
        testConstructor(protectedTester.clazz, privateTester, paramType, true);
        testConstructor(protectedTester.clazz, tester, paramType, true);
        testConstructor(privateTester.clazz, protectedTester, paramType, true);
        testConstructor(privateTester.clazz, tester, paramType, true);
    }

    @Test
    void testPublicFieldAccess() {
        String fieldName = "publicField";

        testField(getClass(), me, fieldName, true);
        testField(getClass(), localTester, fieldName, true);
        testField(getClass(), tester, fieldName, true);
        testField(getClass(), packageTester, fieldName, false);
        testField(getClass(), privateTester, fieldName, false);
        testField(getClass(), protectedTester, fieldName, false);
        testField(tester.clazz, packageTester, fieldName, true);
        testField(tester.clazz, protectedTester, fieldName, true);
        testField(tester.clazz, privateTester, fieldName, true);
        testField(protectedTester.clazz, privateTester, fieldName, true);
        testField(protectedTester.clazz, tester, fieldName, true);
        testField(privateTester.clazz, protectedTester, fieldName, true);
        testField(privateTester.clazz, tester, fieldName, true);
    }

    @Test
    void testPublicMethodAccess() {
        String methodName = "testPublic";

        testMethod(getClass(), me, methodName, true);
        testMethod(getClass(), localTester, methodName, true);
        testMethod(getClass(), tester, methodName, true);
        testMethod(getClass(), packageTester, methodName, false);
        testMethod(getClass(), privateTester, methodName, false);
        testMethod(getClass(), protectedTester, methodName, false);
        testMethod(tester.clazz, packageTester, methodName, true);
        testMethod(tester.clazz, protectedTester, methodName, true);
        testMethod(tester.clazz, privateTester, methodName, true);
        testMethod(protectedTester.clazz, privateTester, methodName, true);
        testMethod(protectedTester.clazz, tester, methodName, true);
        testMethod(privateTester.clazz, protectedTester, methodName, true);
        testMethod(privateTester.clazz, tester, methodName, true);
    }

    @Test
    void testPackageConstructorAccess() {
        Class<?>[] paramType = new Class[] {String.class};

        testConstructor(getClass(), localTester, paramType, true);
        testConstructor(getClass(), tester, paramType, false);
        testConstructor(getClass(), packageTester, paramType, false);
        testConstructor(getClass(), privateTester, paramType, false);
        testConstructor(getClass(), protectedTester, paramType, false);
        testConstructor(tester.clazz, packageTester, paramType, true);
        testConstructor(tester.clazz, protectedTester, paramType, true);
        testConstructor(tester.clazz, privateTester, paramType, true);
        testConstructor(protectedTester.clazz, privateTester, paramType, true);
        testConstructor(protectedTester.clazz, tester, paramType, true);
        testConstructor(privateTester.clazz, protectedTester, paramType, true);
        testConstructor(privateTester.clazz, tester, paramType, true);
    }

    @Test
    void testPackageFieldAccess() {
        String fieldName = "packageField";

        testField(getClass(), me, fieldName, true);
        testField(getClass(), localTester, fieldName, true);
        testField(getClass(), tester, fieldName, false);
        testField(getClass(), packageTester, fieldName, false);
        testField(getClass(), privateTester, fieldName, false);
        testField(getClass(), protectedTester, fieldName, false);
        testField(tester.clazz, packageTester, fieldName, true);
        testField(tester.clazz, protectedTester, fieldName, true);
        testField(tester.clazz, privateTester, fieldName, true);
        testField(protectedTester.clazz, privateTester, fieldName, true);
        testField(protectedTester.clazz, tester, fieldName, true);
        testField(privateTester.clazz, protectedTester, fieldName, true);
        testField(privateTester.clazz, tester, fieldName, true);
    }

    @Test
    void testPackageMethodAccess() {
        String methodName = "testPackage";

        testMethod(getClass(), me, methodName, true);
        testMethod(getClass(), localTester, methodName, true);
        testMethod(getClass(), tester, methodName, false);
        testMethod(getClass(), packageTester, methodName, false);
        testMethod(getClass(), privateTester, methodName, false);
        testMethod(getClass(), protectedTester, methodName, false);
        testMethod(tester.clazz, packageTester, methodName, true);
        testMethod(tester.clazz, protectedTester, methodName, true);
        testMethod(tester.clazz, privateTester, methodName, true);
        testMethod(protectedTester.clazz, privateTester, methodName, true);
        testMethod(protectedTester.clazz, tester, methodName, true);
        testMethod(privateTester.clazz, protectedTester, methodName, true);
        testMethod(privateTester.clazz, tester, methodName, true);
    }

    @Test
    void testProtectedConstructorAccess() {
        Class<?>[] paramType = new Class[] {int.class};

        testConstructor(getClass(), localTester, paramType, false);
        testConstructor(getClass(), tester, paramType, false);
        testConstructor(getClass(), packageTester, paramType, false);
        testConstructor(getClass(), privateTester, paramType, false);
        testConstructor(getClass(), protectedTester, paramType, false);
        testConstructor(tester.clazz, packageTester, paramType, false);
        testConstructor(tester.clazz, protectedTester, paramType, true);
        testConstructor(tester.clazz, privateTester, paramType, true);
        testConstructor(protectedTester.clazz, privateTester, paramType, true);
        testConstructor(protectedTester.clazz, tester, paramType, true);
        testConstructor(privateTester.clazz, protectedTester, paramType, true);
        testConstructor(privateTester.clazz, tester, paramType, true);
    }

    @Test
    void testProtectedFieldAccess() {
        String fieldName = "protectedField";

        testField(getClass(), me, fieldName, true);
        testField(getClass(), localTester, fieldName, false);
        testField(getClass(), tester, fieldName, false);
        testField(getClass(), packageTester, fieldName, false);
        testField(getClass(), privateTester, fieldName, false);
        testField(getClass(), protectedTester, fieldName, false);
        testField(packageTester.clazz, tester, fieldName, true);
        testField(tester.clazz, protectedTester, fieldName, true);
        testField(tester.clazz, privateTester, fieldName, true);
        testField(protectedTester.clazz, privateTester, fieldName, true);
        testField(protectedTester.clazz, tester, fieldName, true);
        testField(privateTester.clazz, protectedTester, fieldName, true);
        testField(privateTester.clazz, tester, fieldName, true);
    }

    @Test
    void testProtectedMethodAccess() {
        String methodName = "testProtected";

        testMethod(getClass(), me, methodName, true);
        testMethod(getClass(), localTester, methodName, false);
        testMethod(getClass(), tester, methodName, false);
        testMethod(getClass(), packageTester, methodName, false);
        testMethod(getClass(), privateTester, methodName, false);
        testMethod(getClass(), protectedTester, methodName, false);
        testMethod(tester.clazz, packageTester, methodName, false);
        testMethod(tester.clazz, protectedTester, methodName, true);
        testMethod(tester.clazz, privateTester, methodName, true);
        testMethod(protectedTester.clazz, privateTester, methodName, true);
        testMethod(protectedTester.clazz, tester, methodName, true);
        testMethod(privateTester.clazz, protectedTester, methodName, true);
        testMethod(privateTester.clazz, tester, methodName, true);
    }

    @Test
    void testPrivateConstructorAccess() {
        Class<?>[] paramType = new Class[] {boolean.class};

        testConstructor(getClass(), localTester, paramType, false);
        testConstructor(getClass(), tester, paramType, false);
        testConstructor(getClass(), packageTester, paramType, false);
        testConstructor(getClass(), privateTester, paramType, false);
        testConstructor(getClass(), protectedTester, paramType, false);
        testConstructor(tester.clazz, packageTester, paramType, false);
        testConstructor(tester.clazz, protectedTester, paramType, true);
        testConstructor(tester.clazz, privateTester, paramType, true);
        testConstructor(protectedTester.clazz, privateTester, paramType, true);
        testConstructor(protectedTester.clazz, tester, paramType, true);
        testConstructor(privateTester.clazz, protectedTester, paramType, true);
        testConstructor(privateTester.clazz, tester, paramType, true);
    }

    @Test
    void testPrivateFieldAccess() {
        String fieldName = "privateField";

        testField(getClass(), me, fieldName, true);
        testField(getClass(), localTester, fieldName, false);
        testField(getClass(), tester, fieldName, false);
        testField(getClass(), packageTester, fieldName, false);
        testField(getClass(), privateTester, fieldName, false);
        testField(getClass(), protectedTester, fieldName, false);
        testField(packageTester.clazz, packageTester, fieldName, true);
        testField(tester.clazz, protectedTester, fieldName, true);
        testField(tester.clazz, privateTester, fieldName, true);
        testField(protectedTester.clazz, privateTester, fieldName, true);
        testField(protectedTester.clazz, tester, fieldName, true);
        testField(privateTester.clazz, protectedTester, fieldName, true);
        testField(privateTester.clazz, tester, fieldName, true);
    }

    @Test
    void testPrivateMethodAccess() {
        String methodName = "testPrivate";

        testMethod(getClass(), me, methodName, true);
        testMethod(getClass(), localTester, methodName, false);
        testMethod(getClass(), tester, methodName, false);
        testMethod(getClass(), packageTester, methodName, false);
        testMethod(getClass(), privateTester, methodName, false);
        testMethod(getClass(), protectedTester, methodName, false);
        testMethod(tester.clazz, packageTester, methodName, false);
        testMethod(tester.clazz, protectedTester, methodName, true);
        testMethod(tester.clazz, privateTester, methodName, true);
        testMethod(protectedTester.clazz, privateTester, methodName, true);
        testMethod(protectedTester.clazz, tester, methodName, true);
        testMethod(privateTester.clazz, protectedTester, methodName, true);
        testMethod(privateTester.clazz, tester, methodName, true);
    }

    private void testConstructor(Class<?> invoker, Target target, Class<?>[] paramType, boolean accessible) {
        Class<?> clazz = target.clazz;

        Constructor<?> constructor = getConstructor(clazz, paramType);
        assertThat("Test that " + invoker.getName() + " can access constructor " + constructor,
                   ReflectionUtil.canAccess(invoker, constructor),
                   CoreMatchers.is(accessible));

    }

    private void testMethod(Class<?> invoker, Target target, String methodName, boolean accessible) {
        Class<?> clazz = target.clazz;
        Object instance = target.instance;

        Method method = getMethod(clazz, methodName);
        assertThat("Test that " + invoker.getName() + " can access method " + clazz.getName() + "." + methodName,
                   ReflectionUtil.canAccess(invoker, method, instance),
                   CoreMatchers.is(accessible));

        method = getMethod(clazz, methodName + "Static");
        assertThat("Test that " + invoker.getName() + " can access field " + clazz.getName() + "." + methodName,
                   ReflectionUtil.canAccess(invoker, method),
                   CoreMatchers.is(accessible));
    }

    private void testField(Class<?> invoker, Target target, String fieldName, boolean accessible) {
        Class<?> clazz = target.clazz;
        Object instance = target.instance;

        Field field = getField(clazz, fieldName);

        assertThat("Test that " + invoker.getName() + " can access field " + clazz.getName() + "." + fieldName,
                   ReflectionUtil.canAccess(invoker, field, instance),
                   CoreMatchers.is(accessible));

        field = getField(clazz, fieldName + "Static");
        assertThat("Test that " + invoker.getName() + " can access field " + clazz.getName() + "." + fieldName,
                   ReflectionUtil.canAccess(invoker, field),
                   CoreMatchers.is(accessible));

    }

    public void testPublic() {
    }

    protected void testProtected() {
    }

    private void testPrivate() {
    }

    void testPackage() {
    }

    private Constructor<?> getConstructor(Class<?> clazz, Class<?>[] paramType) {
        try {
            return clazz.getDeclaredConstructor(paramType);
        } catch (NoSuchMethodException e) {
            Assertions.fail("Wrong test configuration, constructor does not exist in class: " + clazz
                    .getName() + ", with parameters: " + Arrays.toString(paramType), e);
        }
        return null;
    }

    private Method getMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            Assertions.fail("Wrong test configuration, method does not exist: " + clazz.getName() + "." + methodName, e);
        }
        return null;
    }

    private Field getField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Assertions.fail("Wrong test configuration, field does not exist: " + clazz.getName() + "." + fieldName, e);
        }
        return null;
    }

    private static final class Target {
        private Class<?> clazz;
        private Object instance;

        private Target(Class<?> clazz, Object instance) {
            this.clazz = clazz;
            this.instance = instance;
        }
    }
}
