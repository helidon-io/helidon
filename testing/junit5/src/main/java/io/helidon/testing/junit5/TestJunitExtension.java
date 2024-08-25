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

package io.helidon.testing.junit5;

import java.lang.reflect.Method;

import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.ConfigRegistrySupport;
import io.helidon.testing.TestException;
import io.helidon.testing.TestRegistry;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Helidon JUnit extension, added through {@link io.helidon.testing.junit5.Testing.Test}.
 * <p>
 * This extension has the following features:
 * <ul>
 *     <li>Resets {@code io.helidon.common.GlobalInstances} when the test is over</li>
 *     <li>Support configuration annotations to set up configuration before running the tests</li>
 *     <li>Support for injection service registry (if on classpath) to discover configuration</li>
 * </ul>
 */
public class TestJunitExtension implements Extension,
                                           BeforeAllCallback,
                                           AfterAllCallback,
                                           ParameterResolver {

    static {
        LogConfig.initClass();
    }

    private volatile ServiceRegistryManager manager;
    private volatile ServiceRegistry registry;

    /**
     * Default constructor with no side effects.
     */
    protected TestJunitExtension() {
    }

    @SuppressWarnings("removal")
    @Override
    public void beforeAll(ExtensionContext context) {
        LogConfig.configureRuntime();

        io.helidon.common.GlobalInstances.clear();
        Class<?> testClass = context.getRequiredTestClass();

        createRegistry(testClass);
    }

    @SuppressWarnings("removal")
    @Override
    public void afterAll(ExtensionContext context) {
        if (manager != null) {
            manager.shutdown();
            manager = null;
            registry = null;
            afterShutdownMethods(context.getRequiredTestClass());
        }

        io.helidon.common.GlobalInstances.clear();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        if (!GenericType.create(parameterContext.getParameter().getParameterizedType())
                .isClass()) {
            return false;
        }

        return registrySupportedType(paramType);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();

        if (registrySupportedType(paramType)) {
            return registry.get(paramType);
        }

        throw new ParameterResolutionException("Failed to resolve parameter of type "
                                                       + paramType.getName());
    }

    private void afterShutdownMethods(Class<?> requiredTestClass) {
        for (Method declaredMethod : requiredTestClass.getDeclaredMethods()) {
            TestRegistry.AfterShutdown annotation = declaredMethod.getAnnotation(TestRegistry.AfterShutdown.class);
            if (annotation != null) {
                try {
                    declaredMethod.setAccessible(true);
                    declaredMethod.invoke(null);
                } catch (Exception e) {
                    throw new TestException("Failed to invoke @TestRegistry.AfterShutdown annotated method "
                                                    + declaredMethod.getName(), e);

                }
            }
        }
    }

    private void createRegistry(Class<?> testClass) {
        var registryConfig = ServiceRegistryConfig.builder();
        ConfigRegistrySupport.setUp(registryConfig, testClass);

        manager = ServiceRegistryManager.create(registryConfig.build());
        registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        GlobalConfig.config(() -> registry.get(Config.class), true);
    }

    private boolean registrySupportedType(Class<?> paramType) {
        if (ServiceRegistry.class.isAssignableFrom(paramType)) {
            return true;
        }
        // we do not want to get the instance here (yet)
        return !registry.allServices(paramType)
                .isEmpty();
    }
}
