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

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.TypeName;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.ServiceDescriptor;

class InjectionServicesImpl implements InjectionServices {
    private static final ReadWriteLock INSTANCE_LOCK = new ReentrantReadWriteLock();
    private static final AtomicReference<InjectionConfig> CONFIG = new AtomicReference<>();

    private static volatile InjectionServicesImpl instance;

    private final State state = State.create(Phase.INIT);
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final InjectionConfig config;

    private volatile Services services;

    private InjectionServicesImpl(InjectionConfig config) {
        this.config = config;
    }

    static InjectionServices create(InjectionConfig injectionConfig) {
        return new InjectionServicesImpl(injectionConfig);
    }

    static InjectionServices instance() {
        Lock lock = INSTANCE_LOCK.readLock();
        try {
            lock.lock();
            if (instance != null) {
                return instance;
            }
        } finally {
            lock.unlock();
        }
        lock = INSTANCE_LOCK.writeLock();
        try {
            lock.lock();
            InjectionConfig config = CONFIG.get();
            if (config == null) {
                config = InjectionConfig.create(GlobalConfig.config().get("inject"));
            }
            InjectionServicesImpl newInstance = new InjectionServicesImpl(config);

            InjectionServicesImpl.instance = newInstance;

            return newInstance;
        } finally {
            lock.unlock();
        }
    }

    static void configure(InjectionConfig config) {
        CONFIG.set(config);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Services services() {
        Lock readLock = lifecycleLock.readLock();
        try {
            readLock.lock();
            if (services != null) {
                return services;
            }
        } finally {
            readLock.unlock();
        }

        Lock writeLock = lifecycleLock.writeLock();
        try {
            writeLock.lock();
            if (services != null) {
                return services;
            }
            state.currentPhase(Phase.ACTIVATION_STARTING);
            services = new Services(this, state);
            services.bindInstance(Services__ServiceDescriptor.INSTANCE, services);

            for (ServiceDescriptor<?> serviceDescriptor : config.serviceDescriptors()) {
                services.bind(serviceDescriptor);
            }

            if (config.useModules()) {
                List<ModuleComponent> modules = findModules();
                modules.forEach(services::bind);
            }

            state.currentPhase(Phase.GATHERING_DEPENDENCIES);
            if (config.useApplication()) {
                List<Application> apps = findApplications();
                apps.forEach(services::bind);
            }


            state.currentPhase(Phase.POST_BIND_ALL_MODULES);
            services.postBindAllModules();

            state.currentPhase(Phase.FINAL_RESOLVE);

            state.currentPhase(Phase.SERVICES_READY);

            state.finished(true);

            return services;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public InjectionConfig config() {
        return config;
    }

    @Override
    public Map<TypeName, ActivationResult> shutdown() {
        Lock lock = lifecycleLock.writeLock();
        try {
            lock.lock();
            if (services == null) {
                return Map.of();
            }
            State currentState = state.clone().currentPhase(Phase.PRE_DESTROYING);
            return doShutdown(services, currentState);
        } finally {
            state.reset();
            services = null;
            lock.unlock();
        }
    }

    private static Map<TypeName, ActivationResult> doShutdown(Services services, State state) {
        state.currentPhase(Phase.DESTROYED);

        return services.close();
    }

    private List<Application> findApplications() {
        return HelidonServiceLoader.create(ServiceLoader.load(Application.class))
                .asList();
    }

    private List<ModuleComponent> findModules() {
        return HelidonServiceLoader.create(ServiceLoader.load(ModuleComponent.class))
                .asList();
    }
}
