/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.microprofile.security;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class HandlingMethodResolverTest {

    @Test
    void ordinaryMethodIsReturnedUnchanged() throws NoSuchMethodException {
        Method method = SecuredResource.class.getDeclaredMethod("get");

        assertThat(HandlingMethodResolver.resolve(SecuredResource.class, method, method), sameInstance(method));
    }

    @Test
    void proxyOverrideResolvesToUserDeclaration() throws NoSuchMethodException {
        Method definitionMethod = SecuredResource.class.getDeclaredMethod("get");
        Method proxyMethod = ProxyResource.class.getDeclaredMethod("get");

        Method resolved = HandlingMethodResolver.resolve(SecuredResource.class, definitionMethod, proxyMethod);

        assertThat(resolved, is(definitionMethod));
        assertThat(resolved.getAnnotation(RolesAllowed.class), notNullValue());
    }

    @Test
    void genericParameterBridgeResolvesSpecialization() throws NoSuchMethodException {
        Method definitionMethod = GenericContract.class.getDeclaredMethod("apply", Object.class);
        Method bridgeMethod = bridgeMethod(GenericResource.class, "apply");

        Method resolved = HandlingMethodResolver.resolve(GenericResource.class, definitionMethod, bridgeMethod);

        assertThat(resolved.getParameterTypes(), is(new Class<?>[] {String.class}));
        assertThat(resolved.getAnnotation(RolesAllowed.class), notNullValue());
    }

    @Test
    void genericBridgeDoesNotSelectSiblingOverload() throws NoSuchMethodException {
        Method definitionMethod = GenericContract.class.getDeclaredMethod("apply", Object.class);
        Method bridgeMethod = bridgeMethod(OverloadedGenericResource.class, "apply");

        Method resolved = HandlingMethodResolver.resolve(OverloadedGenericResource.class,
                                                         definitionMethod,
                                                         bridgeMethod);

        assertThat(resolved.getParameterTypes(), is(new Class<?>[] {String.class}));
        assertThat(resolved.getAnnotation(RolesAllowed.class), notNullValue());
    }

    @Test
    void proxyBridgeResolvesThroughUserBridge() throws NoSuchMethodException {
        Method definitionMethod = GenericContract.class.getDeclaredMethod("apply", Object.class);
        Method proxyBridge = bridgeMethod(GenericProxyResource.class, "apply");

        Method resolved = HandlingMethodResolver.resolve(GenericResource.class, definitionMethod, proxyBridge);

        assertThat(resolved.getParameterTypes(), is(new Class<?>[] {String.class}));
        assertThat(resolved.getDeclaringClass(), sameInstance(GenericResource.class));
        assertThat(resolved.getAnnotation(RolesAllowed.class), notNullValue());
    }

    @Test
    void covariantReturnBridgeResolvesConcreteMethod() throws NoSuchMethodException {
        Method definitionMethod = CovariantContract.class.getDeclaredMethod("value");
        Method bridgeMethod = bridgeMethod(CovariantResource.class, "value");

        Method resolved = HandlingMethodResolver.resolve(CovariantResource.class, definitionMethod, bridgeMethod);

        assertThat(resolved.getReturnType(), sameInstance(String.class));
        assertThat(resolved.getAnnotation(RolesAllowed.class), notNullValue());
    }

    @Test
    void realCovariantOverrideIsNotReplaced() throws NoSuchMethodException {
        Method definitionMethod = CovariantBase.class.getDeclaredMethod("get");
        Method handlingMethod = CovariantOverride.class.getDeclaredMethod("get");

        Method resolved = HandlingMethodResolver.resolve(CovariantOverride.class, definitionMethod, handlingMethod);

        assertThat(resolved, sameInstance(handlingMethod));
        assertThat(resolved.getAnnotation(RolesAllowed.class), nullValue());
    }

    @Test
    void covariantBridgeResolvesConcreteOverride() throws NoSuchMethodException {
        Method definitionMethod = CovariantBase.class.getDeclaredMethod("get");
        Method bridgeMethod = bridgeMethod(CovariantOverride.class, "get");

        Method resolved = HandlingMethodResolver.resolve(CovariantOverride.class, definitionMethod, bridgeMethod);

        assertThat(resolved.isBridge(), is(false));
        assertThat(resolved.getReturnType(), sameInstance(String.class));
        assertThat(resolved.getDeclaringClass(), sameInstance(CovariantOverride.class));
        assertThat(resolved.getAnnotation(RolesAllowed.class), nullValue());
    }

    @Test
    void missingExactMethodFallsBackToObservedMethod() throws NoSuchMethodException {
        Method definitionMethod = BroadMethod.class.getDeclaredMethod("apply", Object.class);

        Method resolved = HandlingMethodResolver.resolve(AmbiguousResource.class,
                                                         definitionMethod,
                                                         definitionMethod);

        assertThat(resolved, sameInstance(definitionMethod));
    }

    @Test
    void duplicateInheritedSignaturesFallBackToObservedMethod() throws NoSuchMethodException {
        Method observedMethod = FirstContract.class.getDeclaredMethod("value");
        List<Method> candidates = Arrays.stream(AmbiguousContract.class.getMethods())
                .filter(method -> method.getName().equals("value"))
                .toList();

        Method resolved = HandlingMethodResolver.resolve(AmbiguousContract.class,
                                                         observedMethod,
                                                         observedMethod);

        assertThat(candidates.size(), is(2));
        assertThat(resolved, sameInstance(observedMethod));
    }

    private static Method bridgeMethod(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .filter(Method::isBridge)
                .findFirst()
                .orElseThrow();
    }

    private interface GenericContract<T> {
        String apply(T value);
    }

    private interface CovariantContract<T> {
        T value();
    }

    private interface FirstContract {
        String value();
    }

    private interface SecondContract {
        String value();
    }

    private interface AmbiguousContract extends FirstContract, SecondContract {
    }

    private static class SecuredResource {
        @RolesAllowed("admin")
        public String get() {
            return "secured";
        }
    }

    private static final class ProxyResource extends SecuredResource {
        @Override
        public String get() {
            return super.get();
        }
    }

    private static class GenericResource implements GenericContract<String> {
        @Override
        @RolesAllowed("admin")
        public String apply(String value) {
            return value;
        }
    }

    private static final class GenericProxyResource extends GenericResource {
        @Override
        public String apply(String value) {
            return super.apply(value);
        }
    }

    private static final class OverloadedGenericResource implements GenericContract<String> {
        @Override
        @RolesAllowed("admin")
        public String apply(String value) {
            return value;
        }

        public String apply(Number value) {
            return value.toString();
        }
    }

    private static final class CovariantResource implements CovariantContract<String> {
        @Override
        @RolesAllowed("admin")
        public String value() {
            return "value";
        }
    }

    private static class CovariantBase {
        @RolesAllowed("admin")
        public CharSequence get() {
            return "base";
        }
    }

    private static final class CovariantOverride extends CovariantBase {
        @Override
        public String get() {
            return "override";
        }
    }

    private static class BroadMethod {
        public String apply(Object value) {
            return value.toString();
        }
    }

    private static final class AmbiguousResource {
        public String apply(String value) {
            return value;
        }

        public String apply(Number value) {
            return value.toString();
        }
    }

}
