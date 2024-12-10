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

package io.helidon.common.mapper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.service.registry.Services;

// this class should be removed when deprecation is resolved
@SuppressWarnings("removal")
final class GlobalManager {
    private static final System.Logger LOGGER = System.getLogger(GlobalManager.class.getName());
    private static final AtomicBoolean LOGGED_REGISTERED = new AtomicBoolean(false);

    private static final AtomicReference<MapperManager> MANAGER = new AtomicReference<>();

    private GlobalManager() {
    }

    static void mapperManager(MapperManager manager) {
        MANAGER.set(manager);

        try {
            Services.set(Mappers.class, manager);
        } catch (Exception e) {
            if (LOGGED_REGISTERED.compareAndSet(false, true)) {
                // only log this once
                LOGGER.log(System.Logger.Level.WARNING,
                           "Attempting to set a Mappers (MapperManager) instance when it either was already "
                                   + "set once, or it was already used by a component."
                                   + " This will not work in future versions of"
                                   + " Helidon",
                           e);
            }
        }
    }

    static MapperManager mapperManager() {
        MapperManager mapperManager = MANAGER.get();
        return mapperManager == null ? Services.get(MapperManager.class) : mapperManager;
    }
}
