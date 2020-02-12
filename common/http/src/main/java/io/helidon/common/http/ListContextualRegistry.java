/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.context.Context;

/**
 * A {@link ContextualRegistry} implementation with deque registry.
 */
class ListContextualRegistry implements ContextualRegistry {
    private final Context delegate;

    ListContextualRegistry(ContextualRegistry.Builder builder) {
        String configuredId = builder.id();
        Context parent = builder.parent();

        Context.Builder delegateBuilder = Context.builder();

        if (null != parent) {
            if (parent instanceof ListContextualRegistry) {
                delegateBuilder.parent(((ListContextualRegistry) parent).delegate);
            } else {
                delegateBuilder.parent(parent);
            }
        }

        if (null != configuredId) {
            delegateBuilder.id(configuredId);
        }

        this.delegate = delegateBuilder.build();
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public <T> void register(T instance) {
        delegate.register(instance);
    }

    @Override
    public <T> void supply(Class<T> type, Supplier<T> supplier) {
        delegate.supply(type, supplier);
    }

    @Override
    public <T> Optional<T> get(Class<T> type) {
        return delegate.get(type);
    }

    @Override
    public <T> void register(Object classifier, T instance) {
        delegate.register(classifier, instance);
    }

    @Override
    public <T> void supply(Object classifier, Class<T> type, Supplier<T> supplier) {
        delegate.supply(classifier, type, supplier);
    }

    @Override
    public <T> Optional<T> get(Object classifier, Class<T> type) {
        return delegate.get(classifier, type);
    }
}
