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

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import org.testng.IModuleFactory;
import org.testng.ITestContext;

/**
 * A Guice module factory implementation that instantiates instrumented classes.
 */
public class HelidonTestNgModuleFactory implements IModuleFactory {
    @Override
    public Module createModule(ITestContext context, Class<?> testClass) {
        return new ModuleImpl<>(testClass);
    }

    private static class ModuleImpl<T> extends AbstractModule {
        private final Class<T> testClass;

        ModuleImpl(Class<T> testClass) {
            this.testClass = testClass;
        }

        @Override
        protected void configure() {
            bind(testClass).toProvider(() -> ProxyHelper.allocateInstance(testClass));
        }
    }
}
