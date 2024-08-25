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

package io.helidon.common.mapper;

import io.helidon.common.LazyValue;

@SuppressWarnings("removal")
final class GlobalManager {
    private static final LazyValue<MapperManager> DEFAULT_MAPPER = LazyValue.create(() -> MapperManager.builder()
            .useBuiltIn(true)
            .build());

    private GlobalManager() {
    }

    static void mapperManager(MapperManager manager) {
        io.helidon.common.GlobalInstances.set(MapperManagerHolder.class, new MapperManagerHolder(manager));
    }

    static MapperManager mapperManager() {
        return io.helidon.common.GlobalInstances.current(MapperManagerHolder.class)
                .map(MapperManagerHolder::manager)
                .orElseGet(DEFAULT_MAPPER);
    }

    private record MapperManagerHolder(MapperManager manager) implements io.helidon.common.GlobalInstances.GlobalInstance {
        @Override
        public void close() {
        }
    }
}
