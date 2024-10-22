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

package io.helidon.service.registry;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class CoreServiceRegistryManager implements ServiceRegistryManager {
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final ServiceRegistryConfig config;
    private final ServiceDiscovery discovery;

    private CoreServiceRegistry registry;

    CoreServiceRegistryManager(ServiceRegistryConfig config, ServiceDiscovery discovery) {
        this.config = config;
        this.discovery = discovery;
    }

    @Override
    public ServiceRegistry registry() {
        Lock readLock = lifecycleLock.readLock();
        try {
            readLock.lock();
            if (registry != null) {
                return registry;
            }
        } finally {
            readLock.unlock();
        }

        Lock writeLock = lifecycleLock.writeLock();
        try {
            writeLock.lock();
            if (registry != null) {
                return registry;
            }

            registry = new CoreServiceRegistry(config, discovery);

            return registry;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void shutdown() {
        Lock lock = lifecycleLock.writeLock();
        try {
            lock.lock();
            registry.shutdown();
            registry = null;
        } finally {
            lock.unlock();
        }
    }

}
