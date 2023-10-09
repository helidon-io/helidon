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

package io.helidon.webserver.spi;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import io.helidon.webserver.TlsManager;

class TlsManagerCache {
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Map<Object, TlsManager> CACHE = new HashMap<>();

    private TlsManagerCache() {
    }

    static <T> TlsManager getOrCreate(T configBean,
                                      Function<T, TlsManager> creator) {
        Objects.requireNonNull(configBean);
        Objects.requireNonNull(creator);
        LOCK.lock();
        try {
            TlsManager manager = CACHE.get(configBean);
            if (manager != null) {
                return manager;
            }

            manager = creator.apply(configBean);
            Object existing = CACHE.put(configBean, manager);
            assert (existing == null);

            return manager;
        } finally {
            LOCK.unlock();
        }
    }

}
