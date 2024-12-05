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
package io.helidon.microprofile.testing;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Descriptor delegate.
 *
 * @param <T> element type
 */
class HelidonTestDescriptorDelegate<T extends AnnotatedElement> implements HelidonTestDescriptor<T> {

    private final HelidonTestDescriptor<T> delegate;

    HelidonTestDescriptorDelegate(HelidonTestDescriptor<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T element() {
        return delegate.element();
    }

    @Override
    public boolean resetPerTest() {
        return delegate.resetPerTest();
    }

    @Override
    public List<AddExtension> addExtensions() {
        return delegate.addExtensions();
    }

    @Override
    public List<AddBean> addBeans() {
        return delegate.addBeans();
    }

    @Override
    public boolean addJaxRs() {
        return delegate.addJaxRs();
    }

    @Override
    public boolean disableDiscovery() {
        return delegate.disableDiscovery();
    }

    @Override
    public Optional<Configuration> configuration() {
        return delegate.configuration();
    }

    @Override
    public List<AddConfig> addConfigs() {
        return delegate.addConfigs();
    }

    @Override
    public List<AddConfigBlock> addConfigBlocks() {
        return delegate.addConfigBlocks();
    }

    @Override
    public List<Method> addConfigSources() {
        return delegate.addConfigSources();
    }
}
