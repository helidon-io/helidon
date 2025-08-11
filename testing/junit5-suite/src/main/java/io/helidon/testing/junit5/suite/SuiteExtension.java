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

import java.util.HashSet;
import java.util.Set;

import io.helidon.logging.common.LogConfig;
import io.helidon.testing.junit5.suite.spi.SuiteProvider;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

/**
 * Suite junit 5 extension.
 *
 * @deprecated this is a feature in progress of development, there may be backward incompatible changes done to it, so please
 *         use with care
 */
@Deprecated
public class SuiteExtension
        implements BeforeAllCallback, ExtensionContext.Store.CloseableResource, ParameterResolver {

    private static final System.Logger LOGGER = System.getLogger(SuiteExtension.class.getName());

    // Store all stored SuiteProvider instances keys to close them.
    private static final Set<String> PROVIDER_KEYS = new HashSet<>();
    private ExtensionContext.Store globalStore;
    private SuiteDescriptor descriptor;

    /**
     * Creates an instance of suite junit 5 extension.
     */
    public SuiteExtension() {
        LogConfig.configureRuntime();
        globalStore = null;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (context.getTestClass().isPresent()) {
            globalStore = context.getRoot().getStore(GLOBAL);
            TestSuite.Suite suite = suiteFromTestClass(context.getTestClass().get());
            Class<? extends SuiteProvider> providerClass = suite.value();
            String storeKey = providerClass.getName();
            descriptor = providerFromStore(globalStore, storeKey);
            // Run the initialization just once for every suite provider
            if (descriptor == null) {
                descriptor = SuiteDescriptor.create(suite, context);
                LOGGER.log(System.Logger.Level.TRACE,
                           () -> String.format("Initializing the Suite provider %s", providerClass.getSimpleName()));
                descriptor.init();
                storeProvider(globalStore, storeKey, descriptor);
                ensureThisInstanceIsStored(globalStore);
            }
        } else {
            throw new IllegalStateException("Test class was not found in jUnit 5 extension context");
        }
    }

    @Override
    public void close() {
        for (String key : PROVIDER_KEYS) {
            SuiteDescriptor descriptor = globalStore.get(key, SuiteDescriptor.class);
            LOGGER.log(System.Logger.Level.TRACE,
                       () -> String.format("Closing Suite provider %s", descriptor.provider().getClass().getSimpleName()));
            descriptor.close();
        }

    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return descriptor.supportsParameter(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return descriptor.resolveParameter(parameterContext.getParameter().getType());
    }

    private static SuiteDescriptor providerFromStore(ExtensionContext.Store globalStore, String storeKey) {
        return globalStore.get(storeKey, SuiteDescriptor.class);
    }

    private static void storeProvider(ExtensionContext.Store globalStore, String storeKey, SuiteDescriptor descriptor) {
        globalStore.put(storeKey, descriptor);
        PROVIDER_KEYS.add(storeKey);
    }

    private static TestSuite.Suite suiteFromTestClass(Class<?> testClass) {
        TestSuite.Suite suite = testClass.getAnnotation(TestSuite.Suite.class);
        if (suite == null) {
            throw new IllegalStateException(
                    String.format("Suite annotation was not found on %s class", testClass.getSimpleName()));
        }
        return suite;
    }

    private void ensureThisInstanceIsStored(ExtensionContext.Store globalStore) {
        if (globalStore.get(SuiteExtension.class.getName()) == null) {
            globalStore.put(SuiteExtension.class.getName(), this);
        }
    }

}
