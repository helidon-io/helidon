/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.ServiceDescriptor;

interface ServiceInstance<T> extends Supplier<T> {
    static <T> ServiceInstance<T> create(InterceptionMetadata interceptionMetadata,
                                         InjectionContext ctx,
                                         ServiceDescriptor<T> source) {
        if (source.scopes().contains(Injection.Singleton.TYPE_NAME)) {
            return new SingletonInstance<>(ctx, interceptionMetadata, source);
        }
        if (source.scopes().contains(InjectTypes.REQUESTON)) {
            return new RequestonInstance(ctx, interceptionMetadata, source);
        }
        return new OnDemandInstance<>(ctx, interceptionMetadata, source);
    }

    static <T> ServiceInstance<T> create(ServiceDescriptor<T> source, T instance) {
        return new ExplicitInstance<>(source, instance);
    }

    default void construct() {
    }

    default void inject() {
    }

    default void postConstruct() {
    }

    default void preDestroy() {

    }

    private static <T> T inject(ServiceDescriptor<T> source,
                                InjectionContext ctx,
                                InterceptionMetadata interceptionMetadata,
                                T instance) {

        // using linked set, so we can see in debugging what was injected first
        Set<String> injected = new LinkedHashSet<>();
        source.inject(ctx, interceptionMetadata, injected, instance);
        return instance;
    }

    class ExplicitInstance<T> implements ServiceInstance<T> {
        private final ServiceDescriptor<T> source;
        private final T instance;

        ExplicitInstance(ServiceDescriptor<T> source, T instance) {
            this.source = source;
            this.instance = instance;
        }

        @Override
        public T get() {
            return instance;
        }

        @Override
        public void preDestroy() {
            source.preDestroy(instance);
        }
    }

    class SingletonInstance<T> implements ServiceInstance<T> {
        private final InjectionContext ctx;
        private final InterceptionMetadata interceptionMetadata;
        private final ServiceDescriptor<T> source;

        private volatile T instance;

        private SingletonInstance(InjectionContext ctx, InterceptionMetadata interceptionMetadata, ServiceDescriptor<T> source) {
            this.ctx = ctx;
            this.interceptionMetadata = interceptionMetadata;
            this.source = source;
        }

        @Override
        public T get() {
            return instance;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void construct() {
            instance = (T) source.instantiate(ctx, interceptionMetadata);
        }

        @Override
        public void inject() {
            ServiceInstance.inject(source, ctx, interceptionMetadata, instance);
        }

        @Override
        public void postConstruct() {
            source.postConstruct(instance);
        }

        @Override
        public void preDestroy() {
            source.preDestroy(instance);
        }
    }

    class OnDemandInstance<T> implements ServiceInstance<T> {
        private final InjectionContext ctx;
        private final InterceptionMetadata interceptionMetadata;
        private final ServiceDescriptor<T> source;

        OnDemandInstance(InjectionContext ctx, InterceptionMetadata interceptionMetadata, ServiceDescriptor<T> source) {
            this.ctx = ctx;
            this.interceptionMetadata = interceptionMetadata;
            this.source = source;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get() {
            T instance = (T) source.instantiate(ctx, interceptionMetadata);
            return ServiceInstance.inject(source, ctx, interceptionMetadata, instance);
        }
    }
}
