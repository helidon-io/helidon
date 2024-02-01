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
            registry = null;
        } finally {
            lock.unlock();
        }
    }

}
