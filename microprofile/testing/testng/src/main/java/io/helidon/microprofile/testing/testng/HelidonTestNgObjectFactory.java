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
package io.helidon.microprofile.testing.testng;

import io.helidon.microprofile.testing.ProxyHelper;

import org.testng.ITestObjectFactory;

/**
 * TestNG Object factory.
 */
public class HelidonTestNgObjectFactory implements ITestObjectFactory {

    @Override
    public <T> T newInstance(Class<T> cls, Object... parameters) {
        // Use a proxy to start the container after the test instance creation
        // The container is started lazily when invoking a method
        // or when resolving parameters
        return ProxyHelper.proxyDelegate(cls, (testClass, testMethod) -> {
            // class context store specific to the intercepted method
            return HelidonTestContainerHolder.getOrThrow().resolveInstance(testClass);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T newInstance(String clsName, Object... parameters) {
        try {
            return (T) newInstance(Class.forName(clsName));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
