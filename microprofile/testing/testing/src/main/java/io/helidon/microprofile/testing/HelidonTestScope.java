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
package io.helidon.microprofile.testing;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;

/**
 * CDI context that supports {@link HelidonTestScoped}.
 */
public abstract sealed class HelidonTestScope implements Context permits HelidonTestScope.PerThread,
                                                                         HelidonTestScope.PerContainer {

    /**
     * Create a new per-thread scope.
     *
     * @return HelidonTestScope
     */
    public static HelidonTestScope ofThread() {
        return new PerThread();
    }

    /**
     * Create a new per-container scope.
     *
     * @return HelidonTestScope
     */
    public static HelidonTestScope ofContainer() {
        return new PerContainer();
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return HelidonTestScoped.class;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> context) {
        return instances().get(contextual, context);
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        return instances().get(contextual).orElse(null);
    }

    /**
     * Close the scope.
     */
    public void close() {
        instances().destroy();
    }

    /**
     * Get the instances.
     *
     * @return instances
     */
    abstract Instances instances();

    /**
     * Instances per thread.
     */
    static final class PerThread extends HelidonTestScope {
        private static final ThreadLocal<Instances> THREAD_LOCAL = ThreadLocal.withInitial(Instances::new);

        @Override
        Instances instances() {
            return THREAD_LOCAL.get();
        }

        @Override
        public void close() {
            THREAD_LOCAL.remove();
            super.close();
        }
    }

    /**
     * Instances per container.
     */
    static final class PerContainer extends HelidonTestScope {
        private final Instances instances = new Instances(new ConcurrentHashMap<>());

        @Override
        Instances instances() {
            return instances;
        }
    }

    private record Instances(Map<Contextual<?>, Instance<?>> map) {

        Instances() {
            this(new HashMap<>());
        }

        @SuppressWarnings("unchecked")
        <T> Instance<T> create(Contextual<T> contextual, CreationalContext<T> context) {
            return (Instance<T>) map.computeIfAbsent(contextual, k -> new Instance<>((Bean<T>) k, context));
        }

        <T> T get(Contextual<T> contextual, CreationalContext<T> context) {
            return get(contextual).orElseGet(() -> create(contextual, context).it);
        }

        @SuppressWarnings("unchecked")
        <T> Optional<T> get(Contextual<T> contextual) {
            return Optional.ofNullable((Instance<T>) map.get(contextual)).map(Instance::it);
        }

        void destroy() {
            map.values().forEach(Instance::destroy);
        }
    }

    private record Instance<T>(Bean<T> bean, CreationalContext<T> context, T it) {
        Instance(Bean<T> bean, CreationalContext<T> context) {
            this(bean, context, bean.create(context));
        }

        void destroy() {
            bean.destroy(it, context);
        }
    }
}
