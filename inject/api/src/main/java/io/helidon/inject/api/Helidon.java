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

package io.helidon.inject.api;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.logging.common.LogConfig;

/**
 * Entry point to service registry based Helidon applications.
 *
 * @see #start()
 * @see #serviceRegistry()
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class Helidon {
    private static final System.Logger LOGGER = System.getLogger(Helidon.class.getName());
    private static final ReentrantReadWriteLock REENTRANT_READ_WRITE_LOCK = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock READ_LOCK = REENTRANT_READ_WRITE_LOCK.readLock();
    private static final ReentrantReadWriteLock.WriteLock WRITE_LOCK = REENTRANT_READ_WRITE_LOCK.writeLock();
    private static final AtomicBoolean BASIC_INIT_DONE = new AtomicBoolean();
    private static final AtomicBoolean REGISTRY_INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    static {
        LogConfig.initClass();
        ResettableHandler.registerReset();
    }

    private Helidon() {
    }

    /**
     * Initialize Helidon Injection service registry. In case the intent is to also start services, such as WebServer,
     * see {@link #start()}.
     *
     * @return service registry
     */
    public static Services serviceRegistry() {
        if (REGISTRY_INITIALIZED.compareAndSet(false, true)) {
            basicInit();
            registryInit(false);
        }
        try {
            READ_LOCK.lock();
            return InjectionServices.realizedServices();
        } finally {
            READ_LOCK.unlock();
        }
    }

    /**
     * Initialize Helidon Injection service registry, and start all startable services.
     */
    public static void start() {
        if (STARTED.compareAndSet(false, true)) {
            basicInit();
            registryInit(true);
        } else {
            LOGGER.log(System.Logger.Level.WARNING, "Helidon.start() has already been called.");
        }
    }

    private static boolean reset(boolean deep) {
        // for testing purposes
        BASIC_INIT_DONE.set(false);
        REGISTRY_INITIALIZED.set(false);
        STARTED.set(false);

        return true;
    }

    private static void registryInit(boolean bootServices) {
        try {
            WRITE_LOCK.lock();

            boolean explicitConfig = GlobalConfig.configured();
            Config bootstrapConfig = GlobalConfig.config();

            Bootstrap bootstrap = Bootstrap.builder()
                    .config(bootstrapConfig)
                    .build();

            InjectionServices.globalBootstrap(bootstrap);
            Services services = InjectionServices.realizedServices();

            if (!explicitConfig) {
                GlobalConfig.config(() -> services.lookup(Config.class).get(), true);
            }

            if (bootServices) {
                services.lookupAll(Startable.class)
                        .stream()
                        .map(ServiceProvider::get)
                        .forEach(Startable::startService);
            }
        } finally {
            WRITE_LOCK.unlock();
        }
    }

    private static void basicInit() {
        if (BASIC_INIT_DONE.compareAndSet(false, true)) {
            LogConfig.configureRuntime();
        }
    }

    private static final class ResettableHandler extends InjectionServicesHolder {
        @Deprecated
        private ResettableHandler() {
        }

        private static void registerReset() {
            InjectionServicesHolder.addResettable(Helidon::reset);
        }
    }
}
