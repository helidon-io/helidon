/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

/**
 * Integration with microprofile config.
 * This class is an implementation of a java service obtained through ServiceLoader.
 */
public class MpConfigProviderResolver extends ConfigProviderResolver {
    private static final Map<ClassLoader, Config> CONFIGS = new IdentityHashMap<>();
    private static final ReadWriteLock RW_LOCK = new ReentrantReadWriteLock();

    @Override
    public Config getConfig() {
        return getConfig(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public Config getConfig(ClassLoader loader) {
        if (null == loader) {
            loader = ClassLoader.getSystemClassLoader();
        }
        Lock lock = RW_LOCK.readLock();
        try {
            lock.lock();
            Config config = CONFIGS.get(loader);

            if (null == config) {
                lock.unlock();
                lock = RW_LOCK.writeLock();
                lock.lock();
                Config c = buildConfig(loader);
                doRegisterConfig(c, loader);
                return c;
            } else {
                return config;
            }
        } finally {
            lock.unlock();
        }
    }

    private Config buildConfig(ClassLoader loader) {
        return getBuilder().forClassLoader(loader)
                .addDefaultSources()
                .addDiscoveredSources()
                .addDiscoveredConverters()
                .build();
    }

    @Override
    public ConfigBuilder getBuilder() {
        return new MpConfigBuilder();
    }

    @Override
    public void registerConfig(Config config, ClassLoader classLoader) {
        Lock lock = RW_LOCK.writeLock();
        try {
            lock.lock();
            doRegisterConfig(config, classLoader);
        } finally {
            lock.unlock();
        }
    }

    private void doRegisterConfig(Config config, ClassLoader classLoader) {
        CONFIGS.put(classLoader, config);
    }

    @Override
    public void releaseConfig(Config config) {
        // first attempt to find it
        Lock lock = RW_LOCK.readLock();
        AtomicReference<ClassLoader> cl = new AtomicReference<>();

        try {
            lock.lock();
            for (Map.Entry<ClassLoader, Config> entry : CONFIGS.entrySet()) {
                Config configFromRef = entry.getValue();
                if (config == configFromRef) {
                    cl.set(entry.getKey());
                    break;
                }
            }
        } finally {
            lock.unlock();
        }

        // if found, remove it
        if (cl.get() != null) {
            lock = RW_LOCK.writeLock();
            try {
                lock.lock();
                CONFIGS.remove(cl.get());
            } finally {
                lock.unlock();
            }
        }
    }
}
