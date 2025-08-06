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
package io.helidon.testing.junit5.suite;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Function;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.testing.junit5.suite.spi.SuiteProvider;

import org.junit.jupiter.api.extension.ExtensionContext;

// Suite descriptor
@SuppressWarnings("deprecation")
class SuiteDescriptor {

    private final SuiteProvider provider;
    // Shared storage mapped to jUnit 5 global storage
    private final Storage storage;

    // Helidon service loader for SuiteProvider instances
    private static final HelidonServiceLoader<SuiteProvider> LOADER = HelidonServiceLoader
            .builder(ServiceLoader.load(SuiteProvider.class))
            .build();

    private SuiteDescriptor(TestSuite.Suite suite,
                            SuiteProvider provider,
                            ExtensionContext context) {
        this.provider = provider;
        // Isolate ExtensionContext.Store namespaces for individual providers (descriptors)
        this.storage = new StorageImpl(
                context.getRoot().getStore(
                        ExtensionContext.Namespace.create(suite.value().getName())));
    }

    static SuiteDescriptor create(TestSuite.Suite suite, ExtensionContext context) {
        Class<? extends SuiteProvider> providerClass = suite.value();
        SuiteProvider provider;
        List<SuiteProvider> loadedProviders = LOADER.stream()
                .filter(providerClass::isInstance)
                .toList();
        switch (loadedProviders.size()) {
            case 0:
                throw new IllegalStateException(
                        String.format("No SuiteProvider %s instance found on the classpath",
                                      providerClass.getSimpleName()));
            case 1:
                provider = loadedProviders.getFirst();
                break;
            default:
                throw new IllegalStateException(
                        String.format("Multiple SuiteProvider %s instances found on the classpath",
                                      providerClass.getSimpleName()));
        }
        return new SuiteDescriptor(suite, provider, context);
    }

    boolean supportsParameter(Type type) {
        return (provider instanceof SuiteResolver resolver && resolver.supportsParameter(type))
                || (provider instanceof SuiteStorage && Storage.class.isAssignableFrom((Class<?>) type));
    }

    Object resolveParameter(Type type) {
        // SuiteProvider may also implement SuiteResolver
        if (provider instanceof SuiteResolver suiteResolver && suiteResolver.supportsParameter(type)) {
            return suiteResolver.resolveParameter(type);
        // Always resolve Storage when SuiteStorage is implemented
        } else if (provider instanceof SuiteStorage && Storage.class.isAssignableFrom((Class<?>) type)) {
            return storage;
        }
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

    // Run suite initialization
    void init() {
        // Run @BeforeSuite annotated methods
        for (Method method : provider.getClass().getMethods()) {
            if (method.isAnnotationPresent(TestSuite.BeforeSuite.class)) {
                callMethod(method);
            }
        }
    }

    // Run suite cleanup
    void close() {
        // Run @AfterSuite annotated methods
        for (Method method : provider.getClass().getMethods()) {
            if (method.isAnnotationPresent(TestSuite.AfterSuite.class)) {
                callMethod(method);
            }
        }
    }

    SuiteProvider provider() {
        return provider;
    }

    /**
     * Call method with resolved parameters.
     *
     * @param method method handler
     */
    private void callMethod(Method method) {
        Type[] types = method.getGenericParameterTypes();
        int count = method.getParameterCount();
        Object[] parameters = new Object[count];
        for (int i = 0; i < count; i++) {
            parameters[i] = resolve(types[i]);
        }
        invoke(method, parameters);
    }

    // Resolve single method parameter
    private Object resolve(Type type) {
        Function<Type, Object> resolver = null;
        if (supportsParameter(type)) {
            resolver = this::resolveParameter;
            return resolver.apply(type);
        } else {
            throw new IllegalArgumentException(
                    String.format("Cannot resolve parameter Type %s", type.getTypeName()));
        }
    }

    // Invoke provider's method
    private void invoke(Method method, Object[] parameters) {
        try {
            method.invoke(provider, parameters);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(
                    String.format("Could not invoke %s method %s",
                                  provider.getClass().getSimpleName(),
                                  method.getName()),
                    e);
        }
    }

}
