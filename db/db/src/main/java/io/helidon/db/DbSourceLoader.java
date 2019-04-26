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
package io.helidon.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.db.spi.DbProvider;

/**
 * Loads database providers from Java Service loader.
 */
final class DbSourceLoader {
    private static final Map<String, DbProvider> DB_SOURCES = new HashMap<>();
    private static final String[] NAMES;
    private static final DbProvider FIRST;

    static {
        HelidonServiceLoader<DbProvider> serviceLoader = HelidonServiceLoader.builder(ServiceLoader.load(DbProvider.class))
                .build();

        List<DbProvider> sources = serviceLoader.asList();

        DbProvider first = null;

        if (!sources.isEmpty()) {
            first = sources.get(0);
        }

        FIRST = first;
        sources.forEach(dbProvider -> DB_SOURCES.put(dbProvider.name(), dbProvider));
        NAMES = sources.stream()
                .map(DbProvider::name)
                .toArray(String[]::new);
    }

    private DbSourceLoader() {
    }

    static DbProvider first() {
        return FIRST;
    }

    static Optional<DbProvider> get(String name) {
        return Optional.ofNullable(DB_SOURCES.get(name));
    }

    static String[] names() {
        return NAMES;
    }
}
