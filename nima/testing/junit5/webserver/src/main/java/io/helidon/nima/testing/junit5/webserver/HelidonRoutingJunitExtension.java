/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.testing.junit5.webserver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.function.Consumer;

import io.helidon.logging.common.LogConfig;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit5 extension to support Helidon Níma WebServer in tests.
 */
class HelidonRoutingJunitExtension implements BeforeAllCallback,
                                              InvocationInterceptor,
                                              BeforeEachCallback,
                                              ParameterResolver {

    private static DirectClient client;
    private Class<?> testClass;

    @Override
    public void beforeAll(ExtensionContext context) {
        LogConfig.configureRuntime();

        testClass = context.getRequiredTestClass();
        RoutingTest testAnnot = testClass.getAnnotation(RoutingTest.class);
        if (testAnnot == null) {
            throw new IllegalStateException("Invalid test class for this extension: " + testClass + ", missing "
                                                    + RoutingTest.class.getName() + " annotation");
        }

        withRoutingMethod(routing -> client = new DirectClient(routing));
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        client.clientTlsPrincipal(null)
                .clientTlsCertificates(null)
                .clientHost("helidon-unit")
                .clientPort(65000)
                .serverHost("helidon-unit-server")
                .serverPort(8080);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Class<?> paramType = parameterContext.getParameter().getType();
        if (paramType.equals(DirectClient.class)) {
            return true;
        }

        // todo shall we use context?
        // return Context.singletonContext().value(GenericType.create(paramType)).isPresent();
        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Class<?> paramType = parameterContext.getParameter().getType();
        if (paramType.equals(DirectClient.class)) {
            return client;
        }

        // todo shall we use context?
        // return Context.singletonContext().value(GenericType.create(paramType)).orElse(null);
        return false;
    }

    private void withRoutingMethod(Consumer<HttpRouting> handler) {
        LinkedList<Class<?>> hierarchy = new LinkedList<>();
        Class<?> analyzedClass = testClass;
        while (analyzedClass != null && !analyzedClass.equals(Object.class)) {
            hierarchy.addFirst(analyzedClass);
            analyzedClass = analyzedClass.getSuperclass();
        }

        Method found = null;
        for (Class<?> aClass : hierarchy) {
            for (Method method : aClass.getDeclaredMethods()) {
                SetUpRoute annotation = method.getDeclaredAnnotation(SetUpRoute.class);
                if (annotation != null) {
                    // maybe our method
                    if (Modifier.isStatic(method.getModifiers())) {
                        if (found == null) {
                            found = method;
                        } else {
                            throw new IllegalStateException("There is more than one method annotated with "
                                                                    + SetUpRoute.class.getSimpleName()
                                                                    + " in class " + aClass.getName());
                        }
                    } else {
                        throw new IllegalStateException("Method " + method + " is annotated with "
                                                                + SetUpRoute.class.getSimpleName()
                                                                + " yet it is not static in class "
                                                                + aClass.getName());
                    }
                }
            }
        }
        HttpRouting.Builder router = HttpRouting.builder();
        if (found != null) {
            routingMethod(found, router);
        }
        handler.accept(router.build());
    }

    private void routingMethod(Method method, HttpRules router) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        int parameterCount = parameterTypes.length;
        if (parameterCount != 1) {
            throw new IllegalArgumentException("Method " + method + " must have one parameter of "
                                                       + HttpRules.class.getName());
        }
        Class<?> parameterType = parameterTypes[0];
        if (HttpRules.class.isAssignableFrom(parameterType)) {
            try {
                method.setAccessible(true);
                method.invoke(null, router);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Cannot invoke router/socket method", e);
            }
        } else {
            throw new IllegalArgumentException("Method " + method + " must have parameter of "
                                                       + HttpRules.class.getName());
        }
    }

}
