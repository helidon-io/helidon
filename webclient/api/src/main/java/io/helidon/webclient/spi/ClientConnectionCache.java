/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.LazyValue;
import io.helidon.webclient.api.ReleasableResource;

import static java.lang.System.Logger.Level;

/**
 * Client connection cache with release shutdown hook to provide graceful shutdown.
 */
public abstract class ClientConnectionCache implements ReleasableResource {

    private static final System.Logger LOGGER = System.getLogger(ClientConnectionCache.class.getName());
    private static final ReentrantLock UNRELEASED_CACHES_LOCK = new ReentrantLock();
    private static final LazyValue<List<ClientConnectionCache>> UNRELEASED_CACHES = LazyValue.create(() -> {
        Runtime.getRuntime().addShutdownHook(new Thread(ClientConnectionCache::onShutdown));
        return new ArrayList<>();
    });

    protected ClientConnectionCache() {
        UNRELEASED_CACHES_LOCK.lock();
        try {
            UNRELEASED_CACHES.get().add(this);
        } finally {
            UNRELEASED_CACHES_LOCK.unlock();
        }
    }

    protected void removeReleaseShutdownHook() {
        UNRELEASED_CACHES_LOCK.lock();
        try {
            UNRELEASED_CACHES.get().remove(this);
        } finally {
            UNRELEASED_CACHES_LOCK.unlock();
        }
    }

    private static void onShutdown() {
        UNRELEASED_CACHES_LOCK.lock();
        try {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Gracefully closing connections in "
                        + UNRELEASED_CACHES.get().size()
                        + " client connection caches.");
            }
            List.copyOf(UNRELEASED_CACHES.get()).forEach(ReleasableResource::releaseResource);
        } finally {
            UNRELEASED_CACHES_LOCK.unlock();
        }
    }
}
