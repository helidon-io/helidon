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
package io.helidon.dbclient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.dbclient.spi.DbClientProvider;

/**
 * Loads database client providers from Java Service loader.
 */
final class DbClientProviderLoader {
    private static final Map<String, DbClientProvider> DB_SOURCES = new HashMap<>();
    private static final String[] NAMES;
    private static final DbClientProvider FIRST;

    static {
        HelidonServiceLoader<DbClientProvider> serviceLoader = HelidonServiceLoader
                .builder(ServiceLoader.load(DbClientProvider.class))
                .build();

        List<DbClientProvider> sources = serviceLoader.asList();

        DbClientProvider first = null;

        if (!sources.isEmpty()) {
            first = sources.get(0);
        }

        FIRST = first;
        sources.forEach(dbProvider -> DB_SOURCES.put(dbProvider.name(), dbProvider));
        NAMES = sources.stream()
                .map(DbClientProvider::name)
                .toArray(String[]::new);
    }

    private DbClientProviderLoader() {
    }

    static DbClientProvider first() {
        return FIRST;
    }

    static Optional<DbClientProvider> get(String name) {
        return Optional.ofNullable(DB_SOURCES.get(name));
    }

    static String[] names() {
        return NAMES;
    }
}
