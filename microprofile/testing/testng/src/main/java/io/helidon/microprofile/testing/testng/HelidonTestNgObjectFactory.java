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

import java.lang.reflect.Constructor;
import java.util.List;

import io.helidon.microprofile.testing.HelidonTestContainer;
import io.helidon.microprofile.testing.ProxyHelper;

import org.testng.ITestObjectFactory;
import org.testng.annotations.BeforeTest;

import static io.helidon.microprofile.testing.testng.HelidonTestNgListener.CONTAINER;

/**
 * TestNG Object factory.
 */
public class HelidonTestNgObjectFactory implements ITestObjectFactory {

    @Override
    public <T> T newInstance(Class<T> cls, Object... parameters) {
        // Use a proxy to start the container after the test instance creation
        // Make @BeforeTest a no-op when used on instance
        return ProxyHelper.proxyDelegate(cls, List.of(BeforeTest.class), (type, method) -> {
            HelidonTestContainer container = CONTAINER.get();
            if (container == null) {
                throw new IllegalStateException("Container not set");
            }
            return container.resolveInstance(type);
        });
    }

    @Override
    public <T> T newInstance(Constructor<T> constructor, Object... parameters) {
        return newInstance(constructor.getDeclaringClass(), parameters);
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
